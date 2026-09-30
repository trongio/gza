package ge.hackerman.gza.core.ttc.testing

import ge.hackerman.gza.core.ttc.TtcJson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlinx.serialization.Serializable

/** Recorded gateway responses under `src/test/resources/fixtures`, addressed by relative path. */
object Fixtures {
    private const val ROOT = "/fixtures"
    private const val META_SUFFIX = ".meta.json"

    private val rootPath: Path by lazy {
        Paths.get(checkNotNull(javaClass.getResource(ROOT)) { "missing $ROOT" }.toURI())
    }

    fun text(path: String): String =
        checkNotNull(javaClass.getResource("$ROOT/$path")) { "missing fixture $path" }.readText()

    fun exists(path: String): Boolean = javaClass.getResource("$ROOT/$path") != null

    fun metaPath(path: String): String = path.substringBeforeLast('.') + META_SUFFIX

    fun meta(path: String): FixtureMeta = TtcJson.decodeFromString<FixtureMetaJson>(text(metaPath(path))).let {
        FixtureMeta(
            request = checkNotNull(it.request),
            status = checkNotNull(it.status),
            contentType = it.contentType.orEmpty(),
            recordedAt = Instant.parse(checkNotNull(it.recordedAt)),
            derived = checkNotNull(it.derived),
            derivedFrom = it.derivedFrom,
            reason = it.reason
        )
    }

    /** Every file under the root, sidecars included. */
    fun allFiles(): List<String> = Files.walk(rootPath).use { stream ->
        stream.filter { it.isRegularFile() }
            .map { rootPath.relativize(it).invariantSeparatorsPathString }
            .sorted()
            .toList()
    }

    /** Every recorded response, without the sidecars. */
    fun all(): List<String> = allFiles().filterNot { it.endsWith(META_SUFFIX) }

    fun isSidecar(path: String): Boolean = path.endsWith(META_SUFFIX)
}

data class FixtureMeta(
    val request: String,
    val status: Int,
    val contentType: String,
    val recordedAt: Instant,
    val derived: Boolean,
    val derivedFrom: List<String>?,
    val reason: String?
)

@Serializable
private data class FixtureMetaJson(
    val request: String? = null,
    val status: Int? = null,
    val contentType: String? = null,
    val recordedAt: String? = null,
    val derived: Boolean? = null,
    val derivedFrom: List<String>? = null,
    val reason: String? = null
)
