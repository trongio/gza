package ge.hackerman.gza.core.model

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class TbilisiTest {
    @Test
    fun `zone is Asia Tbilisi`() {
        assertEquals("Asia/Tbilisi", TBILISI_ZONE.id)
    }

    @Test
    fun `offset is plus four all year because Georgia has no DST`() {
        val rules = TBILISI_ZONE.rules
        val winter = LocalDateTime.of(2026, 1, 15, 12, 0)
        val summer = LocalDateTime.of(2026, 7, 15, 12, 0)
        assertEquals(ZoneOffset.ofHours(4), rules.getOffset(winter))
        assertEquals(ZoneOffset.ofHours(4), rules.getOffset(summer))
    }
}
