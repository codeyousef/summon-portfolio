package code.yousef.portfolio.ui.fifthwall

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val FIFTH_WALL_CHECKPOINT_KEY = "fifth-wall-progress-v1"
internal const val FIFTH_WALL_SOUND_MODE_KEY = "fifth-wall-sound-mode-v1"
internal const val FIFTH_WALL_CHECKPOINT_EXPIRES_DAYS = 30

private const val CHECKPOINT_VERSION = 1
private const val CHECKPOINT_MAX_AGE_MS = FIFTH_WALL_CHECKPOINT_EXPIRES_DAYS * 24L * 60L * 60L * 1000L
private const val CHECKPOINT_CLOCK_SKEW_MS = 5L * 60L * 1000L

@Serializable
internal enum class FifthWallSoundMode(val volume: Float) {
    FULL(0.72f),
    LOW(0.24f),
    MUTE(0f);

    fun next(): FifthWallSoundMode = when (this) {
        FULL -> LOW
        LOW -> MUTE
        MUTE -> FULL
    }
}

internal enum class FifthWallHudView {
    MANIFEST,
    RULES
}

@Serializable
internal data class FifthWallCheckpoint(
    val version: Int = CHECKPOINT_VERSION,
    val nextBay: Int,
    val score: Int,
    val correctDecisions: Int,
    val countedDecisions: Int,
    val bestStreak: Int,
    val campaignCompleted: Boolean,
    val soundMode: FifthWallSoundMode,
    val savedAtMs: Long
)

internal object FifthWallCheckpointCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(checkpoint: FifthWallCheckpoint): String =
        json.encodeToString(FifthWallCheckpoint.serializer(), checkpoint)

    fun decode(rawValue: String?, nowMs: Long = System.currentTimeMillis()): FifthWallCheckpoint? {
        if (rawValue.isNullOrBlank()) return null
        val candidates = buildList {
            add(rawValue)
            runCatching {
                URLDecoder.decode(rawValue, StandardCharsets.UTF_8)
            }.getOrNull()?.takeIf { it != rawValue }?.let(::add)
        }
        return candidates.firstNotNullOfOrNull { value ->
            runCatching {
                json.decodeFromString(FifthWallCheckpoint.serializer(), value)
            }.getOrNull()?.takeIf { it.isValid(nowMs) }
        }
    }
}

internal fun decodeFifthWallSoundMode(rawValue: String?): FifthWallSoundMode? {
    if (rawValue.isNullOrBlank()) return null
    return buildList {
        add(rawValue)
        runCatching {
            URLDecoder.decode(rawValue, StandardCharsets.UTF_8)
        }.getOrNull()?.takeIf { it != rawValue }?.let(::add)
    }.firstNotNullOfOrNull { candidate ->
        FifthWallSoundMode.entries.firstOrNull { it.name.equals(candidate, ignoreCase = true) }
    }
}

internal fun FifthWallCheckpoint.isValid(nowMs: Long = System.currentTimeMillis()): Boolean {
    val expectedNextBay = if (campaignCompleted) FifthWallLevels.size + 1 else nextBay
    return version == CHECKPOINT_VERSION &&
        nextBay == expectedNextBay &&
        (campaignCompleted || nextBay in 1..FifthWallLevels.size) &&
        score >= 0 &&
        correctDecisions >= 0 &&
        countedDecisions >= correctDecisions &&
        bestStreak >= 0 &&
        savedAtMs <= nowMs + CHECKPOINT_CLOCK_SKEW_MS &&
        nowMs - savedAtMs <= CHECKPOINT_MAX_AGE_MS
}

internal fun FifthWallUiState.completedBayCheckpoint(nowMs: Long): FifthWallCheckpoint? {
    val completed = prompt == FifthWallPrompt.GameComplete
    if (prompt != FifthWallPrompt.LevelComplete && !completed) return null
    return FifthWallCheckpoint(
        nextBay = if (completed) FifthWallLevels.size + 1 else levelIndex + 2,
        score = score,
        correctDecisions = correctDecisions,
        countedDecisions = countedDecisions,
        bestStreak = bestStreak,
        campaignCompleted = completed,
        soundMode = soundMode,
        savedAtMs = nowMs
    )
}
