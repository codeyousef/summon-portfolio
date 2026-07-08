package code.yousef.portfolio.ui.fifthwall

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.modifier.Display
import codes.yousef.summon.modifier.Modifier
import codes.yousef.summon.modifier.className
import codes.yousef.summon.modifier.dataAttribute
import codes.yousef.summon.modifier.display
import codes.yousef.summon.modifier.id

@Composable
internal fun FifthWallPage(
    controller: FifthWallController,
    state: FifthWallUiState
) {
    val level = FifthWallLevels[state.levelIndex.coerceIn(FifthWallLevels.indices)]
    val focusedPackage = state.focusPackage()
    val sceneClasses = buildString {
        append("fw-scene-shell is-canvas-only")
        if (state.glitchActive) append(" is-glitched")
        if (state.ruleShifted || state.priorityShifted) append(" is-shifted")
    }

    FifthWallScaffold {
        Box(modifier = Modifier().className("fw-root is-canvas-only")) {
            Box(modifier = Modifier().className("fw-stage is-canvas-only")) {
                Box(modifier = Modifier().className("fw-primary is-canvas-only")) {
                    Box(modifier = Modifier().className(sceneClasses)) {
                        FifthWallScene(
                            controller = controller,
                            level = level,
                            state = state,
                            focusedPackage = focusedPackage
                        )
                        TelemetryBridge(state = state)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryBridge(state: FifthWallUiState) {
    Box(
        modifier = Modifier()
            .id("fw-telemetry")
            .display(Display.None)
            .dataAttribute("session", state.telemetrySessionId)
            .dataAttribute("revision", state.telemetryRevision.toString())
    ) {
        Text(text = state.telemetryPayloadJson)
    }
}
