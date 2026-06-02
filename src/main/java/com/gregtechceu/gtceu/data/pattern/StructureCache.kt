package com.gregtechceu.gtceu.data.pattern

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition
import com.gregtechceu.gtceu.api.multiblock.BlockPattern
import com.gregtechceu.gtceu.utils.dev.ResourceReloadDetector

import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.ModList

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.github.luben.zstd.ZstdInputStream

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.Comparator
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import kotlin.concurrent.Volatile

object StructureCache {
	@Volatile
	private var futureCache: CompletableFuture<StructureCaches>? = null

	@Volatile
	private var patternResourceIndex: PatternResourceIndex? = null

	private val LOAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor()
	private val CBOR_MAPPER = ObjectMapper(CBORFactory())
	private val JSON_MAPPER = ObjectMapper()
	private val reloadInProgress = AtomicBoolean(false)
	private val cacheStateLock = Any()
	private val cacheLoadLock = Any()

	private data class StructureCaches(
		val binaryDefinitions: Map<ResourceLocation, BlockPattern>,
		val jsonDefinitions: Map<ResourceLocation, StructurePatternResolver.StringArrayDefinition>,
		val binaryPatterns: ConcurrentHashMap<ResourceLocation, BlockPattern> = ConcurrentHashMap(),
		val jsonPatterns: ConcurrentHashMap<ResourceLocation, BlockPattern> = ConcurrentHashMap(),
	)

	private data class PatternSource(val description: String, val root: Path)

	private data class PatternResource(val sourceDescription: String, val sourceFile: Path)

	private data class PatternResourceKey(val type: StructureDefinitionType, val id: ResourceLocation)

	private data class PatternResourceIndex(val entries: Map<PatternResourceKey, PatternResource>)

	private enum class CacheSection {
		BINARY,
		JSON,
		;

		fun existingSource(id: ResourceLocation): String = when (this) {
			BINARY -> "existing binary cache entry for $id"
			JSON -> "existing json cache entry for $id"
		}
	}

	@JvmStatic
	fun loadAsync() {
		GTCEu.LOGGER.info("Loading pattern...")
		val loadingFuture = startInitialLoad() ?: return
		loadingFuture.whenComplete { caches, throwable ->
			try {
				if (throwable != null) {
					clearFailedCache(loadingFuture)
					GTCEu.LOGGER.error("Failed to load pattern cache", unwrapReloadException(throwable))
				} else {
					GTCEu.LOGGER.info("Loaded binary patterns: ${caches.binaryDefinitions.size}")
					GTCEu.LOGGER.info("Loaded json patterns: ${caches.jsonDefinitions.size}")
				}
			} finally {
				reloadInProgress.set(false)
			}
		}
	}

	private fun startInitialLoad(): CompletableFuture<StructureCaches>? = synchronized(cacheStateLock) {
		if (futureCache != null || !reloadInProgress.compareAndSet(false, true)) {
			null
		} else {
			CompletableFuture.supplyAsync({ loadCachesLocked() }, LOAD_EXECUTOR).also { futureCache = it }
		}
	}

	@JvmStatic
	@Throws(IOException::class)
	fun reloadAll(): Int = runReloadTask {
		runOnVirtualThread {
			val caches = loadAndPublishCaches()
			caches.binaryDefinitions.size + caches.jsonDefinitions.size
		}
	}

	@JvmStatic
	@Throws(IOException::class)
	fun reloadType(type: StructureDefinitionType): Int = runReloadTask {
		runOnVirtualThread {
			val root = patternRoot()
			val current = requireCaches()
			publishPatternResourceIndex(syncPatternResourcesToDisk(root))
			val binaryMap = HashMap(current.binaryDefinitions)
			val jsonMap = HashMap(current.jsonDefinitions)
			when (type) {
				StructureDefinitionType.SERIALIZED_BLOCK_PATTERN -> {
					binaryMap.clear()
					loadTypeFromFileSystem(
						root,
						type,
						binaryMap,
						createClaimedSources(jsonMap, CacheSection.JSON),
						::readBinaryStructureDefinition,
					)
				}

				StructureDefinitionType.STRING_ARRAY_JSON -> {
					jsonMap.clear()
					loadTypeFromFileSystem(
						root,
						type,
						jsonMap,
						createClaimedSources(binaryMap, CacheSection.BINARY),
						::readJsonStructureDefinition,
					)
				}
			}
			val caches = when (type) {
				StructureDefinitionType.SERIALIZED_BLOCK_PATTERN -> freezeCaches(binaryMap, jsonMap, ConcurrentHashMap(), current.jsonPatterns)
				StructureDefinitionType.STRING_ARRAY_JSON -> freezeCaches(binaryMap, jsonMap, current.binaryPatterns, ConcurrentHashMap())
			}
			publishCaches(caches)
			when (type) {
				StructureDefinitionType.SERIALIZED_BLOCK_PATTERN -> caches.binaryDefinitions.size
				StructureDefinitionType.STRING_ARRAY_JSON -> caches.jsonDefinitions.size
			}
		}
	}

	@JvmStatic
	@Throws(IOException::class)
	fun reload(type: StructureDefinitionType, id: ResourceLocation): Boolean = runReloadTask {
		runOnVirtualThread {
			val root = patternRoot()
			val current = requireCaches()
			check(syncPatternResourceToDisk(root, type, id)) {
				"Structure definition file not found for '$id' in loaded mod pattern resources"
			}
			when (type) {
				StructureDefinitionType.SERIALIZED_BLOCK_PATTERN -> {
					check(id !in current.jsonDefinitions) {
						"Duplicate structure id '$id' found while loading existing json cache entry for $id"
					}
					val binaryMap = HashMap(current.binaryDefinitions)
					binaryMap.remove(id)
					reloadSingleEntry(
						root,
						type,
						id,
						binaryMap,
						createSingleClaimedSource(id, current.jsonDefinitions, CacheSection.JSON),
						::readBinaryStructureDefinition,
					)
					check(id in binaryMap) {
						"Reloaded structure id '$id' was not produced from ${type.directoryName} definition"
					}
					val binaryPatterns = ConcurrentHashMap(current.binaryPatterns)
					binaryPatterns.remove(id)
					publishCaches(freezeCaches(binaryMap, current.jsonDefinitions, binaryPatterns, current.jsonPatterns))
				}

				StructureDefinitionType.STRING_ARRAY_JSON -> {
					check(id !in current.binaryDefinitions) {
						"Duplicate structure id '$id' found while loading existing binary cache entry for $id"
					}
					val jsonMap = HashMap(current.jsonDefinitions)
					jsonMap.remove(id)
					reloadSingleEntry(
						root,
						type,
						id,
						jsonMap,
						createSingleClaimedSource(id, current.binaryDefinitions, CacheSection.BINARY),
						::readJsonStructureDefinition,
					)
					check(id in jsonMap) {
						"Reloaded structure id '$id' was not produced from ${type.directoryName} definition"
					}
					val jsonPatterns = ConcurrentHashMap(current.jsonPatterns)
					jsonPatterns.remove(id)
					publishCaches(freezeCaches(current.binaryDefinitions, jsonMap, current.binaryPatterns, jsonPatterns))
				}
			}
			true
		}
	}

	@JvmStatic
	fun resolvePattern(id: ResourceLocation, definition: MultiblockMachineDefinition, javaPattern: BlockPattern): BlockPattern {
		val caches = requireCaches()
		caches.binaryDefinitions[id]?.let { binaryDefinition ->
			return caches.binaryPatterns.computeIfAbsent(id) {
				binaryDefinition
			}.also { pattern ->
				pattern.condition = javaPattern.condition
			}
		}

		caches.jsonDefinitions[id]?.let { jsonDefinition ->
			return caches.jsonPatterns.computeIfAbsent(id) {
				StructurePatternResolver.rebuildStringArrayPattern(
					definition,
					id,
					javaPattern,
					jsonDefinition,
				)
			}.also { pattern ->
				pattern.condition = javaPattern.condition
			}
		}

		return javaPattern
	}

	@JvmStatic
	fun getActiveSource(id: ResourceLocation): StructureDefinitionSource {
		val caches = requireCaches()
		if (id in caches.binaryDefinitions) return StructureDefinitionSource.BINARY_JSON
		if (id in caches.jsonDefinitions) return StructureDefinitionSource.JSON
		return StructureDefinitionSource.JAVA
	}

	@JvmStatic
	fun getBinaryCacheSize(): Int = requireCaches().binaryDefinitions.size

	@JvmStatic
	fun getStringArrayPattern(id: ResourceLocation): StructurePatternResolver.StringArrayDefinition? {
		val f: CompletableFuture<StructureCaches>? = futureCache
		return f!!.join().jsonDefinitions[id]
	}

	@JvmStatic
	fun getJsonCacheSize(): Int = requireCaches().jsonDefinitions.size

	private fun patternRoot(): Path = GTCEu.GTCEU_FOLDER.resolve("pattern")

	private fun requireCaches(): StructureCaches {
		val currentFuture = synchronized(cacheStateLock) {
			futureCache ?: CompletableFuture.supplyAsync({
				loadCachesLocked()
			}, LOAD_EXECUTOR).also {
				futureCache = it
			}
		}
		return joinCacheFuture(currentFuture)
	}

	private fun loadCaches(): StructureCaches {
		val root = patternRoot()
		publishPatternResourceIndex(syncPatternResourcesToDisk(root))
		val binaryMap = HashMap<ResourceLocation, BlockPattern>()
		val jsonMap = HashMap<ResourceLocation, StructurePatternResolver.StringArrayDefinition>()
		loadFromFileSystem(root, binaryMap, jsonMap)
		return freezeCaches(binaryMap, jsonMap)
	}

	private fun loadCachesLocked(): StructureCaches = synchronized(cacheLoadLock) {
		loadCaches()
	}

	private fun loadAndPublishCaches(): StructureCaches {
		val loadingFuture = CompletableFuture<StructureCaches>()
		val publishedLoadingFuture = synchronized(cacheStateLock) {
			(futureCache == null).also { shouldPublish ->
				if (shouldPublish) {
					futureCache = loadingFuture
				}
			}
		}
		try {
			val caches = loadCachesLocked()
			if (publishedLoadingFuture) {
				loadingFuture.complete(caches)
			}
			publishCaches(caches)
			return caches
		} catch (e: Throwable) {
			if (publishedLoadingFuture) {
				loadingFuture.completeExceptionally(e)
				clearFailedCache(loadingFuture)
			}
			throwReloadException(e)
		}
	}

	private fun freezeCaches(
		binaryMap: Map<ResourceLocation, BlockPattern>,
		jsonMap: Map<ResourceLocation, StructurePatternResolver.StringArrayDefinition>,
		binaryPatterns: ConcurrentHashMap<ResourceLocation, BlockPattern> = ConcurrentHashMap(),
		jsonPatterns: ConcurrentHashMap<ResourceLocation, BlockPattern> = ConcurrentHashMap(),
	): StructureCaches = StructureCaches(
		freezeMap(binaryMap),
		freezeMap(jsonMap),
		binaryPatterns.also { it.keys.retainAll(binaryMap.keys) },
		jsonPatterns.also { it.keys.retainAll(jsonMap.keys) },
	)

	@Suppress("UNCHECKED_CAST")
	private fun <K, V> freezeMap(map: Map<K, V>): Map<K, V> = when (map) {
		is HashMap<*, *> -> Collections.unmodifiableMap(map as HashMap<K, V>)
		else -> map
	}

	private fun publishCaches(caches: StructureCaches) {
		synchronized(cacheStateLock) {
			futureCache = CompletableFuture.completedFuture(caches)
		}
	}

	private fun publishPatternResourceIndex(index: PatternResourceIndex) {
		synchronized(cacheStateLock) {
			patternResourceIndex = index
		}
	}

	private fun clearFailedCache(failedFuture: CompletableFuture<StructureCaches>) {
		synchronized(cacheStateLock) {
			if (futureCache === failedFuture) {
				futureCache = null
				patternResourceIndex = null
			}
		}
	}

	private fun requirePatternResourceIndex(): PatternResourceIndex {
		patternResourceIndex?.let { return it }
		requireCaches()
		return checkNotNull(patternResourceIndex) {
			"Pattern resource index was not initialized"
		}
	}

	@Throws(IOException::class)
	private fun syncPatternResourcesToDisk(patternRoot: Path): PatternResourceIndex {
		val normalizedPatternRoot = normalizePatternRoot(patternRoot)
		Files.createDirectories(normalizedPatternRoot)
		val expectedPaths = HashSet<Path>()
		expectedPaths.add(normalizedPatternRoot)

		val index = copyPatternSources(collectPatternSources(), normalizedPatternRoot, expectedPaths)
		prunePatternDirectory(normalizedPatternRoot, expectedPaths)
		return index
	}

	@Throws(IOException::class)
	private fun syncPatternResourceToDisk(patternRoot: Path, type: StructureDefinitionType, id: ResourceLocation): Boolean {
		val normalizedPatternRoot = normalizePatternRoot(patternRoot)
		val target = patternFile(normalizedPatternRoot, type, id)
		val normalizedTypeDir = normalizedPatternRoot.resolve(id.namespace).resolve(type.directoryName).toAbsolutePath().normalize()
		check(target.startsWith(normalizedTypeDir)) {
			"Refusing to sync pattern resource outside $normalizedTypeDir: $target"
		}
		val resource = requirePatternResourceIndex().entries[PatternResourceKey(type, id)]
			?.takeIf { Files.isRegularFile(it.sourceFile) }
		if (resource == null) {
			Files.deleteIfExists(target)
			return false
		}

		if (Files.exists(target) && Files.isDirectory(target)) {
			deleteRecursively(target)
		}
		createParentDirectories(target)
		if (shouldCopyFile(resource.sourceFile, target)) {
			Files.copy(resource.sourceFile, target, StandardCopyOption.REPLACE_EXISTING)
		}
		return true
	}

	@Throws(IOException::class)
	private fun collectPatternSources(): List<PatternSource> = ModList.get()
		?.modFiles
		.orEmpty()
		.mapNotNull { modFileInfo ->
			val root = modFileInfo.file.findResource("pattern")
			root.takeIf(Files::isDirectory)?.let { PatternSource("mod:${modFileInfo.file.fileName}", it) }
		}

	@Throws(IOException::class)
	private fun copyPatternSources(sources: List<PatternSource>, targetRoot: Path, expectedPaths: MutableSet<Path>): PatternResourceIndex {
		val claimedTargets = HashMap<Path, String>()
		val index = HashMap<PatternResourceKey, PatternResource>()
		sources
			.map { it.copy(root = it.root.toAbsolutePath().normalize()) }
			.distinctBy(PatternSource::root)
			.forEach { source ->
				copyPatternTree(source, targetRoot, expectedPaths, claimedTargets, index)
			}
		return PatternResourceIndex(Collections.unmodifiableMap(index))
	}

	@Throws(IOException::class)
	private fun copyPatternTree(
		source: PatternSource,
		targetRoot: Path,
		expectedPaths: MutableSet<Path>,
		claimedTargets: MutableMap<Path, String>,
		index: MutableMap<PatternResourceKey, PatternResource>,
	) {
		val sourceRoot = source.root
		if (!Files.isDirectory(sourceRoot)) return

		Files.walk(sourceRoot).use { paths ->
			paths.forEach { path ->
				val relative = sourceRoot.relativize(path)
				val target = targetRoot.resolve(relative.toString()).toAbsolutePath().normalize()
				expectedPaths.add(target)
				if (Files.isDirectory(path)) {
					prepareTargetDirectory(target)
				} else {
					copyPatternFile(source, sourceRoot, path, relative, targetRoot, target, claimedTargets, index)
				}
			}
		}
	}

	@Throws(IOException::class)
	private fun prepareTargetDirectory(target: Path) {
		if (Files.exists(target) && !Files.isDirectory(target)) {
			Files.delete(target)
		}
		Files.createDirectories(target)
	}

	@Throws(IOException::class)
	private fun copyPatternFile(
		source: PatternSource,
		sourceRoot: Path,
		sourceFile: Path,
		relative: Path,
		targetRoot: Path,
		target: Path,
		claimedTargets: MutableMap<Path, String>,
		index: MutableMap<PatternResourceKey, PatternResource>,
	) {
		if (Files.exists(target) && Files.isDirectory(target)) {
			deleteRecursively(target)
		}
		createParentDirectories(target)
		claimPatternTarget(targetRoot, target, source.description, sourceFile, claimedTargets)
		indexPatternResource(source, sourceRoot, sourceFile, relative, index)
		if (shouldCopyFile(sourceFile, target)) {
			Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING)
		}
	}

	private fun claimPatternTarget(targetRoot: Path, target: Path, sourceDescription: String, sourcePath: Path, claimedTargets: MutableMap<Path, String>) {
		val source = "$sourceDescription:$sourcePath"
		val previousSource = claimedTargets.putIfAbsent(target, source)
		check(previousSource == null) {
			val relative = targetRoot.relativize(target).toString().replace('\\', '/')
			"Duplicate pattern resource target 'pattern/$relative' from $source; already provided by $previousSource"
		}
	}

	private fun indexPatternResource(source: PatternSource, sourceRoot: Path, sourceFile: Path, relative: Path, index: MutableMap<PatternResourceKey, PatternResource>) {
		if (relative.nameCount < 3) return

		val modid = relative.getName(0).toString()
		val type = StructureDefinitionType.fromDirectoryName(relative.getName(1).toString()) ?: return
		val relativeFile = relative.subpath(2, relative.nameCount).toString().replace('\\', '/')
		if (!type.matchesFileName(relativeFile)) return

		val id = ResourceLocation.fromNamespaceAndPath(modid, type.stripFileExtension(relativeFile))
		val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
		check(normalizedSourceFile.startsWith(sourceRoot)) {
			"Refusing to index pattern resource outside ${source.description}: $normalizedSourceFile"
		}
		val key = PatternResourceKey(type, id)
		val previous = index.putIfAbsent(key, PatternResource(source.description, normalizedSourceFile))
		check(previous == null) {
			"Duplicate structure resource '$id' found while indexing ${source.description}:$normalizedSourceFile; already provided by ${previous!!.sourceDescription}:${previous.sourceFile}"
		}
	}

	@Throws(IOException::class)
	private fun prunePatternDirectory(patternRoot: Path, expectedPaths: Set<Path>) {
		if (!Files.exists(patternRoot)) return
		Files.walk(patternRoot).use { paths ->
			paths.sorted(Comparator.reverseOrder()).forEach { path ->
				val normalizedPath = path.toAbsolutePath().normalize()
				if (normalizedPath !in expectedPaths) {
					Files.deleteIfExists(normalizedPath)
				}
			}
		}
	}

	private fun normalizePatternRoot(patternRoot: Path): Path {
		val normalizedPatternRoot = patternRoot.toAbsolutePath().normalize()
		val normalizedGtceuRoot = GTCEu.GTCEU_FOLDER.toAbsolutePath().normalize()
		check(normalizedPatternRoot.startsWith(normalizedGtceuRoot)) {
			"Refusing to clear pattern directory outside gtceu folder: $normalizedPatternRoot"
		}
		return normalizedPatternRoot
	}

	@Throws(IOException::class)
	private fun shouldCopyFile(source: Path, target: Path): Boolean {
		if (!Files.exists(target)) return true
		if (!Files.isRegularFile(target)) return true
		if (Files.size(source) != Files.size(target)) return true
		return Files.mismatch(source, target) != -1L
	}

	@Throws(IOException::class)
	private fun createParentDirectories(path: Path) {
		val parent = path.parent ?: return
		if (Files.exists(parent) && !Files.isDirectory(parent)) {
			Files.delete(parent)
		}
		Files.createDirectories(parent)
	}

	@Throws(IOException::class)
	private fun deleteRecursively(path: Path) {
		if (!Files.exists(path)) return
		Files.walk(path).use { paths ->
			paths.sorted(Comparator.reverseOrder()).forEach { nested ->
				Files.deleteIfExists(nested)
			}
		}
	}

	@Throws(IOException::class)
	private fun loadFromFileSystem(dataDir: Path, binaryMap: MutableMap<ResourceLocation, BlockPattern>, jsonMap: MutableMap<ResourceLocation, StructurePatternResolver.StringArrayDefinition>) {
		if (!Files.isDirectory(dataDir)) return

		val claimedSources = HashMap<ResourceLocation, String>()
		loadTypeFromFileSystem(
			dataDir,
			StructureDefinitionType.SERIALIZED_BLOCK_PATTERN,
			binaryMap,
			claimedSources,
			::readBinaryStructureDefinition,
		)
		loadTypeFromFileSystem(
			dataDir,
			StructureDefinitionType.STRING_ARRAY_JSON,
			jsonMap,
			claimedSources,
			::readJsonStructureDefinition,
		)
	}

	private fun <T> loadTypeFromFileSystem(
		dataDir: Path,
		type: StructureDefinitionType,
		map: MutableMap<ResourceLocation, T>,
		claimedSources: MutableMap<ResourceLocation, String>,
		reader: (Path) -> T,
	) {
		val loadTasks = ArrayList<CompletableFuture<Void>>()
		Files.list(dataDir).use { modDirs ->
			modDirs
				.filter { path: Path -> Files.isDirectory(path) }
				.forEach { modDir: Path ->
					enqueueStructureLoads(modDir, type, map, claimedSources, loadTasks, reader)
				}
		}
		CompletableFuture.allOf(*loadTasks.toTypedArray()).join()
	}

	private fun <T> enqueueStructureLoads(
		modDir: Path,
		type: StructureDefinitionType,
		map: MutableMap<ResourceLocation, T>,
		claimedSources: MutableMap<ResourceLocation, String>,
		loadTasks: MutableList<CompletableFuture<Void>>,
		reader: (Path) -> T,
	) {
		val typeDir = modDir.resolve(type.directoryName)
		if (!Files.isDirectory(typeDir)) return
		try {
			Files.walk(typeDir).use { files ->
				files.filter { file: Path -> Files.isRegularFile(file) && type.matchesFileName(file.fileName.toString()) }
					.forEach { file: Path ->
						loadTasks += CompletableFuture.runAsync({
							loadStructureFile(modDir, typeDir, type, file, map, claimedSources, reader)
						}, LOAD_EXECUTOR)
					}
			}
		} catch (e: IOException) {
			throw UncheckedIOException(e)
		}
	}

	private fun <T> loadStructureFile(
		modDir: Path,
		typeDir: Path,
		type: StructureDefinitionType,
		file: Path,
		map: MutableMap<ResourceLocation, T>,
		claimedSources: MutableMap<ResourceLocation, String>,
		reader: (Path) -> T,
	) {
		try {
			val def = reader(file)
			val modid = modDir.fileName.toString()
			val relative = typeDir.relativize(file).toString().replace('\\', '/')
			val name = type.stripFileExtension(relative)
			val structureId = ResourceLocation.fromNamespaceAndPath(modid, name)
			val sourcePath = "pattern/$modid/${type.directoryName}/${typeDir.relativize(file)}"
			synchronized(claimedSources) {
				val previousSource = claimedSources.putIfAbsent(structureId, sourcePath)
				check(previousSource == null) {
					"Duplicate structure id '$structureId' found while loading $sourcePath; already defined at $previousSource"
				}
			}
			synchronized(map) {
				val previous = map.put(structureId, def)
				check(previous == null) {
					"Duplicate structure id '$structureId' found while loading $sourcePath"
				}
			}
		} catch (e: IOException) {
			throw UncheckedIOException("Failed to load structure file: $file", e)
		}
	}

	private fun <T> reloadSingleEntry(
		dataDir: Path,
		type: StructureDefinitionType,
		id: ResourceLocation,
		map: MutableMap<ResourceLocation, T>,
		claimedSources: MutableMap<ResourceLocation, String>,
		reader: (Path) -> T,
	) {
		val modDir = dataDir.resolve(id.namespace)
		val typeDir = modDir.resolve(type.directoryName)
		val file = patternFile(dataDir, type, id)
		val normalizedTypeDir = typeDir.toAbsolutePath().normalize()
		check(file.startsWith(normalizedTypeDir)) {
			"Refusing to reload structure definition outside $normalizedTypeDir: $file"
		}
		check(Files.isRegularFile(file)) {
			"Structure definition file not found for '$id' at $file"
		}
		loadStructureFile(modDir, typeDir, type, file, map, claimedSources, reader)
	}

	private fun patternFile(dataDir: Path, type: StructureDefinitionType, id: ResourceLocation): Path = dataDir
		.resolve(id.namespace)
		.resolve(type.directoryName)
		.resolve(id.path + type.fileExtension)
		.toAbsolutePath()
		.normalize()

	private fun <T> createClaimedSources(map: Map<ResourceLocation, T>, section: CacheSection): MutableMap<ResourceLocation, String> = map.keys.associateWithTo(HashMap()) { id ->
		section.existingSource(id)
	}

	private fun <T> createSingleClaimedSource(id: ResourceLocation, map: Map<ResourceLocation, T>, section: CacheSection): MutableMap<ResourceLocation, String> = if (id in map) {
		hashMapOf(id to section.existingSource(id))
	} else {
		HashMap()
	}

	@Throws(IOException::class)
	private fun readBinaryStructureDefinition(file: Path): BlockPattern {
		val compressed = Files.readAllBytes(file)
		val raw = decompressZstd(compressed)
		return CBOR_MAPPER.readValue(raw, BlockPattern::class.java)
	}

	@Throws(IOException::class)
	private fun decompressZstd(compressed: ByteArray): ByteArray {
		ZstdInputStream(ByteArrayInputStream(compressed)).use { zis ->
			ByteArrayOutputStream().use { baos ->
				zis.transferTo(baos)
				return baos.toByteArray()
			}
		}
	}

	@Throws(IOException::class)
	private fun readJsonStructureDefinition(file: Path): StructurePatternResolver.StringArrayDefinition {
		val raw = Files.readAllBytes(file)
		if (raw.isEmpty() || raw.all(::isJsonWhitespace)) {
			throw IOException("Empty JSON structure definition")
		}
		return StructurePatternResolver.decodeStringArrayDefinition(file.toString(), JSON_MAPPER.readTree(raw))
	}

	private fun isJsonWhitespace(byte: Byte): Boolean = when (byte.toInt()) {
		0x09, 0x0A, 0x0D, 0x20 -> true
		else -> false
	}

	private fun <T> runReloadTask(action: () -> T): T {
		check(GTCEu.isDev() && GTCEu.isClientSide()) {
			"Structure cache reload is only available in development client environment"
		}
		check(reloadInProgress.compareAndSet(false, true)) {
			"Structure cache reload task is already running"
		}
		try {
			return action()
		} finally {
			reloadInProgress.set(false)
		}
	}

	@Throws(IOException::class)
	private fun <T> runOnVirtualThread(action: () -> T): T {
		val resultFuture = CompletableFuture<T>()
		val reloadFutureSupplier = Supplier {
			CompletableFuture.runAsync({
				try {
					resultFuture.complete(action())
				} catch (t: Throwable) {
					resultFuture.completeExceptionally(t)
					throw t
				}
			}, LOAD_EXECUTOR)
		}
		try {
			ResourceReloadDetector.regenerateResourcesOnReload(reloadFutureSupplier).join()
			return joinOrThrow(resultFuture)
		} catch (e: Throwable) {
			throwReloadException(e)
		}
	}

	private fun joinCacheFuture(future: CompletableFuture<StructureCaches>): StructureCaches = try {
		joinOrThrow(future)
	} catch (e: Throwable) {
		clearFailedCache(future)
		throw e
	}

	private fun <T> joinOrThrow(future: CompletableFuture<T>): T {
		try {
			return future.join()
		} catch (e: Throwable) {
			throwReloadException(e)
		}
	}

	private fun throwReloadException(throwable: Throwable): Nothing {
		when (val cause = unwrapReloadException(throwable)) {
			is IOException -> throw cause
			is RuntimeException -> throw cause
			is Error -> throw cause
			else -> throw RuntimeException(cause)
		}
	}

	private tailrec fun unwrapReloadException(throwable: Throwable): Throwable {
		val cause = when (throwable) {
			is CompletionException -> throwable.cause
			is ExecutionException -> throwable.cause
			is UncheckedIOException -> throwable.cause
			else -> null
		}
		return if (cause == null) throwable else unwrapReloadException(cause)
	}
}
