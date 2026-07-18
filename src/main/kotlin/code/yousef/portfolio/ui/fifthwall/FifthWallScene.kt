package code.yousef.portfolio.ui.fifthwall

import codes.yousef.sigil.schema.AdaptiveResolutionData
import codes.yousef.sigil.schema.AnimationEasing
import codes.yousef.sigil.schema.AudioPatch
import codes.yousef.sigil.schema.AudioPatchAction
import codes.yousef.sigil.schema.CameraPatch
import codes.yousef.sigil.schema.CursorHint
import codes.yousef.sigil.schema.GeometryParams
import codes.yousef.sigil.schema.GeometryType
import codes.yousef.sigil.schema.HighlightPatch
import codes.yousef.sigil.schema.HitVolumeData
import codes.yousef.sigil.schema.HitVolumeShape
import codes.yousef.sigil.schema.InteractionMetadata
import codes.yousef.sigil.schema.ProceduralAudioData
import codes.yousef.sigil.schema.ProceduralWaveform
import codes.yousef.sigil.schema.RendererPreference
import codes.yousef.sigil.schema.SceneNodePatch
import codes.yousef.sigil.schema.ScenePatch
import codes.yousef.sigil.schema.ScreenAnchor
import codes.yousef.sigil.schema.ScreenLayoutData
import codes.yousef.sigil.schema.StorageBackend
import codes.yousef.sigil.schema.StoragePatch
import codes.yousef.sigil.schema.StoragePatchAction
import codes.yousef.sigil.schema.TextAlignMode
import codes.yousef.sigil.schema.TextBaselineMode
import codes.yousef.sigil.schema.TextFacingMode
import codes.yousef.sigil.summon.canvas.MateriaCanvas
import codes.yousef.sigil.summon.canvas.SceneConfig
import codes.yousef.sigil.summon.canvas.SigilSceneEventCallbackResponse
import codes.yousef.sigil.summon.canvas.SigilSceneEventHandler
import codes.yousef.sigil.summon.canvas.SigilSceneEventMatch
import codes.yousef.sigil.summon.components.SigilAmbientLight
import codes.yousef.sigil.summon.components.SigilAudio
import codes.yousef.sigil.summon.components.SigilBox
import codes.yousef.sigil.summon.components.SigilCamera
import codes.yousef.sigil.summon.components.SigilDirectionalLight
import codes.yousef.sigil.summon.components.SigilFrameStatsText
import codes.yousef.sigil.summon.components.SigilGroup
import codes.yousef.sigil.summon.components.SigilMesh
import codes.yousef.sigil.summon.components.SigilModel
import codes.yousef.sigil.summon.components.SigilOrbitControls
import codes.yousef.sigil.summon.components.SigilPlane
import codes.yousef.sigil.summon.components.SigilScreenLayer
import codes.yousef.sigil.summon.components.SigilSoundBus
import codes.yousef.sigil.summon.components.SigilSphere
import codes.yousef.sigil.summon.components.SigilText
import codes.yousef.summon.annotation.Composable
import io.materia.core.math.Color
import kotlin.math.PI

private const val PACKAGE_SLOT_COUNT = 3
private const val TRUCK_SLOT_COUNT = 4
private const val MODEL_ASSET_VERSION = "20260711-sigil-0430"
private const val SOUND_STORAGE_KEY = "fifth-wall-sound-volume-v1"
private const val FIFTH_WALL_FONT = "/static/fifth-wall-control-font.json"

private const val INTERACTION_RETURN = "route-return-bin"
private const val INTERACTION_OVERVIEW = "camera-overview"
private const val INTERACTION_INSPECT = "camera-inspect"
private const val INTERACTION_RULES = "camera-rules"
private const val INTERACTION_RESET = "reset-shift"
private const val INTERACTION_SOUND = "cycle-sound"
private const val INTERACTION_REPAIR = "repair-console"

private const val AUDIO_BUS = "fifth-wall-master"
private const val AUDIO_AMBIENCE = "fifth-wall-ambience"
private const val AUDIO_FOCUS = "fifth-wall-focus"
private const val AUDIO_ACCEPT = "fifth-wall-accept"
private const val AUDIO_REJECT = "fifth-wall-reject"
private const val AUDIO_CLEAR = "fifth-wall-clear"

private val CHARCOAL = argb("#0b1118")
private val FLOOR = argb("#142734")
private val PANEL = argb("#101820")
private val PANEL_STRONG = argb("#16232d")
private val SAFETY_WHITE = argb("#f4f7f9")
private val MUTED = argb("#9eb0be")
private val CYAN = argb("#63d5ff")
private val CORAL = argb("#ff6b6b")
private val AMBER = argb("#f7b955")
private val TEAL = argb("#45d7ad")
private val BORDER = argb("#304552")
private val SHADOW = argb("#071016")

private val packageModelUrls = listOf(
    fifthWallModelUrl("cube-crate.glb"),
    fifthWallModelUrl("rectangular-parcel.glb"),
    fifthWallModelUrl("cylinder-drum.glb"),
    fifthWallModelUrl("sphere-package-with-cradle.glb")
)

private val packageColors = listOf(
    "red" to CORAL,
    "blue" to argb("#5aa9ff"),
    "green" to TEAL,
    "yellow" to AMBER,
    "gray" to argb("#9fb0c8"),
    "purple" to argb("#b690ff")
)

private enum class SceneCue {
    NONE,
    FOCUS,
    ROUTE,
    CLEAR,
    REPAIR
}

private data class PromptChoice(val label: String)

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
        height = "calc(100vh - 80px)",
        backgroundColor = CHARCOAL,
        sceneEventHandlers = fifthWallSceneEventHandlers(controller)
    ) {
        SceneConfig(
            backgroundColor = CHARCOAL,
            fogEnabled = false,
            shadowsEnabled = false,
            rendererPreference = RendererPreference.WEBGL,
            adaptiveResolution = AdaptiveResolutionData(
                targetFps = 60f,
                minimumDpr = 0.75f,
                maximumDpr = 1f,
                scaleStep = 0.1f,
                sampleWindow = 20
            )
        )
        SigilCamera(
            position = OVERVIEW_CAMERA_POSITION,
            lookAt = OVERVIEW_CAMERA_TARGET,
            fov = 58f,
            near = 0.1f,
            far = 90f,
            name = "dispatch-camera",
            id = "dispatch-camera"
        )
        SigilOrbitControls(
            target = OVERVIEW_CAMERA_TARGET,
            enableDamping = true,
            dampingFactor = 0.12f,
            dampingTime = 0.075f,
            settleEpsilon = 0.00035f,
            maxDeltaTime = 0.033f,
            minDistance = 9.4f,
            maxDistance = 13.6f,
            minPolarAngle = 0.84f,
            maxPolarAngle = 1.3f,
            minAzimuthAngle = -0.48f,
            maxAzimuthAngle = 0.48f,
            rotateSpeed = 0.38f,
            zoomSpeed = 0.5f,
            enablePan = false,
            enableKeys = false,
            name = "dispatch-orbit",
            id = "dispatch-orbit"
        )

        FifthWallAudio(state.soundMode)
        WarehouseLighting()
        WarehouseShell()
        ConveyorDeck()
        state.visiblePackages().let { packages ->
            repeat(PACKAGE_SLOT_COUNT) { index ->
                PackageSlot(
                    index = index,
                    pkg = packages.getOrNull(index),
                    selected = packages.getOrNull(index)?.id == focusedPackage?.id,
                    interactionEnabled = state.prompt == FifthWallPrompt.None && !state.glitchActive
                )
            }
        }
        repeat(TRUCK_SLOT_COUNT) { index ->
            TruckSlot(
                index = index,
                visible = index < state.activeTrucks(level).size,
                selected = state.lastRouteTarget == "Truck ${'A' + index}",
                interactionEnabled = state.prompt == FifthWallPrompt.None && !state.glitchActive
            )
        }
        ReturnBin(
            selected = state.lastRouteTarget == "Return Bin",
            interactionEnabled = state.prompt == FifthWallPrompt.None && !state.glitchActive
        )
        InspectionStation()
        RepairConsole(
            visible = state.wrenchVisible,
            interactionEnabled = state.wrenchVisible
        )

        DesktopStatusHud(level, state)
        DesktopInfoHud(level, state, focusedPackage)
        DesktopControlsHud(level, state)
        MobileStatusHud(level, state)
        MobileInfoHud(level, state, focusedPackage)
        MobileControlsHud(level, state)
        DesktopPromptHud(level, state)
        MobilePromptHud(level, state)
        ""
    }
}

@Composable
private fun FifthWallAudio(soundMode: FifthWallSoundMode) {
    SigilSoundBus(
        bus = AUDIO_BUS,
        volume = soundMode.volume,
        storageKey = SOUND_STORAGE_KEY,
        id = "fifth-wall-sound-bus"
    )
    SigilAudio(
        id = AUDIO_AMBIENCE,
        procedural = ProceduralAudioData(
            waveform = ProceduralWaveform.SINE,
            startFrequencyHz = 54f,
            endFrequencyHz = 48f,
            durationSeconds = 4f,
            attackSeconds = 0.8f,
            releaseSeconds = 0.8f,
            oscillatorGain = 0.08f,
            noiseGain = 0.2f,
            lowPassFrequencyHz = 520f
        ),
        bus = AUDIO_BUS,
        volume = 0.42f,
        loop = true
    )
    SigilAudio(
        id = AUDIO_FOCUS,
        procedural = ProceduralAudioData(
            startFrequencyHz = 260f,
            endFrequencyHz = 390f,
            durationSeconds = 0.11f,
            oscillatorGain = 0.35f
        ),
        bus = AUDIO_BUS,
        volume = 0.46f
    )
    SigilAudio(
        id = AUDIO_ACCEPT,
        procedural = ProceduralAudioData(
            waveform = ProceduralWaveform.TRIANGLE,
            startFrequencyHz = 330f,
            endFrequencyHz = 720f,
            durationSeconds = 0.24f,
            oscillatorGain = 0.45f
        ),
        bus = AUDIO_BUS,
        volume = 0.58f
    )
    SigilAudio(
        id = AUDIO_REJECT,
        procedural = ProceduralAudioData(
            waveform = ProceduralWaveform.SQUARE,
            startFrequencyHz = 190f,
            endFrequencyHz = 92f,
            durationSeconds = 0.22f,
            oscillatorGain = 0.28f,
            noiseGain = 0.08f,
            lowPassFrequencyHz = 820f
        ),
        bus = AUDIO_BUS,
        volume = 0.5f
    )
    SigilAudio(
        id = AUDIO_CLEAR,
        procedural = ProceduralAudioData(
            waveform = ProceduralWaveform.TRIANGLE,
            startFrequencyHz = 360f,
            endFrequencyHz = 880f,
            durationSeconds = 0.48f,
            oscillatorGain = 0.5f
        ),
        bus = AUDIO_BUS,
        volume = 0.64f
    )
}

@Composable
private fun WarehouseLighting() {
    SigilAmbientLight(color = argb("#dbe6ee"), intensity = 0.62f, name = "warehouse-fill")
    SigilDirectionalLight(
        position = listOf(5f, 11f, 8f),
        target = listOf(0f, 0f, 1f),
        color = argb("#fff1cf"),
        intensity = 0.92f,
        castShadow = false,
        name = "warehouse-key"
    )
    SigilDirectionalLight(
        position = listOf(-8f, 6f, -5f),
        target = listOf(0f, 1f, 0f),
        color = CYAN,
        intensity = 0.32f,
        castShadow = false,
        name = "warehouse-rim"
    )
}

@Composable
private fun WarehouseShell() {
    SigilPlane(
        width = 28f,
        height = 22f,
        position = listOf(0f, 0f, 0f),
        rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
        color = FLOOR,
        receiveShadow = false,
        name = "warehouse-floor",
        id = "warehouse-floor"
    )
    SigilBox(
        width = 28f,
        height = 5.8f,
        depth = 0.26f,
        position = listOf(0f, 2.9f, -7.7f),
        color = argb("#22394a"),
        castShadow = false,
        receiveShadow = false,
        name = "warehouse-back-wall"
    )
    listOf(-8.4f, 0f, 8.4f).forEachIndexed { index, x ->
        SigilBox(
            width = 0.2f,
            height = 4.8f,
            depth = 0.32f,
            position = listOf(x, 2.5f, -7.5f),
            color = AMBER,
            castShadow = false,
            receiveShadow = false,
            name = "wall-marker-$index"
        )
    }
    repeat(8) { index ->
        SigilBox(
            width = 1.15f,
            height = 0.035f,
            depth = 0.22f,
            position = listOf(-7.2f + index * 2.05f, 0.025f, 6.7f),
            rotation = listOf(0f, if (index % 2 == 0) 0.45f else -0.45f, 0f),
            color = if (index % 2 == 0) AMBER else CHARCOAL,
            castShadow = false,
            receiveShadow = false,
            name = "hazard-stripe-$index"
        )
    }
}

@Composable
private fun ConveyorDeck() {
    SigilGroup(position = listOf(-2.6f, 0f, -0.5f), name = "conveyor", id = "conveyor") {
        SigilBox(
            width = 10.4f,
            height = 0.34f,
            depth = 2.8f,
            position = listOf(0f, 0.54f, 0f),
            color = argb("#27343b"),
            metalness = 0.18f,
            roughness = 0.82f,
            castShadow = false,
            receiveShadow = false,
            name = "conveyor-belt"
        )
        listOf(-1.32f, 1.32f).forEachIndexed { index, z ->
            SigilBox(
                width = 10.7f,
                height = 0.22f,
                depth = 0.16f,
                position = listOf(0f, 0.72f, z),
                color = TEAL,
                castShadow = false,
                receiveShadow = false,
                name = "conveyor-rail-$index"
            )
        }
        repeat(14) { index ->
            SigilBox(
                width = 0.08f,
                height = 0.04f,
                depth = 2.44f,
                position = listOf(-4.75f + index * 0.73f, 0.74f, 0f),
                color = argb("#8999a4"),
                castShadow = false,
                receiveShadow = false,
                name = "conveyor-slat-$index"
            )
        }
        ""
    }
}

@Composable
private fun PackageSlot(
    index: Int,
    pkg: FifthWallPackage?,
    selected: Boolean,
    interactionEnabled: Boolean
) {
    val position = packageSlotPosition(index)
    val interaction = worldInteraction(
        interactionId = focusInteraction(index),
        enabled = interactionEnabled && pkg != null,
        size = listOf(2.7f, 3.2f, 2.7f),
        center = listOf(0f, 1.35f, 0f)
    )
    SigilGroup(
        position = position,
        rotation = listOf(0f, packageYaw(pkg, index), 0f),
        visible = pkg != null,
        name = "package-slot-$index",
        interaction = interaction,
        id = "package-slot-$index"
    ) {
        SigilPlane(
            width = 2.25f,
            height = 1.8f,
            position = listOf(0f, 0.79f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = SHADOW,
            receiveShadow = false,
            name = "package-slot-$index-contact",
            id = "package-slot-$index-contact"
        )
        SigilModel(
            url = pkg?.let(::packageModelUrl) ?: packageModelUrls.first(),
            preloadUrls = packageModelUrls,
            position = packageModelPosition(pkg),
            rotation = packageModelRotation(pkg),
            scale = packageModelScale(pkg),
            castShadow = false,
            receiveShadow = false,
            name = "package-slot-$index-model",
            id = "package-slot-$index-model"
        )
        packageColors.forEach { (name, color) ->
            SigilGroup(
                visible = pkg?.color?.name == name,
                name = "package-slot-$index-color-$name",
                id = "package-slot-$index-color-$name"
            ) {
                SigilSphere(
                    radius = 0.14f,
                    widthSegments = 8,
                    heightSegments = 6,
                    position = listOf(-0.66f, 1.66f, 0.57f),
                    color = color,
                    castShadow = false,
                    receiveShadow = false,
                    name = "package-slot-$index-color-marker-$name"
                )
                SigilBox(
                    width = 0.52f,
                    height = 0.06f,
                    depth = 0.08f,
                    position = listOf(-0.4f, 1.66f, 0.57f),
                    color = color,
                    castShadow = false,
                    receiveShadow = false,
                    name = "package-slot-$index-color-stem-$name"
                )
                ""
            }
        }
        SigilMesh(
            geometryType = GeometryType.RING,
            geometryParams = GeometryParams(innerRadius = 1.16f, outerRadius = 1.35f, radialSegments = 28),
            position = listOf(0f, 0.82f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = CYAN,
            visible = selected,
            castShadow = false,
            receiveShadow = false,
            name = "package-slot-$index-selection",
            id = "package-slot-$index-selection"
        )
        SigilText(
            text = "P${index + 1}",
            position = listOf(0f, 2.42f, 0f),
            color = SAFETY_WHITE,
            size = 0.3f,
            depth = 0.01f,
            curveSegments = 6,
            align = TextAlignMode.CENTER,
            baseline = TextBaselineMode.MIDDLE,
            facingMode = TextFacingMode.BILLBOARD,
            fontUrl = FIFTH_WALL_FONT,
            name = "package-slot-$index-world-label",
            id = "package-slot-$index-world-label"
        )
        ""
    }
}

@Composable
private fun TruckSlot(
    index: Int,
    visible: Boolean,
    selected: Boolean,
    interactionEnabled: Boolean
) {
    val accent = listOf(CORAL, argb("#5aa9ff"), TEAL, AMBER)[index]
    SigilGroup(
        position = truckSlotPosition(index),
        visible = visible,
        name = "truck-slot-$index",
        interaction = worldInteraction(
            interactionId = truckInteraction(index),
            enabled = visible && interactionEnabled,
            size = listOf(3.6f, 3.7f, 3f),
            center = listOf(0f, 1.5f, 0f)
        ),
        id = "truck-slot-$index"
    ) {
        SigilPlane(
            width = 3.5f,
            height = 2.45f,
            position = listOf(0f, 0.03f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = SHADOW,
            receiveShadow = false,
            name = "truck-slot-$index-contact"
        )
        SigilModel(
            url = fifthWallModelUrl("delivery-truck.glb"),
            position = listOf(0f, 0.02f, 0f),
            scale = listOf(3.45f, 3.45f, 3.45f),
            castShadow = false,
            receiveShadow = false,
            name = "truck-slot-$index-model",
            id = "truck-slot-$index-model"
        )
        SigilBox(
            width = 1.15f,
            height = 0.08f,
            depth = 0.12f,
            position = listOf(-0.42f, 2.38f, 0.42f),
            color = accent,
            castShadow = false,
            receiveShadow = false,
            name = "truck-slot-$index-accent"
        )
        SigilMesh(
            geometryType = GeometryType.RING,
            geometryParams = GeometryParams(innerRadius = 1.48f, outerRadius = 1.7f, radialSegments = 28),
            position = listOf(0f, 0.06f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = accent,
            visible = selected,
            castShadow = false,
            receiveShadow = false,
            name = "truck-slot-$index-selection",
            id = "truck-slot-$index-selection"
        )
        SigilText(
            text = "TRUCK ${'A' + index}",
            position = listOf(0f, 2.86f, 0f),
            color = SAFETY_WHITE,
            size = 0.28f,
            depth = 0.01f,
            curveSegments = 6,
            align = TextAlignMode.CENTER,
            baseline = TextBaselineMode.MIDDLE,
            facingMode = TextFacingMode.BILLBOARD,
            fontUrl = FIFTH_WALL_FONT,
            name = "truck-slot-$index-world-label",
            id = "truck-slot-$index-world-label"
        )
        ""
    }
}

@Composable
private fun ReturnBin(selected: Boolean, interactionEnabled: Boolean) {
    SigilGroup(
        position = listOf(8.7f, 0f, 4.8f),
        name = "return-bin",
        interaction = worldInteraction(
            interactionId = INTERACTION_RETURN,
            enabled = interactionEnabled,
            size = listOf(2.8f, 3.2f, 2.8f),
            center = listOf(0f, 1.3f, 0f)
        ),
        id = "return-bin"
    ) {
        SigilBox(
            width = 2.35f,
            height = 1.6f,
            depth = 2.35f,
            position = listOf(0f, 0.82f, 0f),
            color = argb("#1a2b35"),
            metalness = 0.1f,
            roughness = 0.9f,
            castShadow = false,
            receiveShadow = false,
            name = "return-bin-body"
        )
        SigilBox(
            width = 1.62f,
            height = 0.08f,
            depth = 1.62f,
            position = listOf(0f, 1.66f, 0f),
            color = CHARCOAL,
            castShadow = false,
            receiveShadow = false,
            name = "return-bin-opening"
        )
        SigilMesh(
            geometryType = GeometryType.RING,
            geometryParams = GeometryParams(innerRadius = 1.02f, outerRadius = 1.24f, radialSegments = 28),
            position = listOf(0f, 0.05f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = AMBER,
            visible = selected,
            castShadow = false,
            receiveShadow = false,
            name = "return-bin-selection",
            id = "return-bin-selection"
        )
        SigilText(
            text = "RETURN BIN",
            position = listOf(0f, 2.28f, 0f),
            color = SAFETY_WHITE,
            size = 0.27f,
            depth = 0.01f,
            curveSegments = 6,
            align = TextAlignMode.CENTER,
            baseline = TextBaselineMode.MIDDLE,
            facingMode = TextFacingMode.BILLBOARD,
            fontUrl = FIFTH_WALL_FONT,
            name = "return-bin-world-label",
            id = "return-bin-world-label"
        )
        ""
    }
}

@Composable
private fun InspectionStation() {
    SigilGroup(position = listOf(7.9f, 0f, -4.7f), name = "inspection-station", id = "inspection-station") {
        SigilMesh(
            geometryType = GeometryType.CYLINDER,
            geometryParams = GeometryParams(radiusTop = 1.16f, radiusBottom = 1.32f, height = 0.28f, radialSegments = 14),
            position = listOf(0f, 0.16f, 0f),
            color = argb("#294456"),
            castShadow = false,
            receiveShadow = false,
            name = "inspection-base"
        )
        SigilBox(
            width = 0.24f,
            height = 2.2f,
            depth = 0.24f,
            position = listOf(0f, 1.35f, 0f),
            color = CYAN,
            castShadow = false,
            receiveShadow = false,
            name = "inspection-column"
        )
        SigilMesh(
            geometryType = GeometryType.TORUS,
            geometryParams = GeometryParams(radius = 1f, tube = 0.08f, radialSegments = 8, tubularSegments = 24),
            position = listOf(0f, 2.38f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = CYAN,
            castShadow = false,
            receiveShadow = false,
            name = "inspection-ring"
        )
        ""
    }
}

@Composable
private fun RepairConsole(visible: Boolean, interactionEnabled: Boolean) {
    SigilGroup(
        position = listOf(6.2f, 0.45f, -1.4f),
        rotation = listOf(0f, 0.18f, -0.22f),
        visible = visible,
        name = "repair-console",
        interaction = worldInteraction(
            interactionId = INTERACTION_REPAIR,
            enabled = interactionEnabled,
            size = listOf(1.8f, 2.5f, 1.8f),
            center = listOf(0f, 0.9f, 0f)
        ),
        id = "repair-console"
    ) {
        SigilBox(
            width = 0.34f,
            height = 2f,
            depth = 0.28f,
            position = listOf(0f, 0.8f, 0f),
            color = AMBER,
            metalness = 0.5f,
            roughness = 0.35f,
            castShadow = false,
            receiveShadow = false,
            name = "repair-handle"
        )
        SigilMesh(
            geometryType = GeometryType.TORUS,
            geometryParams = GeometryParams(radius = 0.52f, tube = 0.14f, radialSegments = 8, tubularSegments = 20),
            position = listOf(0f, 1.82f, 0f),
            color = AMBER,
            castShadow = false,
            receiveShadow = false,
            name = "repair-head"
        )
        SigilText(
            text = "REPAIR",
            position = listOf(0f, 2.65f, 0f),
            color = AMBER,
            size = 0.28f,
            depth = 0.01f,
            curveSegments = 6,
            align = TextAlignMode.CENTER,
            baseline = TextBaselineMode.MIDDLE,
            facingMode = TextFacingMode.BILLBOARD,
            fontUrl = FIFTH_WALL_FONT,
            name = "repair-world-label"
        )
        ""
    }
}

@Composable
private fun DesktopStatusHud(level: FifthWallLevel, state: FifthWallUiState) {
    SigilScreenLayer(
        id = "desktop-status-layer",
        desktop = ScreenLayoutData(ScreenAnchor.TOP_LEFT, 18f, 18f, visible = true),
        mobile = ScreenLayoutData(ScreenAnchor.TOP_LEFT, 0f, 0f, visible = false),
        order = 10
    ) {
        HudPanel(width = 940f, height = 84f, centerX = 470f, centerY = -42f, id = "desktop-status-panel")
        ScreenText("COURIER PROTOCOL", 18f, -17f, 16f, CYAN, "desktop-status-title")
        ScreenText(statusBayText(level), 18f, -48f, 13f, SAFETY_WHITE, "desktop-status-bay")
        ScreenText(level.name.uppercase(), 164f, -48f, 13f, MUTED, "desktop-status-level")
        ScreenText("SCORE ${state.score}", 414f, -48f, 13f, SAFETY_WHITE, "desktop-status-score")
        ScreenText("ACCURACY ${state.accuracyLabel()}", 552f, -48f, 13f, SAFETY_WHITE, "desktop-status-accuracy")
        ScreenText("STREAK ${state.streak}", 728f, -48f, 13f, SAFETY_WHITE, "desktop-status-streak")
        SigilFrameStatsText(
            position = listOf(918f, -48f, 3f),
            prefix = "FPS ",
            size = 12f,
            color = MUTED,
            align = TextAlignMode.RIGHT,
            baseline = TextBaselineMode.TOP,
            fontUrl = FIFTH_WALL_FONT,
            updateIntervalMs = 300,
            id = "desktop-status-fps"
        )
        ""
    }
}

@Composable
private fun DesktopInfoHud(level: FifthWallLevel, state: FifthWallUiState, pkg: FifthWallPackage?) {
    val title = infoTitle(state)
    val body = infoBody(level, state, pkg)
    SigilScreenLayer(
        id = "desktop-info-layer",
        desktop = ScreenLayoutData(ScreenAnchor.TOP_RIGHT, 18f, 116f, visible = true),
        mobile = ScreenLayoutData(ScreenAnchor.TOP_RIGHT, 0f, 0f, visible = false),
        order = 11
    ) {
        HudPanel(width = 392f, height = 456f, centerX = -196f, centerY = -228f, id = "desktop-info-panel")
        ScreenText(title, -370f, -28f, 17f, CYAN, "desktop-info-title")
        ScreenText(
            text = body,
            x = -370f,
            y = -72f,
            size = 14f,
            color = SAFETY_WHITE,
            id = "desktop-info-body",
            maxWidth = 344f,
            lineHeight = 1.38f,
            wordWrap = false
        )
        ScreenText(
            text = infoFooter(level, state),
            x = -370f,
            y = -404f,
            size = 12f,
            color = MUTED,
            id = "desktop-info-footer",
            maxWidth = 344f,
            lineHeight = 1.28f,
            wordWrap = false
        )
        ""
    }
}

@Composable
private fun DesktopControlsHud(level: FifthWallLevel, state: FifthWallUiState) {
    SigilScreenLayer(
        id = "desktop-controls-layer",
        desktop = ScreenLayoutData(ScreenAnchor.BOTTOM_LEFT, 18f, 18f, visible = true),
        mobile = ScreenLayoutData(ScreenAnchor.BOTTOM_LEFT, 0f, 0f, visible = false),
        order = 12
    ) {
        HudPanel(width = 1000f, height = 174f, centerX = 500f, centerY = 87f, id = "desktop-controls-panel")
        ScreenText(
            text = state.feedback,
            x = 18f,
            y = 150f,
            size = 14f,
            color = feedbackColor(state.feedbackTone),
            id = "desktop-controls-feedback",
            maxWidth = 956f,
            wordWrap = true
        )
        repeat(PACKAGE_SLOT_COUNT) { index ->
            HudButton(
                prefix = "desktop-controls",
                id = "focus-$index",
                label = "FOCUS PACKAGE ${index + 1}",
                interactionId = focusInteraction(index),
                centerX = 91f + index * 178f,
                centerY = 108f,
                width = 164f,
                height = 34f,
                enabled = state.visiblePackages().getOrNull(index) != null && state.prompt == FifthWallPrompt.None,
                selected = state.visiblePackages().getOrNull(index)?.id == state.focusPackage()?.id
            )
        }
        listOf(
            Triple("overview", "OVERVIEW", INTERACTION_OVERVIEW),
            Triple("inspect", "INSPECT", INTERACTION_INSPECT),
            Triple("rules", "RULES", INTERACTION_RULES)
        ).forEachIndexed { index, (id, label, interactionId) ->
            HudButton(
                prefix = "desktop-controls",
                id = id,
                label = label,
                interactionId = interactionId,
                centerX = 603f + index * 128f,
                centerY = 108f,
                width = 116f,
                height = 34f,
                enabled = state.prompt == FifthWallPrompt.None,
                selected = id == "rules" && state.hudView == FifthWallHudView.RULES
            )
        }
        repeat(TRUCK_SLOT_COUNT) { index ->
            HudButton(
                prefix = "desktop-controls",
                id = "truck-$index",
                label = "TRUCK ${'A' + index}",
                interactionId = truckInteraction(index),
                centerX = 67f + index * 126f,
                centerY = 63f,
                width = 116f,
                height = 34f,
                enabled = index < state.activeTrucks(level).size && state.prompt == FifthWallPrompt.None && !state.glitchActive,
                selected = state.lastRouteTarget == "Truck ${'A' + index}"
            )
        }
        HudButton("desktop-controls", "return", "RETURN BIN", INTERACTION_RETURN, 574f, 63f, 136f, 34f, state.prompt == FifthWallPrompt.None && !state.glitchActive, state.lastRouteTarget == "Return Bin")
        HudButton("desktop-controls", "reset", "RESET", INTERACTION_RESET, 704f, 63f, 100f, 34f, true, false, AMBER)
        HudButton("desktop-controls", "sound", soundLabel(state), INTERACTION_SOUND, 847f, 63f, 166f, 34f, true, state.soundMode != FifthWallSoundMode.MUTE, TEAL)
        ""
    }
}

@Composable
private fun MobileStatusHud(level: FifthWallLevel, state: FifthWallUiState) {
    SigilScreenLayer(
        id = "mobile-status-layer",
        desktop = ScreenLayoutData(ScreenAnchor.TOP_LEFT, 0f, 0f, visible = false),
        mobile = ScreenLayoutData(ScreenAnchor.TOP_LEFT, 8f, 8f, visible = true),
        mobileBreakpoint = 640,
        order = 20
    ) {
        HudPanel(width = 374f, height = 92f, centerX = 187f, centerY = -46f, id = "mobile-status-panel")
        ScreenText("COURIER PROTOCOL", 12f, -15f, 13f, CYAN, "mobile-status-title")
        ScreenText(statusBayText(level), 12f, -42f, 12f, SAFETY_WHITE, "mobile-status-bay")
        ScreenText("SCORE ${state.score}", 154f, -42f, 12f, SAFETY_WHITE, "mobile-status-score")
        ScreenText("ACC ${state.accuracyLabel()}", 252f, -42f, 12f, SAFETY_WHITE, "mobile-status-accuracy")
        ScreenText("STK ${state.streak}", 362f, -42f, 12f, SAFETY_WHITE, "mobile-status-streak", align = TextAlignMode.RIGHT)
        SigilFrameStatsText(
            position = listOf(362f, -70f, 3f),
            prefix = "FPS ",
            size = 11f,
            color = MUTED,
            align = TextAlignMode.RIGHT,
            baseline = TextBaselineMode.TOP,
            fontUrl = FIFTH_WALL_FONT,
            updateIntervalMs = 300,
            id = "mobile-status-fps"
        )
        ScreenText(level.name.uppercase(), 12f, -70f, 11f, MUTED, "mobile-status-level")
        ""
    }
}

@Composable
private fun MobileInfoHud(level: FifthWallLevel, state: FifthWallUiState, pkg: FifthWallPackage?) {
    SigilScreenLayer(
        id = "mobile-info-layer",
        desktop = ScreenLayoutData(ScreenAnchor.BOTTOM_CENTER, 0f, 0f, visible = false),
        mobile = ScreenLayoutData(ScreenAnchor.BOTTOM_CENTER, 0f, 154f, visible = true),
        mobileBreakpoint = 640,
        order = 21
    ) {
        HudPanel(width = 374f, height = 214f, centerX = 0f, centerY = 107f, id = "mobile-info-panel")
        ScreenText(infoTitle(state), -174f, 190f, 14f, CYAN, "mobile-info-title")
        ScreenText(
            text = infoBody(level, state, pkg),
            x = -174f,
            y = 160f,
            size = 11f,
            color = SAFETY_WHITE,
            id = "mobile-info-body",
            maxWidth = 348f,
            lineHeight = 1.27f,
            wordWrap = false
        )
        ScreenText(
            text = state.feedback,
            x = -174f,
            y = 27f,
            size = 11f,
            color = feedbackColor(state.feedbackTone),
            id = "mobile-info-footer",
            maxWidth = 348f,
            wordWrap = true
        )
        ""
    }
}

@Composable
private fun MobileControlsHud(level: FifthWallLevel, state: FifthWallUiState) {
    SigilScreenLayer(
        id = "mobile-controls-layer",
        desktop = ScreenLayoutData(ScreenAnchor.BOTTOM_CENTER, 0f, 0f, visible = false),
        mobile = ScreenLayoutData(ScreenAnchor.BOTTOM_CENTER, 0f, 8f, visible = true),
        mobileBreakpoint = 640,
        order = 22
    ) {
        HudPanel(width = 374f, height = 140f, centerX = 0f, centerY = 70f, id = "mobile-controls-panel")
        repeat(PACKAGE_SLOT_COUNT) { index ->
            HudButton(
                prefix = "mobile-controls",
                id = "focus-$index",
                label = "FOCUS PACKAGE ${index + 1}",
                interactionId = focusInteraction(index),
                centerX = -122f + index * 122f,
                centerY = 116f,
                width = 114f,
                height = 28f,
                enabled = state.visiblePackages().getOrNull(index) != null && state.prompt == FifthWallPrompt.None,
                selected = state.visiblePackages().getOrNull(index)?.id == state.focusPackage()?.id,
                textSize = 9.5f
            )
        }
        repeat(TRUCK_SLOT_COUNT) { index ->
            HudButton(
                prefix = "mobile-controls",
                id = "truck-$index",
                label = "TRUCK ${'A' + index}",
                interactionId = truckInteraction(index),
                centerX = -138f + index * 92f,
                centerY = 80f,
                width = 84f,
                height = 28f,
                enabled = index < state.activeTrucks(level).size && state.prompt == FifthWallPrompt.None && !state.glitchActive,
                selected = state.lastRouteTarget == "Truck ${'A' + index}",
                textSize = 10f
            )
        }
        val finalRow = listOf(
            Triple("return", "RETURN", INTERACTION_RETURN),
            Triple("overview", "VIEW", INTERACTION_OVERVIEW),
            Triple("inspect", "INSPECT", INTERACTION_INSPECT),
            Triple("rules", "RULES", INTERACTION_RULES),
            Triple("reset", "RESET", INTERACTION_RESET),
            Triple("sound", state.soundMode.name, INTERACTION_SOUND)
        )
        finalRow.forEachIndexed { index, (id, label, interactionId) ->
            HudButton(
                prefix = "mobile-controls",
                id = id,
                label = label,
                interactionId = interactionId,
                centerX = -153f + index * 61.2f,
                centerY = 43f,
                width = 56f,
                height = 28f,
                enabled = when (id) {
                    "return" -> state.prompt == FifthWallPrompt.None && !state.glitchActive
                    "overview", "inspect", "rules" -> state.prompt == FifthWallPrompt.None
                    else -> true
                },
                selected = when (id) {
                    "return" -> state.lastRouteTarget == "Return Bin"
                    "rules" -> state.hudView == FifthWallHudView.RULES
                    "sound" -> state.soundMode != FifthWallSoundMode.MUTE
                    else -> false
                },
                accent = when (id) {
                    "reset" -> AMBER
                    "sound" -> TEAL
                    else -> CYAN
                },
                textSize = 8.5f
            )
        }
        ""
    }
}

@Composable
private fun DesktopPromptHud(level: FifthWallLevel, state: FifthWallUiState) {
    val choices = promptChoices(level, state)
    SigilScreenLayer(
        id = "desktop-prompt-layer",
        visible = state.prompt != FifthWallPrompt.None,
        desktop = ScreenLayoutData(ScreenAnchor.CENTER, 0f, 0f, visible = true),
        mobile = ScreenLayoutData(ScreenAnchor.CENTER, 0f, 0f, visible = false),
        order = 50
    ) {
        PromptShield(760f, 430f, "desktop-prompt-shield")
        HudPanel(720f, 390f, 0f, 0f, "desktop-prompt-panel")
        ScreenText(promptTitle(state.prompt), -320f, 148f, 23f, CYAN, "desktop-prompt-title")
        ScreenText(promptBody(level, state), -320f, 100f, 16f, SAFETY_WHITE, "desktop-prompt-body", 640f, 1.35f, true)
        repeat(5) { index ->
            val choice = choices.getOrNull(index)
            HudButton(
                prefix = "desktop-prompt",
                id = "choice-$index",
                label = choice?.label ?: "--",
                interactionId = promptInteraction(index),
                centerX = -252f + (index % 3) * 252f,
                centerY = if (index < 3) -80f else -132f,
                width = 226f,
                height = 42f,
                enabled = choice != null,
                selected = false,
                visible = choice != null,
                textSize = 14f
            )
        }
        ""
    }
}

@Composable
private fun MobilePromptHud(level: FifthWallLevel, state: FifthWallUiState) {
    val choices = promptChoices(level, state)
    SigilScreenLayer(
        id = "mobile-prompt-layer",
        visible = state.prompt != FifthWallPrompt.None,
        desktop = ScreenLayoutData(ScreenAnchor.CENTER, 0f, 0f, visible = false),
        mobile = ScreenLayoutData(ScreenAnchor.CENTER, 0f, 0f, visible = true),
        mobileBreakpoint = 640,
        order = 51
    ) {
        PromptShield(384f, 650f, "mobile-prompt-shield")
        HudPanel(360f, 430f, 0f, 0f, "mobile-prompt-panel")
        ScreenText(promptTitle(state.prompt), -158f, 176f, 18f, CYAN, "mobile-prompt-title")
        ScreenText(promptBody(level, state), -158f, 132f, 13f, SAFETY_WHITE, "mobile-prompt-body", 316f, 1.32f, true)
        repeat(5) { index ->
            val choice = choices.getOrNull(index)
            HudButton(
                prefix = "mobile-prompt",
                id = "choice-$index",
                label = choice?.label ?: "--",
                interactionId = promptInteraction(index),
                centerX = 0f,
                centerY = 6f - index * 48f,
                width = 316f,
                height = 38f,
                enabled = choice != null,
                selected = false,
                visible = choice != null,
                textSize = 13f
            )
        }
        ""
    }
}

@Composable
private fun HudPanel(width: Float, height: Float, centerX: Float, centerY: Float, id: String) {
    SigilPlane(
        width = width,
        height = height,
        position = listOf(centerX, centerY, 0f),
        color = PANEL,
        receiveShadow = false,
        name = id,
        id = id
    )
    SigilPlane(
        width = width,
        height = 3f,
        position = listOf(centerX, centerY + height / 2f - 1.5f, 1f),
        color = CYAN,
        receiveShadow = false,
        name = "$id-accent"
    )
}

@Composable
private fun PromptShield(width: Float, height: Float, id: String) {
    SigilPlane(
        width = width,
        height = height,
        position = listOf(0f, 0f, -1f),
        color = argb("#080d12"),
        receiveShadow = false,
        interaction = screenInteraction("prompt-shield", true, width, height),
        name = id,
        id = id
    )
}

@Composable
private fun HudButton(
    prefix: String,
    id: String,
    label: String,
    interactionId: String,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    enabled: Boolean,
    selected: Boolean,
    accent: Int = CYAN,
    visible: Boolean = true,
    textSize: Float = 12f
) {
    SigilGroup(visible = visible, name = "$prefix-$id", id = "$prefix-$id") {
        SigilPlane(
            width = width,
            height = height,
            position = listOf(centerX, centerY, 2f),
            color = if (selected) PANEL_STRONG else argb("#18242c"),
            receiveShadow = false,
            interaction = screenInteraction(interactionId, enabled, width, height),
            name = "$prefix-$id-surface",
            id = "$prefix-$id-surface"
        )
        SigilPlane(
            width = width,
            height = if (selected) 3f else 1.5f,
            position = listOf(centerX, centerY - height / 2f + 1.5f, 3f),
            color = accent,
            receiveShadow = false,
            name = "$prefix-$id-accent"
        )
        ScreenText(
            text = label,
            x = centerX,
            y = centerY,
            size = textSize,
            color = if (enabled) SAFETY_WHITE else MUTED,
            id = "$prefix-$id-label",
            align = TextAlignMode.CENTER,
            baseline = TextBaselineMode.MIDDLE
        )
        ""
    }
}

@Composable
private fun ScreenText(
    text: String,
    x: Float,
    y: Float,
    size: Float,
    color: Int,
    id: String,
    maxWidth: Float? = null,
    lineHeight: Float = 1.18f,
    wordWrap: Boolean = false,
    align: TextAlignMode = TextAlignMode.LEFT,
    baseline: TextBaselineMode = TextBaselineMode.TOP
) {
    SigilText(
        text = text.uppercase().ifBlank { "-" },
        position = listOf(x, y, 4f),
        color = color,
        size = size,
        depth = 0f,
        curveSegments = 6,
        lineHeight = lineHeight,
        align = align,
        baseline = baseline,
        maxWidth = maxWidth,
        wordWrap = wordWrap,
        fontUrl = FIFTH_WALL_FONT,
        name = id,
        id = id
    )
}

private fun fifthWallSceneEventHandlers(controller: FifthWallController): List<SigilSceneEventHandler> {
    val handlers = mutableListOf<SigilSceneEventHandler>()
    repeat(PACKAGE_SLOT_COUNT) { index ->
        handlers += sceneHandler(
            interactionId = focusInteraction(index),
            requestKey = "focus-$index",
            optimisticPatch = focusOptimisticPatch(index),
            onEvent = { controller.selectVisiblePackage(index) },
            onResponse = { stateResponse(controller, cue = SceneCue.FOCUS) }
        )
    }
    repeat(TRUCK_SLOT_COUNT) { index ->
        handlers += sceneHandler(
            interactionId = truckInteraction(index),
            requestKey = "route-truck-$index",
            optimisticPatch = routeOptimisticPatch("truck-slot-$index-selection"),
            onEvent = { controller.routeToTruck(index) },
            onResponse = { stateResponse(controller, cue = SceneCue.ROUTE) }
        )
    }
    handlers += sceneHandler(
        interactionId = INTERACTION_RETURN,
        requestKey = "route-return",
        optimisticPatch = routeOptimisticPatch("return-bin-selection"),
        onEvent = controller::routeToReturn,
        onResponse = { stateResponse(controller, cue = SceneCue.ROUTE) }
    )
    handlers += sceneHandler(
        interactionId = INTERACTION_OVERVIEW,
        requestKey = "camera-overview",
        onEvent = controller::showManifest,
        onResponse = { stateResponse(controller, camera = overviewCameraPatch()) }
    )
    handlers += sceneHandler(
        interactionId = INTERACTION_INSPECT,
        requestKey = "camera-inspect",
        onEvent = controller::showManifest,
        onResponse = { stateResponse(controller, camera = inspectCameraPatch()) }
    )
    handlers += sceneHandler(
        interactionId = INTERACTION_RULES,
        requestKey = "camera-rules",
        onEvent = controller::showRules,
        onResponse = { stateResponse(controller, camera = rulesCameraPatch()) }
    )
    handlers += sceneHandler(
        interactionId = INTERACTION_RESET,
        requestKey = "reset-shift",
        onEvent = controller::reset,
        onResponse = {
            stateResponse(
                controller,
                camera = overviewCameraPatch(),
                clearCheckpoint = true,
                startAmbience = true
            )
        }
    )
    handlers += sceneHandler(
        interactionId = INTERACTION_SOUND,
        requestKey = "cycle-sound",
        onEvent = controller::cycleSoundMode,
        onResponse = { stateResponse(controller, updateSound = true) }
    )
    handlers += sceneHandler(
        interactionId = INTERACTION_REPAIR,
        requestKey = "repair-console",
        onEvent = controller::repairGlitch,
        onResponse = { stateResponse(controller, cue = SceneCue.REPAIR) }
    )
    repeat(5) { index ->
        handlers += sceneHandler(
            interactionId = promptInteraction(index),
            requestKey = "prompt-choice-$index",
            onEvent = { controller.activatePromptChoice(index) },
            onResponse = {
                val nextState = controller.state.value
                val nextLevel = FifthWallLevels[nextState.levelIndex]
                val freshShift = nextState.levelIndex == 0 &&
                    nextState.score == 0 &&
                    nextState.countedDecisions == 0 &&
                    nextState.prompt == FifthWallPrompt.None
                stateResponse(
                    controller = controller,
                    camera = if (nextState.prompt == FifthWallPrompt.None) overviewCameraPatch() else null,
                    clearCheckpoint = freshShift,
                    startAmbience = nextState.prompt == FifthWallPrompt.None && nextState.processedCount(nextLevel) == 0
                )
            }
        )
    }
    return handlers
}

private fun sceneHandler(
    interactionId: String,
    requestKey: String,
    optimisticPatch: ScenePatch? = null,
    onEvent: () -> Unit,
    onResponse: () -> SigilSceneEventCallbackResponse
): SigilSceneEventHandler =
    SigilSceneEventHandler(
        match = SigilSceneEventMatch(type = "click", interactionId = interactionId),
        onEvent = onEvent,
        onResponse = onResponse,
        optimisticPatch = optimisticPatch,
        requestKey = "fifth-wall:$requestKey",
        suppressWhilePending = true,
        reloadOnSuccess = false,
        preventDefault = true,
        stopPropagation = true
    )

private fun stateResponse(
    controller: FifthWallController,
    cue: SceneCue = SceneCue.NONE,
    camera: CameraPatch? = null,
    clearCheckpoint: Boolean = false,
    startAmbience: Boolean = false,
    updateSound: Boolean = false
): SigilSceneEventCallbackResponse {
    val state = controller.state.value
    val audio = mutableListOf<AudioPatch>()
    if (startAmbience) {
        audio += AudioPatch(AudioPatchAction.UNLOCK)
        audio += AudioPatch(AudioPatchAction.SET_VOLUME, bus = AUDIO_BUS, volume = state.soundMode.volume, persist = true)
        audio += AudioPatch(AudioPatchAction.PLAY, sourceId = AUDIO_AMBIENCE, loop = true)
    }
    if (updateSound) {
        audio += AudioPatch(AudioPatchAction.UNLOCK)
        audio += AudioPatch(AudioPatchAction.SET_VOLUME, bus = AUDIO_BUS, volume = state.soundMode.volume, persist = true)
    }
    when (cue) {
        SceneCue.NONE -> Unit
        SceneCue.FOCUS -> audio += AudioPatch(AudioPatchAction.PLAY, sourceId = AUDIO_FOCUS)
        SceneCue.ROUTE -> audio += AudioPatch(
            AudioPatchAction.PLAY,
            sourceId = when {
                state.prompt == FifthWallPrompt.LevelComplete || state.prompt == FifthWallPrompt.GameComplete -> AUDIO_CLEAR
                state.feedbackTone == FifthWallFeedbackTone.Negative -> AUDIO_REJECT
                else -> AUDIO_ACCEPT
            }
        )
        SceneCue.CLEAR -> audio += AudioPatch(AudioPatchAction.PLAY, sourceId = AUDIO_CLEAR)
        SceneCue.REPAIR -> audio += AudioPatch(AudioPatchAction.PLAY, sourceId = AUDIO_ACCEPT)
    }
    val storage = when {
        clearCheckpoint -> checkpointRemovalPatches()
        else -> checkpointSavePatches(state)
    }.toMutableList()
    if (updateSound) {
        storage += soundModeStoragePatches(state.soundMode)
    }
    return SigilSceneEventCallbackResponse(
        action = "patch",
        status = "ok",
        scenePatch = fifthWallScenePatch(state, camera, audio, storage)
    )
}

private fun fifthWallScenePatch(
    state: FifthWallUiState,
    camera: CameraPatch? = null,
    audio: List<AudioPatch> = emptyList(),
    storage: List<StoragePatch> = emptyList()
): ScenePatch {
    val level = FifthWallLevels[state.levelIndex]
    val packages = state.visiblePackages()
    val focusedId = state.focusPackage()?.id
    val interactionEnabled = state.prompt == FifthWallPrompt.None && !state.glitchActive
    val nodes = mutableListOf<SceneNodePatch>()

    repeat(PACKAGE_SLOT_COUNT) { index ->
        val pkg = packages.getOrNull(index)
        val selected = pkg?.id == focusedId
        nodes += SceneNodePatch(
            id = "package-slot-$index",
            rotation = listOf(0f, packageYaw(pkg, index), 0f),
            visible = pkg != null,
            interactionEnabled = interactionEnabled && pkg != null
        )
        nodes += SceneNodePatch(
            id = "package-slot-$index-model",
            position = packageModelPosition(pkg),
            rotation = packageModelRotation(pkg),
            scale = packageModelScale(pkg),
            modelUrl = pkg?.let(::packageModelUrl)
        )
        nodes += SceneNodePatch(
            id = "package-slot-$index-selection",
            visible = selected,
            highlight = HighlightPatch(selected, CYAN, if (selected) 0.9f else 0f)
        )
        packageColors.forEach { (name, _) ->
            nodes += SceneNodePatch(
                id = "package-slot-$index-color-$name",
                visible = pkg?.color?.name == name
            )
        }
    }

    repeat(TRUCK_SLOT_COUNT) { index ->
        val active = index < state.activeTrucks(level).size
        val selected = state.lastRouteTarget == "Truck ${'A' + index}"
        nodes += SceneNodePatch(
            id = "truck-slot-$index",
            visible = active,
            interactionEnabled = active && interactionEnabled
        )
        nodes += SceneNodePatch(
            id = "truck-slot-$index-selection",
            visible = selected,
            highlight = HighlightPatch(
                active = selected,
                color = routeFeedbackColor(state, selected, listOf(CORAL, argb("#5aa9ff"), TEAL, AMBER)[index]),
                intensity = if (selected) 0.94f else 0f
            )
        )
    }
    val returnSelected = state.lastRouteTarget == "Return Bin"
    nodes += SceneNodePatch(id = "return-bin", interactionEnabled = interactionEnabled)
    nodes += SceneNodePatch(
        id = "return-bin-selection",
        visible = returnSelected,
        highlight = HighlightPatch(
            active = returnSelected,
            color = routeFeedbackColor(state, returnSelected, AMBER),
            intensity = if (returnSelected) 0.94f else 0f
        )
    )
    nodes += SceneNodePatch(
        id = "repair-console",
        visible = state.wrenchVisible,
        interactionEnabled = state.wrenchVisible
    )
    nodes += hudPatchNodes(level, state)

    return ScenePatch(nodes = nodes, camera = camera, audio = audio, storage = storage)
}

private fun hudPatchNodes(level: FifthWallLevel, state: FifthWallUiState): List<SceneNodePatch> {
    val nodes = mutableListOf<SceneNodePatch>()
    val pkg = state.focusPackage()
    listOf("desktop", "mobile").forEach { prefix ->
        nodes += SceneNodePatch(id = "$prefix-status-bay", text = statusBayText(level))
        nodes += SceneNodePatch(id = "$prefix-status-level", text = level.name.uppercase())
        nodes += SceneNodePatch(id = "$prefix-status-score", text = "SCORE ${state.score}")
        nodes += SceneNodePatch(
            id = "$prefix-status-accuracy",
            text = if (prefix == "mobile") "ACC ${state.accuracyLabel()}" else "ACCURACY ${state.accuracyLabel()}"
        )
        nodes += SceneNodePatch(
            id = "$prefix-status-streak",
            text = if (prefix == "mobile") "STK ${state.streak}" else "STREAK ${state.streak}"
        )
        nodes += SceneNodePatch(id = "$prefix-info-title", text = infoTitle(state))
        nodes += SceneNodePatch(id = "$prefix-info-body", text = infoBody(level, state, pkg))
    }
    nodes += SceneNodePatch(id = "desktop-info-footer", text = infoFooter(level, state))
    nodes += SceneNodePatch(id = "mobile-info-footer", text = state.feedback)
    nodes += SceneNodePatch(id = "desktop-controls-feedback", text = state.feedback)

    listOf("desktop-controls", "mobile-controls").forEach { prefix ->
        repeat(PACKAGE_SLOT_COUNT) { index ->
            val packageAtIndex = state.visiblePackages().getOrNull(index)
            nodes += buttonPatch(
                prefix = prefix,
                id = "focus-$index",
                label = "FOCUS PACKAGE ${index + 1}",
                visible = true,
                enabled = packageAtIndex != null && state.prompt == FifthWallPrompt.None,
                selected = packageAtIndex?.id == state.focusPackage()?.id,
                color = CYAN
            )
        }
        repeat(TRUCK_SLOT_COUNT) { index ->
            val active = index < state.activeTrucks(level).size
            nodes += buttonPatch(
                prefix = prefix,
                id = "truck-$index",
                label = "TRUCK ${'A' + index}",
                visible = true,
                enabled = active && interactionEnabled(state),
                selected = state.lastRouteTarget == "Truck ${'A' + index}",
                color = listOf(CORAL, argb("#5aa9ff"), TEAL, AMBER)[index]
            )
        }
        nodes += buttonPatch(prefix, "return", if (prefix.startsWith("mobile")) "RETURN" else "RETURN BIN", true, interactionEnabled(state), state.lastRouteTarget == "Return Bin", AMBER)
        nodes += buttonPatch(prefix, "overview", if (prefix.startsWith("mobile")) "VIEW" else "OVERVIEW", true, state.prompt == FifthWallPrompt.None, false, CYAN)
        nodes += buttonPatch(prefix, "inspect", "INSPECT", true, state.prompt == FifthWallPrompt.None, false, CYAN)
        nodes += buttonPatch(prefix, "rules", "RULES", true, state.prompt == FifthWallPrompt.None, state.hudView == FifthWallHudView.RULES, CYAN)
        nodes += buttonPatch(prefix, "reset", "RESET", true, true, false, AMBER)
        nodes += buttonPatch(
            prefix,
            "sound",
            if (prefix.startsWith("mobile")) state.soundMode.name else soundLabel(state),
            true,
            true,
            state.soundMode != FifthWallSoundMode.MUTE,
            TEAL
        )
    }

    val promptVisible = state.prompt != FifthWallPrompt.None
    val choices = promptChoices(level, state)
    listOf("desktop", "mobile").forEach { prefix ->
        nodes += SceneNodePatch(id = "$prefix-prompt-layer", visible = promptVisible)
        nodes += SceneNodePatch(id = "$prefix-prompt-title", text = promptTitle(state.prompt))
        nodes += SceneNodePatch(id = "$prefix-prompt-body", text = promptBody(level, state))
        repeat(5) { index ->
            val choice = choices.getOrNull(index)
            nodes += buttonPatch(
                prefix = "$prefix-prompt",
                id = "choice-$index",
                label = choice?.label ?: "--",
                visible = choice != null,
                enabled = choice != null,
                selected = false,
                color = CYAN
            )
        }
    }
    return nodes
}

private fun buttonPatch(
    prefix: String,
    id: String,
    label: String,
    visible: Boolean,
    enabled: Boolean,
    selected: Boolean,
    color: Int
): List<SceneNodePatch> = listOf(
    SceneNodePatch(id = "$prefix-$id", visible = visible),
    SceneNodePatch(
        id = "$prefix-$id-surface",
        interactionEnabled = enabled,
        highlight = HighlightPatch(selected, color, if (selected) 0.72f else 0f)
    ),
    SceneNodePatch(id = "$prefix-$id-label", text = label)
)

private fun focusOptimisticPatch(index: Int): ScenePatch = ScenePatch(
    nodes = buildList {
        repeat(PACKAGE_SLOT_COUNT) { slot ->
            add(SceneNodePatch(id = "package-slot-$slot-selection", visible = slot == index))
            listOf("desktop-controls", "mobile-controls").forEach { prefix ->
                add(
                    SceneNodePatch(
                        id = "$prefix-focus-$slot-surface",
                        highlight = HighlightPatch(slot == index, CYAN, if (slot == index) 0.8f else 0f)
                    )
                )
            }
        }
    }
)

private fun routeOptimisticPatch(selectionNodeId: String): ScenePatch = ScenePatch(
    nodes = listOf(
        SceneNodePatch(
            id = selectionNodeId,
            visible = true,
            highlight = HighlightPatch(true, CYAN, 0.9f)
        )
    )
)

private fun checkpointSavePatches(state: FifthWallUiState): List<StoragePatch> {
    val checkpoint = state.completedBayCheckpoint(System.currentTimeMillis()) ?: return emptyList()
    val encoded = FifthWallCheckpointCodec.encode(checkpoint)
    return listOf(StorageBackend.LOCAL_STORAGE, StorageBackend.COOKIE).map { backend ->
        StoragePatch(
            action = StoragePatchAction.SET,
            key = FIFTH_WALL_CHECKPOINT_KEY,
            value = encoded,
            backend = backend,
            expiresDays = FIFTH_WALL_CHECKPOINT_EXPIRES_DAYS
        )
    }
}

private fun checkpointRemovalPatches(): List<StoragePatch> =
    listOf(StorageBackend.LOCAL_STORAGE, StorageBackend.COOKIE).map { backend ->
        StoragePatch(
            action = StoragePatchAction.REMOVE,
            key = FIFTH_WALL_CHECKPOINT_KEY,
            backend = backend,
            expiresDays = FIFTH_WALL_CHECKPOINT_EXPIRES_DAYS
        )
    }

private fun soundModeStoragePatches(soundMode: FifthWallSoundMode): List<StoragePatch> =
    listOf(StorageBackend.LOCAL_STORAGE, StorageBackend.COOKIE).map { backend ->
        StoragePatch(
            action = StoragePatchAction.SET,
            key = FIFTH_WALL_SOUND_MODE_KEY,
            value = soundMode.name,
            backend = backend,
            expiresDays = FIFTH_WALL_CHECKPOINT_EXPIRES_DAYS
        )
    }

private fun promptChoices(level: FifthWallLevel, state: FifthWallUiState): List<PromptChoice> =
    when (state.prompt) {
        FifthWallPrompt.None -> emptyList()
        FifthWallPrompt.Intro -> if (state.resumeBay != null) {
            listOf(PromptChoice("RESUME BAY ${state.resumeBay}"), PromptChoice("NEW SHIFT"))
        } else {
            listOf(PromptChoice("ENTER BAY"), PromptChoice("NEW SHIFT"))
        }
        FifthWallPrompt.ProbabilityPrediction -> {
            val expected = ((level.trucks.firstOrNull()?.probability ?: 0.5) * level.packageCount).toInt()
            listOf(0, expected, level.packageCount).distinct().map { PromptChoice("PREDICT $it") }
        }
        FifthWallPrompt.TeamDiscussion -> listOf(PromptChoice("CONTINUE SHIFT"))
        FifthWallPrompt.RuleGuess -> listOf(PromptChoice("SUBMIT RULE"))
        FifthWallPrompt.Confidence -> listOf("LOW", "MEDIUM", "HIGH").map(::PromptChoice)
        FifthWallPrompt.LevelComplete -> listOf(PromptChoice("ENTER BAY ${level.id + 1}"))
        FifthWallPrompt.GameComplete -> listOf(PromptChoice("NEW SHIFT"))
    }

private fun promptTitle(prompt: FifthWallPrompt): String = when (prompt) {
    FifthWallPrompt.None -> " "
    FifthWallPrompt.Intro -> "COURIER PROTOCOL 3D"
    FifthWallPrompt.ProbabilityPrediction -> "PREDICTION LOCK"
    FifthWallPrompt.TeamDiscussion -> "DISPATCH PAUSE"
    FifthWallPrompt.RuleGuess -> "HIDDEN RULE"
    FifthWallPrompt.Confidence -> "CONFIDENCE CHECK"
    FifthWallPrompt.LevelComplete -> "BAY CLEARED"
    FifthWallPrompt.GameComplete -> "SHIFT COMPLETE"
}

private fun promptBody(level: FifthWallLevel, state: FifthWallUiState): String = when (state.prompt) {
    FifthWallPrompt.None -> " "
    FifthWallPrompt.Intro -> when {
        state.campaignCompleted -> "All 20 bays are complete. Start a clean shift to run the dispatch campaign again."
        state.resumeBay != null -> "A validated checkpoint is ready. Resume at Bay ${state.resumeBay} with score ${state.score}, or begin a new shift."
        else -> "Route each package to a matching truck or the Return Bin. The canvas keeps the whole campaign live without page reloads."
    }
    FifthWallPrompt.ProbabilityPrediction -> "How many of ${level.packageCount} packages will Truck A accept? Choose a forecast before the bay starts."
    FifthWallPrompt.TeamDiscussion -> "Dispatch recorded the team discussion. Continue when you are ready to return to the lane."
    FifthWallPrompt.RuleGuess -> "Six experiments are logged. Submit the inferred rule to unlock the bay."
    FifthWallPrompt.Confidence -> "Record confidence in the route outcome before the next package enters the active slot."
    FifthWallPrompt.LevelComplete -> "Bay ${level.id} report: score ${state.score}, accuracy ${state.accuracyLabel()}, best streak ${state.bestStreak}, average decision ${state.averageDecisionSecondsLabel()}. Progress is saved."
    FifthWallPrompt.GameComplete -> "All 20 bays are clear. Final score ${state.score}, accuracy ${state.accuracyLabel()}, best streak ${state.bestStreak}. Campaign completion is saved."
}

private fun infoTitle(state: FifthWallUiState): String = when {
    state.glitchActive -> "MANUAL OVERRIDE"
    state.hudView == FifthWallHudView.RULES -> "RULE BOARD"
    else -> "PACKAGE MANIFEST"
}

private fun infoBody(level: FifthWallLevel, state: FifthWallUiState, pkg: FifthWallPackage?): String {
    if (state.glitchActive) {
        return "ROUTING OFFLINE\n\nFind the amber REPAIR control in the warehouse and activate it to restore dispatch."
    }
    if (state.hudView == FifthWallHudView.RULES) {
        return state.activeTrucks(level).mapIndexed { index, rule ->
            val revealed = level.hiddenRuleIndex != index || state.hiddenRuleRevealed || state.ruleShifted
            "${'A' + index}  ${rule.label(revealed).removePrefix("Rule: ").uppercase()}"
        }.plus("RET  NO TRUCK MATCHES").joinToString("\n\n")
    }
    return if (pkg == null) {
        "NO ACTIVE PACKAGE\n\nComplete the current prompt to load the next dispatch slot."
    } else {
        buildString {
            appendLine("COLOR       ${pkg.color.name.uppercase()}")
            appendLine("SHAPE       ${pkg.shapeDisplayLabel().uppercase()}")
            appendLine("WEIGHT      ${pkg.weight} KG")
            appendLine("VOLUME      ${pkg.volume} L")
            appendLine("PATTERN     ${pkg.pattern.uppercase()}")
            append("DEST        ${pkg.destination.uppercase()}")
            pkg.geometry?.let { append("\nGEOMETRY    ${it.uppercase()}") }
        }
    }
}

private fun infoFooter(level: FifthWallLevel, state: FifthWallUiState): String {
    val guidance = level.guidance()
    val progress = "PROCESSED ${state.processedCount(level)}/${level.packageCount}"
    val shift = when {
        state.ruleShifted -> "RULE BOARD UPDATED"
        state.priorityShifted -> "PRIORITY UPDATED"
        else -> guidance.phase.uppercase()
    }
    return "$progress  |  $shift\n${wrapCanvasText(guidance.objective, 48)}"
}

private fun wrapCanvasText(text: String, maxCharacters: Int): String {
    if (text.length <= maxCharacters) return text
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    text.split(' ').forEach { word ->
        if (current.isNotEmpty() && current.length + word.length + 1 > maxCharacters) {
            lines += current.toString()
            current = StringBuilder(word)
        } else {
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
    }
    if (current.isNotEmpty()) lines += current.toString()
    return lines.joinToString("\n")
}

private fun statusBayText(level: FifthWallLevel): String = "BAY ${level.id.toString().padStart(2, '0')} / 20"

private fun soundLabel(state: FifthWallUiState): String = "SOUND: ${state.soundMode.name}"

private fun feedbackColor(tone: FifthWallFeedbackTone): Int = when (tone) {
    FifthWallFeedbackTone.Neutral -> SAFETY_WHITE
    FifthWallFeedbackTone.Positive -> TEAL
    FifthWallFeedbackTone.Negative -> CORAL
}

private fun routeFeedbackColor(state: FifthWallUiState, selected: Boolean, fallback: Int): Int = when {
    !selected -> fallback
    state.lastRouteAccepted == true -> TEAL
    state.lastRouteAccepted == false -> CORAL
    else -> fallback
}

private fun interactionEnabled(state: FifthWallUiState): Boolean =
    state.prompt == FifthWallPrompt.None && !state.glitchActive

private fun worldInteraction(
    interactionId: String,
    enabled: Boolean,
    size: List<Float>,
    center: List<Float>
): InteractionMetadata = InteractionMetadata(
    interactionId = interactionId,
    cursor = CursorHint.POINTER,
    hitVolume = HitVolumeData(HitVolumeShape.BOX, center = center, size = size),
    actions = listOf("activate"),
    events = listOf("click"),
    enabled = enabled
)

private fun screenInteraction(
    interactionId: String,
    enabled: Boolean,
    width: Float,
    height: Float
): InteractionMetadata = InteractionMetadata(
    interactionId = interactionId,
    cursor = if (enabled) CursorHint.POINTER else CursorHint.AUTO,
    hitVolume = HitVolumeData(
        shape = HitVolumeShape.BOX,
        center = listOf(0f, 0f, 0f),
        size = listOf(width, height, 8f)
    ),
    actions = listOf("activate"),
    events = listOf("click"),
    enabled = enabled
)

private fun focusInteraction(index: Int): String = "focus-package-$index"

private fun truckInteraction(index: Int): String = "route-truck-$index"

private fun promptInteraction(index: Int): String = "prompt-choice-$index"

private fun packageSlotPosition(index: Int): List<Float> =
    listOf(-5.4f + index * 3.05f, 0f, -0.5f)

private fun truckSlotPosition(index: Int): List<Float> =
    listOf(-5.8f + index * 3.85f, 0f, 4.65f)

private fun packageYaw(pkg: FifthWallPackage?, index: Int): Float = when (pkg?.shape) {
    "rect" -> 0.14f + index * 0.04f
    "cylinder" -> 0.08f + index * 0.04f
    "sphere" -> -0.12f + index * 0.04f
    else -> -0.08f + index * 0.04f
}

private fun packageModelUrl(pkg: FifthWallPackage): String = fifthWallModelUrl(
    when (pkg.shape) {
        "rect" -> "rectangular-parcel.glb"
        "cylinder" -> "cylinder-drum.glb"
        "sphere" -> "sphere-package-with-cradle.glb"
        else -> "cube-crate.glb"
    }
)

private fun packageModelPosition(pkg: FifthWallPackage?): List<Float> = when (pkg?.shape) {
    "sphere" -> listOf(0f, 0.9f, 0f)
    else -> listOf(0f, 0.84f, 0f)
}

private fun packageModelScale(pkg: FifthWallPackage?): List<Float> = when (pkg?.shape) {
    "rect" -> listOf(2.08f, 1.22f, 1.08f)
    "cylinder" -> listOf(1.34f, 1.36f, 1.34f)
    "sphere" -> listOf(1.48f, 1.36f, 1.48f)
    else -> listOf(1.36f, 1.18f, 1.36f)
}

private fun packageModelRotation(pkg: FifthWallPackage?): List<Float> = when (pkg?.shape) {
    "cylinder" -> listOf(0f, 0.18f, 0f)
    "sphere" -> listOf(0f, -0.12f, 0f)
    else -> listOf(0f, 0f, 0f)
}

private fun fifthWallModelUrl(fileName: String): String =
    "/static/models/fifth-wall/optimized/$fileName?v=$MODEL_ASSET_VERSION"

private val OVERVIEW_CAMERA_POSITION = listOf(0.7f, 8.8f, 14.2f)
private val OVERVIEW_CAMERA_TARGET = listOf(0.5f, 1.2f, 0.9f)

private fun overviewCameraPatch(): CameraPatch = CameraPatch(
    position = OVERVIEW_CAMERA_POSITION,
    lookAt = OVERVIEW_CAMERA_TARGET,
    orbitTarget = OVERVIEW_CAMERA_TARGET,
    durationMs = 600,
    easing = AnimationEasing.EASE_IN_OUT,
    cancelMomentum = true
)

private fun inspectCameraPatch(): CameraPatch = CameraPatch(
    position = listOf(5.9f, 5.6f, 8.8f),
    lookAt = listOf(-2.2f, 1.25f, -0.4f),
    orbitTarget = listOf(-2.2f, 1.25f, -0.4f),
    durationMs = 600,
    easing = AnimationEasing.EASE_IN_OUT,
    cancelMomentum = true
)

private fun rulesCameraPatch(): CameraPatch = CameraPatch(
    position = listOf(8.8f, 5.9f, 8.9f),
    lookAt = listOf(0.3f, 1.15f, 4.4f),
    orbitTarget = listOf(0.3f, 1.15f, 4.4f),
    durationMs = 600,
    easing = AnimationEasing.EASE_IN_OUT,
    cancelMomentum = true
)

private fun argb(hex: String): Int = (0xFF shl 24) or Color(hex).getHex()
