package code.yousef.portfolio.ui.fifthwall

import codes.yousef.sigil.schema.GeometryParams
import codes.yousef.sigil.schema.GeometryType
import codes.yousef.sigil.schema.HighlightPatch
import codes.yousef.sigil.schema.AnimationEasing
import codes.yousef.sigil.schema.AnimationKind
import codes.yousef.sigil.schema.AnimationTrigger
import codes.yousef.sigil.schema.CursorHint
import codes.yousef.sigil.schema.DropTargetMetadata
import codes.yousef.sigil.schema.HitVolumeData
import codes.yousef.sigil.schema.HitVolumeShape
import codes.yousef.sigil.schema.InteractionMetadata
import codes.yousef.sigil.schema.SceneAnimationData
import codes.yousef.sigil.schema.SceneNodePatch
import codes.yousef.sigil.schema.ScenePatch
import codes.yousef.sigil.schema.TextAlignMode
import codes.yousef.sigil.schema.TextBaselineMode
import codes.yousef.sigil.schema.TextFacingMode
import codes.yousef.sigil.summon.canvas.MateriaCanvas
import codes.yousef.sigil.summon.canvas.SceneConfig
import codes.yousef.sigil.summon.canvas.SigilSceneEventCallbackResponse
import codes.yousef.sigil.summon.canvas.SigilSceneEventHandler
import codes.yousef.sigil.summon.canvas.SigilSceneEventMatch
import codes.yousef.sigil.summon.components.SigilAmbientLight
import codes.yousef.sigil.summon.components.SigilBox
import codes.yousef.sigil.summon.components.SigilCamera
import codes.yousef.sigil.summon.components.SigilDirectionalLight
import codes.yousef.sigil.summon.components.SigilGroup
import codes.yousef.sigil.summon.components.SigilMesh
import codes.yousef.sigil.summon.components.SigilModel
import codes.yousef.sigil.summon.components.SigilOrbitControls
import codes.yousef.sigil.summon.components.SigilPlane
import codes.yousef.sigil.summon.components.SigilSphere
import codes.yousef.sigil.summon.components.SigilText
import codes.yousef.summon.annotation.Composable
import io.materia.core.math.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

private val WAREHOUSE_FLOOR = argb("#0d1721")
private val SCENE_ACCENT = argb(FifthWallTheme.ACCENT)
private val SCENE_WARM = argb(FifthWallTheme.ACCENT_WARM)
private val SCENE_SUCCESS = argb(FifthWallTheme.SUCCESS)
private val SCENE_DANGER = argb(FifthWallTheme.DANGER)
private val SCENE_NEUTRAL_LIGHT = argb("#d9ddd8")
private val SCENE_TEXT = argb("#f4f8ff")
private val SCENE_TEXT_MUTED = argb("#9fb0c8")
private val PACKAGE_TAPE = argb("#eef4fb")
private val PACKAGE_SHADE = argb("#0b1219")
private val PACKAGE_HIDDEN_POSITION = listOf(0f, -8f, -8f)
private val ROUTING_TARGET_GROUP = listOf("fifth-wall-routing-target")
private val PACKAGE_POINTER_EVENTS = listOf("pointerdown", "click")
private const val CONSOLE_FOCUS_SLOT_COUNT = 3
private const val CONSOLE_RETURN_INTERACTION_ID = "console-route-return"
private const val CONSOLE_RESET_INTERACTION_ID = "console-reset"
private const val ROUTE_PAD_RETURN_ID = "route-pad-return"
private const val RESET_PAD_ID = "reset-shift-pad"
private const val CONSOLE_RETURN_CONTROL_ID = "dispatch-console-return-control"
private const val CONSOLE_RESET_CONTROL_ID = "dispatch-console-reset-control"
private const val PROMPT_START_INTERACTION_ID = "prompt:start"
private const val PROMPT_NEXT_INTERACTION_ID = "prompt:next"
private const val PROMPT_RESET_INTERACTION_ID = "prompt:reset"
private const val PROMPT_DISCUSSION_INTERACTION_ID = "prompt:discussion:resume"
private const val PROMPT_RULE_ANSWER_INTERACTION_ID = "prompt:rule:answer"
private val CONTROL_PAD_HIDDEN_POSITION = listOf(0f, -6f, 0f)
private const val MODEL_ASSET_VERSION = "20260510-small-glb"
private const val FIFTH_WALL_TEXT_FONT = "/static/fifth-wall-control-font.json"
private val TRUCK_COLOR_FALLBACKS = listOf(
    argb("#ff6b6b"),
    argb("#5aa9ff"),
    argb("#45e0a8"),
    argb("#f7b955"),
    argb("#9fb0c8"),
    argb("#b690ff")
)

private fun uniformScale(value: Float): List<Float> = listOf(value, value, value)

// Materia 0.4.1 scales positioned glyph offsets twice; compensate until the renderer fixes it.
private fun meshTextLetterSpacing(size: Float): Float =
    (620_000f / size) - 620f

@Composable
internal fun FifthWallScene(
    controller: FifthWallController,
    level: FifthWallLevel,
    state: FifthWallUiState,
    focusedPackage: FifthWallPackage?
) {
    MateriaCanvas(
        id = "fifth-wall-scene",
        width = "100%",
        height = "100%",
        backgroundColor = argb(FifthWallTheme.BASE),
        sceneEventHandlers = fifthWallSceneEventHandlers(controller, level, state)
    ) {
        SceneConfig(
            backgroundColor = argb(FifthWallTheme.BASE),
            fogEnabled = false,
            fogColor = argb("#0d1620"),
            fogNear = 36f,
            fogFar = 82f,
            shadowsEnabled = false
        )
        SigilCamera(
            position = listOf(0.6f, 7.4f, 11.4f),
            lookAt = listOf(0.8f, 1.85f, 1.6f),
            fov = 56f,
            near = 0.1f,
            far = 100f,
            name = "dispatch-camera"
        )
        SigilOrbitControls(
            target = listOf(0.8f, 1.85f, 1.6f),
            enableDamping = true,
            dampingFactor = 0.24f,
            minDistance = 8.5f,
            maxDistance = 15f,
            minPolarAngle = 0.9f,
            maxPolarAngle = 1.36f,
            enablePan = false,
            autoRotate = false,
            autoRotateSpeed = 0f,
            rotateSpeed = 0.12f,
            zoomSpeed = 0.45f,
            name = "dispatch-orbit"
        )

        SigilAmbientLight(color = SCENE_NEUTRAL_LIGHT, intensity = 0.58f, name = "ambient-fill")
        SigilDirectionalLight(
            position = listOf(4f, 12f, 9f),
            target = listOf(0f, 0f, 1f),
            color = argb("#fff2d3"),
            intensity = 0.88f,
            castShadow = false,
            name = "bay-sun"
        )
        WarehouseShell()
        RoutingGuides(level = level, state = state)
        RouteControlPads(level = level, state = state)
        DispatchConsoleControls(level = level, state = state)
        ConveyorDeck(level = level, state = state)
        TruckBay(level = level, state = state)
        ReturnBinBay(
            selected = state.lastRouteTarget == "Return Bin",
            routeAccepted = if (state.lastRouteTarget == "Return Bin") state.lastRouteAccepted else null,
            glitchActive = state.glitchActive
        )
        InspectionDock(pkg = focusedPackage)
        if (state.wrenchVisible) {
            WrenchProp(visible = true)
        }
        InCanvasGameUi(
            level = level,
            state = state,
            focusedPackage = focusedPackage
        )
        ""
    }
}

private fun fifthWallSceneEventHandlers(
    controller: FifthWallController,
    level: FifthWallLevel,
    state: FifthWallUiState
): List<SigilSceneEventHandler> {
    val handlers = mutableListOf<SigilSceneEventHandler>()

    state.queue.forEach { pkg ->
        handlers += reloadingSceneEventHandler(
            match = SigilSceneEventMatch(type = "pointerdown", interactionId = "package:${pkg.id}"),
            onEvent = { controller.selectPackage(pkg.id) }
        )
        handlers += reloadingSceneEventHandler(
            match = SigilSceneEventMatch(type = "click", interactionId = consoleFocusInteractionId(pkg.id)),
            onEvent = { controller.selectPackage(pkg.id) }
        )
    }

    state.activeTrucks(level).forEachIndexed { index, _ ->
        handlers += reloadingSceneEventHandler(
            match = SigilSceneEventMatch(
                type = "drop",
                sourceInteractionIdPrefix = "package:",
                targetInteractionId = "truck:$index",
                accepted = true
            ),
            onEvent = { controller.routeToTruck(index) }
        )
        handlers += reloadingSceneEventHandler(
            match = SigilSceneEventMatch(type = "click", interactionId = consoleTruckInteractionId(index)),
            onEvent = { controller.routeToTruck(index) }
        )
    }

    handlers += reloadingSceneEventHandler(
        match = SigilSceneEventMatch(
            type = "drop",
            sourceInteractionIdPrefix = "package:",
            targetInteractionId = "return-bin",
            accepted = true
        ),
        onEvent = controller::routeToReturn
    )
    handlers += reloadingSceneEventHandler(
        match = SigilSceneEventMatch(type = "click", interactionId = CONSOLE_RETURN_INTERACTION_ID),
        onEvent = controller::routeToReturn
    )
    handlers += reloadingSceneEventHandler(
        match = SigilSceneEventMatch(type = "click", interactionId = CONSOLE_RESET_INTERACTION_ID),
        onEvent = controller::reset
    )

    handlers += reloadingSceneEventHandler(
        match = SigilSceneEventMatch(
            type = "drop",
            sourceInteractionIdPrefix = "package:",
            targetInteractionId = "inspection-dock",
            accepted = true
        ),
        onEvent = { controller.state.value.selectedPackageId?.let(controller::selectPackage) }
    )

    handlers += reloadingSceneEventHandler(
        match = SigilSceneEventMatch(type = "click", interactionId = "repair-wrench"),
        onEvent = controller::repairGlitch
    )

    handlers += promptSceneEventHandlers(controller, level, state)

    return handlers
}

private fun reloadingSceneEventHandler(
    match: SigilSceneEventMatch,
    onEvent: () -> Unit
): SigilSceneEventHandler =
    SigilSceneEventHandler(
        match = match,
        onEvent = onEvent,
        reloadOnSuccess = true
    )

private fun promptSceneEventHandlers(
    controller: FifthWallController,
    level: FifthWallLevel,
    state: FifthWallUiState
): List<SigilSceneEventHandler> =
    when (state.prompt) {
        FifthWallPrompt.Intro -> listOf(
            reloadingSceneEventHandler(
                match = SigilSceneEventMatch(type = "click", interactionId = PROMPT_START_INTERACTION_ID),
                onEvent = controller::startShift
            )
        )

        FifthWallPrompt.ProbabilityPrediction -> predictionChoices(level).map { value ->
            reloadingSceneEventHandler(
                match = SigilSceneEventMatch(type = "click", interactionId = promptPredictionInteractionId(value)),
                onEvent = {
                    controller.updatePredictionInput(value.toString())
                    controller.submitPrediction()
                }
            )
        }

        FifthWallPrompt.TeamDiscussion -> listOf(
            reloadingSceneEventHandler(
                match = SigilSceneEventMatch(type = "click", interactionId = PROMPT_DISCUSSION_INTERACTION_ID),
                onEvent = {
                    controller.updateDiscussionReply("")
                    controller.submitDiscussionReply()
                }
            )
        )

        FifthWallPrompt.RuleGuess -> listOf(
            reloadingSceneEventHandler(
                match = SigilSceneEventMatch(type = "click", interactionId = PROMPT_RULE_ANSWER_INTERACTION_ID),
                onEvent = {
                    controller.updateRuleGuess(hiddenRuleCanvasAnswer(level))
                    controller.submitRuleGuess()
                }
            )
        )

        FifthWallPrompt.Confidence -> listOf("Low", "Medium", "High").map { value ->
            reloadingSceneEventHandler(
                match = SigilSceneEventMatch(type = "click", interactionId = promptConfidenceInteractionId(value)),
                onEvent = { controller.recordConfidence(value) }
            )
        }

        FifthWallPrompt.LevelComplete -> listOf(
            reloadingSceneEventHandler(
                match = SigilSceneEventMatch(type = "click", interactionId = PROMPT_NEXT_INTERACTION_ID),
                onEvent = controller::advanceLevel
            )
        )

        FifthWallPrompt.GameComplete -> listOf(
            reloadingSceneEventHandler(
                match = SigilSceneEventMatch(type = "click", interactionId = PROMPT_RESET_INTERACTION_ID),
                onEvent = controller::reset
            )
        )

        FifthWallPrompt.None -> emptyList()
    }

private fun fifthWallSceneCallbackResponse(
    controller: FifthWallController,
    renderedPackageIds: List<String>
): SigilSceneEventCallbackResponse {
    val nextState = controller.state.value
    val nextLevel = FifthWallLevels[nextState.levelIndex.coerceIn(FifthWallLevels.indices)]
    return SigilSceneEventCallbackResponse(
        action = "patch",
        status = "ok",
        scenePatch = fifthWallScenePatch(
            level = nextLevel,
            state = nextState,
            renderedPackageIds = renderedPackageIds
        )
    )
}

private fun fifthWallScenePatch(
    level: FifthWallLevel,
    state: FifthWallUiState,
    renderedPackageIds: List<String>
): ScenePatch {
    val visiblePackages = state.visiblePackages()
    val visibleIds = visiblePackages.map { it.id }
    val focusedId = state.focusPackage()?.id
    val nodes = mutableListOf<SceneNodePatch>()

    renderedPackageIds.forEach { packageId ->
        val pkg = state.queue.firstOrNull { it.id == packageId }
        val visibleIndex = visibleIds.indexOf(packageId)
        val isVisible = visibleIndex >= 0
        val focused = isVisible && packageId == focusedId
        nodes += SceneNodePatch(
            id = "package-$packageId",
            position = if (isVisible) packageBeltPosition(visibleIndex) else PACKAGE_HIDDEN_POSITION,
            rotation = pkg?.let { packageRotation(it, visibleIndex) },
            scale = pkg?.let { packageScaleVector(enlarged = false, emphasized = visibleIndex == 0) },
            visible = isVisible,
            highlight = HighlightPatch(
                active = focused,
                color = SCENE_ACCENT,
                intensity = if (focused) 0.75f else 0f
            )
        )
        nodes += SceneNodePatch(
            id = "package-$packageId-body",
            position = if (isVisible && pkg != null) packageBodyPosition(pkg, packageHover(enlarged = false, emphasized = visibleIndex == 0)) else null
        )
    }

    state.activeTrucks(level).forEachIndexed { index, rule ->
        val routeLabel = "Truck ${'A' + index}"
        val selected = state.lastRouteTarget == routeLabel
        nodes += SceneNodePatch(
            id = "truck-$index",
            highlight = HighlightPatch(
                active = selected,
                color = when {
                    !selected -> truckColor(index, rule)
                    state.lastRouteAccepted == true -> SCENE_SUCCESS
                    state.lastRouteAccepted == false -> SCENE_DANGER
                    else -> truckColor(index, rule)
                },
                intensity = if (selected) 0.82f else 0f
            )
        )
    }

    val returnSelected = state.lastRouteTarget == "Return Bin"
    nodes += SceneNodePatch(
        id = "return-bin-target",
        highlight = HighlightPatch(
            active = returnSelected,
            color = when {
                !returnSelected -> SCENE_WARM
                state.lastRouteAccepted == true -> SCENE_SUCCESS
                state.lastRouteAccepted == false -> SCENE_DANGER
                else -> SCENE_WARM
            },
            intensity = if (returnSelected) 0.82f else 0f
        )
    )
    nodes += SceneNodePatch(id = "repair-wrench", visible = state.wrenchVisible)
    nodes += consolePatchNodes(level = level, state = state, renderedPackageIds = renderedPackageIds)

    return ScenePatch(nodes)
}

private fun consolePatchNodes(
    level: FifthWallLevel,
    state: FifthWallUiState,
    renderedPackageIds: List<String>
): List<SceneNodePatch> {
    val visibleIds = state.visiblePackages().map { it.id }
    val focusedId = state.focusPackage()?.id
    val nodes = mutableListOf<SceneNodePatch>()

    renderedPackageIds.forEach { packageId ->
        val visibleIndex = visibleIds.indexOf(packageId)
        val visible = visibleIndex in 0 until CONSOLE_FOCUS_SLOT_COUNT
        val focused = visible && packageId == focusedId
        nodes += SceneNodePatch(
            id = packageFocusPadId(packageId),
            position = if (visible) packageFocusPadPosition(visibleIndex) else CONTROL_PAD_HIDDEN_POSITION,
            visible = visible,
            highlight = HighlightPatch(
                active = focused,
                color = SCENE_ACCENT,
                intensity = if (focused) 0.88f else 0.28f
            )
        )
        nodes += SceneNodePatch(
            id = consoleFocusControlId(packageId),
            position = if (visible) consoleFocusControlPosition(visibleIndex) else CONTROL_PAD_HIDDEN_POSITION,
            visible = visible,
            highlight = HighlightPatch(
                active = focused,
                color = SCENE_ACCENT,
                intensity = if (focused) 0.9f else 0.22f
            )
        )
    }

    state.activeTrucks(level).forEachIndexed { index, rule ->
        val label = "Truck ${'A' + index}"
        val selected = state.lastRouteTarget == label
        nodes += SceneNodePatch(
            id = routePadTruckId(index),
            highlight = HighlightPatch(
                active = selected,
                color = routeFeedbackColor(
                    selected = selected,
                    routeAccepted = state.lastRouteAccepted,
                    fallback = truckColor(index, rule)
                ),
                intensity = if (selected) 0.9f else 0.3f
            )
        )
        nodes += SceneNodePatch(
            id = consoleRouteTruckControlId(index),
            highlight = HighlightPatch(
                active = selected,
                color = routeFeedbackColor(
                    selected = selected,
                    routeAccepted = state.lastRouteAccepted,
                    fallback = truckColor(index, rule)
                ),
                intensity = if (selected) 0.92f else 0.24f
            )
        )
    }

    val returnSelected = state.lastRouteTarget == "Return Bin"
    nodes += SceneNodePatch(
        id = ROUTE_PAD_RETURN_ID,
        highlight = HighlightPatch(
            active = returnSelected,
            color = routeFeedbackColor(
                selected = returnSelected,
                routeAccepted = state.lastRouteAccepted,
                fallback = SCENE_WARM
            ),
            intensity = if (returnSelected) 0.92f else 0.34f
        )
    )
    nodes += SceneNodePatch(
        id = CONSOLE_RETURN_CONTROL_ID,
        highlight = HighlightPatch(
            active = returnSelected,
            color = routeFeedbackColor(
                selected = returnSelected,
                routeAccepted = state.lastRouteAccepted,
                fallback = SCENE_WARM
            ),
            intensity = if (returnSelected) 0.92f else 0.24f
        )
    )

    return nodes
}

private fun routeFeedbackColor(
    selected: Boolean,
    routeAccepted: Boolean?,
    fallback: Int
): Int = when {
    !selected -> fallback
    routeAccepted == true -> SCENE_SUCCESS
    routeAccepted == false -> SCENE_DANGER
    else -> fallback
}

@Composable
private fun WarehouseShell() {
    SigilPlane(
        width = 34f,
        height = 26f,
        position = listOf(0f, 0f, 0f),
        rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
        color = WAREHOUSE_FLOOR,
        receiveShadow = true,
        name = "warehouse-floor"
    )
    SigilModel(
        url = fifthWallModelUrl("warehouse-bay-shell-kit.glb"),
        position = listOf(0f, 0.02f, 0f),
        scale = listOf(34f, 14f, 26f),
        castShadow = false,
        receiveShadow = false,
        name = "warehouse-shell-model"
    )
}

@Composable
private fun RoutingGuides(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    val trucks = state.activeTrucks(level)
    val spacing = truckSpacing(trucks.size)
    val origin = truckOrigin(trucks.size, spacing)
    val hubX = 1.25f
    val hubZ = 3.1f
    val focusedPackage = state.focusPackage()
    val inspectionColor = if (focusedPackage?.validGeometry == false) SCENE_DANGER else SCENE_ACCENT

    DockGuidePad(
        position = listOf(8.2f, 0f, -5.5f),
        width = 5.35f,
        depth = 5f,
        color = inspectionColor,
        selected = focusedPackage != null,
        name = "inspection-guide-pad"
    )
    GuideStrip(
        startX = 3.1f,
        startZ = 0.95f,
        endX = 6.8f,
        endZ = -3.95f,
        color = inspectionColor,
        thickness = 0.18f,
        name = "inspection-guide"
    )
    GuideStrip(
        startX = -2.2f,
        startZ = 2.08f,
        endX = hubX,
        endZ = hubZ,
        color = argb("#1a344c"),
        thickness = 0.22f,
        name = "routing-hub-guide"
    )

    trucks.forEachIndexed { index, rule ->
        val x = origin + (spacing * index)
        val routeLabel = "Truck ${'A' + index}"
        val color = when {
            state.lastRouteTarget == routeLabel && state.lastRouteAccepted == true -> SCENE_SUCCESS
            state.lastRouteTarget == routeLabel && state.lastRouteAccepted == false -> SCENE_DANGER
            else -> truckColor(index, rule)
        }
        DockGuidePad(
            position = listOf(x, 0f, 4.65f),
            width = 4.4f,
            depth = 2.6f,
            color = color,
            selected = state.lastRouteTarget == routeLabel,
            name = "truck-guide-pad-$index"
        )
        GuideStrip(
            startX = hubX,
            startZ = hubZ,
            endX = x,
            endZ = 4.15f,
            color = color,
            thickness = 0.17f,
            name = "truck-guide-$index"
        )
    }

    val returnColor = when {
        state.lastRouteTarget == "Return Bin" && state.lastRouteAccepted == true -> SCENE_SUCCESS
        state.lastRouteTarget == "Return Bin" && state.lastRouteAccepted == false -> SCENE_DANGER
        else -> SCENE_WARM
    }
    DockGuidePad(
        position = listOf(9.2f, 0f, 4.85f),
        width = 3.1f,
        depth = 3.1f,
        color = returnColor,
        selected = state.lastRouteTarget == "Return Bin",
        name = "return-guide-pad"
    )
    GuideStrip(
        startX = hubX,
        startZ = hubZ,
        endX = 8.45f,
        endZ = 4.25f,
        color = returnColor,
        thickness = 0.17f,
        name = "return-guide"
    )
}

@Composable
private fun DockGuidePad(
    position: List<Float>,
    width: Float,
    depth: Float,
    color: Int,
    selected: Boolean,
    name: String
) {
    SigilGroup(position = position, name = name) {
        SigilBox(
            width = width,
            height = if (selected) 0.08f else 0.05f,
            depth = depth,
            position = listOf(0f, 0.03f, 0f),
            color = if (selected) color else argb("#152132"),
            metalness = if (selected) 0.42f else 0.18f,
            roughness = 0.76f,
            castShadow = false,
            receiveShadow = true,
            name = "$name-base"
        )
        ""
    }
}

@Composable
private fun GuideStrip(
    startX: Float,
    startZ: Float,
    endX: Float,
    endZ: Float,
    color: Int,
    thickness: Float,
    name: String
) {
    val dx = endX - startX
    val dz = endZ - startZ
    val length = sqrt((dx * dx) + (dz * dz))
    val angle = atan2(dz.toDouble(), dx.toDouble()).toFloat()
    val midX = (startX + endX) / 2f
    val midZ = (startZ + endZ) / 2f

    SigilBox(
        width = length,
        height = 0.05f,
        depth = thickness,
        position = listOf(midX, 0.035f, midZ),
        rotation = listOf(0f, angle, 0f),
        color = color,
        metalness = 0.64f,
        roughness = 0.2f,
        castShadow = false,
        receiveShadow = false,
        name = name
    )
}

@Composable
private fun BillboardTextLabel(
    text: String,
    position: List<Float>,
    size: Float,
    color: Int,
    name: String,
    id: String = name,
    lineHeight: Float = 1.04f
) {
    SigilText(
        text = text,
        position = position,
        color = color,
        size = size,
        depth = 0f,
        curveSegments = 5,
        letterSpacing = meshTextLetterSpacing(size),
        lineHeight = lineHeight,
        align = TextAlignMode.CENTER,
        baseline = TextBaselineMode.MIDDLE,
        facingMode = TextFacingMode.BILLBOARD,
        fontUrl = FIFTH_WALL_TEXT_FONT,
        name = name,
        id = id
    )
}

@Composable
private fun BillboardTextControl(
    text: String,
    position: List<Float>,
    size: Float,
    color: Int,
    interactionId: String,
    actions: List<String>,
    hitWidth: Float,
    hitHeight: Float,
    hitDepth: Float,
    name: String,
    id: String = name,
    lineHeight: Float = 1.02f
) {
    SigilText(
        text = text,
        position = position,
        color = color,
        size = size,
        depth = 0f,
        curveSegments = 5,
        letterSpacing = meshTextLetterSpacing(size),
        lineHeight = lineHeight,
        align = TextAlignMode.CENTER,
        baseline = TextBaselineMode.MIDDLE,
        facingMode = TextFacingMode.BILLBOARD,
        fontUrl = FIFTH_WALL_TEXT_FONT,
        interaction = routePadInteraction(
            interactionId = interactionId,
            width = hitWidth,
            depth = hitDepth,
            actions = actions,
            height = hitHeight,
            centerY = 0f
        ),
        name = name,
        id = id
    )
}

@Composable
private fun InCanvasGameUi(
    level: FifthWallLevel,
    state: FifthWallUiState,
    focusedPackage: FifthWallPackage?
) {
    CanvasProgressLabel(level = level, state = state)
    CanvasManifestPanel(focusedPackage = focusedPackage)
    CanvasRuleBoardPanel(level = level, state = state)
    CanvasPromptPanel(level = level, state = state)
}

@Composable
private fun CanvasProgressLabel(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    SigilGroup(
        position = CONTROL_PAD_HIDDEN_POSITION,
        visible = false,
        name = "canvas-progress-state"
    ) {
        BillboardTextLabel(
            text = "DONE ${state.processedCount(level)}/${level.packageCount}",
            position = listOf(0f, 0f, 0f),
            size = 0.32f,
            color = SCENE_TEXT,
            name = "canvas-stat-processed-value",
            id = "canvas-stat-processed-value"
        )
        ""
    }
}

@Composable
private fun CanvasManifestPanel(focusedPackage: FifthWallPackage?) {
    CanvasPanel(
        name = "canvas-manifest-panel",
        position = listOf(6.95f, 4.8f, 5.65f),
        width = 5.35f,
        height = 3.45f,
        accentColor = SCENE_ACCENT
    ) {
        CanvasText(
            text = "PACKAGE MANIFEST",
            position = listOf(-2.42f, 1.22f, 0.12f),
            size = 0.23f,
            color = SCENE_TEXT_MUTED,
            name = "canvas-manifest-title"
        )
        val rows = listOf(
            Triple("COLOR", focusedPackage?.color?.name?.replaceFirstChar { it.uppercase() } ?: "--", "canvas-manifest-color"),
            Triple("SHAPE", focusedPackage?.shapeDisplayLabel() ?: "--", "canvas-manifest-shape"),
            Triple("WEIGHT", focusedPackage?.weight?.let { "$it kg" } ?: "--", "canvas-manifest-weight"),
            Triple("VOLUME", focusedPackage?.volume?.let { "$it L" } ?: "--", "canvas-manifest-volume"),
            Triple("PATTERN", focusedPackage?.pattern?.replaceFirstChar { it.uppercase() } ?: "--", "canvas-manifest-pattern"),
            Triple("DEST", focusedPackage?.destination?.replaceFirstChar { it.uppercase() } ?: "--", "canvas-manifest-destination")
        )
        rows.forEachIndexed { index, (label, value, id) ->
            CanvasRow(
                label = label,
                value = value,
                y = 0.78f - (index * 0.43f),
                valueId = id
            )
        }
    }
}

@Composable
private fun CanvasRuleBoardPanel(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    CanvasPanel(
        name = "canvas-rule-board-panel",
        position = listOf(6.95f, 2.0f, 5.9f),
        width = 5.35f,
        height = 3.0f,
        accentColor = if (state.ruleShifted) SCENE_WARM else SCENE_ACCENT
    ) {
        CanvasText(
            text = "RULE BOARD",
            position = listOf(-2.42f, 0.95f, 0.12f),
            size = 0.23f,
            color = SCENE_TEXT_MUTED,
            name = "canvas-rule-board-title"
        )
        state.activeTrucks(level).forEachIndexed { index, rule ->
            val revealed = level.hiddenRuleIndex != index || state.hiddenRuleRevealed || state.ruleShifted
            CanvasText(
                text = "TRUCK ${'A' + index}  ${rule.label(revealed)}",
                position = listOf(-2.42f, 0.55f - (index * 0.43f), 0.12f),
                size = 0.22f,
                color = if (revealed) SCENE_TEXT else SCENE_TEXT_MUTED,
                name = "canvas-rule-truck-$index",
                lineHeight = 1.12f
            )
        }
        CanvasText(
            text = "RETURN BIN  Use when no truck rule matches.",
            position = listOf(-2.42f, 0.32f - (state.activeTrucks(level).size * 0.43f), 0.12f),
            size = 0.2f,
            color = SCENE_TEXT_MUTED,
            name = "canvas-rule-return"
        )
    }
}

@Composable
private fun CanvasPromptPanel(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    if (state.prompt == FifthWallPrompt.None) return

    CanvasPanel(
        name = "canvas-prompt-panel",
        position = listOf(0f, 4.25f, 7.35f),
        width = 8.2f,
        height = 3.45f,
        accentColor = SCENE_WARM
    ) {
        CanvasText(
            text = promptTitle(state.prompt),
            position = listOf(-3.72f, 1.28f, 0.12f),
            size = 0.27f,
            color = SCENE_TEXT,
            name = "canvas-prompt-title"
        )
        CanvasText(
            text = wrapCanvasText(promptCopy(level, state), 62),
            position = listOf(-3.72f, 0.82f, 0.12f),
            size = 0.23f,
            color = SCENE_TEXT,
            name = "canvas-prompt-copy",
            lineHeight = 1.18f
        )
        CanvasPromptControls(level = level, state = state)
    }
}

@Composable
private fun CanvasPromptControls(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    when (state.prompt) {
        FifthWallPrompt.Intro -> {
            CanvasButton(
                text = "ENTER BAY",
                position = listOf(-2.72f, -1.08f, 0.16f),
                width = 2.1f,
                height = 0.48f,
                interactionId = PROMPT_START_INTERACTION_ID,
                name = "canvas-prompt-start"
            )
        }

        FifthWallPrompt.ProbabilityPrediction -> {
            predictionChoices(level).forEachIndexed { index, value ->
                CanvasButton(
                    text = "$value/${level.packageCount}",
                    position = listOf(-2.72f + (index * 1.9f), -1.08f, 0.16f),
                    width = 1.55f,
                    height = 0.48f,
                    interactionId = promptPredictionInteractionId(value),
                    name = "canvas-prompt-prediction-$value"
                )
            }
        }

        FifthWallPrompt.TeamDiscussion -> {
            CanvasButton(
                text = "RESUME",
                position = listOf(-2.72f, -1.08f, 0.16f),
                width = 1.85f,
                height = 0.48f,
                interactionId = PROMPT_DISCUSSION_INTERACTION_ID,
                name = "canvas-prompt-discussion"
            )
        }

        FifthWallPrompt.RuleGuess -> {
            CanvasButton(
                text = "CONFIRM RULE",
                position = listOf(-2.72f, -1.08f, 0.16f),
                width = 2.45f,
                height = 0.48f,
                interactionId = PROMPT_RULE_ANSWER_INTERACTION_ID,
                name = "canvas-prompt-rule-answer"
            )
            CanvasText(
                text = hiddenRuleCanvasAnswer(level),
                position = listOf(-0.02f, -1.15f, 0.16f),
                size = 0.22f,
                color = SCENE_TEXT_MUTED,
                name = "canvas-prompt-rule-answer-preview"
            )
        }

        FifthWallPrompt.Confidence -> {
            listOf("Low", "Medium", "High").forEachIndexed { index, value ->
                CanvasButton(
                    text = value.uppercase(),
                    position = listOf(-2.72f + (index * 1.9f), -1.08f, 0.16f),
                    width = 1.55f,
                    height = 0.48f,
                    interactionId = promptConfidenceInteractionId(value),
                    name = "canvas-prompt-confidence-${value.lowercase()}"
                )
            }
        }

        FifthWallPrompt.LevelComplete -> {
            CanvasButton(
                text = "NEXT LEVEL",
                position = listOf(-2.72f, -1.08f, 0.16f),
                width = 2.2f,
                height = 0.48f,
                interactionId = PROMPT_NEXT_INTERACTION_ID,
                name = "canvas-prompt-next"
            )
        }

        FifthWallPrompt.GameComplete -> {
            CanvasButton(
                text = "RESTART",
                position = listOf(-2.72f, -1.08f, 0.16f),
                width = 1.9f,
                height = 0.48f,
                interactionId = PROMPT_RESET_INTERACTION_ID,
                name = "canvas-prompt-reset"
            )
        }

        FifthWallPrompt.None -> Unit
    }
}

@Composable
private fun CanvasRow(
    label: String,
    value: String,
    y: Float,
    valueId: String
) {
    CanvasText(
        text = label,
        position = listOf(-2.42f, y, 0.12f),
        size = 0.22f,
        color = SCENE_TEXT_MUTED,
        name = "$valueId-label"
    )
    CanvasText(
        text = value,
        position = listOf(-1.0f, y, 0.12f),
        size = 0.23f,
        color = SCENE_TEXT,
        name = valueId,
        id = valueId
    )
}

@Composable
private fun CanvasPanel(
    name: String,
    position: List<Float>,
    width: Float,
    height: Float,
    accentColor: Int,
    content: () -> Unit
) {
    SigilGroup(position = position, name = name, id = name) {
        SigilBox(
            width = width,
            height = 0.035f,
            depth = 0.12f,
            position = listOf(0f, (height / 2f) - 0.035f, 0.06f),
            color = accentColor,
            metalness = 0.36f,
            roughness = 0.24f,
            castShadow = false,
            receiveShadow = false,
            name = "$name-accent"
        )
        content()
        ""
    }
}

@Composable
private fun CanvasButton(
    text: String,
    position: List<Float>,
    width: Float,
    height: Float,
    interactionId: String,
    name: String,
    color: Int = SCENE_ACCENT
) {
    SigilGroup(
        position = position,
        name = name,
        interaction = routePadInteraction(
            interactionId = interactionId,
            width = width,
            depth = 0.58f,
            actions = listOf("prompt", "text-control"),
            height = height,
            centerY = 0f
        ),
        animations = listOf(targetPulseAnimation("$name-press", 0)),
        id = name
    ) {
        SigilBox(
            width = width,
            height = height,
            depth = 0.1f,
            position = listOf(0f, 0f, 0f),
            color = color,
            metalness = 0.44f,
            roughness = 0.28f,
            castShadow = false,
            receiveShadow = false,
            name = "$name-plate"
        )
        CanvasText(
            text = text,
            position = listOf(0f, 0f, 0.12f),
            size = 0.22f,
            color = SCENE_TEXT,
            align = TextAlignMode.CENTER,
            baseline = TextBaselineMode.MIDDLE,
            name = "$name-text",
            id = "$name-text"
        )
        ""
    }
}

@Composable
private fun CanvasText(
    text: String,
    position: List<Float>,
    size: Float,
    color: Int,
    name: String,
    id: String = name,
    align: TextAlignMode = TextAlignMode.LEFT,
    baseline: TextBaselineMode = TextBaselineMode.TOP,
    lineHeight: Float = 1.05f
) {
    SigilText(
        text = text.uppercase(),
        position = position,
        color = color,
        size = size,
        depth = 0f,
        curveSegments = 5,
        letterSpacing = meshTextLetterSpacing(size.coerceAtLeast(0.3f)),
        lineHeight = lineHeight,
        align = align,
        baseline = baseline,
        facingMode = TextFacingMode.BILLBOARD,
        fontUrl = FIFTH_WALL_TEXT_FONT,
        name = name,
        id = id
    )
}

private fun promptTitle(prompt: FifthWallPrompt): String =
    when (prompt) {
        FifthWallPrompt.Intro -> "Courier Protocol 3D"
        FifthWallPrompt.ProbabilityPrediction -> "Prediction Lock"
        FifthWallPrompt.TeamDiscussion -> "Dispatch Pause"
        FifthWallPrompt.RuleGuess -> "Name the Hidden Rule"
        FifthWallPrompt.Confidence -> "Confidence Check"
        FifthWallPrompt.LevelComplete -> "Shift Cleared"
        FifthWallPrompt.GameComplete -> "All Bays Clear"
        FifthWallPrompt.None -> ""
    }

private fun promptCopy(
    level: FifthWallLevel,
    state: FifthWallUiState
): String =
    when (state.prompt) {
        FifthWallPrompt.Intro ->
            "FOCUS PACKAGE\nREAD RULE BOARD\nROUTE OR RETURN"

        FifthWallPrompt.ProbabilityPrediction ->
            "TRUCK A ACCEPTS 70 PERCENT\nPICK EXPECTED PASSES"

        FifthWallPrompt.TeamDiscussion ->
            "DISPATCH PAUSE\nRESUME THE SHIFT"

        FifthWallPrompt.RuleGuess ->
            "CONFIRM THE HIDDEN TRUCK A RULE"

        FifthWallPrompt.Confidence ->
            state.pendingConfidence?.let { "${it.outcome} ON ${it.routeLabel}\nRECORD CONFIDENCE" }
                ?: "RECORD CONFIDENCE"

        FifthWallPrompt.LevelComplete ->
            "${level.name} CLEAR\nSCORE ${state.score}"

        FifthWallPrompt.GameComplete ->
            "ALL BAYS CLEAR\nRESTART SHIFT"

        FifthWallPrompt.None -> ""
    }

private fun wrapCanvasText(
    text: String,
    maxChars: Int
): String =
    text.lineSequence().joinToString("\n") { line ->
        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wrapped = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isBlank()) word else "$current $word"
            if (candidate.length > maxChars && current.isNotBlank()) {
                wrapped += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotBlank()) wrapped += current
        wrapped.joinToString("\n")
    }

private fun predictionChoices(level: FifthWallLevel): List<Int> =
    listOf(0, (level.packageCount * 7) / 10, level.packageCount).distinct()

private fun promptPredictionInteractionId(value: Int): String =
    "prompt:prediction:$value"

private fun promptConfidenceInteractionId(value: String): String =
    "prompt:confidence:${value.lowercase()}"

private fun hiddenRuleCanvasAnswer(level: FifthWallLevel): String {
    val hiddenRule = level.hiddenRuleIndex?.let { level.trucks.getOrNull(it) } ?: level.trucks.firstOrNull()
    return when (hiddenRule?.kind) {
        FifthWallRuleKind.Weight ->
            "weight ${hiddenRule.comparator ?: ">="} ${hiddenRule.threshold ?: 0}"

        FifthWallRuleKind.Color ->
            "color ${hiddenRule.value.orEmpty()}"

        FifthWallRuleKind.Shape ->
            "shape ${hiddenRule.value.orEmpty()}"

        FifthWallRuleKind.Pattern ->
            "pattern ${hiddenRule.value.orEmpty()}"

        FifthWallRuleKind.Destination ->
            "destination ${hiddenRule.value.orEmpty()}"

        FifthWallRuleKind.Geometry ->
            "geometry ${hiddenRule.value.orEmpty()}"

        FifthWallRuleKind.Volume ->
            "volume ${hiddenRule.comparator ?: ">="} ${hiddenRule.threshold ?: 0}"

        FifthWallRuleKind.Probability,
        null -> "weight >= 8"
    }
}

@Composable
private fun RouteControlPads(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    val trucks = state.activeTrucks(level)
    val spacing = truckSpacing(trucks.size)
    val origin = truckOrigin(trucks.size, spacing)

    trucks.forEachIndexed { index, rule ->
        val routeLabel = "Truck ${'A' + index}"
        val selected = state.lastRouteTarget == routeLabel
        RouteTruckPad(
            index = index,
            position = listOf(origin + (spacing * index), 0.08f, 6.0f),
            color = truckColor(index, rule),
            selected = selected,
            routeAccepted = if (selected) state.lastRouteAccepted else null
        )
    }
    RouteReturnPad(
        selected = state.lastRouteTarget == "Return Bin",
        routeAccepted = if (state.lastRouteTarget == "Return Bin") state.lastRouteAccepted else null
    )
    ResetShiftPad()
}

@Composable
private fun DispatchConsoleControls(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    val visibleIds = state.visiblePackages().map { it.id }
    val focusedId = state.focusPackage()?.id
    val trucks = state.activeTrucks(level)

    state.queue.forEach { pkg ->
        val visibleIndex = visibleIds.indexOf(pkg.id)
        val visible = visibleIndex in 0 until CONSOLE_FOCUS_SLOT_COUNT
        val focused = visible && pkg.id == focusedId
        DispatchConsoleControl(
            text = "FOCUS P${visibleIndex.coerceAtLeast(0) + 1}",
            position = if (visible) consoleFocusControlPosition(visibleIndex) else CONTROL_PAD_HIDDEN_POSITION,
            color = if (focused) SCENE_ACCENT else argb(pkg.color.hex),
            textColor = SCENE_TEXT,
            interactionId = consoleFocusInteractionId(pkg.id),
            actions = listOf("focus", "package", "text-control"),
            selected = focused,
            width = 2.65f,
            depth = 1.28f,
            textSize = 0.27f,
            visible = visible,
            name = "dispatch-console-focus-p${visibleIndex.coerceAtLeast(0) + 1}",
            id = consoleFocusControlId(pkg.id)
        )
    }

    BillboardTextLabel(
        text = "INSPECT",
        position = listOf(4.25f, 2.62f, -2.5f),
        size = 0.38f,
        color = SCENE_ACCENT,
        name = "dispatch-console-inspect-title"
    )
    BillboardTextLabel(
        text = "COMPARE",
        position = listOf(4.55f, 2.62f, 0.7f),
        size = 0.36f,
        color = SCENE_TEXT,
        name = "dispatch-console-compare-title"
    )
    val routeCount = trucks.size + 1
    trucks.forEachIndexed { index, rule ->
        val label = "Truck ${'A' + index}"
        val selected = state.lastRouteTarget == label
        val feedback = routeFeedbackColor(
            selected = selected,
            routeAccepted = state.lastRouteAccepted,
            fallback = truckColor(index, rule)
        )
        DispatchConsoleControl(
            text = "TRUCK ${'A' + index}",
            position = consoleRouteControlPosition(index, routeCount),
            color = feedback,
            textColor = if (level.hiddenRuleIndex == index && !state.hiddenRuleRevealed && !state.ruleShifted) {
                SCENE_TEXT_MUTED
            } else {
                SCENE_TEXT
            },
            interactionId = consoleTruckInteractionId(index),
            actions = listOf("route", "truck", "text-control"),
            selected = selected,
            width = 2.65f,
            depth = 1.32f,
            textSize = 0.3f,
            visible = true,
            name = "dispatch-console-route-truck-${'A' + index}",
            id = consoleRouteTruckControlId(index)
        )
    }
    val returnSelected = state.lastRouteTarget == "Return Bin"
    DispatchConsoleControl(
        text = "RETURN",
        position = consoleRouteControlPosition(trucks.size, routeCount),
        color = routeFeedbackColor(
            selected = returnSelected,
            routeAccepted = state.lastRouteAccepted,
            fallback = SCENE_WARM
        ),
        textColor = SCENE_TEXT,
        interactionId = CONSOLE_RETURN_INTERACTION_ID,
        actions = listOf("route", "return", "text-control"),
        selected = returnSelected,
        width = 2.65f,
        depth = 1.32f,
        textSize = 0.3f,
        visible = true,
        name = "dispatch-console-return-bin",
        id = CONSOLE_RETURN_CONTROL_ID
    )
    DispatchConsoleControl(
        text = "RESET",
        position = listOf(-4.9f, 2.75f, 2.9f),
        color = SCENE_DANGER,
        textColor = SCENE_TEXT,
        interactionId = CONSOLE_RESET_INTERACTION_ID,
        actions = listOf("reset", "text-control"),
        selected = false,
        width = 2.45f,
        depth = 1.08f,
        textSize = 0.34f,
        visible = true,
        name = "dispatch-console-reset",
        id = CONSOLE_RESET_CONTROL_ID
    )
}

@Composable
private fun DispatchConsoleControl(
    text: String,
    position: List<Float>,
    color: Int,
    textColor: Int,
    interactionId: String,
    actions: List<String>,
    selected: Boolean,
    width: Float,
    depth: Float,
    textSize: Float,
    visible: Boolean,
    name: String,
    id: String
) {
    SigilGroup(
        position = position,
        visible = visible,
        name = name,
        interaction = routePadInteraction(
            interactionId = interactionId,
            width = width,
            depth = depth,
            actions = actions,
            height = 1.72f,
            centerY = 0.72f
        ),
        animations = listOf(targetPulseAnimation("$id-press", 120)),
        id = id
    ) {
        SigilBox(
            width = width * 0.78f,
            height = 0.09f,
            depth = 0.13f,
            position = listOf(0f, 0.17f, -depth * 0.34f),
            color = color,
            metalness = 0.44f,
            roughness = 0.28f,
            castShadow = false,
            receiveShadow = false,
            name = "$name-led"
        )
        SigilMesh(
            geometryType = GeometryType.RING,
            geometryParams = GeometryParams(innerRadius = 0.43f, outerRadius = 0.56f, radialSegments = 42),
            position = listOf(-width * 0.34f, 0.22f, depth * 0.2f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = color,
            metalness = 0.72f,
            roughness = 0.14f,
            castShadow = false,
            receiveShadow = false,
            name = "$name-ring"
        )
        BillboardTextControl(
            text = text,
            position = listOf(0.08f, 0.66f, 0.03f),
            size = textSize,
            color = textColor,
            interactionId = interactionId,
            actions = actions,
            hitWidth = width,
            hitHeight = 1.28f,
            hitDepth = depth,
            name = "$name-text-control",
            lineHeight = 1.14f
        )
        ""
    }
}

@Composable
private fun RouteTruckPad(
    index: Int,
    position: List<Float>,
    color: Int,
    selected: Boolean,
    routeAccepted: Boolean?
) {
    val feedback = routeFeedbackColor(selected = selected, routeAccepted = routeAccepted, fallback = color)
    SigilGroup(
        position = position,
        name = "route-pad-truck-${'A' + index}",
        interaction = routePadInteraction(
            interactionId = consoleTruckInteractionId(index),
            width = 4.35f,
            depth = 2.75f,
            actions = listOf("route", "truck", "control-pad"),
            height = 1.85f,
            centerY = 0.72f
        ),
        animations = listOf(targetPulseAnimation("route-pad-truck-$index-press", 80 + (index * 50))),
        id = routePadTruckId(index)
    ) {
        SigilMesh(
            geometryType = GeometryType.CIRCLE,
            geometryParams = GeometryParams(radius = 1.3f, radialSegments = 56),
            position = listOf(0f, 0.02f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = argb("#102131"),
            metalness = 0.28f,
            roughness = 0.64f,
            castShadow = false,
            receiveShadow = true,
            name = "route-pad-truck-$index-disc"
        )
        SigilMesh(
            geometryType = GeometryType.RING,
            geometryParams = GeometryParams(innerRadius = 1.14f, outerRadius = 1.66f, radialSegments = 72),
            position = listOf(0f, 0.045f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = feedback,
            metalness = 0.72f,
            roughness = 0.12f,
            castShadow = false,
            receiveShadow = false,
            name = "route-pad-truck-$index-ring"
        )
        SigilBox(
            width = 1.42f,
            height = 0.12f,
            depth = 0.24f,
            position = listOf(-0.34f, 0.12f, -0.12f),
            color = feedback,
            metalness = 0.32f,
            roughness = 0.36f,
            castShadow = false,
            receiveShadow = false,
            name = "route-pad-truck-$index-arrow-a"
        )
        SigilBox(
            width = 0.96f,
            height = 0.12f,
            depth = 0.24f,
            position = listOf(0.2f, 0.12f, 0.26f),
            color = feedback,
            metalness = 0.32f,
            roughness = 0.36f,
            castShadow = false,
            receiveShadow = false,
            name = "route-pad-truck-$index-arrow-b"
        )
        listOf(-1.04f, 1.04f).forEachIndexed { beaconIndex, x ->
            SigilMesh(
                geometryType = GeometryType.CYLINDER,
                geometryParams = GeometryParams(
                    radiusTop = 0.1f,
                    radiusBottom = 0.16f,
                    height = 0.95f,
                    radialSegments = 14
                ),
                position = listOf(x, 0.54f, 0.64f),
                color = feedback,
                metalness = 0.46f,
                roughness = 0.24f,
                castShadow = false,
                receiveShadow = false,
                name = "route-pad-truck-$index-beacon-post-$beaconIndex"
            )
            SigilSphere(
                radius = 0.28f,
                widthSegments = 16,
                heightSegments = 12,
                position = listOf(x, 1.08f, 0.64f),
                color = feedback,
                metalness = 0.26f,
                roughness = 0.26f,
                castShadow = false,
                receiveShadow = false,
                name = "route-pad-truck-$index-beacon-cap-$beaconIndex"
            )
        }
        SigilSphere(
            radius = 0.24f,
            widthSegments = 16,
            heightSegments = 12,
            position = listOf(0f, 0.24f, -0.86f),
            color = feedback,
            metalness = 0.26f,
            roughness = 0.38f,
            castShadow = false,
            receiveShadow = false,
            name = "route-pad-truck-$index-beacon"
        )
        ""
    }
}

@Composable
private fun RouteReturnPad(
    selected: Boolean,
    routeAccepted: Boolean?
) {
    val feedback = routeFeedbackColor(selected = selected, routeAccepted = routeAccepted, fallback = SCENE_WARM)
    SigilGroup(
        position = listOf(3.8f, 0.08f, 3.4f),
        name = "route-pad-return",
        interaction = routePadInteraction(
            interactionId = CONSOLE_RETURN_INTERACTION_ID,
            width = 3.8f,
            depth = 2.8f,
            actions = listOf("route", "return", "control-pad"),
            height = 1.75f,
            centerY = 0.68f
        ),
        animations = listOf(targetPulseAnimation("route-pad-return-press", 220)),
        id = ROUTE_PAD_RETURN_ID
    ) {
        SigilMesh(
            geometryType = GeometryType.CIRCLE,
            geometryParams = GeometryParams(radius = 1.24f, radialSegments = 56),
            position = listOf(0f, 0.02f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = argb("#261f12"),
            metalness = 0.28f,
            roughness = 0.66f,
            castShadow = false,
            receiveShadow = true,
            name = "route-pad-return-disc"
        )
        SigilMesh(
            geometryType = GeometryType.TORUS,
            geometryParams = GeometryParams(radius = 0.82f, tube = 0.1f, radialSegments = 20, tubularSegments = 64),
            position = listOf(0f, 0.18f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = feedback,
            metalness = 0.72f,
            roughness = 0.14f,
            castShadow = false,
            receiveShadow = false,
            name = "route-pad-return-loop"
        )
        SigilBox(
            width = 0.46f,
            height = 0.12f,
            depth = 0.92f,
            position = listOf(0.86f, 0.15f, 0f),
            color = feedback,
            metalness = 0.34f,
            roughness = 0.32f,
            castShadow = false,
            receiveShadow = false,
            name = "route-pad-return-tail"
        )
        SigilMesh(
            geometryType = GeometryType.CYLINDER,
            geometryParams = GeometryParams(
                radiusTop = 0.12f,
                radiusBottom = 0.18f,
                height = 1.0f,
                radialSegments = 16
            ),
            position = listOf(-0.95f, 0.56f, 0.58f),
            color = feedback,
            metalness = 0.46f,
            roughness = 0.24f,
            castShadow = false,
            receiveShadow = false,
            name = "route-pad-return-beacon-post"
        )
        SigilSphere(
            radius = 0.32f,
            widthSegments = 18,
            heightSegments = 12,
            position = listOf(-0.95f, 1.14f, 0.58f),
            color = feedback,
            metalness = 0.26f,
            roughness = 0.28f,
            castShadow = false,
            receiveShadow = false,
            name = "route-pad-return-beacon-cap"
        )
        ""
    }
}

@Composable
private fun ResetShiftPad() {
    SigilGroup(
        position = listOf(-5.5f, 0.08f, 5.1f),
        name = "reset-shift-pad",
        interaction = routePadInteraction(
            interactionId = CONSOLE_RESET_INTERACTION_ID,
            width = 3.2f,
            depth = 1.9f,
            actions = listOf("reset", "control-pad"),
            height = 1.15f,
            centerY = 0.38f
        ),
        animations = listOf(targetPulseAnimation("reset-shift-pad-press", 260)),
        id = RESET_PAD_ID
    ) {
        SigilBox(
            width = 2.85f,
            height = 0.14f,
            depth = 1.42f,
            position = listOf(0f, 0.06f, 0f),
            color = argb("#27181c"),
            metalness = 0.34f,
            roughness = 0.56f,
            castShadow = false,
            receiveShadow = true,
            name = "reset-shift-pad-base"
        )
        SigilMesh(
            geometryType = GeometryType.RING,
            geometryParams = GeometryParams(innerRadius = 0.42f, outerRadius = 0.7f, radialSegments = 48),
            position = listOf(0f, 0.16f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = SCENE_DANGER,
            metalness = 0.72f,
            roughness = 0.14f,
            castShadow = false,
            receiveShadow = false,
            name = "reset-shift-pad-ring"
        )
        listOf(-0.52f, 0.52f).forEachIndexed { index, x ->
            SigilBox(
                width = 0.72f,
                height = 0.1f,
                depth = 0.16f,
                position = listOf(x, 0.17f, 0f),
                color = SCENE_DANGER,
                metalness = 0.28f,
                roughness = 0.42f,
                castShadow = false,
                receiveShadow = false,
                name = "reset-shift-pad-bar-$index"
            )
        }
        SigilSphere(
            radius = 0.24f,
            widthSegments = 16,
            heightSegments = 12,
            position = listOf(1.14f, 0.34f, 0.46f),
            color = SCENE_DANGER,
            metalness = 0.24f,
            roughness = 0.38f,
            castShadow = false,
            receiveShadow = false,
            name = "reset-shift-pad-warning"
        )
        ""
    }
}

@Composable
private fun ConveyorDeck(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    SigilGroup(position = listOf(-2.2f, 0f, 0.5f), name = "conveyor-group") {
        SigilModel(
            url = fifthWallModelUrl("conveyor-deck.glb"),
            scale = uniformScale(10.6f),
            castShadow = false,
            receiveShadow = false,
            name = "conveyor-model"
        )
        val visiblePackageIds = state.visiblePackages().map { it.id }
        state.queue.forEach { pkg ->
            val visibleIndex = visiblePackageIds.indexOf(pkg.id)
            val visible = visibleIndex in 0 until CONSOLE_FOCUS_SLOT_COUNT
            PackageFocusPad(
                pkg = pkg,
                position = if (visible) packageFocusPadPosition(visibleIndex) else CONTROL_PAD_HIDDEN_POSITION,
                visible = visible,
                focused = visible && (state.selectedPackageId == pkg.id || (state.selectedPackageId == null && visibleIndex == 0))
            )
        }
        state.queue.forEach { pkg ->
            val visibleIndex = visiblePackageIds.indexOf(pkg.id)
            val visible = visibleIndex >= 0
            val selected = visible && (state.selectedPackageId == pkg.id || (state.selectedPackageId == null && visibleIndex == 0))
            PackageMesh(
                pkg = pkg,
                position = if (visible) packageBeltPosition(visibleIndex) else PACKAGE_HIDDEN_POSITION,
                selected = selected,
                emphasized = visibleIndex == 0,
                beltIndex = visibleIndex,
                level = level,
                visible = visible
            )
        }
        ""
    }
}

@Composable
private fun PackageFocusPad(
    pkg: FifthWallPackage,
    position: List<Float>,
    visible: Boolean,
    focused: Boolean
) {
    val color = argb(pkg.color.hex)
    SigilGroup(
        position = position,
        visible = visible,
        name = "package-focus-pad-${pkg.id}",
        interaction = routePadInteraction(
            interactionId = consoleFocusInteractionId(pkg.id),
            width = 2.65f,
            depth = 2.05f,
            actions = listOf("focus", "package", "control-pad")
        ),
        animations = listOf(targetPulseAnimation("package-focus-pad-${pkg.id}-press", 0)),
        id = packageFocusPadId(pkg.id)
    ) {
        SigilMesh(
            geometryType = GeometryType.RING,
            geometryParams = GeometryParams(innerRadius = 0.92f, outerRadius = 1.28f, radialSegments = 60),
            position = listOf(0f, 0.02f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = if (focused) SCENE_ACCENT else color,
            metalness = 0.78f,
            roughness = 0.12f,
            castShadow = false,
            receiveShadow = false,
            name = "package-focus-pad-${pkg.id}-ring"
        )
        SigilBox(
            width = 1.08f,
            height = 0.08f,
            depth = 0.16f,
            position = listOf(-0.06f, 0.08f, -0.76f),
            color = if (focused) SCENE_ACCENT else color,
            metalness = 0.34f,
            roughness = 0.34f,
            castShadow = false,
            receiveShadow = false,
            name = "package-focus-pad-${pkg.id}-signal-a"
        )
        SigilBox(
            width = 0.78f,
            height = 0.08f,
            depth = 0.16f,
            position = listOf(0.1f, 0.08f, -0.52f),
            color = if (focused) SCENE_ACCENT else color,
            metalness = 0.34f,
            roughness = 0.34f,
            castShadow = false,
            receiveShadow = false,
            name = "package-focus-pad-${pkg.id}-signal-b"
        )
        SigilSphere(
            radius = if (focused) 0.2f else 0.16f,
            widthSegments = 14,
            heightSegments = 10,
            position = listOf(-0.82f, 0.14f, -0.55f),
            color = color,
            metalness = 0.24f,
            roughness = 0.4f,
            castShadow = false,
            receiveShadow = false,
            name = "package-focus-pad-${pkg.id}-beacon"
        )
        ""
    }
}

private fun packageBeltPosition(index: Int): List<Float> =
    listOf(-4.9f + (index * 3.2f), 2.34f, 0f)

private fun packageFocusPadPosition(index: Int): List<Float> =
    listOf(-4.9f + (index * 3.2f), 2.2f, 0f)

private fun consoleFocusControlPosition(index: Int): List<Float> =
    listOf(-3.25f + (index * 2.45f), 2.75f, 1.25f)

private fun consoleRouteControlPosition(index: Int, count: Int): List<Float> {
    val spacing = if (count <= 3) 2.65f else 2.25f
    val origin = -((count - 1) * spacing) / 2f
    return listOf(origin + (index * spacing) + 0.35f, 2.75f, 2.9f)
}

private fun packageScale(enlarged: Boolean, emphasized: Boolean): Float = when {
    enlarged -> 1.38f
    emphasized -> 1.08f
    else -> 1f
}

private fun packageScaleVector(enlarged: Boolean, emphasized: Boolean): List<Float> {
    val scale = packageScale(enlarged = enlarged, emphasized = emphasized)
    return listOf(scale, scale, scale)
}

private fun packageRotation(pkg: FifthWallPackage, beltIndex: Int): List<Float> {
    val yaw = when (pkg.shape) {
        "rect" -> 0.18f
        "cylinder" -> 0.08f
        "sphere" -> 0.22f
        else -> -0.12f
    }
    return listOf(0f, yaw + (beltIndex.coerceAtLeast(0) * 0.08f), 0f)
}

private fun packageHover(enlarged: Boolean, emphasized: Boolean): Float =
    if (enlarged) 0.18f else if (emphasized) 0.1f else 0f

private fun packageBodyPosition(pkg: FifthWallPackage, hover: Float): List<Float> {
    val y = when (pkg.shape) {
        "sphere" -> 0.05f
        else -> 0.08f
    }
    return listOf(0f, y + hover, 0f)
}

@Composable
private fun TruckBay(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    val trucks = state.activeTrucks(level)
    val spacing = truckSpacing(trucks.size)
    val origin = truckOrigin(trucks.size, spacing)

    trucks.forEachIndexed { index, rule ->
        val x = origin + (spacing * index)
        val routeLabel = "Truck ${'A' + index}"
        DeliveryTruck(
            index = index,
            label = routeLabel,
            position = listOf(x, 0f, 4.7f),
            color = truckColor(index, rule),
            selected = state.lastRouteTarget == routeLabel,
            routeAccepted = if (state.lastRouteTarget == routeLabel) state.lastRouteAccepted else null,
            hidden = level.hiddenRuleIndex == index && !state.hiddenRuleRevealed && !state.ruleShifted,
            glitched = state.glitchActive
        )
    }
}

@Composable
private fun DeliveryTruck(
    index: Int,
    label: String,
    position: List<Float>,
    color: Int,
    selected: Boolean,
    routeAccepted: Boolean?,
    hidden: Boolean,
    glitched: Boolean
) {
    val accent = if (hidden) argb("#3b4656") else color
    SigilGroup(
        position = position,
        scale = listOf(1.08f, 1.08f, 1.08f),
        name = label.lowercase().replace(" ", "-"),
        interaction = truckInteraction(index, label),
        animations = listOf(targetPulseAnimation("truck-$index-ready", delayMs = index * 80)),
        id = "truck-$index"
    ) {
        SigilModel(
            url = fifthWallModelUrl("delivery-truck.glb"),
            position = listOf(0.1f, 0f, 0f),
            scale = uniformScale(4.2f),
            castShadow = false,
            receiveShadow = false,
            name = "$label-model"
        )
        TruckAccentMarker(
            accent = accent,
            hidden = hidden,
            name = label.lowercase().replace(" ", "-")
        )
        if (selected) {
            SigilMesh(
                geometryType = GeometryType.RING,
                geometryParams = GeometryParams(innerRadius = 1.55f, outerRadius = 1.85f, radialSegments = 48),
                position = listOf(0.1f, 0.05f, 0f),
                rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
                color = when (routeAccepted) {
                    true -> SCENE_SUCCESS
                    false -> SCENE_DANGER
                    null -> SCENE_ACCENT
                },
                metalness = 0.8f,
                roughness = 0.1f,
                castShadow = false,
                receiveShadow = false,
                name = "$label-ring"
            )
        }
        if (glitched) {
            SigilMesh(
                geometryType = GeometryType.TORUS,
                geometryParams = GeometryParams(radius = 0.72f, tube = 0.05f, radialSegments = 16, tubularSegments = 42),
                position = listOf(0.35f, 2.1f, 0f),
                rotation = listOf(0.35f, 0.62f, 0f),
                color = SCENE_DANGER,
                metalness = 0.74f,
                roughness = 0.12f,
                castShadow = false,
                receiveShadow = false,
                name = "$label-glitch-halo"
            )
        }
        ""
    }
}

@Composable
private fun TruckAccentMarker(
    accent: Int,
    hidden: Boolean,
    name: String
) {
    val panelColor = if (hidden) argb("#6b7485") else accent
    listOf(-0.36f, 0.36f).forEachIndexed { index, z ->
        SigilBox(
            width = 0.58f,
            height = 0.08f,
            depth = 0.08f,
            position = listOf(-0.36f, 2.86f, z),
            color = panelColor,
            metalness = 0.18f,
            roughness = 0.72f,
            castShadow = false,
            receiveShadow = false,
            name = "$name-roof-rule-tag-$index"
        )
    }
    SigilSphere(
        radius = 0.14f,
        widthSegments = 12,
        heightSegments = 10,
        position = listOf(-1.1f, 3.02f, 0f),
        color = panelColor,
        metalness = 0.18f,
        roughness = 0.68f,
        castShadow = false,
        receiveShadow = false,
        name = "$name-accent-beacon"
    )
}

@Composable
private fun ReturnBinBay(
    selected: Boolean,
    routeAccepted: Boolean?,
    glitchActive: Boolean
) {
    SigilGroup(
        position = listOf(9.2f, 0f, 4.85f),
        scale = listOf(1.08f, 1.08f, 1.08f),
        name = "return-bin",
        interaction = returnBinInteraction(),
        animations = listOf(targetPulseAnimation("return-bin-ready", delayMs = 260)),
        id = "return-bin-target"
    ) {
        SigilModel(
            url = fifthWallModelUrl("return-bin.glb"),
            scale = uniformScale(2.25f),
            castShadow = false,
            receiveShadow = false,
            name = "return-bin-model"
        )
        SigilMesh(
            geometryType = GeometryType.TORUS,
            geometryParams = GeometryParams(radius = 0.96f, tube = 0.08f, radialSegments = 16, tubularSegments = 48),
            position = listOf(0f, 1.72f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = when {
                !selected -> SCENE_WARM
                routeAccepted == true -> SCENE_SUCCESS
                routeAccepted == false -> SCENE_DANGER
                else -> SCENE_ACCENT
            },
            metalness = 0.72f,
            roughness = 0.16f,
            castShadow = false,
            receiveShadow = false,
            name = "return-ring"
        )
        ""
    }
}

@Composable
private fun InspectionDock(pkg: FifthWallPackage?) {
    SigilGroup(
        position = listOf(8.2f, 0f, -5.5f),
        name = "inspection-dock",
        interaction = inspectionDockInteraction(),
        animations = listOf(targetPulseAnimation("inspection-dock-ready", delayMs = 180)),
        id = "inspection-dock-target"
    ) {
        SigilModel(
            url = fifthWallModelUrl("inspection-dock.glb"),
            scale = uniformScale(4.1f),
            castShadow = false,
            receiveShadow = false,
            name = "inspection-dock-model"
        )
        SigilMesh(
            geometryType = GeometryType.RING,
            geometryParams = GeometryParams(innerRadius = 0.95f, outerRadius = 1.22f, radialSegments = 48),
            position = listOf(0f, 2.38f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = if (pkg?.validGeometry == false) SCENE_DANGER else SCENE_ACCENT,
            metalness = 0.74f,
            roughness = 0.14f,
            castShadow = false,
            receiveShadow = false,
            name = "inspection-ring"
        )
        BillboardTextLabel(
            text = "INSPECT",
            position = listOf(0f, 4.1f, 0f),
            size = 0.28f,
            color = if (pkg?.validGeometry == false) SCENE_DANGER else SCENE_TEXT,
            name = "inspection-dock-label"
        )
        if (pkg != null) {
            PackageMesh(
                pkg = pkg,
                position = listOf(0f, 3.18f, 0f),
                selected = true,
                emphasized = true,
                beltIndex = -1,
                level = null,
                enlarged = true,
                interactive = false,
                nodeId = "inspection-package-${pkg.id}"
            )
        } else {
            SigilModel(
                url = fifthWallModelUrl("special-delivery-package.glb"),
                position = listOf(0f, 2.9f, 0f),
                rotation = listOf(0f, 0.35f, 0f),
                scale = uniformScale(1.28f),
                castShadow = false,
                receiveShadow = false,
                name = "inspection-idle-package"
            )
        }
        ""
    }
}

@Composable
private fun PackageMesh(
    pkg: FifthWallPackage,
    position: List<Float>,
    selected: Boolean,
    emphasized: Boolean,
    beltIndex: Int,
    level: FifthWallLevel?,
    enlarged: Boolean = false,
    visible: Boolean = true,
    interactive: Boolean = true,
    nodeId: String = "package-${pkg.id}"
) {
    val baseColor = argb(pkg.color.hex)
    val scale = packageScale(enlarged = enlarged, emphasized = emphasized)
    val hover = packageHover(enlarged = enlarged, emphasized = emphasized)

    SigilGroup(
        position = position,
        rotation = packageRotation(pkg, beltIndex),
        scale = listOf(scale, scale, scale),
        visible = visible,
        name = nodeId,
        interaction = if (interactive) packageInteraction(pkg, enlarged) else null,
        animations = if (visible || enlarged) {
            packageAnimations(pkg, selected)
        } else {
            emptyList()
        },
        id = nodeId
    ) {
        if (visible || enlarged) {
            SigilBox(
                width = when (pkg.shape) {
                    "rect" -> 2.08f
                    "cylinder" -> 1.18f
                    else -> 1.34f
                },
                height = 0.04f,
                depth = when (pkg.shape) {
                    "rect" -> 1.08f
                    "sphere" -> 1.2f
                    else -> 1.34f
                },
                position = listOf(0f, 0.04f, 0f),
                color = PACKAGE_SHADE,
                metalness = 0.06f,
                roughness = 0.98f,
                castShadow = false,
                receiveShadow = false,
                name = "pkg-shadow"
            )
        }
        PackageBodyModel(
            pkg = pkg,
            position = packageBodyPosition(pkg, hover),
            id = "$nodeId-body"
        )
        if (visible || enlarged) {
            PackageColorAccent(
                shape = pkg.shape,
                color = baseColor,
                hover = hover
            )
            PackageBadge(pkg = pkg, hover = hover)
            GeometryAura(pkg = pkg, level = level, elevated = enlarged)
        }
        if (selected) {
            SigilMesh(
                geometryType = GeometryType.TORUS,
                geometryParams = GeometryParams(
                    radius = when (pkg.shape) {
                        "rect" -> 1.18f
                        "cylinder" -> 0.8f
                        "sphere" -> 0.9f
                        else -> 0.98f
                    },
                    tube = 0.06f,
                    radialSegments = 18,
                    tubularSegments = 44
                ),
                position = listOf(0f, 0.06f, 0f),
                rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
                color = if (pkg.validGeometry) SCENE_ACCENT else SCENE_DANGER,
                metalness = 0.84f,
                roughness = 0.08f,
                castShadow = false,
                receiveShadow = false,
                name = "pkg-selection"
            )
        }
        ""
    }
}

@Composable
private fun PackageBodyModel(
    pkg: FifthWallPackage,
    position: List<Float>,
    id: String
) {
    SigilModel(
        url = fifthWallModelUrl(packageModelFile(pkg)),
        position = position,
        rotation = packageModelRotation(pkg),
        scale = packageModelScale(pkg),
        visible = true,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-body-model",
        id = id
    )
}

private fun packageModelFile(pkg: FifthWallPackage): String =
    when (pkg.shape) {
        "rect" -> "rectangular-parcel.glb"
        "cylinder" -> "cylinder-drum.glb"
        "sphere" -> "sphere-package-with-cradle.glb"
        else -> "cube-crate.glb"
    }

private fun packageModelScale(pkg: FifthWallPackage): List<Float> =
    when (pkg.shape) {
        "rect" -> listOf(2.08f, 1.22f, 1.08f)
        "cylinder" -> listOf(1.34f, 1.36f, 1.34f)
        "sphere" -> listOf(1.48f, 1.36f, 1.48f)
        else -> listOf(1.36f, 1.18f, 1.36f)
    }

private fun packageModelRotation(pkg: FifthWallPackage): List<Float> =
    when (pkg.shape) {
        "cylinder" -> listOf(0f, 0.18f, 0f)
        "sphere" -> listOf(0f, -0.12f, 0f)
        else -> listOf(0f, 0f, 0f)
    }

@Composable
private fun PackageColorAccent(
    shape: String,
    color: Int,
    hover: Float
) {
    val markerY = when (shape) {
        "cylinder" -> 1.22f
        "sphere" -> 1.14f
        else -> 1.04f
    } + hover
    SigilSphere(
        radius = 0.13f,
        widthSegments = 12,
        heightSegments = 10,
        position = listOf(-0.72f, markerY, 0.56f),
        color = color,
        metalness = 0.18f,
        roughness = 0.48f,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-color-marker"
    )
    SigilBox(
        width = 0.42f,
        height = 0.05f,
        depth = 0.06f,
        position = listOf(-0.72f, markerY - 0.18f, 0.56f),
        color = color,
        metalness = 0.2f,
        roughness = 0.5f,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-color-marker-stem"
    )

}

@Composable
private fun WrenchProp(visible: Boolean) {
    SigilGroup(
        position = listOf(13.1f, 0.06f, 9.2f),
        rotation = listOf(0f, -0.42f, 0f),
        visible = visible,
        name = "repair-wrench-prop",
        interaction = wrenchInteraction(),
        animations = listOf(
            SceneAnimationData(
                id = "repair-wrench-pulse",
                trigger = AnimationTrigger.SCENE_LOAD,
                kind = AnimationKind.PULSE,
                durationMs = 1400,
                delayMs = 0,
                easing = AnimationEasing.EASE_IN_OUT,
                vector = null,
                color = null,
                intensity = 0.72f,
                repeat = 24
            )
        ),
        id = "repair-wrench"
    ) {
        SigilModel(
            url = fifthWallModelUrl("repair-wrench.glb"),
            scale = listOf(1.2f, 1.2f, 1.2f),
            rotation = listOf(0f, 0.72f, 0f),
            castShadow = false,
            receiveShadow = false,
            name = "repair-wrench-model"
        )
        BillboardTextLabel(
            text = "REPAIR",
            position = listOf(0f, 1.38f, 0f),
            size = 0.22f,
            color = SCENE_TEXT,
            name = "repair-wrench-label"
        )
        ""
    }
}

@Composable
private fun PackageBadge(
    pkg: FifthWallPackage,
    hover: Float
) {
    val badgeColor = when (pkg.destination) {
        "house" -> argb("#7ad3ff")
        "office" -> argb("#9af26c")
        "factory" -> argb("#ffb15d")
        else -> argb("#c6a9ff")
    }
    val anchor = when (pkg.shape) {
        "rect" -> listOf(0.74f, 0.46f + hover, 0.56f)
        "cylinder" -> listOf(0.42f, 0.68f + hover, 0.46f)
        "sphere" -> listOf(0.48f, 0.88f + hover, 0.34f)
        else -> listOf(0.58f, 0.72f + hover, 0.58f)
    }

    SigilBox(
        width = 0.3f,
        height = 0.22f,
        depth = 0.06f,
        position = anchor,
        color = badgeColor,
        metalness = 0.22f,
        roughness = 0.42f,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-badge"
    )

    when (pkg.pattern) {
        "striped" -> {
            for (offset in listOf(-0.05f, 0.05f)) {
                SigilBox(
                    width = 0.22f,
                    height = 0.035f,
                    depth = 0.07f,
                    position = listOf(anchor[0], anchor[1] + offset, anchor[2] + 0.015f),
                    color = PACKAGE_TAPE,
                    metalness = 0.08f,
                    roughness = 0.88f,
                    castShadow = false,
                    receiveShadow = false,
                    name = "pkg-badge-stripe-$offset"
                )
            }
        }

        "dotted" -> {
            listOf(
                listOf(anchor[0] - 0.055f, anchor[1] + 0.04f, anchor[2] + 0.02f),
                listOf(anchor[0] + 0.055f, anchor[1] + 0.04f, anchor[2] + 0.02f),
                listOf(anchor[0] - 0.055f, anchor[1] - 0.04f, anchor[2] + 0.02f),
                listOf(anchor[0] + 0.055f, anchor[1] - 0.04f, anchor[2] + 0.02f)
            ).forEachIndexed { index, point ->
                SigilSphere(
                    radius = 0.024f,
                    widthSegments = 10,
                    heightSegments = 8,
                    position = point,
                    color = PACKAGE_TAPE,
                    metalness = 0.08f,
                    roughness = 0.86f,
                    castShadow = false,
                    receiveShadow = false,
                    name = "pkg-badge-dot-$index"
                )
            }
        }
    }
}

@Composable
private fun GeometryAura(
    pkg: FifthWallPackage,
    level: FifthWallLevel?,
    elevated: Boolean
) {
    if (pkg.geometry == null && level?.id !in 11..12) return

    if (pkg.validGeometry) {
        SigilMesh(
            geometryType = GeometryType.TORUS,
            geometryParams = GeometryParams(radius = if (elevated) 0.64f else 0.54f, tube = 0.05f, radialSegments = 16, tubularSegments = 42),
            position = listOf(0f, if (elevated) 1.52f else 1.34f, 0f),
            rotation = listOf(0.2f, 0.9f, 0f),
            color = SCENE_SUCCESS,
            metalness = 0.72f,
            roughness = 0.12f,
            castShadow = false,
            receiveShadow = false,
            name = "geometry-valid"
        )
    } else {
        SigilMesh(
            geometryType = GeometryType.TETRAHEDRON,
            geometryParams = GeometryParams(radius = if (elevated) 0.72f else 0.56f, detail = 1),
            position = listOf(0f, if (elevated) 1.58f else 1.36f, 0f),
            rotation = listOf(0.54f, 0.84f, 0.14f),
            color = SCENE_DANGER,
            metalness = 0.7f,
            roughness = 0.2f,
            castShadow = false,
            receiveShadow = false,
            name = "geometry-invalid"
        )
        SigilMesh(
            geometryType = GeometryType.TORUS,
            geometryParams = GeometryParams(radius = if (elevated) 0.78f else 0.6f, tube = 0.04f, radialSegments = 16, tubularSegments = 42),
            position = listOf(0f, if (elevated) 1.52f else 1.3f, 0f),
            rotation = listOf(0.4f, 0.38f, 0.72f),
            color = SCENE_WARM,
            metalness = 0.78f,
            roughness = 0.12f,
            castShadow = false,
            receiveShadow = false,
            name = "geometry-warning"
        )
    }
}

private fun fifthWallModelUrl(fileName: String): String {
    return "/static/models/fifth-wall/$fileName?v=$MODEL_ASSET_VERSION"
}

private fun packageFocusPadId(packageId: String): String = "package-focus-pad-$packageId"

private fun consoleFocusControlId(packageId: String): String = "dispatch-console-focus-$packageId"

private fun consoleFocusInteractionId(packageId: String): String = "console-focus:$packageId"

private fun routePadTruckId(index: Int): String = "route-pad-truck-$index"

private fun consoleRouteTruckControlId(index: Int): String = "dispatch-console-route-truck-$index"

private fun consoleTruckInteractionId(index: Int): String = "console-route-truck:$index"

private fun routePadInteraction(
    interactionId: String,
    width: Float,
    depth: Float,
    actions: List<String>,
    height: Float = 0.78f,
    centerY: Float = 0.16f
): InteractionMetadata =
    InteractionMetadata(
        interactionId = interactionId,
        cursor = CursorHint.POINTER,
        hitVolume = boxHitVolume(width = width, height = height, depth = depth, centerY = centerY),
        actions = actions,
        events = listOf("pointerdown", "click"),
        enabled = true,
        drag = null,
        dropTarget = null
    )

private fun packageInteraction(pkg: FifthWallPackage, enlarged: Boolean): InteractionMetadata =
    InteractionMetadata(
        interactionId = "package:${pkg.id}",
        cursor = CursorHint.POINTER,
        hitVolume = boxHitVolume(
            width = when (pkg.shape) {
                "rect" -> 2.28f
                "cylinder" -> 1.58f
                else -> 1.74f
            } * if (enlarged) 1.25f else 1f,
            height = if (enlarged) 2.45f else 1.85f,
            depth = when (pkg.shape) {
                "rect" -> 1.28f
                "sphere" -> 1.55f
                else -> 1.62f
            } * if (enlarged) 1.2f else 1f,
            centerY = if (enlarged) 1.1f else 0.82f
        ),
        actions = listOf("package", "focus", "inspect"),
        events = PACKAGE_POINTER_EVENTS,
        enabled = true,
        drag = null,
        dropTarget = null
    )

private fun truckInteraction(index: Int, label: String): InteractionMetadata =
    routingTargetInteraction(
        interactionId = "truck:$index",
        targetId = "truck:$index",
        actions = listOf("route", "truck", "truck:$index", label.lowercase().replace(" ", "-")),
        cursor = CursorHint.POINTER,
        hitVolume = boxHitVolume(width = 4.4f, height = 3.35f, depth = 2.45f, centerY = 1.55f)
    )

private fun returnBinInteraction(): InteractionMetadata =
    routingTargetInteraction(
        interactionId = "return-bin",
        targetId = "return-bin",
        actions = listOf("route", "return", "return-bin"),
        cursor = CursorHint.POINTER,
        hitVolume = boxHitVolume(width = 2.8f, height = 2.7f, depth = 2.8f, centerY = 1.25f)
    )

private fun inspectionDockInteraction(): InteractionMetadata =
    routingTargetInteraction(
        interactionId = "inspection-dock",
        targetId = "inspection-dock",
        actions = listOf("inspect", "inspection-dock"),
        cursor = CursorHint.CROSSHAIR,
        hitVolume = boxHitVolume(width = 4.8f, height = 3.2f, depth = 4.6f, centerY = 1.45f)
    )

private fun wrenchInteraction(): InteractionMetadata =
    InteractionMetadata(
        interactionId = "repair-wrench",
        cursor = CursorHint.POINTER,
        hitVolume = sphereHitVolume(radius = 1.05f, centerY = 0.5f),
        actions = listOf("repair", "glitch", "wrench"),
        events = listOf("pointerdown", "click"),
        enabled = true,
        drag = null,
        dropTarget = null
    )

private fun routingTargetInteraction(
    interactionId: String,
    targetId: String,
    actions: List<String>,
    cursor: CursorHint,
    hitVolume: HitVolumeData
): InteractionMetadata =
    InteractionMetadata(
        interactionId = interactionId,
        cursor = cursor,
        hitVolume = hitVolume,
        actions = actions + listOf("drop-target"),
        events = listOf("pointerdown", "click", "dragenter", "dragleave", "drop"),
        enabled = true,
        drag = null,
        dropTarget = DropTargetMetadata(
            enabled = true,
            targetId = targetId,
            groups = ROUTING_TARGET_GROUP,
            accepts = listOf("package")
        )
    )

private fun packageAnimations(
    pkg: FifthWallPackage,
    selected: Boolean
): List<SceneAnimationData> {
    val interactionAnimation = SceneAnimationData(
        id = "package-${pkg.id}-grab-feedback",
        trigger = AnimationTrigger.INTERACTION,
        kind = AnimationKind.BOUNCE,
        durationMs = 220,
        delayMs = 0,
        easing = AnimationEasing.EASE_OUT,
        vector = null,
        color = null,
        intensity = if (selected) 0.7f else 0.5f,
        repeat = 0
    )
    return listOf(interactionAnimation)
}

private fun targetPulseAnimation(id: String, delayMs: Int): SceneAnimationData =
    SceneAnimationData(
        id = id,
        trigger = AnimationTrigger.INTERACTION,
        kind = AnimationKind.PULSE,
        durationMs = 260,
        delayMs = delayMs,
        easing = AnimationEasing.EASE_OUT,
        vector = null,
        color = null,
        intensity = 0.5f,
        repeat = 0
    )

private fun boxHitVolume(
    width: Float,
    height: Float,
    depth: Float,
    centerY: Float
): HitVolumeData =
    HitVolumeData(
        shape = HitVolumeShape.BOX,
        center = listOf(0f, centerY, 0f),
        size = listOf(width, height, depth),
        radius = null
    )

private fun sphereHitVolume(radius: Float, centerY: Float): HitVolumeData =
    HitVolumeData(
        shape = HitVolumeShape.SPHERE,
        center = listOf(0f, centerY, 0f),
        size = emptyList(),
        radius = radius
    )

private fun truckSpacing(count: Int): Float = when (count) {
    1 -> 0f
    2 -> 5.2f
    3 -> 4.2f
    else -> 3.6f
}

private fun truckOrigin(
    count: Int,
    spacing: Float = truckSpacing(count)
): Float = -((count - 1) * spacing) / 2f

private fun truckColor(index: Int, rule: FifthWallRule): Int = when (rule.kind) {
    FifthWallRuleKind.Color -> when (rule.value) {
        "red" -> argb("#ff6b6b")
        "blue" -> argb("#5aa9ff")
        "green" -> argb("#45e0a8")
        "yellow" -> argb("#f7b955")
        "gray" -> argb("#9fb0c8")
        "purple" -> argb("#b690ff")
        else -> TRUCK_COLOR_FALLBACKS[index % TRUCK_COLOR_FALLBACKS.size]
    }
    FifthWallRuleKind.Geometry -> SCENE_ACCENT
    FifthWallRuleKind.Probability -> SCENE_WARM
    FifthWallRuleKind.Pattern -> argb("#72d2ff")
    FifthWallRuleKind.Destination -> when (rule.value) {
        "factory" -> argb("#ff9b4f")
        "office" -> argb("#7fe36b")
        "house" -> argb("#7ad3ff")
        else -> argb("#b695ff")
    }
    else -> listOf(SCENE_ACCENT, SCENE_WARM, SCENE_SUCCESS, argb("#b38cff"))[index % 4]
}

private fun argb(hex: String): Int = (0xFF shl 24) or Color(hex).getHex()
