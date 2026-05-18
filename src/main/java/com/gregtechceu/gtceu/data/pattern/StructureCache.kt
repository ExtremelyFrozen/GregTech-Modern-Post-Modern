package com.gregtechceu.gtceu.data.pattern

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.multiblock.FactoryMultiBlockPattern

import net.minecraft.resources.ResourceLocation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.github.luben.zstd.ZstdInputStream

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.Volatile

object StructureCache {
	@Volatile
	private var futureCache: CompletableFuture<MutableMap<ResourceLocation, FactoryMultiBlockPattern>>? = null
	private val VIRTUAL_THREAD_EXECUTOR: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
	private val CBOR_MAPPER = ObjectMapper(CBORFactory())

	@JvmStatic
	fun loadAsync() {
		GTCEu.LOGGER.info("Loading pattern...")
		if (futureCache != null) return

		futureCache = CompletableFuture.supplyAsync({
			val map: MutableMap<ResourceLocation, FactoryMultiBlockPattern> =
				HashMap()
			try {
				// 遍历 classpath 下所有 data 目录
				val dataUrls = StructureCache::class.java.getClassLoader().getResources("pattern")
				while (dataUrls.hasMoreElements()) {
					val url = dataUrls.nextElement()
					val uri = url.toURI()

					if (uri.scheme == "file") {
						// 开发环境：直接文件系统遍历
						val dataPath = Paths.get(uri)
						loadFromFileSystem(dataPath, map)
					} else if (uri.scheme == "jar") {
						// 生产 JAR 环境：需要使用 FileSystem 读取
						FileSystems.newFileSystem(uri, mutableMapOf<String, Any>()).use { fs ->
							val dataPath = fs.getPath("pattern")
							loadFromFileSystem(dataPath, map)
						}
					}
				}
			} catch (e: Exception) {
				throw RuntimeException("Asynchronous loading of structure definition failed", e)
			}
			Collections.unmodifiableMap(map)
		}, VIRTUAL_THREAD_EXECUTOR)
		GTCEu.LOGGER.info("Loading data: ${futureCache!!.join().size}")
	}

	@Throws(IOException::class)
	private fun loadFromFileSystem(dataDir: Path, map: MutableMap<ResourceLocation, FactoryMultiBlockPattern>) {
		if (!Files.isDirectory(dataDir)) return

		Files.list(dataDir).use { modDirs ->
			modDirs.filter { path: Path -> Files.isDirectory(path) }.forEach { modDir: Path ->
				val structuresDir = modDir.resolve("structures")
				if (!Files.isDirectory(structuresDir)) return@forEach
				try {
					Files.list(structuresDir).use { files ->
						files.filter { f: Path -> f.toString().endsWith(".cbor.zst") }
							.forEach { file: Path ->
								try {
									val compressed = Files.readAllBytes(file)
									val raw = decompressZstd(compressed)
									val def: FactoryMultiBlockPattern = CBOR_MAPPER.readValue(
										raw,
										FactoryMultiBlockPattern::class.java,
									)
									val modid = modDir.fileName.toString()
									val name = file.fileName.toString().replace(".cbor.zst", "")
									synchronized(map) {
										map.put(ResourceLocation.fromNamespaceAndPath(modid, name), def)
									}
								} catch (e: IOException) {
									throw UncheckedIOException("Failed to load structure file: $file", e)
								}
							}
					}
				} catch (e: IOException) {
					throw UncheckedIOException(e)
				}
			}
		}
	}

	fun get(id: ResourceLocation): FactoryMultiBlockPattern? {
		val f: CompletableFuture<MutableMap<ResourceLocation, FactoryMultiBlockPattern>>? = futureCache
		return f!!.join()[id]
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
}
