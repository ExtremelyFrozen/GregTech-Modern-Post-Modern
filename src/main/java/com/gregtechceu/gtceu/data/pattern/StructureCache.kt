package com.gregtechceu.gtceu.data.pattern

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.multiblock.FactoryMultiBlockPattern
import com.gregtechceu.gtceu.utils.dev.ResourceReloadDetector

import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.ModList

import com.fasterxml.jackson.databind.JsonNode
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
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import kotlin.concurrent.Volatile

object StructureCache {
	@Volatile
	private var futureCache: CompletableFuture<StructureCaches>? = null

	private val LOAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor()
	private val CBOR_MAPPER = ObjectMapper(CBORFactory())
	private val JSON_MAPPER = ObjectMapper()
	private val reloadInProgress = AtomicBoolean(false)

	private data class StructureCaches(val binary: MutableMap<ResourceLocation, FactoryMultiBlockPattern>, val json: MutableMap<ResourceLocation, JsonNode>)

	private data class PatternSource(val description: String, val root: Path)

	private enum class CacheSection {
		BINARY,
		JSON,
	}

	@JvmStatic
	fun loadAsync() {
		GTCEu.LOGGER.info("Loading pattern...")
		if (futureCache != null) return
		if (!reloadInProgress.compareAndSet(false, true)) return

		val loadingFuture = CompletableFuture.supplyAsync({
			loadCaches()
		}, LOAD_EXECUTOR)
		futureCache = loadingFuture
		loadingFuture.whenComplete { caches, throwable ->
			reloadInProgress.set(false)
			if (throwable != null) {
				if (futureCache === loadingFuture) {
					futureCache = null
				}
				GTCEu.LOGGER.error("Failed to load pattern cache", throwable)
			} else {
				GTCEu.LOGGER.info("Loaded binary patterns: ${caches.binary.size}")
				GTCEu.LOGGER.info("Loaded json patterns: ${caches.json.size}")
			}
		}
	}

	@JvmStatic
	@Throws(IOException::class)
	fun reloadAll(): Int = runReloadTask {
		runOnVirtualThread {
			val caches = loadCaches()
			futureCache = CompletableFuture.completedFuture(caches)
			caches.binary.size + caches.json.size
		}
	}

	@JvmStatic
	@Throws(IOException::class)
	fun reloadType(type: StructureDefinitionType): Int = runReloadTask {
		runOnVirtualThread {
			val current = requireCaches()
			syncPatternResourcesToDisk(patternRoot())
			val binaryMap = HashMap(current.binary)
			val jsonMap = HashMap(current.json)
			when (type) {
				StructureDefinitionType.SERIALIZED_BLOCK_PATTERN -> {
					binaryMap.clear()
					loadTypeFromFileSystem(
						patternRoot(),
						type,
						binaryMap,
						createClaimedSources(jsonMap, CacheSection.JSON),
						::readBinaryStructureDefinition,
					)
				}

				StructureDefinitionType.STRING_ARRAY_JSON -> {
					jsonMap.clear()
					loadTypeFromFileSystem(
						patternRoot(),
						type,
						jsonMap,
						createClaimedSources(binaryMap, CacheSection.BINARY),
						::readJsonStructureDefinition,
					)
				}
			}
			val caches = freezeCaches(binaryMap, jsonMap)
			futureCache = CompletableFuture.completedFuture(caches)
			when (type) {
				StructureDefinitionType.SERIALIZED_BLOCK_PATTERN -> caches.binary.size
				StructureDefinitionType.STRING_ARRAY_JSON -> caches.json.size
			}
		}
	}

	@JvmStatic
	@Throws(IOException::class)
	fun reload(type: StructureDefinitionType, id: ResourceLocation): Boolean = runReloadTask {
		runOnVirtualThread {
			val current = requireCaches()
			syncPatternResourcesToDisk(patternRoot())
			val binaryMap = HashMap(current.binary)
			val jsonMap = HashMap(current.json)
			when (type) {
				StructureDefinitionType.SERIALIZED_BLOCK_PATTERN -> {
					binaryMap.remove(id)
					reloadSingleEntry(
						patternRoot(),
						type,
						id,
						binaryMap,
						createClaimedSources(jsonMap, CacheSection.JSON),
						::readBinaryStructureDefinition,
					)
				}

				StructureDefinitionType.STRING_ARRAY_JSON -> {
					jsonMap.remove(id)
					reloadSingleEntry(
						patternRoot(),
						type,
						id,
						jsonMap,
						createClaimedSources(binaryMap, CacheSection.BINARY),
						::readJsonStructureDefinition,
					)
				}
			}
			futureCache = CompletableFuture.completedFuture(freezeCaches(binaryMap, jsonMap))
			true
		}
	}

	@JvmStatic
	fun getSerializedBlockPattern(id: ResourceLocation): FactoryMultiBlockPattern? {
		val f: CompletableFuture<StructureCaches>? = futureCache
		return f!!.join().binary[id]
	}

	@JvmStatic
	fun getBinaryCacheSize(): Int = requireCaches().binary.size

	@JvmStatic
	fun getStringArrayPattern(id: ResourceLocation): JsonNode? {
		val f: CompletableFuture<StructureCaches>? = futureCache
		return f!!.join().json[id]
	}

	@JvmStatic
	fun getJsonCacheSize(): Int = requireCaches().json.size

	private fun patternRoot(): Path = GTCEu.GTCEU_FOLDER.resolve("pattern")

	private fun requireCaches(): StructureCaches {
		val currentFuture = futureCache
		if (currentFuture != null) {
			return currentFuture.join()
		}

		val caches = loadCaches()
		futureCache = CompletableFuture.completedFuture(caches)
		return caches
	}

	private fun loadCaches(): StructureCaches {
		val root = patternRoot()
		syncPatternResourcesToDisk(root)
		val binaryMap = HashMap<ResourceLocation, FactoryMultiBlockPattern>()
		val jsonMap = HashMap<ResourceLocation, JsonNode>()
		loadFromFileSystem(root, binaryMap, jsonMap)
		return freezeCaches(binaryMap, jsonMap)
	}

	private fun freezeCaches(binaryMap: MutableMap<ResourceLocation, FactoryMultiBlockPattern>, jsonMap: MutableMap<ResourceLocation, JsonNode>): StructureCaches = StructureCaches(
		Collections.unmodifiableMap(binaryMap),
		Collections.unmodifiableMap(jsonMap),
	)

	@Throws(IOException::class)
	private fun syncPatternResourcesToDisk(patternRoot: Path) {
		val normalizedPatternRoot = normalizePatternRoot(patternRoot)
		Files.createDirectories(normalizedPatternRoot)
		val expectedPaths = HashSet<Path>()
		expectedPaths.add(normalizedPatternRoot)

		copyPatternSources(collectPatternSources(), normalizedPatternRoot, expectedPaths)
		prunePatternDirectory(normalizedPatternRoot, expectedPaths)
	}

	@Throws(IOException::class)
	private fun collectPatternSources(): List<PatternSource> {
		val sources = ArrayList<PatternSource>()
		collectModPatternSources(sources)
		return sources
	}

	private fun collectModPatternSources(sources: MutableList<PatternSource>) {
		val modList = ModList.get() ?: return
		for (modFileInfo in modList.modFiles) {
			val patternRoot = modFileInfo.file.findResource("pattern")
			if (Files.isDirectory(patternRoot)) {
				sources += PatternSource("mod:${modFileInfo.file.fileName}", patternRoot)
			}
		}
	}

	@Throws(IOException::class)
	private fun copyPatternSources(sources: List<PatternSource>, targetRoot: Path, expectedPaths: MutableSet<Path>) {
		val seenRoots = HashSet<Path>()
		val claimedTargets = HashMap<Path, String>()
		for (source in sources) {
			val normalizedRoot = source.root.toAbsolutePath().normalize()
			if (seenRoots.add(normalizedRoot)) {
				copyPatternTree(PatternSource(source.description, normalizedRoot), targetRoot, expectedPaths, claimedTargets)
			}
		}
	}

	@Throws(IOException::class)
	private fun copyPatternTree(source: PatternSource, targetRoot: Path, expectedPaths: MutableSet<Path>, claimedTargets: MutableMap<Path, String>) {
		val sourceRoot = source.root
		if (!Files.isDirectory(sourceRoot)) return

		Files.walk(sourceRoot).use { paths ->
			paths.forEach { path ->
				val relative = sourceRoot.relativize(path)
				val target = targetRoot.resolve(relative.toString()).toAbsolutePath().normalize()
				expectedPaths.add(target)
				if (Files.isDirectory(path)) {
					if (Files.exists(target) && !Files.isDirectory(target)) {
						Files.delete(target)
					}
					Files.createDirectories(target)
				} else {
					if (Files.exists(target) && Files.isDirectory(target)) {
						deleteRecursively(target)
					}
					Files.createDirectories(target.parent)
					claimPatternTarget(targetRoot, target, source.description, path, claimedTargets)
					if (shouldCopyFile(path, target)) {
						Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
					}
				}
			}
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
	private fun deleteRecursively(path: Path) {
		if (!Files.exists(path)) return
		Files.walk(path).use { paths ->
			paths.sorted(Comparator.reverseOrder()).forEach { nested ->
				Files.deleteIfExists(nested)
			}
		}
	}

	@Throws(IOException::class)
	private fun loadFromFileSystem(dataDir: Path, binaryMap: MutableMap<ResourceLocation, FactoryMultiBlockPattern>, jsonMap: MutableMap<ResourceLocation, JsonNode>) {
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
		val file = typeDir.resolve(id.path + type.fileExtension)
		check(Files.isRegularFile(file)) {
			"Structure definition file not found for '$id' at $file"
		}
		loadStructureFile(modDir, typeDir, type, file, map, claimedSources, reader)
	}

	private fun <T> createClaimedSources(map: Map<ResourceLocation, T>, section: CacheSection): MutableMap<ResourceLocation, String> {
		val claimedSources = HashMap<ResourceLocation, String>()
		for (id in map.keys) {
			claimedSources[id] = when (section) {
				CacheSection.BINARY -> "existing binary cache entry for $id"
				CacheSection.JSON -> "existing json cache entry for $id"
			}
		}
		return claimedSources
	}

	@Throws(IOException::class)
	private fun readBinaryStructureDefinition(file: Path): FactoryMultiBlockPattern {
		val compressed = Files.readAllBytes(file)
		val raw = decompressZstd(compressed)
		return CBOR_MAPPER.readValue(raw, FactoryMultiBlockPattern::class.java)
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
	private fun readJsonStructureDefinition(file: Path): JsonNode {
		val raw = Files.readAllBytes(file)
		if (raw.isEmpty() || raw.all(::isJsonWhitespace)) {
			throw IOException("Empty JSON structure definition")
		}
		return JSON_MAPPER.readTree(raw)
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
			return resultFuture.join()
		} catch (e: CompletionException) {
			val cause = e.cause ?: e
			when (cause) {
				is IOException -> throw cause
				is RuntimeException -> throw cause
				is Error -> throw cause
				else -> throw RuntimeException(cause)
			}
		}
	}
}
