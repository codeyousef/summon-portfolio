package code.yousef.portfolio.ui.fifthwall

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FifthWallGameTest {
    @Test
    fun correctRoutesAwardBasePointsAndCappedStreakBonuses() {
        val controller = FifthWallController(seed = 11)
        controller.startShift()

        repeat(FifthWallLevels.first().packageCount) {
            routeSelectedPackageCorrectly(controller)
        }

        val state = controller.state.value
        assertEquals(FifthWallPrompt.LevelComplete, state.prompt)
        assertEquals(2_250, state.score)
        assertEquals(10, state.correctDecisions)
        assertEquals(10, state.countedDecisions)
        assertEquals(10, state.bestStreak)
        assertTrue(state.feedback.contains("BAY CLEAR +500"))
    }

    @Test
    fun incorrectNormalRouteConsumesPackageAndAppliesFloorBoundedPenalty() {
        val controller = FifthWallController(seed = 23)
        controller.startShift()
        routeSelectedPackageIncorrectly(controller)

        val first = controller.state.value
        assertEquals(0, first.score)
        assertEquals(1, first.countedDecisions)
        assertEquals(0, first.correctDecisions)
        assertEquals(0, first.streak)
        assertEquals(9, first.queue.size)
        assertEquals("ROUTE REJECTED  0", first.feedback)

        routeSelectedPackageCorrectly(controller)
        routeSelectedPackageIncorrectly(controller)
        val third = controller.state.value
        assertEquals(25, third.score)
        assertEquals(3, third.countedDecisions)
        assertEquals(1, third.correctDecisions)
        assertEquals(0, third.streak)
        assertEquals("ROUTE REJECTED  -75", third.feedback)
    }

    @Test
    fun hiddenRuleExperimentsDoNotChangeScoreAccuracyOrStreak() {
        val controller = controllerAtBay(5, seed = 31)
        val before = controller.state.value

        controller.routeToTruck(0)

        val after = controller.state.value
        assertEquals(before.score, after.score)
        assertEquals(before.correctDecisions, after.correctDecisions)
        assertEquals(before.countedDecisions, after.countedDecisions)
        assertEquals(before.streak, after.streak)
        assertEquals(before.queue.size - 1, after.queue.size)
        assertEquals("EXPERIMENT RECORDED  +0", after.feedback)
    }

    @Test
    fun probabilityRejectionsAreEvidenceRatherThanMistakes() {
        val rejectedState = (0..128).firstNotNullOfOrNull { seed ->
            val controller = controllerAtBay(8, seed)
            controller.routeToTruck(0)
            controller.state.value.takeIf { it.lastRouteAccepted == false }
        }

        assertNotNull(rejectedState)
        assertEquals(0, rejectedState.score)
        assertEquals(0, rejectedState.correctDecisions)
        assertEquals(0, rejectedState.countedDecisions)
        assertEquals(0, rejectedState.streak)
        assertEquals("EXPERIMENT RECORDED  +0", rejectedState.feedback)
    }

    @Test
    fun decisionTimeIsRecordedWithoutChangingOutcome() {
        var now = 1_000_000L
        val controller = FifthWallController(seed = 41, clock = { now })
        controller.startShift()
        now += 1_650L

        routeSelectedPackageCorrectly(controller)

        val state = controller.state.value
        assertEquals(1_650L, state.decisionTimeTotalMs)
        assertEquals(1, state.decisionTimeSamples)
        assertEquals("1.6s", state.averageDecisionSecondsLabel())
    }

    @Test
    fun dedicatedSoundPreferenceOverridesTheOlderCheckpointValue() {
        val now = 3_000_000L
        val controller = FifthWallController(
            seed = 43,
            initialCheckpoint = FifthWallCheckpoint(
                nextBay = 4,
                score = 500,
                correctDecisions = 3,
                countedDecisions = 4,
                bestStreak = 2,
                campaignCompleted = false,
                soundMode = FifthWallSoundMode.LOW,
                savedAtMs = now
            ),
            initialSoundMode = FifthWallSoundMode.MUTE,
            clock = { now }
        )

        assertEquals(FifthWallSoundMode.MUTE, controller.state.value.soundMode)
        controller.resumeShift()
        assertEquals(FifthWallSoundMode.MUTE, controller.state.value.soundMode)
        assertEquals(3, controller.state.value.levelIndex)
    }

    @Test
    fun everyCampaignBayBuildsItsDeclaredQueueAndStartsInCanvas() {
        FifthWallLevels.forEachIndexed { index, level ->
            val controller = controllerAtBay(index + 1, seed = 100 + index)
            val state = controller.state.value
            assertEquals(index, state.levelIndex)
            assertEquals(level.packageCount, state.queue.size)
            val expectedPrompt = when (level.prePrompt) {
                FifthWallPrePrompt.ProbabilityPrediction -> FifthWallPrompt.ProbabilityPrediction
                FifthWallPrePrompt.TeamDiscussion -> FifthWallPrompt.TeamDiscussion
                null -> FifthWallPrompt.None
            }
            assertEquals(expectedPrompt, state.prompt)
            assertFalse(state.campaignCompleted)
        }
    }

    private fun routeSelectedPackageCorrectly(controller: FifthWallController) {
        val state = controller.state.value
        val level = FifthWallLevels[state.levelIndex]
        val pkg = state.selectedPackage() ?: error("Expected an active package")
        val index = state.activeTrucks(level).indexOfFirst { rule -> matches(rule, pkg) }
        if (index >= 0) controller.routeToTruck(index) else controller.routeToReturn()
    }

    private fun routeSelectedPackageIncorrectly(controller: FifthWallController) {
        val state = controller.state.value
        val level = FifthWallLevels[state.levelIndex]
        val pkg = state.selectedPackage() ?: error("Expected an active package")
        val rules = state.activeTrucks(level)
        val wrongIndex = rules.indexOfFirst { rule -> !matches(rule, pkg) }
        if (wrongIndex >= 0) {
            controller.routeToTruck(wrongIndex)
        } else {
            controller.routeToReturn()
        }
    }

    private fun matches(rule: FifthWallRule, pkg: FifthWallPackage): Boolean = when (rule.kind) {
        FifthWallRuleKind.Color -> pkg.color.name == rule.value
        FifthWallRuleKind.Shape -> pkg.shape == rule.value
        FifthWallRuleKind.Pattern -> pkg.pattern == rule.value
        FifthWallRuleKind.Destination -> pkg.destination == rule.value
        FifthWallRuleKind.Geometry -> if (rule.value == "valid") pkg.validGeometry else !pkg.validGeometry
        FifthWallRuleKind.Weight -> compare(pkg.weight, rule.comparator, rule.threshold)
        FifthWallRuleKind.Volume -> compare(pkg.volume, rule.comparator, rule.threshold)
        FifthWallRuleKind.Probability -> false
    }

    private fun compare(value: Int, comparator: String?, threshold: Int?): Boolean = when (comparator) {
        ">" -> value > requireNotNull(threshold)
        ">=" -> value >= requireNotNull(threshold)
        "<" -> value < requireNotNull(threshold)
        "<=" -> value <= requireNotNull(threshold)
        "==" -> value == requireNotNull(threshold)
        else -> false
    }

    private fun controllerAtBay(bay: Int, seed: Int): FifthWallController {
        val now = 2_000_000L
        val controller = FifthWallController(
            seed = seed,
            initialCheckpoint = FifthWallCheckpoint(
                nextBay = bay,
                score = 0,
                correctDecisions = 0,
                countedDecisions = 0,
                bestStreak = 0,
                campaignCompleted = false,
                soundMode = FifthWallSoundMode.LOW,
                savedAtMs = now
            ),
            clock = { now }
        )
        controller.resumeShift()
        return controller
    }
}
