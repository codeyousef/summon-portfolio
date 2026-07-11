package code.yousef.portfolio.ui.fifthwall

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FifthWallCheckpointTest {
    private val now = 5_000_000_000L

    @Test
    fun checkpointRoundTripsFromPlainAndCookieEncodedJson() {
        val checkpoint = validCheckpoint(nextBay = 9, soundMode = FifthWallSoundMode.FULL)
        val encoded = FifthWallCheckpointCodec.encode(checkpoint)
        val cookieEncoded = URLEncoder.encode(encoded, StandardCharsets.UTF_8)

        assertEquals(checkpoint, FifthWallCheckpointCodec.decode(encoded, now))
        assertEquals(checkpoint, FifthWallCheckpointCodec.decode(cookieEncoded, now))
    }

    @Test
    fun obsoleteExpiredAndMalformedCheckpointsAreIgnored() {
        val expired = validCheckpoint().copy(savedAtMs = now - (31L * 24L * 60L * 60L * 1000L))
        val obsolete = FifthWallCheckpointCodec.encode(validCheckpoint()).replace("\"version\":1", "\"version\":0")

        assertNull(FifthWallCheckpointCodec.decode(FifthWallCheckpointCodec.encode(expired), now))
        assertNull(FifthWallCheckpointCodec.decode(obsolete, now))
        assertNull(FifthWallCheckpointCodec.decode("not-json", now))
        assertNull(FifthWallCheckpointCodec.decode(null, now))
    }

    @Test
    fun completedBayCheckpointStoresOnlyTheNextBayAndCampaignMetrics() {
        val state = FifthWallUiState(
            levelIndex = 6,
            score = 1_275,
            correctDecisions = 8,
            countedDecisions = 10,
            bestStreak = 5,
            soundMode = FifthWallSoundMode.MUTE,
            prompt = FifthWallPrompt.LevelComplete
        )

        val checkpoint = state.completedBayCheckpoint(now)

        assertEquals(8, checkpoint?.nextBay)
        assertEquals(1_275, checkpoint?.score)
        assertEquals(8, checkpoint?.correctDecisions)
        assertEquals(10, checkpoint?.countedDecisions)
        assertEquals(5, checkpoint?.bestStreak)
        assertEquals(FifthWallSoundMode.MUTE, checkpoint?.soundMode)
        assertFalse(checkpoint?.campaignCompleted ?: true)
        assertNull(state.copy(prompt = FifthWallPrompt.None).completedBayCheckpoint(now))
    }

    @Test
    fun campaignCompletionUsesTheTerminalBayMarker() {
        val checkpoint = FifthWallUiState(
            levelIndex = FifthWallLevels.lastIndex,
            prompt = FifthWallPrompt.GameComplete
        ).completedBayCheckpoint(now)

        assertEquals(FifthWallLevels.size + 1, checkpoint?.nextBay)
        assertTrue(checkpoint?.campaignCompleted == true)
        assertTrue(checkpoint?.isValid(now) == true)
    }

    @Test
    fun soundModeDecoderAcceptsCookieValuesAndRejectsUnknownModes() {
        assertEquals(FifthWallSoundMode.LOW, decodeFifthWallSoundMode("LOW"))
        assertEquals(FifthWallSoundMode.FULL, decodeFifthWallSoundMode("full"))
        assertNull(decodeFifthWallSoundMode("MAXIMUM"))
    }

    private fun validCheckpoint(
        nextBay: Int = 4,
        soundMode: FifthWallSoundMode = FifthWallSoundMode.LOW
    ): FifthWallCheckpoint = FifthWallCheckpoint(
        nextBay = nextBay,
        score = 900,
        correctDecisions = 6,
        countedDecisions = 8,
        bestStreak = 4,
        campaignCompleted = false,
        soundMode = soundMode,
        savedAtMs = now
    )
}
