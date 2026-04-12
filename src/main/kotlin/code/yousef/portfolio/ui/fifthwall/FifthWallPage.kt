package code.yousef.portfolio.ui.fifthwall

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.foundation.RawHtml
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.modifier.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val FifthWallActionPath = "/fifth-wall"

@Composable
internal fun FifthWallPage(state: FifthWallUiState) {
    val level = FifthWallLevels[state.levelIndex]
    val focusedPackage = state.focusPackage()

    FifthWallScaffold {
        Box(modifier = Modifier().className("fw-root")) {
            TopBar(level = level, state = state)

            Box(modifier = Modifier().className("fw-stage")) {
                Column(modifier = Modifier().className("fw-primary")) {
                    Box(
                        modifier = Modifier().className(
                            buildString {
                                append("fw-scene-shell")
                                if (state.glitchActive) append(" is-glitched")
                            }
                        )
                    ) {
                        FifthWallScene(level = level, state = state, focusedPackage = focusedPackage)
                        SceneOverlay(level = level, state = state, focusedPackage = focusedPackage)
                        SceneFlowStrip(state = state, focusedPackage = focusedPackage)
                        SceneTargetStrip(level = level, state = state, focusedPackage = focusedPackage)
                        if (state.wrenchVisible) {
                            WrenchButton()
                        }
                    }

                    FeedbackBanner(state = state)
                    QueueDeck(state = state, focusedPackage = focusedPackage)
                    RouteConsole(level = level, state = state, focusedPackage = focusedPackage)
                }

                Column(modifier = Modifier().className("fw-side")) {
                    PlayLoopPanel(level = level, state = state, focusedPackage = focusedPackage)
                    BriefingPanel(level = level, state = state)
                    RuleBoard(level = level, state = state)
                    ManifestCard(selectedPackage = focusedPackage)
                    LogCard(entries = state.logMessages)
                    ChatCard(state = state)
                }
            }

            if (state.prompt != FifthWallPrompt.None) {
                PromptOverlay(state = state, level = level)
            }

            TelemetryBridge(state = state)
        }
    }
}

@Composable
private fun TopBar(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    Box(modifier = Modifier().className("fw-topbar")) {
        Box(modifier = Modifier().className("fw-brand")) {
            Text(text = "FIFTH WALL", modifier = Modifier().className("fw-title"))
            Text(text = "Courier Protocol 3D", modifier = Modifier().className("fw-subtitle"))
        }

        Box(modifier = Modifier().className("fw-stats")) {
            StatCell(label = "Level", value = "${level.id}/${FifthWallLevels.size}")
            StatCell(label = "Score", value = state.score.toString())
            StatCell(label = "Processed", value = "${state.processedCount(level)}/${level.packageCount}")
        }

        Box(modifier = Modifier().className("fw-actions")) {
            if (state.glitchActive) {
                ActionControl(
                    label = "Restart console",
                    action = "fake-restart",
                    classes = "fw-action-link fw-ui-btn-danger"
                )
            }
            ActionControl(
                label = "Reset shift",
                action = "reset",
                classes = "fw-action-link fw-ui-btn"
            )
            AnchorLink(
                label = "Exit",
                href = "/experiments",
                modifier = Modifier().className("fw-link-btn"),
                navigationMode = LinkNavigationMode.Native
            )
        }
    }
}

@Composable
private fun SceneOverlay(
    level: FifthWallLevel,
    state: FifthWallUiState,
    focusedPackage: FifthWallPackage?
) {
    Column(modifier = Modifier().className("fw-scene-hud fw-scene-hud-left")) {
        Text(text = "Warehouse Bay", modifier = Modifier().className("fw-scene-heading"))
        Text(
            text = if (focusedPackage == null) {
                "1 Focus a package in Conveyor Queue. 2 Read Package Manifest. 3 Route it to a truck or Return Bin."
            } else {
                "Focused package: ${focusedPackage.summaryLabel()}. Read its manifest, match the rule board, then send it through Bay Targets below."
            },
            modifier = Modifier().className("fw-scene-copy")
        )
        Box(modifier = Modifier().className("fw-scene-chip-row")) {
            SceneChip("Queue selects focus")
            SceneChip("Manifest on right")
            SceneChip("Route below")
            if (state.ruleShifted) SceneChip("Rules shifted")
            if (state.glitchActive) SceneChip("Console fault")
            if (focusedPackage?.geometry != null) SceneChip("Inspection active")
        }
    }

    Column(modifier = Modifier().className("fw-scene-hud fw-scene-hud-right")) {
        Text(text = level.name, modifier = Modifier().className("fw-scene-badge"))
        Text(
            text = if (state.chatEnabled) "Dispatch link live" else "Read manifest, then route",
            modifier = Modifier().className("fw-scene-copy is-muted")
        )
    }
}

@Composable
private fun SceneChip(text: String) {
    Box(modifier = Modifier().className("fw-scene-chip")) {
        Text(text = text)
    }
}

@Composable
private fun SceneFlowStrip(
    state: FifthWallUiState,
    focusedPackage: FifthWallPackage?
) {
    val routeText = if (focusedPackage == null) {
        "Route after you focus a package"
    } else {
        "Route ${focusedPackage.summaryLabel()}"
    }

    Box(modifier = Modifier().className("fw-scene-flow")) {
        SceneFlowStep(number = "1", text = "Focus from queue")
        SceneFlowStep(number = "2", text = "Read manifest + rule board")
        SceneFlowStep(
            number = "3",
            text = if (state.prompt == FifthWallPrompt.None) routeText else "Clear prompt, then route"
        )
    }
}

@Composable
private fun SceneFlowStep(
    number: String,
    text: String
) {
    Box(modifier = Modifier().className("fw-scene-flow-step")) {
        Box(modifier = Modifier().className("fw-scene-flow-index")) {
            Text(text = number)
        }
        Text(text = text, modifier = Modifier().className("fw-scene-flow-copy"))
    }
}

@Composable
private fun SceneTargetStrip(
    level: FifthWallLevel,
    state: FifthWallUiState,
    focusedPackage: FifthWallPackage?
) {
    val controlsBlocked = state.prompt != FifthWallPrompt.None

    Column(modifier = Modifier().className("fw-scene-target-strip")) {
        Text(text = "3 Route Target", modifier = Modifier().className("fw-scene-target-title"))
        Box(modifier = Modifier().className("fw-scene-target-grid")) {
            state.activeTrucks(level).forEachIndexed { index, _ ->
                val label = "Truck ${'A' + index}"
                ActionControl(
                    label = label,
                    action = "route-truck",
                    classes = buildSceneTargetClasses(
                        isActive = state.lastRouteTarget == label,
                        wasAccepted = state.lastRouteAccepted
                    ),
                    disabled = focusedPackage == null || controlsBlocked,
                    params = arrayOf("truck" to index.toString())
                )
            }
            ActionControl(
                label = "Return Bin",
                action = "route-return",
                classes = buildSceneTargetClasses(
                    isActive = state.lastRouteTarget == "Return Bin",
                    wasAccepted = state.lastRouteAccepted,
                    isReturn = true
                ),
                disabled = focusedPackage == null || controlsBlocked
            )
        }
        Text(
            text = when {
                controlsBlocked -> "Resolve the current prompt to continue routing."
                focusedPackage == null -> "Focus a package to enable bay targets."
                else -> "Routing ${focusedPackage.summaryLabel()}. Match a truck rule or use Return Bin if none apply."
            },
            modifier = Modifier().className("fw-scene-target-note")
        )
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Box(modifier = Modifier().className("fw-stat")) {
        Text(text = label, modifier = Modifier().className("fw-stat-label"))
        Text(text = value, modifier = Modifier().className("fw-stat-value"))
    }
}

@Composable
private fun BriefingPanel(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    Box(modifier = Modifier().className("fw-panel")) {
        Text(text = "Dispatch Brief", modifier = Modifier().className("fw-panel-title"))
        Text(text = level.name, modifier = Modifier().className("fw-panel-headline"))
        Paragraph(text = level.briefing, modifier = Modifier().className("fw-panel-copy"))
        Box(modifier = Modifier().className("fw-chip-cloud")) {
            state.activePriorityRule?.let { Chip(text = "Priority ${it.badgeLabel()}") }
            if (state.ruleShifted) Chip(text = "Rule board changed")
            if (state.glitchActive) Chip(text = "Physical override required")
        }
    }
}

@Composable
private fun PlayLoopPanel(
    level: FifthWallLevel,
    state: FifthWallUiState,
    focusedPackage: FifthWallPackage?
) {
    val truckTargets = state.activeTrucks(level).indices.joinToString(" / ") { "Truck ${'A' + it}" }

    Box(modifier = Modifier().className("fw-panel")) {
        Text(text = "How To Play", modifier = Modifier().className("fw-panel-title"))
        Text(
            text = focusedPackage?.let { "Current focus: ${it.summaryLabel()}." }
                ?: "Start with Conveyor Queue. The focused package appears enlarged on the inspection dock.",
            modifier = Modifier().className("fw-panel-copy")
        )
        Column(modifier = Modifier().className("fw-play-list")) {
            PlayStep(
                number = "1",
                text = if (focusedPackage == null) {
                    "Click Focus package on a queue card."
                } else {
                    "Read the focused package manifest."
                }
            )
            PlayStep(
                number = "2",
                text = "Compare that manifest to the Rule Board."
            )
            PlayStep(
                number = "3",
                text = "If a rule matches, route to $truckTargets."
            )
            PlayStep(
                number = "4",
                text = "If no truck rule matches, use Return Bin. Only the front three packages are shown in 3D."
            )
        }
        Box(modifier = Modifier().className("fw-chip-cloud")) {
            Chip(text = "Cube = crate")
            Chip(text = "Rect = parcel")
            Chip(text = "Cylinder = drum")
            Chip(text = "Sphere = orb")
        }
    }
}

@Composable
private fun PlayStep(
    number: String,
    text: String
) {
    Box(modifier = Modifier().className("fw-play-step")) {
        Box(modifier = Modifier().className("fw-play-step-index")) {
            Text(text = number)
        }
        Text(text = text, modifier = Modifier().className("fw-play-step-copy"))
    }
}

@Composable
private fun RuleBoard(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    Box(modifier = Modifier().className("fw-panel")) {
        Text(text = "Rule Board", modifier = Modifier().className("fw-panel-title"))
        Column(modifier = Modifier().className("fw-rule-list")) {
            state.activeTrucks(level).forEachIndexed { index, rule ->
                val revealed = level.hiddenRuleIndex != index || state.hiddenRuleRevealed || state.ruleShifted
                Box(modifier = Modifier().className("fw-rule-card")) {
                    Text(text = "Truck ${'A' + index}", modifier = Modifier().className("fw-rule-name"))
                    Text(text = rule.label(revealed), modifier = Modifier().className("fw-rule-text"))
                }
            }
            Box(modifier = Modifier().className("fw-rule-card is-return")) {
                Text(text = "Return Bin", modifier = Modifier().className("fw-rule-name"))
                Text(text = "Use when no truck rule matches.", modifier = Modifier().className("fw-rule-text"))
            }
        }
    }
}

@Composable
private fun QueueDeck(
    state: FifthWallUiState,
    focusedPackage: FifthWallPackage?
) {
    Box(modifier = Modifier().className("fw-panel")) {
        Text(text = "Conveyor Queue", modifier = Modifier().className("fw-panel-title"))
        if (state.visiblePackages().isEmpty()) {
            Text(text = "Lane clear. No packages waiting.", modifier = Modifier().className("fw-empty"))
        } else {
            Column(modifier = Modifier().className("fw-queue-grid")) {
                state.visiblePackages().forEachIndexed { index, pkg ->
                    val classes = buildString {
                        append("fw-queue-card")
                        if (focusedPackage?.id == pkg.id) append(" is-focused")
                    }
                    Box(modifier = Modifier().className(classes)) {
                        Box(modifier = Modifier().className("fw-queue-head")) {
                            Text(text = "Slot ${index + 1}", modifier = Modifier().className("fw-slot-label"))
                            Text(
                                text = pkg.color.name.replaceFirstChar { it.uppercase() },
                                modifier = Modifier().className("fw-slot-accent")
                            )
                        }
                        Text(text = pkg.summaryLabel(), modifier = Modifier().className("fw-queue-title"))
                        Text(
                            text = "${pkg.weight}kg · ${pkg.volume}L · ${pkg.destination.replaceFirstChar { it.uppercase() }}",
                            modifier = Modifier().className("fw-queue-copy")
                        )
                        ActionControl(
                            label = if (focusedPackage?.id == pkg.id) "Focused" else "Focus package",
                            action = "select",
                            classes = if (focusedPackage?.id == pkg.id) {
                                "fw-action-link fw-ui-btn-accent"
                            } else {
                                "fw-action-link fw-ui-btn-outline"
                            },
                            params = arrayOf("package" to pkg.id)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteConsole(
    level: FifthWallLevel,
    state: FifthWallUiState,
    focusedPackage: FifthWallPackage?
) {
    val controlsBlocked = state.prompt != FifthWallPrompt.None
    val trucks = state.activeTrucks(level)

    Box(modifier = Modifier().className("fw-panel")) {
        Text(text = "Routing Console", modifier = Modifier().className("fw-panel-title"))
        Text(
            text = if (focusedPackage == null) {
                "Focus a package from the queue, then route it through the bay."
            } else {
                "Send ${focusedPackage.summaryLabel()} to a truck, or return it if no rule matches."
            },
            modifier = Modifier().className("fw-panel-copy")
        )
        Box(modifier = Modifier().className("fw-route-grid")) {
            trucks.forEachIndexed { index, rule ->
                Box(modifier = Modifier().className("fw-route-card")) {
                    Text(text = "Truck ${'A' + index}", modifier = Modifier().className("fw-route-name"))
                    Text(
                        text = rule.label(level.hiddenRuleIndex != index || state.hiddenRuleRevealed || state.ruleShifted),
                        modifier = Modifier().className("fw-route-copy")
                    )
                    ActionControl(
                        label = if (state.glitchActive) "Attempt route" else "Route to Truck ${'A' + index}",
                        action = "route-truck",
                        classes = "fw-action-link fw-ui-btn",
                        disabled = focusedPackage == null || controlsBlocked,
                        params = arrayOf("truck" to index.toString())
                    )
                }
            }
            Box(modifier = Modifier().className("fw-route-card is-return")) {
                Text(text = "Return Bin", modifier = Modifier().className("fw-route-name"))
                Text(text = "Only valid when no truck rule matches the current package.", modifier = Modifier().className("fw-route-copy"))
                ActionControl(
                    label = if (state.glitchActive) "Attempt return" else "Return package",
                    action = "route-return",
                    classes = "fw-action-link fw-ui-btn-outline",
                    disabled = focusedPackage == null || controlsBlocked
                )
            }
        }
    }
}

@Composable
private fun FeedbackBanner(state: FifthWallUiState) {
    val classes = buildString {
        append("fw-feedback")
        when (state.feedbackTone) {
            FifthWallFeedbackTone.Positive -> append(" is-positive")
            FifthWallFeedbackTone.Negative -> append(" is-negative")
            FifthWallFeedbackTone.Neutral -> append(" is-neutral")
        }
    }
    Box(modifier = Modifier().className(classes)) {
        Text(text = state.feedback)
    }
}

@Composable
private fun ManifestCard(selectedPackage: FifthWallPackage?) {
    Box(modifier = Modifier().className("fw-panel")) {
        Text(text = "Package Manifest", modifier = Modifier().className("fw-panel-title"))
        ManifestRow(label = "Color", value = selectedPackage?.color?.name?.replaceFirstChar { it.uppercase() } ?: "--")
        ManifestRow(label = "Shape", value = selectedPackage?.shapeDisplayLabel() ?: "--")
        ManifestRow(label = "Weight", value = selectedPackage?.weight?.let { "$it kg" } ?: "--")
        ManifestRow(label = "Volume", value = selectedPackage?.volume?.let { "$it L" } ?: "--")
        ManifestRow(label = "Pattern", value = selectedPackage?.pattern?.replaceFirstChar { it.uppercase() } ?: "--")
        ManifestRow(label = "Destination", value = selectedPackage?.destination?.replaceFirstChar { it.uppercase() } ?: "--")
        ManifestRow(label = "Geometry", value = selectedPackage?.geometry ?: "--")
        ManifestRow(label = "Label", value = selectedPackage?.labelText ?: "--")
    }
}

@Composable
private fun ManifestRow(label: String, value: String) {
    Box(modifier = Modifier().className("fw-manifest-row")) {
        Text(text = label, modifier = Modifier().className("fw-manifest-label"))
        Text(text = value, modifier = Modifier().className("fw-manifest-value"))
    }
}

@Composable
private fun LogCard(entries: List<String>) {
    Box(modifier = Modifier().className("fw-panel")) {
        Text(text = "Shift Log", modifier = Modifier().className("fw-panel-title"))
        if (entries.isEmpty()) {
            Text(text = "No routing events yet.", modifier = Modifier().className("fw-empty"))
        } else {
            Column(modifier = Modifier().className("fw-log-list")) {
                entries.reversed().forEach { entry ->
                    Box(modifier = Modifier().className("fw-log-item")) {
                        Text(text = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatCard(state: FifthWallUiState) {
    Box(modifier = Modifier().className("fw-panel")) {
        Text(text = "Dispatch Channel", modifier = Modifier().className("fw-panel-title"))
        if (state.chatMessages.isEmpty()) {
            Text(text = "Dispatch is still quiet.", modifier = Modifier().className("fw-empty"))
        } else {
            Column(modifier = Modifier().className("fw-chat-log")) {
                state.chatMessages.forEach { message ->
                    val classes = if (message.self) "fw-chat-message is-self" else "fw-chat-message"
                    Box(modifier = Modifier().className(classes)) {
                        Text(text = message.author, modifier = Modifier().className("fw-chat-author"))
                        Text(text = message.text)
                    }
                }
            }
        }
        if (state.chatEnabled) {
            RawHtml(
                html = buildInlineForm(
                    action = "send-chat",
                    buttonLabel = "Send",
                    placeholder = "Message dispatch",
                    value = state.chatDraft,
                    layoutClass = "fw-chat-input"
                )
            )
        }
        Text(
            text = if (state.chatEnabled) "Dispatch unlocked after Level 4." else "Dispatch unlocks after Level 4.",
            modifier = Modifier().className("fw-note")
        )
    }
}

@Composable
private fun PromptOverlay(
    state: FifthWallUiState,
    level: FifthWallLevel
) {
    Box(modifier = Modifier().className("fw-modal is-open")) {
        Box(modifier = Modifier().className("fw-modal-card")) {
            when (state.prompt) {
                FifthWallPrompt.Intro -> {
                    PromptTitle("Courier Protocol 3D")
                    Paragraph(
                        text = "Play loop: focus a package in Conveyor Queue, read Package Manifest, compare it to Rule Board, then route it to a truck or Return Bin. Only the front three packages are shown on the belt; the focused package is enlarged on the inspection dock.",
                        modifier = Modifier().className("fw-modal-copy")
                    )
                    PromptActionRow(
                        label = "Enter bay",
                        action = "start",
                        classes = "fw-action-link fw-ui-btn-accent"
                    )
                }

                FifthWallPrompt.ProbabilityPrediction -> {
                    PromptTitle("Prediction Lock")
                    Paragraph(
                        text = "Truck A will accept packages 70 percent of the time. Guess how many out of ${level.packageCount} the bay will swallow.",
                        modifier = Modifier().className("fw-modal-copy")
                    )
                    RawHtml(
                        html = buildPromptForm(
                            action = "submit-prediction",
                            buttonLabel = "Lock prediction",
                            placeholder = "0-${level.packageCount}",
                            value = state.predictionInput,
                            inputType = "number"
                        )
                    )
                }

                FifthWallPrompt.TeamDiscussion -> {
                    PromptTitle("Dispatch Pause")
                    Paragraph(
                        text = "The lane is paused for a team prompt. Reply if you want, or resume the shift and leave the message unsent.",
                        modifier = Modifier().className("fw-modal-copy")
                    )
                    Column(modifier = Modifier().className("fw-discussion-stack")) {
                        state.chatMessages.takeLast(3).forEach { message ->
                            Box(modifier = Modifier().className("fw-discussion-line")) {
                                Text(text = message.author, modifier = Modifier().className("fw-chat-author"))
                                Text(text = message.text)
                            }
                        }
                    }
                    RawHtml(
                        html = buildPromptForm(
                            action = "submit-discussion",
                            buttonLabel = "Resume shift",
                            placeholder = "Optional reply",
                            value = state.discussionReply
                        )
                    )
                }

                FifthWallPrompt.RuleGuess -> {
                    PromptTitle("Name the Hidden Rule")
                    Paragraph(
                        text = "You have enough test runs. Name Truck A's rule before dispatch reopens the conveyor.",
                        modifier = Modifier().className("fw-modal-copy")
                    )
                    RawHtml(
                        html = buildPromptForm(
                            action = "submit-rule-guess",
                            buttonLabel = "Submit rule",
                            placeholder = "Describe the hidden rule",
                            value = state.ruleGuessInput
                        )
                    )
                }

                FifthWallPrompt.Confidence -> {
                    PromptTitle("Confidence Check")
                    Paragraph(
                        text = state.pendingConfidence?.let { "${it.outcome} on ${it.routeLabel}. How confident were you?" }
                            ?: "How confident were you?",
                        modifier = Modifier().className("fw-modal-copy")
                    )
                    Box(modifier = Modifier().className("fw-confidence-grid")) {
                        ConfidenceAction("Low")
                        ConfidenceAction("Medium")
                        ConfidenceAction("High")
                    }
                }

                FifthWallPrompt.LevelComplete -> {
                    PromptTitle("Shift Cleared")
                    Paragraph(
                        text = "${level.name} is clear. Visible score: ${state.score}. Step into the next bay when ready.",
                        modifier = Modifier().className("fw-modal-copy")
                    )
                    PromptActionRow(
                        label = "Next level",
                        action = "advance",
                        classes = "fw-action-link fw-ui-btn-accent"
                    )
                }

                FifthWallPrompt.GameComplete -> {
                    PromptTitle("All Bays Clear")
                    Paragraph(
                        text = "All twenty levels are complete. The 3D dispatch floor is stable again. Reset the shift if you want another run.",
                        modifier = Modifier().className("fw-modal-copy")
                    )
                    PromptActionRow(
                        label = "Restart shift",
                        action = "reset",
                        classes = "fw-action-link fw-ui-btn-accent"
                    )
                }

                FifthWallPrompt.None -> Unit
            }
        }
    }
}

@Composable
private fun PromptTitle(text: String) {
    Text(text = text, modifier = Modifier().className("fw-modal-title"))
}

@Composable
private fun PromptActionRow(
    label: String,
    action: String,
    classes: String
) {
    Box(modifier = Modifier().className("fw-modal-actions")) {
        ActionControl(label = label, action = action, classes = classes)
    }
}

@Composable
private fun ConfidenceAction(label: String) {
    ActionControl(
        label = label,
        action = "confidence",
        classes = "fw-action-link fw-ui-btn-outline",
        params = arrayOf("value" to label)
    )
}

@Composable
private fun Chip(text: String) {
    Box(modifier = Modifier().className("fw-chip")) {
        Text(text = text)
    }
}

@Composable
private fun WrenchButton() {
    ActionControl(
        label = "Wrench",
        action = "repair",
        classes = "fw-action-link fw-wrench-btn"
    )
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

@Composable
private fun ActionControl(
    label: String,
    action: String,
    classes: String,
    disabled: Boolean = false,
    params: Array<Pair<String, String?>> = emptyArray()
) {
    if (disabled) {
        Box(modifier = Modifier().className("$classes is-disabled")) {
            Text(text = label)
        }
        return
    }

    AnchorLink(
        label = label,
        href = actionHref(action, *params),
        modifier = Modifier().className(classes),
        navigationMode = LinkNavigationMode.Native
    )
}

private fun actionHref(action: String, vararg params: Pair<String, String?>): String {
    val query = buildList {
        add("action" to action)
        params.forEach { add(it) }
    }.joinToString("&") { (key, value) ->
        "${urlEncode(key)}=${urlEncode(value.orEmpty())}"
    }
    return "$FifthWallActionPath?$query"
}

private fun buildPromptForm(
    action: String,
    buttonLabel: String,
    placeholder: String,
    value: String,
    inputType: String = "text"
): String = buildInlineForm(
    action = action,
    buttonLabel = buttonLabel,
    placeholder = placeholder,
    value = value,
    inputType = inputType,
    layoutClass = "fw-native-form"
)

private fun buildInlineForm(
    action: String,
    buttonLabel: String,
    placeholder: String,
    value: String,
    inputType: String = "text",
    layoutClass: String
): String {
    val escapedAction = htmlEscape(action)
    val escapedPlaceholder = htmlEscape(placeholder)
    val escapedValue = htmlEscape(value)
    val escapedButtonLabel = htmlEscape(buttonLabel)

    return """
        <form method="get" action="$FifthWallActionPath" class="$layoutClass">
            <input type="hidden" name="action" value="$escapedAction">
            <input class="fw-input fw-native-input" type="$inputType" name="value" value="$escapedValue" placeholder="$escapedPlaceholder" autocomplete="off">
            <button class="fw-html-btn fw-ui-btn-accent" type="submit">$escapedButtonLabel</button>
        </form>
    """.trimIndent()
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

private fun htmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun buildSceneTargetClasses(
    isActive: Boolean,
    wasAccepted: Boolean?,
    isReturn: Boolean = false
): String =
    buildString {
        append("fw-action-link fw-scene-target-action ")
        append(
            when {
                isActive && wasAccepted == false -> "fw-ui-btn-danger"
                isActive -> "fw-ui-btn-accent"
                isReturn -> "fw-ui-btn-outline"
                else -> "fw-ui-btn"
            }
        )
    }
