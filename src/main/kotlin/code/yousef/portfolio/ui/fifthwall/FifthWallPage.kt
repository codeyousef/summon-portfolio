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
internal fun FifthWallPage(
    controller: FifthWallController,
    state: FifthWallUiState
) {
    val level = FifthWallLevels[state.levelIndex.coerceIn(FifthWallLevels.indices)]
    val focusedPackage = state.focusPackage()
    val sceneClasses = buildString {
        append("fw-scene-shell")
        if (state.glitchActive) append(" is-glitched")
        if (state.ruleShifted || state.priorityShifted) append(" is-shifted")
    }

    FifthWallScaffold {
        Box(modifier = Modifier().className("fw-root")) {
            TopBar(level = level, state = state)
            Box(modifier = Modifier().className("fw-stage")) {
                Box(modifier = Modifier().className("fw-primary")) {
                    Box(modifier = Modifier().className(sceneClasses)) {
                        FifthWallScene(
                            controller = controller,
                            level = level,
                            state = state,
                            focusedPackage = focusedPackage
                        )
                        SceneOverlay(
                            level = level,
                            state = state,
                            focusedPackage = focusedPackage
                        )
                        SceneDashboard(
                            level = level,
                            state = state,
                            focusedPackage = focusedPackage
                        )
                        SceneFeedback(state = state)
                        if (state.prompt != FifthWallPrompt.None) {
                            PromptOverlay(state = state, level = level)
                        }
                        TelemetryBridge(state = state)
                    }
                    AccessibilityFallbackPanel(
                        level = level,
                        state = state,
                        focusedPackage = focusedPackage
                    )
                }
            }
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
            StatCell(label = "Level", value = "${level.id}/${FifthWallLevels.size}", valueId = "fw-stat-level")
            StatCell(label = "Score", value = state.score.toString(), valueId = "fw-stat-score")
            StatCell(
                label = "Processed",
                value = "${state.processedCount(level)}/${level.packageCount}",
                valueId = "fw-stat-processed"
            )
        }

        Box(modifier = Modifier().className("fw-actions")) {
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
    val guidance = level.guidance()
    Column(modifier = Modifier().className("fw-scene-hud fw-scene-hud-left")) {
        Text(text = guidance.phase, modifier = Modifier().className("fw-scene-badge"))
        Text(text = level.name, modifier = Modifier().className("fw-scene-heading"))
        Text(
            text = if (focusedPackage == null) {
                guidance.objective
            } else {
                "Focused: ${focusedPackage.summaryLabel()}. Compare the manifest to the Rule Board, then route or return."
            },
            modifier = Modifier().className("fw-scene-copy")
        )
        Box(modifier = Modifier().className("fw-scene-chip-row")) {
            SceneChip("Focus")
            SceneChip("Inspect")
            SceneChip("Compare")
            SceneChip("Route")
            if (state.ruleShifted) SceneChip("Rules shifted")
            if (state.glitchActive) SceneChip("Console fault")
            if (focusedPackage?.geometry != null) SceneChip("Inspection active")
        }
    }

}

@Composable
private fun SceneDashboard(
    level: FifthWallLevel,
    state: FifthWallUiState,
    focusedPackage: FifthWallPackage?
) {
    Box(modifier = Modifier().className("fw-scene-dashboard")) {
        Box(modifier = Modifier().className("fw-scene-dashboard-panel is-manifest")) {
            ManifestCard(selectedPackage = focusedPackage)
        }
        Box(modifier = Modifier().className("fw-scene-dashboard-panel is-rules")) {
            RuleBoard(level = level, state = state)
        }
        Box(modifier = Modifier().className("fw-scene-dashboard-panel is-chat")) {
            ChatCard(state = state)
        }
    }
}

@Composable
private fun SceneFeedback(state: FifthWallUiState) {
    Box(modifier = Modifier().className("fw-scene-feedback")) {
        FeedbackBanner(state = state)
    }
}

@Composable
private fun AccessibilityFallbackPanel(
    level: FifthWallLevel,
    state: FifthWallUiState,
    focusedPackage: FifthWallPackage?
) {
    val controlsBlocked = state.prompt != FifthWallPrompt.None

    Box(modifier = Modifier().className("fw-accessibility-fallback")) {
        Text(text = "Accessibility fallback", modifier = Modifier().className("fw-panel-title"))
        Text(
            text = "The primary game surface is the Materia/Sigil scene above. These native controls remain for keyboard and screen-reader fallback.",
            modifier = Modifier().className("fw-panel-copy")
        )
        Box(modifier = Modifier().className("fw-fallback-row")) {
            if (state.prompt == FifthWallPrompt.Intro) {
                ActionControl(
                    label = "Enter bay",
                    action = "start",
                    classes = "fw-action-link fw-ui-btn-accent"
                )
            }
            if (state.prompt == FifthWallPrompt.LevelComplete) {
                ActionControl(
                    label = "Next level",
                    action = "advance",
                    classes = "fw-action-link fw-ui-btn-accent"
                )
            }
            if (state.glitchActive) {
                ActionControl(
                    label = "Restart console",
                    action = "fake-restart",
                    classes = "fw-action-link fw-ui-btn-danger"
                )
            }
            if (state.wrenchVisible) {
                ActionControl(
                    label = "Repair with wrench",
                    action = "repair",
                    classes = "fw-action-link fw-ui-btn-accent"
                )
            }
            ActionControl(
                label = "Reset shift",
                action = "reset",
                classes = "fw-action-link fw-ui-btn-outline"
            )
        }
        if (state.visiblePackages().isNotEmpty()) {
            Box(modifier = Modifier().className("fw-fallback-row")) {
                state.visiblePackages().forEachIndexed { index, pkg ->
                    ActionControl(
                        label = if (focusedPackage?.id == pkg.id) "Focused P${index + 1}" else "Focus P${index + 1}",
                        action = "select",
                        classes = if (focusedPackage?.id == pkg.id) {
                            "fw-action-link fw-ui-btn-accent"
                        } else {
                            "fw-action-link fw-ui-btn"
                        },
                        disabled = controlsBlocked,
                        params = arrayOf("package" to pkg.id)
                    )
                }
            }
        }
        Box(modifier = Modifier().className("fw-fallback-row")) {
            state.activeTrucks(level).forEachIndexed { index, _ ->
                ActionControl(
                    label = "Truck ${'A' + index}",
                    action = "route-truck",
                    classes = "fw-action-link fw-ui-btn",
                    disabled = focusedPackage == null || controlsBlocked,
                    params = arrayOf("truck" to index.toString())
                )
            }
            ActionControl(
                label = "Return Bin",
                action = "route-return",
                classes = "fw-action-link fw-ui-btn-outline",
                disabled = focusedPackage == null || controlsBlocked
            )
        }
    }
}

@Composable
private fun SceneChip(text: String) {
    Box(modifier = Modifier().className("fw-scene-chip")) {
        Text(text = text)
    }
}

@Composable
private fun StatCell(label: String, value: String, valueId: String? = null) {
    Box(modifier = Modifier().className("fw-stat")) {
        Text(text = label, modifier = Modifier().className("fw-stat-label"))
        Text(
            text = value,
            modifier = valueId?.let { Modifier().className("fw-stat-value").id(it) }
                ?: Modifier().className("fw-stat-value")
        )
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
        Text(text = state.feedback, modifier = Modifier().id("fw-feedback-text"))
    }
}

@Composable
private fun ManifestCard(selectedPackage: FifthWallPackage?) {
    Box(modifier = Modifier().className("fw-panel")) {
        Text(text = "Package Manifest", modifier = Modifier().className("fw-panel-title"))
        ManifestRow(
            label = "Color",
            value = selectedPackage?.color?.name?.replaceFirstChar { it.uppercase() } ?: "--",
            valueId = "fw-manifest-color"
        )
        ManifestRow(
            label = "Shape",
            value = selectedPackage?.shapeDisplayLabel() ?: "--",
            valueId = "fw-manifest-shape"
        )
        ManifestRow(
            label = "Weight",
            value = selectedPackage?.weight?.let { "$it kg" } ?: "--",
            valueId = "fw-manifest-weight"
        )
        ManifestRow(
            label = "Volume",
            value = selectedPackage?.volume?.let { "$it L" } ?: "--",
            valueId = "fw-manifest-volume"
        )
        ManifestRow(
            label = "Pattern",
            value = selectedPackage?.pattern?.replaceFirstChar { it.uppercase() } ?: "--",
            valueId = "fw-manifest-pattern"
        )
        ManifestRow(
            label = "Destination",
            value = selectedPackage?.destination?.replaceFirstChar { it.uppercase() } ?: "--",
            valueId = "fw-manifest-destination"
        )
        ManifestRow(label = "Geometry", value = selectedPackage?.geometry ?: "--", valueId = "fw-manifest-geometry")
        ManifestRow(label = "Label", value = selectedPackage?.labelText ?: "--", valueId = "fw-manifest-label")
    }
}

@Composable
private fun ManifestRow(label: String, value: String, valueId: String? = null) {
    Box(modifier = Modifier().className("fw-manifest-row")) {
        Text(text = label, modifier = Modifier().className("fw-manifest-label"))
        Text(
            text = value,
            modifier = valueId?.let { Modifier().className("fw-manifest-value").id(it) }
                ?: Modifier().className("fw-manifest-value")
        )
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
                        text = "Play loop: focus a package mesh on the conveyor, inspect its manifest, compare it to the Rule Board, then route it to a truck or the Return Bin. Use Return Bin only when no truck rule matches. The focused package is enlarged on the inspection dock.",
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
