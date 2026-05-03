package code.yousef.portfolio.ui.fifthwall

import codes.yousef.summon.state.MutableState
import codes.yousef.summon.state.mutableStateOf
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

internal enum class FifthWallRuleKind {
    Color,
    Shape,
    Pattern,
    Destination,
    Geometry,
    Weight,
    Volume,
    Probability
}

internal enum class FifthWallFeedbackTone {
    Neutral,
    Positive,
    Negative
}

internal enum class FifthWallPrePrompt {
    ProbabilityPrediction,
    TeamDiscussion
}

internal data class FifthWallColor(
    val name: String,
    val hex: String
)

internal data class FifthWallRule(
    val kind: FifthWallRuleKind,
    val text: String,
    val value: String? = null,
    val comparator: String? = null,
    val threshold: Int? = null,
    val probability: Double? = null
)

internal data class FifthWallPriorityRule(
    val colorName: String,
    val multiplier: Int,
    val text: String
)

internal data class FifthWallLevel(
    val id: Int,
    val name: String,
    val briefing: String,
    val packageCount: Int,
    val trucks: List<FifthWallRule>,
    val shiftedTrucks: List<FifthWallRule> = emptyList(),
    val shiftAfterProcessed: Int? = null,
    val rejectRate: Double,
    val forceReject: Boolean = false,
    val hiddenRuleIndex: Int? = null,
    val hiddenRuleMatcher: ((String) -> Boolean)? = null,
    val confidenceCheck: Boolean = false,
    val socialPressure: Boolean = false,
    val prePrompt: FifthWallPrePrompt? = null,
    val priorityRule: FifthWallPriorityRule? = null,
    val shiftedPriorityRule: FifthWallPriorityRule? = null,
    val priorityShiftAfterProcessed: Int? = null,
    val glitchAfterProcessed: Int? = null
)

internal data class FifthWallLevelGuidance(
    val phase: String,
    val objective: String,
    val mechanic: String,
    val twist: String
)

internal data class FifthWallPackage(
    val id: String,
    val color: FifthWallColor,
    val shape: String,
    val pattern: String,
    val destination: String,
    val weight: Int,
    val volume: Int,
    val geometry: String? = null,
    val validGeometry: Boolean = true,
    val labelText: String? = null
)

internal data class FifthWallChatMessage(
    val author: String,
    val text: String,
    val self: Boolean = false
)

internal data class FifthWallPendingConfidence(
    val outcome: String,
    val routeLabel: String
)

internal sealed interface FifthWallPrompt {
    data object None : FifthWallPrompt
    data object Intro : FifthWallPrompt
    data object ProbabilityPrediction : FifthWallPrompt
    data object TeamDiscussion : FifthWallPrompt
    data object RuleGuess : FifthWallPrompt
    data object Confidence : FifthWallPrompt
    data object LevelComplete : FifthWallPrompt
    data object GameComplete : FifthWallPrompt
}

internal data class FifthWallUiState(
    val levelIndex: Int = 0,
    val score: Int = 0,
    val queue: List<FifthWallPackage> = emptyList(),
    val selectedPackageId: String? = null,
    val draggedPackageId: String? = null,
    val dropTargetId: String? = null,
    val testsUsed: Int = 0,
    val hiddenRuleRevealed: Boolean = false,
    val ruleShifted: Boolean = false,
    val priorityShifted: Boolean = false,
    val activePriorityRule: FifthWallPriorityRule? = null,
    val chatEnabled: Boolean = false,
    val chatDraft: String = "",
    val chatMessages: List<FifthWallChatMessage> = emptyList(),
    val logMessages: List<String> = emptyList(),
    val feedback: String = "Dispatch console ready.",
    val feedbackTone: FifthWallFeedbackTone = FifthWallFeedbackTone.Neutral,
    val lastRouteTarget: String? = null,
    val lastRouteAccepted: Boolean? = null,
    val prompt: FifthWallPrompt = FifthWallPrompt.Intro,
    val ruleGuessInput: String = "",
    val predictionInput: String = "",
    val discussionReply: String = "",
    val pendingConfidence: FifthWallPendingConfidence? = null,
    val confidenceEntries: List<String> = emptyList(),
    val probabilityPrediction: Int? = null,
    val probabilityAccepted: Int = 0,
    val socialPressureTriggered: Boolean = false,
    val glitchActive: Boolean = false,
    val glitchTriggered: Boolean = false,
    val wrenchVisible: Boolean = false,
    val telemetrySessionId: String = "",
    val telemetryPayloadJson: String = "",
    val telemetryRevision: Int = 0
)

internal val FifthWallLevels = listOf(
    FifthWallLevel(
        id = 1,
        name = "Orientation",
        briefing = "Sort by color. One package does not belong and has to be returned.",
        packageCount = 10,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Color, "Color: Red", value = "red"),
            FifthWallRule(FifthWallRuleKind.Color, "Color: Blue", value = "blue")
        ),
        rejectRate = 0.12,
        forceReject = true
    ),
    FifthWallLevel(
        id = 2,
        name = "Mass Split",
        briefing = "Weight is the only signal. Read carefully before routing.",
        packageCount = 12,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Weight, "Weight > 7kg", comparator = ">", threshold = 7),
            FifthWallRule(FifthWallRuleKind.Weight, "Weight <= 7kg", comparator = "<=", threshold = 7)
        ),
        rejectRate = 0.10
    ),
    FifthWallLevel(
        id = 3,
        name = "Triad",
        briefing = "Multiple properties overlap. Pick the strongest match and keep moving.",
        packageCount = 14,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Color, "Color: Green", value = "green"),
            FifthWallRule(FifthWallRuleKind.Shape, "Shape: Cylinder", value = "cylinder"),
            FifthWallRule(FifthWallRuleKind.Weight, "Weight >= 10kg", comparator = ">=", threshold = 10)
        ),
        rejectRate = 0.14
    ),
    FifthWallLevel(
        id = 4,
        name = "Signal Mix",
        briefing = "Pattern, destination, and volume now compete for attention.",
        packageCount = 16,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Pattern, "Pattern: Striped", value = "striped"),
            FifthWallRule(FifthWallRuleKind.Destination, "Destination: Factory", value = "factory"),
            FifthWallRule(FifthWallRuleKind.Volume, "Volume < 20L", comparator = "<", threshold = 20)
        ),
        rejectRate = 0.18
    ),
    FifthWallLevel(
        id = 5,
        name = "Rule Discovery",
        briefing = "Truck A is hiding its rule. Test packages, then name the rule when dispatch asks.",
        packageCount = 12,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Weight, "Weight >= 8kg", comparator = ">=", threshold = 8)
        ),
        rejectRate = 0.25,
        hiddenRuleIndex = 0,
        hiddenRuleMatcher = { guess ->
            val normalized = guess.lowercase()
            normalized.contains("weight") &&
                (normalized.contains(">=") || normalized.contains("over") || normalized.contains("at least")) &&
                normalized.contains("8")
        }
    ),
    FifthWallLevel(
        id = 6,
        name = "Calibration",
        briefing = "After every route, report your confidence before the next package enters the lane.",
        packageCount = 14,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Color, "Color: Yellow", value = "yellow"),
            FifthWallRule(FifthWallRuleKind.Shape, "Shape: Sphere", value = "sphere"),
            FifthWallRule(FifthWallRuleKind.Weight, "Weight <= 6kg", comparator = "<=", threshold = 6)
        ),
        rejectRate = 0.16,
        confidenceCheck = true
    ),
    FifthWallLevel(
        id = 7,
        name = "Pressure",
        briefing = "Normal routing, but the dispatch channel is no longer purely friendly.",
        packageCount = 16,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Pattern, "Pattern: Dotted", value = "dotted"),
            FifthWallRule(FifthWallRuleKind.Destination, "Destination: Office", value = "office"),
            FifthWallRule(FifthWallRuleKind.Weight, "Weight >= 9kg", comparator = ">=", threshold = 9)
        ),
        rejectRate = 0.20,
        socialPressure = true
    ),
    FifthWallLevel(
        id = 8,
        name = "Probability",
        briefing = "Truck A accepts packages 70 percent of the time. Make a prediction before the shift starts.",
        packageCount = 10,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Probability, "Acceptance: 70%", probability = 0.70)
        ),
        rejectRate = 0.0,
        prePrompt = FifthWallPrePrompt.ProbabilityPrediction
    ),
    FifthWallLevel(
        id = 9,
        name = "Team Discussion",
        briefing = "The routing stays active, but dispatch wants an answer to a hypothetical before you continue.",
        packageCount = 12,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Color, "Color: Purple", value = "purple"),
            FifthWallRule(FifthWallRuleKind.Volume, "Volume > 25L", comparator = ">", threshold = 25),
            FifthWallRule(FifthWallRuleKind.Shape, "Shape: Cube", value = "cube")
        ),
        rejectRate = 0.20,
        prePrompt = FifthWallPrePrompt.TeamDiscussion
    ),
    FifthWallLevel(
        id = 10,
        name = "Overlapping Grid",
        briefing = "All four trucks are live. Read the manifest, then commit fast.",
        packageCount = 18,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Color, "Color: Red", value = "red"),
            FifthWallRule(FifthWallRuleKind.Shape, "Shape: Sphere", value = "sphere"),
            FifthWallRule(FifthWallRuleKind.Weight, "Weight >= 12kg", comparator = ">=", threshold = 12),
            FifthWallRule(FifthWallRuleKind.Destination, "Destination: Lab", value = "lab")
        ),
        rejectRate = 0.22
    ),
    FifthWallLevel(
        id = 11,
        name = "Impossible Geometry I",
        briefing = "Only valid geometry ships today. Inspect the manifest and return impossible forms.",
        packageCount = 12,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Geometry, "Geometry: Valid", value = "valid")
        ),
        rejectRate = 0.38
    ),
    FifthWallLevel(
        id = 12,
        name = "Impossible Geometry II",
        briefing = "Geometry is still live, but color and destination now overlap it.",
        packageCount = 14,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Geometry, "Geometry: Valid", value = "valid"),
            FifthWallRule(FifthWallRuleKind.Color, "Color: Yellow", value = "yellow"),
            FifthWallRule(FifthWallRuleKind.Destination, "Destination: Lab", value = "lab")
        ),
        rejectRate = 0.26
    ),
    FifthWallLevel(
        id = 13,
        name = "Semantic Precision",
        briefing = "Package labels grew softer. Keep the routing precise while dispatch argues about wording.",
        packageCount = 14,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Destination, "Destination: Office", value = "office"),
            FifthWallRule(FifthWallRuleKind.Weight, "Weight >= 8kg", comparator = ">=", threshold = 8),
            FifthWallRule(FifthWallRuleKind.Pattern, "Pattern: Dotted", value = "dotted")
        ),
        rejectRate = 0.20
    ),
    FifthWallLevel(
        id = 14,
        name = "Rule Change I",
        briefing = "The rules may shift midstream. Watch the board and adapt without a prompt.",
        packageCount = 14,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Color, "Color: Red", value = "red"),
            FifthWallRule(FifthWallRuleKind.Shape, "Shape: Cylinder", value = "cylinder")
        ),
        shiftedTrucks = listOf(
            FifthWallRule(FifthWallRuleKind.Color, "Color: Blue", value = "blue"),
            FifthWallRule(FifthWallRuleKind.Shape, "Shape: Sphere", value = "sphere")
        ),
        shiftAfterProcessed = 7,
        rejectRate = 0.16
    ),
    FifthWallLevel(
        id = 15,
        name = "Rule Change II",
        briefing = "Another shift. Subtle cues only. Keep reading before you commit.",
        packageCount = 16,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Weight, "Weight >= 9kg", comparator = ">=", threshold = 9),
            FifthWallRule(FifthWallRuleKind.Volume, "Volume < 18L", comparator = "<", threshold = 18),
            FifthWallRule(FifthWallRuleKind.Destination, "Destination: Factory", value = "factory")
        ),
        shiftedTrucks = listOf(
            FifthWallRule(FifthWallRuleKind.Weight, "Weight <= 5kg", comparator = "<=", threshold = 5),
            FifthWallRule(FifthWallRuleKind.Volume, "Volume > 26L", comparator = ">", threshold = 26),
            FifthWallRule(FifthWallRuleKind.Destination, "Destination: Lab", value = "lab")
        ),
        shiftAfterProcessed = 8,
        rejectRate = 0.18
    ),
    FifthWallLevel(
        id = 16,
        name = "Team Falsification",
        briefing = "Dispatch already has a theory about Truck A. Test the edge cases before you answer.",
        packageCount = 14,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Weight, "Weight >= 10kg", comparator = ">=", threshold = 10)
        ),
        rejectRate = 0.24,
        hiddenRuleIndex = 0,
        hiddenRuleMatcher = { guess ->
            val normalized = guess.lowercase()
            normalized.contains("weight") &&
                (normalized.contains(">=") || normalized.contains("over") || normalized.contains("at least")) &&
                normalized.contains("10")
        }
    ),
    FifthWallLevel(
        id = 17,
        name = "Update Speed",
        briefing = "Routing stays constant, but the score target changes when new data arrives.",
        packageCount = 15,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Color, "Color: Red", value = "red"),
            FifthWallRule(FifthWallRuleKind.Color, "Color: Blue", value = "blue"),
            FifthWallRule(FifthWallRuleKind.Weight, "Weight >= 11kg", comparator = ">=", threshold = 11)
        ),
        rejectRate = 0.18,
        priorityRule = FifthWallPriorityRule("red", 2, "Red packages pay 2x."),
        shiftedPriorityRule = FifthWallPriorityRule("blue", 2, "Blue packages now pay 2x."),
        priorityShiftAfterProcessed = 5
    ),
    FifthWallLevel(
        id = 18,
        name = "High Pressure I",
        briefing = "The belt is faster, the chat is louder, and dispatch wants a reply before the lane feels safe.",
        packageCount = 18,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Pattern, "Pattern: Striped", value = "striped"),
            FifthWallRule(FifthWallRuleKind.Destination, "Destination: Office", value = "office"),
            FifthWallRule(FifthWallRuleKind.Volume, "Volume > 24L", comparator = ">", threshold = 24),
            FifthWallRule(FifthWallRuleKind.Weight, "Weight <= 6kg", comparator = "<=", threshold = 6)
        ),
        rejectRate = 0.24,
        socialPressure = true,
        prePrompt = FifthWallPrePrompt.TeamDiscussion
    ),
    FifthWallLevel(
        id = 19,
        name = "High Pressure II",
        briefing = "Four trucks, more rejects, less patience. Keep the queue moving.",
        packageCount = 20,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Color, "Color: Green", value = "green"),
            FifthWallRule(FifthWallRuleKind.Shape, "Shape: Cube", value = "cube"),
            FifthWallRule(FifthWallRuleKind.Weight, "Weight >= 12kg", comparator = ">=", threshold = 12),
            FifthWallRule(FifthWallRuleKind.Destination, "Destination: Factory", value = "factory")
        ),
        rejectRate = 0.30,
        socialPressure = true
    ),
    FifthWallLevel(
        id = 20,
        name = "Glitchy Level",
        briefing = "The console has been stable all night. It should stay that way.",
        packageCount = 12,
        trucks = listOf(
            FifthWallRule(FifthWallRuleKind.Color, "Color: Purple", value = "purple"),
            FifthWallRule(FifthWallRuleKind.Geometry, "Geometry: Valid", value = "valid"),
            FifthWallRule(FifthWallRuleKind.Destination, "Destination: Lab", value = "lab")
        ),
        rejectRate = 0.24,
        glitchAfterProcessed = 6
    )
)

private val FifthWallGuidanceByLevel = mapOf(
    1 to FifthWallLevelGuidance(
        phase = "Solo Orientation",
        objective = "Route red and blue packages; return the package that matches no truck.",
        mechanic = "Focus a queue card, read its manifest, then choose a bay target.",
        twist = "Return Bin is a valid route when every truck rejects the manifest."
    ),
    2 to FifthWallLevelGuidance(
        phase = "Solo Sorting",
        objective = "Split the lane by package weight.",
        mechanic = "Compare the manifest weight against the two truck thresholds.",
        twist = "Both trucks cover the full rule, so Return Bin should be rare."
    ),
    3 to FifthWallLevelGuidance(
        phase = "Solo Sorting",
        objective = "Route packages across color, shape, and weight rules.",
        mechanic = "Use the first matching truck rule you can prove from the manifest.",
        twist = "Different manifest fields can matter on consecutive packages."
    ),
    4 to FifthWallLevelGuidance(
        phase = "Solo Sorting",
        objective = "Balance pattern, destination, and volume rules.",
        mechanic = "Read the whole manifest before committing.",
        twist = "This is the last quiet bay before dispatch chat unlocks."
    ),
    5 to FifthWallLevelGuidance(
        phase = "Team Dispatch",
        objective = "Discover Truck A's hidden rule by testing packages.",
        mechanic = "Use accepted and rejected routes as evidence, then name the rule.",
        twist = "After enough tests the bay pauses until the rule is named."
    ),
    6 to FifthWallLevelGuidance(
        phase = "Team Dispatch",
        objective = "Route normally and record confidence after each result.",
        mechanic = "Choose Low, Medium, or High before the next package.",
        twist = "The line waits for the confidence check."
    ),
    7 to FifthWallLevelGuidance(
        phase = "Team Dispatch",
        objective = "Keep routing while dispatch commentary gets sharper.",
        mechanic = "Use the same manifest-to-rule loop under social pressure.",
        twist = "Chat can react to mistakes, but routing rules do not change."
    ),
    8 to FifthWallLevelGuidance(
        phase = "Probability Bay",
        objective = "Predict and route through a probabilistic truck.",
        mechanic = "Lock a count, then observe how many packages Truck A accepts.",
        twist = "Correct play is about expectation, not controlling each result."
    ),
    9 to FifthWallLevelGuidance(
        phase = "Team Dispatch",
        objective = "Answer or skip the dispatch prompt, then resume routing.",
        mechanic = "The bay pauses for chat before the conveyor reopens.",
        twist = "The package rules are ordinary; the interruption is the hard part."
    ),
    10 to FifthWallLevelGuidance(
        phase = "Multi-Rule Bay",
        objective = "Route through four overlapping truck rules.",
        mechanic = "Scan manifest fields in a consistent order before choosing.",
        twist = "Return Bin matters when none of four rules applies."
    ),
    11 to FifthWallLevelGuidance(
        phase = "Geometry Bay",
        objective = "Ship valid geometry and return impossible forms.",
        mechanic = "Use the manifest geometry field and the inspection dock model.",
        twist = "The focused package appears enlarged for visual inspection."
    ),
    12 to FifthWallLevelGuidance(
        phase = "Geometry Bay",
        objective = "Resolve geometry, color, and destination rules together.",
        mechanic = "A valid geometry can still match a non-geometry truck.",
        twist = "Impossible geometry still belongs in Return Bin unless another rule applies."
    ),
    13 to FifthWallLevelGuidance(
        phase = "Language Bay",
        objective = "Route precisely while labels become less direct.",
        mechanic = "Use manifest fields for routing; use label text as context.",
        twist = "Dispatch chat debates wording while the rules stay concrete."
    ),
    14 to FifthWallLevelGuidance(
        phase = "Rule-Shift Bay",
        objective = "Adapt when the rule board changes midstream.",
        mechanic = "Re-read Truck A and Truck B after the board update.",
        twist = "The cue is subtle, but the active rules update in the Rule Board."
    ),
    15 to FifthWallLevelGuidance(
        phase = "Rule-Shift Bay",
        objective = "Handle a larger midstream rule swap.",
        mechanic = "Treat the current board as source of truth after every route.",
        twist = "Old rules become wrong the moment the board changes."
    ),
    16 to FifthWallLevelGuidance(
        phase = "Discovery Bay",
        objective = "Test dispatch's theory before naming the hidden rule.",
        mechanic = "Look for edge cases that could prove the theory wrong.",
        twist = "The chat has a guess, but the bay only accepts evidence."
    ),
    17 to FifthWallLevelGuidance(
        phase = "Priority Bay",
        objective = "Route correctly while the bonus color changes.",
        mechanic = "Keep truck rules separate from the score priority.",
        twist = "When the priority flips, the best strategy flips too."
    ),
    18 to FifthWallLevelGuidance(
        phase = "Pressure Bay",
        objective = "Route four-rule packages after a dispatch pause.",
        mechanic = "Resume the same focus, inspect, compare, route loop.",
        twist = "The lane feels louder, but the control grammar is unchanged."
    ),
    19 to FifthWallLevelGuidance(
        phase = "Pressure Bay",
        objective = "Clear a larger four-rule queue with more rejects.",
        mechanic = "Use Return Bin confidently when no truck rule matches.",
        twist = "Speed pressure should not override the manifest."
    ),
    20 to FifthWallLevelGuidance(
        phase = "Fault Bay",
        objective = "Clear the final bay and recover if the console fails.",
        mechanic = "Route as usual until the room asks for a physical override.",
        twist = "A visible wrench repairs the jammed routing controls."
    )
)

private val FifthWallColors = listOf(
    FifthWallColor("red", "#ff6b6b"),
    FifthWallColor("blue", "#5aa9ff"),
    FifthWallColor("green", "#45e0a8"),
    FifthWallColor("yellow", "#f7b955"),
    FifthWallColor("gray", "#9fb0c8"),
    FifthWallColor("purple", "#b690ff")
)

private val FifthWallShapes = listOf("cube", "cylinder", "sphere", "rect")
private val FifthWallPatterns = listOf("solid", "striped", "dotted")
private val FifthWallDestinations = listOf("house", "office", "factory", "lab")
private val FifthWallValidGeometry = listOf("True prism", "Stable arch", "Clean cube")
private val FifthWallImpossibleGeometry = listOf("Penrose loop", "Escher stair", "Impossible trident")
private val FifthWallSemanticLabels = listOf(
    "housing insecurity",
    "justice-involved",
    "food insecure",
    "undocumented"
)

internal class FifthWallController {
    private val random = Random(System.currentTimeMillis().toInt())
    private var nextPackageId = 0
    private var telemetrySessionId = UUID.randomUUID().toString()
    private var telemetryRevision = 0
    private val telemetryEvents = mutableListOf<FifthWallTelemetryEvent>()
    private var metricAccumulator = FifthWallMetricAccumulator()
    private var sessionStartedAtMs = now()
    private var currentLevelStartedAtMs = sessionStartedAtMs
    private var shiftErrorsAfterChange = 0
    private var shiftAdaptationRecorded = false
    private var pressureCritiqueTriggered = false
    private var affectiveRecorded = false
    private var semanticRecorded = false
    private var falsificationSocialRecorded = false
    private var updateSpeedRecorded = false
    private var tribalStancePartA: String? = null

    private fun now(): Long = System.currentTimeMillis()

    private fun resetTelemetrySession() {
        telemetrySessionId = UUID.randomUUID().toString()
        telemetryRevision = 0
        telemetryEvents.clear()
        metricAccumulator = FifthWallMetricAccumulator()
        sessionStartedAtMs = now()
        currentLevelStartedAtMs = sessionStartedAtMs
        tribalStancePartA = null
        resetLevelTelemetry()
    }

    private fun resetLevelTelemetry() {
        currentLevelStartedAtMs = now()
        shiftErrorsAfterChange = 0
        shiftAdaptationRecorded = false
        pressureCritiqueTriggered = false
        affectiveRecorded = false
        semanticRecorded = false
        falsificationSocialRecorded = false
        updateSpeedRecorded = false
    }

    private fun emitTelemetry(
        type: String,
        levelId: Int,
        details: Map<String, String> = emptyMap()
    ) {
        telemetryRevision += 1
        telemetryEvents += FifthWallTelemetryEvent(
            id = "ev-$telemetryRevision",
            type = type,
            levelId = levelId,
            timestampMs = now(),
            details = details
        )
    }

    private fun completedLevels(state: FifthWallUiState): Int =
        when (state.prompt) {
            FifthWallPrompt.LevelComplete,
            FifthWallPrompt.GameComplete -> (state.levelIndex + 1).coerceAtMost(FifthWallLevels.size)
            FifthWallPrompt.Intro -> if (telemetryEvents.lastOrNull()?.type == "session_complete") FifthWallLevels.size else 0
            else -> state.levelIndex.coerceIn(0, FifthWallLevels.size)
        }

    private fun decorateState(base: FifthWallUiState): FifthWallUiState {
        val summary = metricAccumulator.toSummary(
            visibleScore = base.score,
            completedLevels = completedLevels(base),
            sessionDurationMs = now() - sessionStartedAtMs,
            generatedAtMs = now()
        )
        val payload = FifthWallTelemetryPayload(
            sessionId = telemetrySessionId,
            revision = telemetryRevision,
            events = telemetryEvents.toList(),
            summary = summary
        )
        return base.copy(
            telemetrySessionId = telemetrySessionId,
            telemetryPayloadJson = FifthWallTelemetryJson.encodeToString(payload),
            telemetryRevision = telemetryRevision
        )
    }

    private fun pushState(base: FifthWallUiState) {
        state.value = decorateState(base)
    }

    val state: MutableState<FifthWallUiState> = mutableStateOf(
        decorateState(
            buildLevelState(levelIndex = 0, score = 0, chatMessages = emptyList()).copy(
            prompt = FifthWallPrompt.Intro,
            feedback = "Dispatch console ready. Begin when you want the first shift.",
            feedbackTone = FifthWallFeedbackTone.Neutral
            )
        )
    )

    fun startShift() {
        resetTelemetrySession()
        emitTelemetry("session_started", levelId = 1, details = mapOf("entry" to "intro"))
        emitTelemetry("level_started", levelId = 1, details = mapOf("reason" to "begin_shift"))
        pushState(buildLevelState(levelIndex = 0, score = 0, chatMessages = emptyList()))
    }

    fun reset() {
        resetTelemetrySession()
        emitTelemetry("session_reset", levelId = 1)
        emitTelemetry("level_started", levelId = 1, details = mapOf("reason" to "reset"))
        pushState(buildLevelState(levelIndex = 0, score = 0, chatMessages = emptyList()).copy(
            feedback = "Shift reset. Focus a package and route it from the console."
        ))
    }

    fun selectPackage(packageId: String) {
        val current = state.value
        pushState(current.copy(
            selectedPackageId = packageId,
            draggedPackageId = null,
            dropTargetId = null,
            lastRouteTarget = null,
            lastRouteAccepted = null,
            feedback = "Package focused. Route it from the console or return bin.",
            feedbackTone = FifthWallFeedbackTone.Neutral
        ))
    }

    fun beginDrag(packageId: String) {
        val current = state.value
        if (current.queue.none { it.id == packageId }) return
        pushState(current.copy(
            selectedPackageId = packageId,
            draggedPackageId = packageId,
            dropTargetId = null,
            feedback = "Package picked up. Drop it on a truck or the return bin.",
            feedbackTone = FifthWallFeedbackTone.Neutral
        ))
    }

    fun hoverDropTarget(targetId: String?) {
        val current = state.value
        if (current.draggedPackageId == null || current.dropTargetId == targetId) return
        state.value = current.copy(dropTargetId = targetId)
    }

    fun endDrag() {
        val current = state.value
        if (current.draggedPackageId == null && current.dropTargetId == null) return
        pushState(current.copy(
            draggedPackageId = null,
            dropTargetId = null
        ))
    }

    fun fakeRestart() {
        val current = state.value
        if (!current.glitchActive) return
        emitTelemetry("fake_restart", levelId = FifthWallLevels[current.levelIndex].id)
        pushState(current.copy(
            feedback = "Restart looped. The problem is physical, not software.",
            feedbackTone = FifthWallFeedbackTone.Negative,
            logMessages = appendLog(current.logMessages, "Fake restart attempted during console glitch.")
        ))
    }

    fun repairGlitch() {
        val current = state.value
        if (!current.wrenchVisible) return
        metricAccumulator = metricAccumulator.copy(glitchRepairFound = true)
        emitTelemetry("glitch_repaired", levelId = FifthWallLevels[current.levelIndex].id)
        pushState(current.copy(
            glitchActive = false,
            wrenchVisible = false,
            draggedPackageId = null,
            dropTargetId = null,
            feedback = "Manual override complete. Routing controls restored.",
            feedbackTone = FifthWallFeedbackTone.Positive,
            logMessages = appendLog(current.logMessages, "Manual wrench override restored routing controls.")
        ))
    }

    fun updateChatDraft(value: String) {
        state.value = state.value.copy(chatDraft = value)
    }

    fun sendChatMessage() {
        val current = state.value
        if (!current.chatEnabled) return
        val levelId = FifthWallLevels[current.levelIndex].id
        val text = current.chatDraft.trim()
        if (text.isEmpty()) return
        val withPlayer = appendChat(current.chatMessages, FifthWallChatMessage("You", text, self = true))
        val response = when (levelId) {
            7 -> FifthWallChatMessage("Sarah_4521", "Copy. Keep the rule straight.")
            8 -> FifthWallChatMessage("Marcus_9103", "Variance is part of the game. Stay loose.")
            9 -> FifthWallChatMessage("Dev_2847", "That answer tells me more than the score does.")
            13 -> FifthWallChatMessage("Dev_2847", "Precision matters more than comfort when the label does real work.")
            16 -> FifthWallChatMessage("Sarah_4521", "Try a disconfirming case before you lock the rule.")
            17 -> FifthWallChatMessage("Marcus_9103", "Copy. Strategy updates faster than pride.")
            18, 19 -> FifthWallChatMessage("Dispatch", "Keep moving. The belt is not slowing down.")
            20 -> FifthWallChatMessage("Dispatch", "If the console fails, use the room, not the menu.")
            else -> FifthWallChatMessage("Dispatch", "Received.")
        }
        applyChatMetric(levelId, text)
        emitTelemetry(
            type = "chat_message",
            levelId = levelId,
            details = mapOf(
                "length" to text.length.toString(),
                "preview" to text.take(80)
            )
        )
        pushState(current.copy(
            chatDraft = "",
            chatMessages = appendChat(withPlayer, response),
            feedback = "Message sent to dispatch.",
            feedbackTone = FifthWallFeedbackTone.Neutral
        ))
    }

    fun updateRuleGuess(value: String) {
        state.value = state.value.copy(ruleGuessInput = value)
    }

    fun submitRuleGuess() {
        val current = state.value
        val level = FifthWallLevels[current.levelIndex]
        val matcher = level.hiddenRuleMatcher ?: return
        val guess = current.ruleGuessInput.trim()
        if (!matcher(guess)) {
            emitTelemetry(
                "rule_guess_failed",
                level.id,
                details = mapOf("guess" to guess.take(80))
            )
            pushState(current.copy(
                feedback = "Rule guess rejected. Keep testing edge cases.",
                feedbackTone = FifthWallFeedbackTone.Negative
            ))
            return
        }

        val nextPrompt = if (current.queue.isEmpty()) finishPromptForLevel(current.levelIndex) else FifthWallPrompt.None
        emitTelemetry(
            "rule_guess_submitted",
            level.id,
            details = mapOf("guess" to guess.take(80))
        )
        pushState(current.copy(
            hiddenRuleRevealed = true,
            ruleGuessInput = "",
            prompt = nextPrompt,
            feedback = "Hidden rule confirmed. Dispatch unlocked the label.",
            feedbackTone = FifthWallFeedbackTone.Positive,
            logMessages = appendLog(
                current.logMessages,
                "Hidden rule confirmed: ${level.trucks[level.hiddenRuleIndex ?: 0].text}."
            )
        ))
    }

    fun updatePredictionInput(value: String) {
        state.value = state.value.copy(predictionInput = value)
    }

    fun submitPrediction() {
        val current = state.value
        val level = FifthWallLevels[current.levelIndex]
        val guess = current.predictionInput.toIntOrNull()
        if (guess == null || guess !in 0..level.packageCount) {
            pushState(current.copy(
                feedback = "Enter a number between 0 and ${level.packageCount}.",
                feedbackTone = FifthWallFeedbackTone.Negative
            ))
            return
        }

        emitTelemetry(
            "probability_prediction",
            level.id,
            details = mapOf("guess" to guess.toString(), "count" to level.packageCount.toString())
        )
        pushState(current.copy(
            probabilityPrediction = guess,
            predictionInput = "",
            prompt = FifthWallPrompt.None,
            feedback = "Prediction locked: $guess/${level.packageCount}.",
            feedbackTone = FifthWallFeedbackTone.Positive,
            logMessages = appendLog(current.logMessages, "Prediction logged at $guess/${level.packageCount}.")
        ))
    }

    fun updateDiscussionReply(value: String) {
        state.value = state.value.copy(discussionReply = value)
    }

    fun submitDiscussionReply() {
        val current = state.value
        var chatMessages = current.chatMessages
        val reply = current.discussionReply.trim()
        if (reply.isNotEmpty()) {
            chatMessages = appendChat(chatMessages, FifthWallChatMessage("You", reply, self = true))
            chatMessages = appendChat(chatMessages, FifthWallChatMessage("Sarah_4521", "Noted. Back to the belt."))
        }
        val levelId = FifthWallLevels[current.levelIndex].id
        metricAccumulator = metricAccumulator.copy(
            socialDecouplingScoreSum = metricAccumulator.socialDecouplingScoreSum + classifyDiscussionReply(reply),
            socialDecouplingSamples = metricAccumulator.socialDecouplingSamples + 1
        )
        emitTelemetry(
            "discussion_reply",
            levelId,
            details = mapOf("reply" to if (reply.isBlank()) "<empty>" else reply.take(80))
        )
        pushState(current.copy(
            discussionReply = "",
            prompt = FifthWallPrompt.None,
            chatMessages = chatMessages,
            feedback = if (reply.isEmpty()) "Discussion skipped. Dispatch resumed the line." else "Discussion logged. Dispatch resumed the line.",
            feedbackTone = FifthWallFeedbackTone.Neutral
        ))
    }

    fun recordConfidence(label: String) {
        val current = state.value
        val pending = current.pendingConfidence ?: return
        val nextEntries = (current.confidenceEntries + "${pending.outcome} via ${pending.routeLabel} / confidence: $label").takeLast(6)
        val nextPrompt = if (current.queue.isEmpty()) finishPromptForLevel(current.levelIndex) else FifthWallPrompt.None
        val predicted = confidenceValue(label)
        val actual = if (pending.outcome == "Accepted") 1.0 else 0.0
        metricAccumulator = metricAccumulator.copy(
            confidenceSamples = metricAccumulator.confidenceSamples + 1,
            confidenceSquaredErrorSum = metricAccumulator.confidenceSquaredErrorSum + ((predicted - actual) * (predicted - actual))
        )
        emitTelemetry(
            "confidence_recorded",
            FifthWallLevels[current.levelIndex].id,
            details = mapOf("label" to label, "outcome" to pending.outcome, "route" to pending.routeLabel)
        )
        pushState(current.copy(
            pendingConfidence = null,
            confidenceEntries = nextEntries,
            prompt = nextPrompt,
            feedback = "Confidence logged as $label.",
            feedbackTone = FifthWallFeedbackTone.Positive,
            logMessages = appendLog(current.logMessages, "Confidence logged: $label after ${pending.outcome.lowercase()}.")
        ))
    }

    fun advanceLevel() {
        val current = state.value
        finalizeLevelTelemetry(current)
        val nextIndex = current.levelIndex + 1
        if (nextIndex >= FifthWallLevels.size) {
            emitTelemetry(
                "session_complete",
                FifthWallLevels[current.levelIndex].id,
                details = mapOf("score" to current.score.toString())
            )
            pushState(buildLevelState(levelIndex = 0, score = current.score, chatMessages = emptyList()).copy(
                prompt = FifthWallPrompt.Intro,
                feedback = "Shift complete. Restart when ready.",
                feedbackTone = FifthWallFeedbackTone.Neutral
            ))
            return
        }

        emitTelemetry(
            "level_completed",
            FifthWallLevels[current.levelIndex].id,
            details = mapOf(
                "durationMs" to (now() - currentLevelStartedAtMs).toString(),
                "score" to current.score.toString()
            )
        )
        resetLevelTelemetry()
        emitTelemetry(
            "level_started",
            FifthWallLevels[nextIndex].id,
            details = mapOf("reason" to "advance")
        )
        pushState(buildLevelState(
            levelIndex = nextIndex,
            score = current.score,
            chatMessages = current.chatMessages
        ))
    }

    fun routeToTruck(index: Int) {
        routeTruck(index = index, packageId = null, draggedOnly = false)
    }

    fun dropOnTruck(index: Int, packageId: String?) {
        routeTruck(index = index, packageId = packageId, draggedOnly = true)
    }

    fun routeToReturn() {
        routeReturn(packageId = null, draggedOnly = false)
    }

    fun dropOnReturn(packageId: String?) {
        routeReturn(packageId = packageId, draggedOnly = true)
    }

    private fun routeTruck(
        index: Int,
        packageId: String?,
        draggedOnly: Boolean
    ) {
        val current = state.value
        if (current.glitchActive) {
            pushState(current.copy(
                draggedPackageId = null,
                dropTargetId = null,
                feedback = "Routing controls are jammed. Find a physical override.",
                feedbackTone = FifthWallFeedbackTone.Negative
            ))
            return
        }
        val level = FifthWallLevels[current.levelIndex]
        val pkg = resolveRoutePackage(current, packageId, draggedOnly) ?: run {
            if (draggedOnly) {
                endDrag()
                return
            }
            pushState(current.copy(
                feedback = "Focus a package before routing.",
                feedbackTone = FifthWallFeedbackTone.Negative
            ))
            return
        }

        val rule = current.activeTrucks(level).getOrNull(index) ?: return
        val accepted = evaluateTruck(rule, pkg)
        val routeLabel = "Truck ${'A' + index}"
        pushState(postDecision(current, level, pkg, accepted, routeLabel))
    }

    private fun routeReturn(
        packageId: String?,
        draggedOnly: Boolean
    ) {
        val current = state.value
        if (current.glitchActive) {
            pushState(current.copy(
                draggedPackageId = null,
                dropTargetId = null,
                feedback = "Routing controls are jammed. Find a physical override.",
                feedbackTone = FifthWallFeedbackTone.Negative
            ))
            return
        }
        val level = FifthWallLevels[current.levelIndex]
        val pkg = resolveRoutePackage(current, packageId, draggedOnly) ?: run {
            if (draggedOnly) {
                endDrag()
                return
            }
            pushState(current.copy(
                feedback = "Focus a package before routing.",
                feedbackTone = FifthWallFeedbackTone.Negative
            ))
            return
        }

        val accepted = !matchesAnyTruck(current.activeTrucks(level), pkg)
        pushState(postDecision(current, level, pkg, accepted, "Return Bin"))
    }

    private fun resolveRoutePackage(
        current: FifthWallUiState,
        packageId: String?,
        draggedOnly: Boolean
    ): FifthWallPackage? {
        val explicitId = packageId?.takeIf { it.isNotBlank() }
        if (explicitId != null) {
            current.queue.firstOrNull { it.id == explicitId }?.let { return it }
        }
        if (draggedOnly) return current.draggedPackage()
        return current.draggedPackage() ?: current.selectedPackage()
    }

    private fun postDecision(
        current: FifthWallUiState,
        level: FifthWallLevel,
        pkg: FifthWallPackage,
        accepted: Boolean,
        routeLabel: String
    ): FifthWallUiState {
        val remaining = current.queue.filterNot { it.id == pkg.id }
        var chatMessages = current.chatMessages
        var logMessages = current.logMessages
        var feedback = if (accepted) {
            "$routeLabel accepted ${pkg.summaryLabel()}."
        } else {
            "$routeLabel rejected ${pkg.summaryLabel()}."
        }
        var feedbackTone = if (accepted) FifthWallFeedbackTone.Positive else FifthWallFeedbackTone.Negative
        val priorityBonus = if (accepted) {
            current.activePriorityRule
                ?.takeIf { it.colorName == pkg.color.name }
                ?.let { (it.multiplier - 1) * 100 }
                ?: 0
        } else {
            0
        }
        var score = current.score + if (accepted) 100 + priorityBonus else 0
        var probabilityAccepted = current.probabilityAccepted + if (accepted && level.id == 8) 1 else 0
        var testsUsed = current.testsUsed + if (level.hiddenRuleIndex != null && !current.hiddenRuleRevealed) 1 else 0
        var prompt: FifthWallPrompt = FifthWallPrompt.None
        var pendingConfidence: FifthWallPendingConfidence? = null
        var socialTriggered = current.socialPressureTriggered
        var ruleShifted = current.ruleShifted
        var priorityShifted = current.priorityShifted
        var activePriorityRule = current.activePriorityRule
        var glitchActive = current.glitchActive
        var glitchTriggered = current.glitchTriggered
        var wrenchVisible = current.wrenchVisible
        val wasShiftedBeforeDecision = current.ruleShifted
        val wasPriorityShiftedBeforeDecision = current.priorityShifted

        logMessages = appendLog(logMessages, feedback)

        if (priorityBonus > 0 && current.activePriorityRule != null) {
            feedback += " Bonus ${current.activePriorityRule.multiplier}x applied."
            logMessages = appendLog(logMessages, "Priority bonus applied to ${pkg.color.name} package.")
        }

        if (!accepted && level.socialPressure && !socialTriggered) {
            socialTriggered = true
            pressureCritiqueTriggered = true
            chatMessages = appendChat(chatMessages, FifthWallChatMessage("Sarah_4521", "That package was wrong. Read the rule again."))
            chatMessages = appendChat(chatMessages, FifthWallChatMessage("Marcus_9103", "You need to tighten up or the lane falls apart."))
        }

        if (level.confidenceCheck) {
            prompt = FifthWallPrompt.Confidence
            pendingConfidence = FifthWallPendingConfidence(
                outcome = if (accepted) "Accepted" else "Rejected",
                routeLabel = routeLabel
            )
        }

        if (level.hiddenRuleIndex != null && !current.hiddenRuleRevealed && testsUsed >= 6) {
            prompt = FifthWallPrompt.RuleGuess
            feedback = "Dispatch wants the hidden rule before continuing."
            feedbackTone = FifthWallFeedbackTone.Neutral
        }

        val processed = level.packageCount - remaining.size

        if (!ruleShifted && level.shiftAfterProcessed != null && level.shiftedTrucks.isNotEmpty() && processed >= level.shiftAfterProcessed) {
            ruleShifted = true
            shiftErrorsAfterChange = 0
            shiftAdaptationRecorded = false
            feedback = "Dispatch updated the rule board."
            feedbackTone = FifthWallFeedbackTone.Neutral
            logMessages = appendLog(logMessages, "Rule board changed mid-shift.")
            emitTelemetry("rule_shifted", level.id, details = mapOf("processed" to processed.toString()))
            when (level.id) {
                14 -> {
                    chatMessages = appendChat(chatMessages, FifthWallChatMessage("Sarah_4521", "Did the board just flip from red to blue?"))
                }

                15 -> {
                    chatMessages = appendChat(chatMessages, FifthWallChatMessage("Dev_2847", "Factory just disappeared. Read the wall again."))
                }
            }
        }

        if (!priorityShifted && level.priorityShiftAfterProcessed != null && level.shiftedPriorityRule != null && processed >= level.priorityShiftAfterProcessed) {
            priorityShifted = true
            activePriorityRule = level.shiftedPriorityRule
            updateSpeedRecorded = false
            feedback = level.shiftedPriorityRule.text
            feedbackTone = FifthWallFeedbackTone.Neutral
            logMessages = appendLog(logMessages, "Score priority changed: ${level.shiftedPriorityRule.text}")
            emitTelemetry("priority_shifted", level.id, details = mapOf("priority" to level.shiftedPriorityRule.colorName))
            if (level.id == 17) {
                chatMessages = appendChat(chatMessages, FifthWallChatMessage("Marcus_9103", "Oh. Blues are worth double. Switching strategy."))
            }
        }

        if (!glitchTriggered && level.glitchAfterProcessed != null && processed >= level.glitchAfterProcessed) {
            glitchTriggered = true
            glitchActive = true
            wrenchVisible = true
            feedback = "Console desynced. Drag and click routing just failed."
            feedbackTone = FifthWallFeedbackTone.Negative
            logMessages = appendLog(logMessages, "Console glitch triggered. Manual repair required.")
            chatMessages = appendChat(chatMessages, FifthWallChatMessage("Dispatch", "Control path lost. If you see hardware, use it."))
            emitTelemetry("glitch_triggered", level.id, details = mapOf("processed" to processed.toString()))
        }

        if (remaining.isEmpty()) {
            score += 500
            logMessages = appendLog(logMessages, "Level clear bonus awarded.")
        }

        if (level.hiddenRuleIndex != null && !current.hiddenRuleRevealed) {
            metricAccumulator = metricAccumulator.copy(
                hiddenRuleTests = metricAccumulator.hiddenRuleTests + 1,
                hiddenRuleRejects = metricAccumulator.hiddenRuleRejects + if (accepted) 0 else 1
            )
        }

        if (level.id == 1 && routeLabel == "Return Bin" && accepted) {
            metricAccumulator = metricAccumulator.copy(explorationReturnFound = true)
        }

        if (pkg.geometry != null) {
            metricAccumulator = metricAccumulator.copy(
                geometryChecks = metricAccumulator.geometryChecks + 1,
                geometryCorrect = metricAccumulator.geometryCorrect + if (accepted) 1 else 0
            )
        }

        if (wasShiftedBeforeDecision && !shiftAdaptationRecorded) {
            if (accepted) {
                metricAccumulator = metricAccumulator.copy(
                    ruleShiftSamples = metricAccumulator.ruleShiftSamples + 1,
                    ruleShiftErrorSum = metricAccumulator.ruleShiftErrorSum + shiftErrorsAfterChange
                )
                shiftAdaptationRecorded = true
            } else {
                shiftErrorsAfterChange += 1
            }
        }

        if (level.id == 17 && wasPriorityShiftedBeforeDecision && !updateSpeedRecorded && accepted) {
            metricAccumulator = metricAccumulator.copy(
                updateSpeedScore = if (current.activePriorityRule?.colorName == pkg.color.name) 1.0 else 0.0
            )
            updateSpeedRecorded = true
        }

        emitTelemetry(
            "package_routed",
            level.id,
            details = mapOf(
                "packageId" to pkg.id,
                "route" to routeLabel,
                "accepted" to accepted.toString(),
                "color" to pkg.color.name,
                "shape" to pkg.shape,
                "weight" to pkg.weight.toString(),
                "priorityBonus" to priorityBonus.toString(),
                "processed" to processed.toString(),
                "ruleShifted" to ruleShifted.toString(),
                "priorityShifted" to priorityShifted.toString(),
                "wasRuleShifted" to wasShiftedBeforeDecision.toString(),
                "wasPriorityShifted" to wasPriorityShiftedBeforeDecision.toString()
            )
        )

        if (remaining.isEmpty() && prompt == FifthWallPrompt.None) {
            prompt = finishPromptForLevel(current.levelIndex)
        }

        return current.copy(
            queue = remaining,
            selectedPackageId = remaining.firstOrNull()?.id,
            draggedPackageId = null,
            dropTargetId = null,
            score = score,
            testsUsed = testsUsed,
            ruleShifted = ruleShifted,
            priorityShifted = priorityShifted,
            activePriorityRule = activePriorityRule,
            chatMessages = chatMessages,
            logMessages = logMessages,
            feedback = feedback,
            feedbackTone = feedbackTone,
            lastRouteTarget = routeLabel,
            lastRouteAccepted = accepted,
            prompt = prompt,
            pendingConfidence = pendingConfidence,
            probabilityAccepted = probabilityAccepted,
            socialPressureTriggered = socialTriggered,
            glitchActive = glitchActive,
            glitchTriggered = glitchTriggered,
            wrenchVisible = wrenchVisible
        )
    }

    private fun buildLevelState(
        levelIndex: Int,
        score: Int,
        chatMessages: List<FifthWallChatMessage>
    ): FifthWallUiState {
        val level = FifthWallLevels[levelIndex]
        val queue = buildQueue(level)
        var nextChatMessages = chatMessages
        val chatEnabled = level.id >= 5

        if (chatEnabled && chatMessages.none { it.author == "Dispatch" }) {
            nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Dispatch", "Dispatch Team 7 online. Keep the lane clean."))
            nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Sarah_4521", "If the rules drift, call it out fast."))
        }

        when (level.id) {
            13 -> {
                nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Dev_2847", "This label just says 'housing insecurity'."))
                nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Dev_2847", "Why not just write homeless if that is what they mean?"))
            }

            14 -> {
                nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Marcus_9103", "Random question: should companies fire people for political beliefs?"))
                nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Sarah_4521", "No way. That is authoritarian."))
            }

            15 -> {
                nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Dev_2847", "Should companies fire people who refuse to use preferred pronouns?"))
                nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Marcus_9103", "Yeah, obviously. That is workplace harassment."))
            }

            16 -> {
                nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Sarah_4521", "I think Truck A is just packages over 5kg."))
                nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Sarah_4521", "I tested like six packages and they all worked."))
            }

            17 -> {
                nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Marcus_9103", "I think we should prioritize red packages first."))
            }

            20 -> {
                nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Dispatch", "Console noise is climbing. If controls fail, improvise."))
            }
        }

        if (level.prePrompt == FifthWallPrePrompt.TeamDiscussion) {
            when (level.id) {
                18 -> {
                    nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Dispatch", "Team needs your input before we speed the belt again."))
                    nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Sarah_4521", "Do we optimize for certainty or throughput?"))
                    nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Marcus_9103", "Throughput. The lane dies if we hesitate."))
                }

                else -> {
                    nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Sarah_4521", "Hypothetical: save 1 friend or 10 strangers?"))
                    nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Marcus_9103", "Ten strangers. It is just math."))
                    nextChatMessages = appendChat(nextChatMessages, FifthWallChatMessage("Dev_2847", "That answer is colder than the warehouse floor."))
                }
            }
        }

        return FifthWallUiState(
            levelIndex = levelIndex,
            score = score,
            queue = queue,
            selectedPackageId = queue.firstOrNull()?.id,
            testsUsed = 0,
            hiddenRuleRevealed = false,
            ruleShifted = false,
            priorityShifted = false,
            activePriorityRule = level.priorityRule,
            chatEnabled = chatEnabled,
            chatMessages = nextChatMessages,
            logMessages = appendLog(emptyList(), "Level ${level.id}: ${level.name}."),
            feedback = listOfNotNull(level.briefing, level.priorityRule?.text).joinToString(" "),
            feedbackTone = FifthWallFeedbackTone.Neutral,
            prompt = when (level.prePrompt) {
                FifthWallPrePrompt.ProbabilityPrediction -> FifthWallPrompt.ProbabilityPrediction
                FifthWallPrePrompt.TeamDiscussion -> FifthWallPrompt.TeamDiscussion
                null -> FifthWallPrompt.None
            },
            glitchActive = false,
            glitchTriggered = false,
            wrenchVisible = false
        )
    }

    private fun finalizeLevelTelemetry(current: FifthWallUiState) {
        val level = FifthWallLevels[current.levelIndex]
        when (level.id) {
            7 -> {
                if (pressureCritiqueTriggered && !affectiveRecorded) {
                    metricAccumulator = metricAccumulator.copy(
                        affectiveScoreSum = metricAccumulator.affectiveScoreSum + 0.5,
                        affectiveSamples = metricAccumulator.affectiveSamples + 1
                    )
                    affectiveRecorded = true
                }
            }

            8 -> {
                val prediction = current.probabilityPrediction
                val score = if (prediction != null) {
                    (1.0 - (abs(prediction - current.probabilityAccepted).toDouble() / level.packageCount.toDouble())).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                metricAccumulator = metricAccumulator.copy(probabilityScore = score)
            }

            13 -> {
                if (!semanticRecorded) {
                    metricAccumulator = metricAccumulator.copy(
                        semanticScoreSum = metricAccumulator.semanticScoreSum + 0.5,
                        semanticSamples = metricAccumulator.semanticSamples + 1
                    )
                    semanticRecorded = true
                }
            }

            14 -> {
                if (tribalStancePartA == null) {
                    tribalStancePartA = "none"
                }
            }

            15 -> {
                if (metricAccumulator.tribalScore == null) {
                    metricAccumulator = metricAccumulator.copy(
                        tribalScore = scoreTribalConsistency(tribalStancePartA, "none")
                    )
                }
            }

            16 -> {
                if (!falsificationSocialRecorded) {
                    metricAccumulator = metricAccumulator.copy(falsificationSocialScore = 0.0)
                    falsificationSocialRecorded = true
                }
            }

            17 -> {
                if (metricAccumulator.updateSpeedScore == null) {
                    metricAccumulator = metricAccumulator.copy(updateSpeedScore = 0.4)
                }
            }
        }
    }

    private fun applyChatMetric(levelId: Int, text: String) {
        when (levelId) {
            7 -> {
                if (pressureCritiqueTriggered && !affectiveRecorded) {
                    metricAccumulator = metricAccumulator.copy(
                        affectiveScoreSum = metricAccumulator.affectiveScoreSum + classifyAffectiveReply(text),
                        affectiveSamples = metricAccumulator.affectiveSamples + 1
                    )
                    affectiveRecorded = true
                }
            }

            13 -> {
                if (!semanticRecorded) {
                    metricAccumulator = metricAccumulator.copy(
                        semanticScoreSum = metricAccumulator.semanticScoreSum + classifySemanticReply(text),
                        semanticSamples = metricAccumulator.semanticSamples + 1
                    )
                    semanticRecorded = true
                }
            }

            14 -> {
                tribalStancePartA = classifyTribalStance(text)
            }

            15 -> {
                metricAccumulator = metricAccumulator.copy(
                    tribalScore = scoreTribalConsistency(tribalStancePartA, classifyTribalStance(text))
                )
            }

            16 -> {
                if (!falsificationSocialRecorded) {
                    metricAccumulator = metricAccumulator.copy(
                        falsificationSocialScore = classifyFalsificationReply(text)
                    )
                    falsificationSocialRecorded = true
                }
            }
        }
    }

    private fun confidenceValue(label: String): Double =
        when (label.lowercase()) {
            "low" -> 0.25
            "medium" -> 0.6
            "high" -> 0.85
            else -> 0.5
        }

    private fun classifyDiscussionReply(text: String): Double {
        val normalized = text.lowercase().trim()
        if (normalized.isEmpty()) return 0.5
        return when {
            normalized.contains("depend") || normalized.contains("both") || normalized.contains("tradeoff") || normalized.contains("context") -> 1.0
            normalized.contains("friend") || normalized.contains("stranger") || normalized.contains("ten") || normalized.contains("10") -> 0.2
            else -> 0.5
        }
    }

    private fun classifyAffectiveReply(text: String): Double {
        val normalized = text.lowercase()
        return when {
            listOf("copy", "understood", "recheck", "adjusting", "reading again").any { normalized.contains(it) } -> 1.0
            listOf("stupid", "shut", "wrong about me", "idiot", "whatever").any { normalized.contains(it) } -> 0.0
            else -> 0.5
        }
    }

    private fun classifySemanticReply(text: String): Double {
        val normalized = text.lowercase()
        return when {
            (normalized.contains("precision") || normalized.contains("specific") || normalized.contains("clear")) &&
                (normalized.contains("respect") || normalized.contains("tradeoff") || normalized.contains("both")) -> 1.0
            normalized.contains("respectful") || normalized.contains("dehumanizing") -> 0.0
            normalized.contains("stupid") || normalized.contains("dumb") || normalized.contains("annoying") -> 0.6
            else -> 0.5
        }
    }

    private fun classifyTribalStance(text: String): String {
        val normalized = text.lowercase()
        return when {
            normalized.isBlank() -> "none"
            normalized.contains("depend") || normalized.contains("context") || normalized.contains("case by case") -> "depends"
            normalized.contains("should") && normalized.contains("not") -> "no"
            normalized.contains("no") || normalized.contains("shouldn't") || normalized.contains("should not") -> "no"
            normalized.contains("yes") || normalized.contains("should") || normalized.contains("allowed") -> "yes"
            else -> "unclear"
        }
    }

    private fun scoreTribalConsistency(partA: String?, partB: String?): Double =
        when {
            partA == null && partB == null -> 0.5
            partA == null || partB == null -> 0.5
            partA == "unclear" || partB == "unclear" -> 0.5
            partA == "none" && partB == "none" -> 0.5
            partA == partB -> 1.0
            else -> 0.0
        }

    private fun classifyFalsificationReply(text: String): Double {
        val normalized = text.lowercase()
        return when {
            listOf("exactly", "under 5", "below 5", "what about 5", "edge case", "disconfirm").any { normalized.contains(it) } -> 1.0
            listOf("all over", "which packages", "what range", "clarify").any { normalized.contains(it) } -> 0.7
            else -> 0.0
        }
    }

    private fun buildQueue(level: FifthWallLevel): List<FifthWallPackage> {
        scriptedQueue(level)?.let { return it }

        val split = level.shiftAfterProcessed?.takeIf { it in 1 until level.packageCount }
        if (split != null && level.shiftedTrucks.isNotEmpty()) {
            return buildQueueSegment(
                level = level,
                rules = level.trucks,
                count = split,
                forceReject = level.forceReject
            ) + buildQueueSegment(
                level = level,
                rules = level.shiftedTrucks,
                count = level.packageCount - split,
                forceReject = false
            )
        }

        return buildQueueSegment(
            level = level,
            rules = level.trucks,
            count = level.packageCount,
            forceReject = level.forceReject
        )
    }

    private fun scriptedQueue(level: FifthWallLevel): List<FifthWallPackage>? =
        when (level.id) {
            1 -> listOf(
                scriptedPackage("red", "cube", "solid", "house", weight = 13, volume = 20),
                scriptedPackage("blue", "rect", "striped", "office", weight = 6, volume = 17),
                scriptedPackage("gray", "cylinder", "dotted", "factory", weight = 11, volume = 29),
                scriptedPackage("blue", "sphere", "solid", "lab", weight = 4, volume = 14),
                scriptedPackage("red", "rect", "dotted", "office", weight = 8, volume = 24),
                scriptedPackage("blue", "cylinder", "striped", "house", weight = 10, volume = 31),
                scriptedPackage("red", "sphere", "solid", "factory", weight = 3, volume = 11),
                scriptedPackage("blue", "cube", "dotted", "lab", weight = 9, volume = 22),
                scriptedPackage("red", "cylinder", "striped", "office", weight = 12, volume = 34),
                scriptedPackage("blue", "rect", "solid", "house", weight = 5, volume = 16)
            )

            else -> null
        }

    private fun scriptedPackage(
        colorName: String,
        shape: String,
        pattern: String,
        destination: String,
        weight: Int,
        volume: Int,
        geometry: String? = null,
        validGeometry: Boolean = true,
        labelText: String? = null
    ): FifthWallPackage =
        FifthWallPackage(
            id = "pkg-${nextPackageId++}",
            color = FifthWallColors.first { it.name == colorName },
            shape = shape,
            pattern = pattern,
            destination = destination,
            weight = weight,
            volume = volume,
            geometry = geometry,
            validGeometry = validGeometry,
            labelText = labelText
        )

    private fun buildQueueSegment(
        level: FifthWallLevel,
        rules: List<FifthWallRule>,
        count: Int,
        forceReject: Boolean
    ): List<FifthWallPackage> {
        if (count <= 0) return emptyList()
        val queue = mutableListOf<FifthWallPackage>()
        var forceRejectPending = forceReject
        repeat(count) { index ->
            val shouldForceReject = forceRejectPending && index == count / 2
            val pkg = when {
                shouldForceReject -> {
                    forceRejectPending = false
                    buildRejectPackage(level, rules)
                }

                rules.singleOrNull()?.kind == FifthWallRuleKind.Probability -> randomPackage(level)
                random.nextDouble() < level.rejectRate -> buildRejectPackage(level, rules)
                else -> buildMatchingPackage(level, rules)
            }
            queue += pkg
        }
        return queue
    }

    private fun buildMatchingPackage(
        level: FifthWallLevel,
        rules: List<FifthWallRule>
    ): FifthWallPackage {
        val pkg = randomPackage(level)
        val rule = rules[random.nextInt(rules.size)]
        return applyRule(pkg, rule)
    }

    private fun buildRejectPackage(
        level: FifthWallLevel,
        rules: List<FifthWallRule>
    ): FifthWallPackage {
        repeat(30) {
            val candidate = randomPackage(level)
            if (!matchesAnyTruck(rules, candidate)) return candidate
        }
        return randomPackage(level, colorName = "gray")
    }

    private fun randomPackage(
        level: FifthWallLevel,
        colorName: String? = null
    ): FifthWallPackage {
        val color = colorName?.let { name -> FifthWallColors.firstOrNull { it.name == name } } ?: FifthWallColors.random(random)
        val geometry = when (level.id) {
            11, 12, 20 -> randomGeometry(valid = random.nextBoolean())
            else -> null
        }
        return FifthWallPackage(
            id = "pkg-${nextPackageId++}",
            color = color,
            shape = FifthWallShapes.random(random),
            pattern = FifthWallPatterns.random(random),
            destination = FifthWallDestinations.random(random),
            weight = random.nextInt(1, 16),
            volume = random.nextInt(5, 41),
            geometry = geometry?.first,
            validGeometry = geometry?.second ?: true,
            labelText = if (level.id == 13) FifthWallSemanticLabels.random(random) else null
        )
    }

    private fun randomGeometry(valid: Boolean): Pair<String, Boolean> {
        val name = if (valid) FifthWallValidGeometry.random(random) else FifthWallImpossibleGeometry.random(random)
        return name to valid
    }

    private fun applyRule(pkg: FifthWallPackage, rule: FifthWallRule): FifthWallPackage =
        when (rule.kind) {
            FifthWallRuleKind.Color -> {
                val color = FifthWallColors.first { it.name == rule.value }
                pkg.copy(color = color)
            }
            FifthWallRuleKind.Shape -> pkg.copy(shape = rule.value.orEmpty())
            FifthWallRuleKind.Pattern -> pkg.copy(pattern = rule.value.orEmpty())
            FifthWallRuleKind.Destination -> pkg.copy(destination = rule.value.orEmpty())
            FifthWallRuleKind.Geometry -> {
                val valid = rule.value == "valid"
                val geometry = randomGeometry(valid)
                pkg.copy(
                    geometry = geometry.first,
                    validGeometry = geometry.second
                )
            }
            FifthWallRuleKind.Weight -> pkg.copy(weight = satisfy(rule.comparator, rule.threshold, minimum = 1, maximum = 15))
            FifthWallRuleKind.Volume -> pkg.copy(volume = satisfy(rule.comparator, rule.threshold, minimum = 5, maximum = 40))
            FifthWallRuleKind.Probability -> pkg
        }

    private fun satisfy(
        comparator: String?,
        threshold: Int?,
        minimum: Int,
        maximum: Int
    ): Int {
        val base = threshold ?: minimum
        return when (comparator) {
            ">" -> (base + random.nextInt(1, 5)).coerceIn(minimum, maximum)
            ">=" -> (base + random.nextInt(0, 5)).coerceIn(minimum, maximum)
            "<" -> (base - random.nextInt(1, 5)).coerceIn(minimum, maximum)
            "<=" -> (base - random.nextInt(0, 5)).coerceIn(minimum, maximum)
            else -> base.coerceIn(minimum, maximum)
        }
    }

    private fun evaluateTruck(rule: FifthWallRule, pkg: FifthWallPackage): Boolean =
        when (rule.kind) {
            FifthWallRuleKind.Color -> pkg.color.name == rule.value
            FifthWallRuleKind.Shape -> pkg.shape == rule.value
            FifthWallRuleKind.Pattern -> pkg.pattern == rule.value
            FifthWallRuleKind.Destination -> pkg.destination == rule.value
            FifthWallRuleKind.Geometry -> when (rule.value) {
                "valid" -> pkg.validGeometry
                "invalid" -> !pkg.validGeometry
                else -> false
            }
            FifthWallRuleKind.Weight -> compare(pkg.weight, rule.comparator, rule.threshold)
            FifthWallRuleKind.Volume -> compare(pkg.volume, rule.comparator, rule.threshold)
            FifthWallRuleKind.Probability -> random.nextDouble() <= (rule.probability ?: 0.0)
        }

    private fun matchesAnyTruck(rules: List<FifthWallRule>, pkg: FifthWallPackage): Boolean =
        rules.any { rule -> evaluateTruckForReject(rule, pkg) }

    private fun evaluateTruckForReject(rule: FifthWallRule, pkg: FifthWallPackage): Boolean =
        when (rule.kind) {
            FifthWallRuleKind.Probability -> true
            else -> evaluateTruck(rule, pkg)
        }

    private fun compare(value: Int, comparator: String?, threshold: Int?): Boolean {
        val target = threshold ?: return false
        return when (comparator) {
            ">" -> value > target
            ">=" -> value >= target
            "<" -> value < target
            "<=" -> value <= target
            "==" -> value == target
            else -> false
        }
    }

    private fun finishPromptForLevel(levelIndex: Int): FifthWallPrompt =
        if (levelIndex >= FifthWallLevels.lastIndex) FifthWallPrompt.GameComplete else FifthWallPrompt.LevelComplete

    private fun appendLog(existing: List<String>, entry: String): List<String> =
        (existing + entry).takeLast(7)

    private fun appendChat(existing: List<FifthWallChatMessage>, entry: FifthWallChatMessage): List<FifthWallChatMessage> =
        (existing + entry).takeLast(12)
}

internal fun FifthWallUiState.selectedPackage(): FifthWallPackage? =
    queue.firstOrNull { it.id == selectedPackageId } ?: queue.firstOrNull()

internal fun FifthWallUiState.draggedPackage(): FifthWallPackage? =
    queue.firstOrNull { it.id == draggedPackageId }

internal fun FifthWallUiState.focusPackage(): FifthWallPackage? =
    draggedPackage() ?: selectedPackage()

internal fun FifthWallUiState.activeTrucks(level: FifthWallLevel): List<FifthWallRule> =
    if (ruleShifted && level.shiftedTrucks.isNotEmpty()) level.shiftedTrucks else level.trucks

internal fun FifthWallUiState.visiblePackages(): List<FifthWallPackage> =
    queue.take(3)

internal fun FifthWallUiState.processedCount(level: FifthWallLevel): Int =
    level.packageCount - queue.size

internal fun FifthWallLevel.guidance(): FifthWallLevelGuidance =
    FifthWallGuidanceByLevel[id] ?: FifthWallLevelGuidance(
        phase = "Dispatch Bay",
        objective = briefing,
        mechanic = "Focus, inspect, compare, then route or return.",
        twist = "Use the current Rule Board as source of truth."
    )

internal fun FifthWallRule.label(isRevealed: Boolean): String =
    if (!isRevealed) {
        "Rule: ???"
    } else {
        when (kind) {
            FifthWallRuleKind.Shape -> "Shape: ${value?.fifthWallShapeLabel() ?: "Unknown"}"
            else -> text
        }
    }

internal fun FifthWallPriorityRule.badgeLabel(): String =
    "${colorName.replaceFirstChar { it.uppercase() }} x$multiplier"

internal fun String.fifthWallShapeLabel(): String =
    when (lowercase()) {
        "cube" -> "Cube Crate"
        "rect" -> "Rect Parcel"
        "cylinder" -> "Cylinder Drum"
        "sphere" -> "Sphere Orb"
        else -> replaceFirstChar { it.uppercase() }
    }

internal fun FifthWallPackage.shapeDisplayLabel(): String =
    shape.fifthWallShapeLabel()

internal fun FifthWallPackage.summaryLabel(): String =
    "${color.name.replaceFirstChar { it.uppercase() }} ${shapeDisplayLabel()}"
