package code.yousef.portfolio.ui.fifthwall

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal val FifthWallTelemetryJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

@Serializable
internal data class FifthWallTelemetryEvent(
    val id: String,
    val type: String,
    val levelId: Int,
    val timestampMs: Long,
    val details: Map<String, String> = emptyMap()
)

@Serializable
internal data class FifthWallHiddenMetrics(
    val explorationIndex: Double,
    val falsificationRate: Double,
    val calibrationAccuracy: Double,
    val probabilityIntuition: Double,
    val cognitiveFlexibility: Double,
    val impossibleGeometry: Double,
    val affectiveDecoupling: Double,
    val cognitiveDecouplingSocial: Double,
    val semanticPrecision: Double,
    val tribalResistance: Double,
    val falsificationSocial: Double,
    val updateSpeed: Double,
    val puzzleScore: Double,
    val socialScore: Double,
    val finalRqScore: Double
)

@Serializable
internal data class FifthWallTelemetrySummary(
    val visibleScore: Int,
    val completedLevels: Int,
    val sessionDurationMs: Long,
    val generatedAtMs: Long,
    val metrics: FifthWallHiddenMetrics
)

@Serializable
internal data class FifthWallTelemetryPayload(
    val sessionId: String,
    val revision: Int,
    val events: List<FifthWallTelemetryEvent>,
    val summary: FifthWallTelemetrySummary
)

@Serializable
internal data class FifthWallTelemetrySessionRecord(
    val sessionId: String,
    val lastRevision: Int = 0,
    val updatedAtMs: Long = 0L,
    val events: List<FifthWallTelemetryEvent> = emptyList(),
    val summary: FifthWallTelemetrySummary? = null
)

@Serializable
internal data class FifthWallTelemetryDatabase(
    val sessions: Map<String, FifthWallTelemetrySessionRecord> = emptyMap()
)

internal interface FifthWallTelemetryStore {
    suspend fun merge(payload: FifthWallTelemetryPayload)
}

internal class FileFifthWallTelemetryStore(
    private val path: Path = Path.of("storage/fifth-wall-telemetry.json")
) : FifthWallTelemetryStore {

    private val lock = ReentrantLock()

    override suspend fun merge(payload: FifthWallTelemetryPayload) = lock.withLock {
        path.parent?.createDirectories()
        val database = readDatabase()
        val existing = database.sessions[payload.sessionId]
        val mergedEvents = (existing?.events.orEmpty() + payload.events)
            .distinctBy { it.id }
            .sortedBy { it.timestampMs }

        val mergedRecord = FifthWallTelemetrySessionRecord(
            sessionId = payload.sessionId,
            lastRevision = maxOf(existing?.lastRevision ?: 0, payload.revision),
            updatedAtMs = payload.summary.generatedAtMs,
            events = mergedEvents,
            summary = payload.summary
        )

        val updated = FifthWallTelemetryDatabase(
            sessions = database.sessions + (payload.sessionId to mergedRecord)
        )
        path.writeText(FifthWallTelemetryJson.encodeToString(updated))
    }

    private fun readDatabase(): FifthWallTelemetryDatabase {
        if (!path.exists()) return FifthWallTelemetryDatabase()
        val text = path.readText().trim()
        if (text.isBlank()) return FifthWallTelemetryDatabase()
        return runCatching {
            FifthWallTelemetryJson.decodeFromString<FifthWallTelemetryDatabase>(text)
        }.getOrElse {
            FifthWallTelemetryDatabase()
        }
    }
}

internal data class FifthWallMetricAccumulator(
    val explorationReturnFound: Boolean = false,
    val glitchRepairFound: Boolean = false,
    val hiddenRuleRejects: Int = 0,
    val hiddenRuleTests: Int = 0,
    val confidenceSamples: Int = 0,
    val confidenceSquaredErrorSum: Double = 0.0,
    val probabilityScore: Double? = null,
    val ruleShiftSamples: Int = 0,
    val ruleShiftErrorSum: Int = 0,
    val geometryChecks: Int = 0,
    val geometryCorrect: Int = 0,
    val affectiveScoreSum: Double = 0.0,
    val affectiveSamples: Int = 0,
    val socialDecouplingScoreSum: Double = 0.0,
    val socialDecouplingSamples: Int = 0,
    val semanticScoreSum: Double = 0.0,
    val semanticSamples: Int = 0,
    val tribalScore: Double? = null,
    val falsificationSocialScore: Double? = null,
    val updateSpeedScore: Double? = null
)

internal fun FifthWallMetricAccumulator.toSummary(
    visibleScore: Int,
    completedLevels: Int,
    sessionDurationMs: Long,
    generatedAtMs: Long
): FifthWallTelemetrySummary {
    val explorationIndex = clamp01(
        (if (explorationReturnFound) 0.45 else 0.0) +
            (if (glitchRepairFound) 0.55 else 0.0)
    )
    val falsificationRate = averageOrNeutral(hiddenRuleRejects.toDouble(), hiddenRuleTests)
    val calibrationAccuracy = if (confidenceSamples > 0) {
        clamp01(1.0 - (confidenceSquaredErrorSum / confidenceSamples.toDouble()))
    } else {
        0.5
    }
    val probabilityIntuition = probabilityScore ?: 0.5
    val cognitiveFlexibility = if (ruleShiftSamples > 0) {
        clamp01(1.0 - (ruleShiftErrorSum.toDouble() / ruleShiftSamples.toDouble()).coerceAtMost(3.0) / 3.0)
    } else {
        0.5
    }
    val impossibleGeometry = averageOrNeutral(geometryCorrect.toDouble(), geometryChecks)
    val affectiveDecoupling = meanOrNeutral(affectiveScoreSum, affectiveSamples)
    val cognitiveDecouplingSocial = meanOrNeutral(socialDecouplingScoreSum, socialDecouplingSamples)
    val semanticPrecision = meanOrNeutral(semanticScoreSum, semanticSamples)
    val tribalResistance = tribalScore ?: 0.5
    val falsificationSocial = falsificationSocialScore ?: 0.5
    val updateSpeed = updateSpeedScore ?: 0.5
    val puzzleScore = mean(
        explorationIndex,
        falsificationRate,
        calibrationAccuracy,
        probabilityIntuition,
        cognitiveFlexibility,
        impossibleGeometry
    )
    val socialScore = mean(
        affectiveDecoupling,
        cognitiveDecouplingSocial,
        semanticPrecision,
        tribalResistance,
        falsificationSocial,
        updateSpeed
    )
    val finalRqScore = clamp01((0.4 * puzzleScore) + (0.6 * socialScore))

    return FifthWallTelemetrySummary(
        visibleScore = visibleScore,
        completedLevels = completedLevels,
        sessionDurationMs = sessionDurationMs,
        generatedAtMs = generatedAtMs,
        metrics = FifthWallHiddenMetrics(
            explorationIndex = explorationIndex,
            falsificationRate = falsificationRate,
            calibrationAccuracy = calibrationAccuracy,
            probabilityIntuition = probabilityIntuition,
            cognitiveFlexibility = cognitiveFlexibility,
            impossibleGeometry = impossibleGeometry,
            affectiveDecoupling = affectiveDecoupling,
            cognitiveDecouplingSocial = cognitiveDecouplingSocial,
            semanticPrecision = semanticPrecision,
            tribalResistance = tribalResistance,
            falsificationSocial = falsificationSocial,
            updateSpeed = updateSpeed,
            puzzleScore = puzzleScore,
            socialScore = socialScore,
            finalRqScore = finalRqScore
        )
    )
}

private fun averageOrNeutral(success: Double, total: Int): Double =
    if (total > 0) clamp01(success / total.toDouble()) else 0.5

private fun meanOrNeutral(sum: Double, samples: Int): Double =
    if (samples > 0) clamp01(sum / samples.toDouble()) else 0.5

private fun mean(vararg values: Double): Double =
    if (values.isEmpty()) 0.0 else clamp01(values.average())

private fun clamp01(value: Double): Double =
    value.coerceIn(0.0, 1.0)
