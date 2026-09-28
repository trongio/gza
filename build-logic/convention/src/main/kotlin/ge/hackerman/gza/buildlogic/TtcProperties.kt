package ge.hackerman.gza.buildlogic

import java.util.Properties
import org.gradle.api.Project

/**
 * Reads `ttc.properties` from the root project, written by tools/fetch-ttc-config.sh and
 * gitignored. Read through providers so it is a tracked configuration cache input. A
 * missing file is not an error: the app then relies on Remote Config for the key.
 */
internal fun Project.readTtcProperties(): Map<String, String> {
    val file = rootProject.layout.projectDirectory.file("ttc.properties")
    val text = providers.fileContents(file).asText.orNull
    if (text == null) {
        logger.warn(
            "ttc.properties not found: building with an empty gateway key. Run ./tools/fetch-ttc-config.sh"
        )
    }
    val props = Properties().apply { text?.let { load(it.reader()) } }
    return props.stringPropertyNames().associateWith { props.getProperty(it).trim() }
}
