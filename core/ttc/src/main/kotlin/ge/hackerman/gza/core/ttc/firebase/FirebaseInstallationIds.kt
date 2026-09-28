package ge.hackerman.gza.core.ttc.firebase

import java.security.SecureRandom
import java.util.Base64
import java.util.Random

/** Firebase installation ids, generated with the same algorithm as the Firebase JS SDK. */
object FirebaseInstallationIds {
    private const val FID_BYTES = 17
    private const val FID_LENGTH = 22

    // 0b0111 in the top nibble makes the first base64 char one of c, d, e, f.
    private const val FID_PREFIX_BITS = 0x70
    private const val LOW_NIBBLE = 0x0F

    private val secureRandom = SecureRandom()

    fun generate(random: Random = secureRandom): String {
        val bytes = ByteArray(FID_BYTES).also(random::nextBytes)
        bytes[0] = (FID_PREFIX_BITS or (bytes[0].toInt() and LOW_NIBBLE)).toByte()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).take(FID_LENGTH)
    }
}
