package code.yousef.portfolio.ui.fifthwall

import codes.yousef.sigil.schema.GeometryParams
import codes.yousef.sigil.schema.GeometryType
import codes.yousef.sigil.summon.canvas.MateriaCanvas
import codes.yousef.sigil.summon.canvas.SceneConfig
import codes.yousef.sigil.summon.components.SigilAmbientLight
import codes.yousef.sigil.summon.components.SigilBox
import codes.yousef.sigil.summon.components.SigilCamera
import codes.yousef.sigil.summon.components.SigilDirectionalLight
import codes.yousef.sigil.summon.components.SigilGroup
import codes.yousef.sigil.summon.components.SigilMesh
import codes.yousef.sigil.summon.components.SigilOrbitControls
import codes.yousef.sigil.summon.components.SigilPlane
import codes.yousef.sigil.summon.components.SigilPointLight
import codes.yousef.sigil.summon.components.SigilSphere
import codes.yousef.summon.annotation.Composable
import io.materia.core.math.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val WAREHOUSE_FLOOR = argb("#08111a")
private val WAREHOUSE_WALL = argb("#101b27")
private val CONVEYOR_FRAME = argb("#1a2635")
private val CONVEYOR_STRIP = argb(FifthWallTheme.ACCENT_SOFT)
private val SCENE_ACCENT = argb(FifthWallTheme.ACCENT)
private val SCENE_WARM = argb(FifthWallTheme.ACCENT_WARM)
private val SCENE_SUCCESS = argb(FifthWallTheme.SUCCESS)
private val SCENE_DANGER = argb(FifthWallTheme.DANGER)
private val SCENE_TEXT = argb(FifthWallTheme.TEXT_PRIMARY)
private val PACKAGE_TAPE = argb("#eef4fb")
private val PACKAGE_TRIM = argb("#d7e3ef")
private val PACKAGE_SHADE = argb("#0b1219")
private val TRUCK_COLOR_FALLBACKS = listOf(
    argb("#ff6b6b"),
    argb("#5aa9ff"),
    argb("#45e0a8"),
    argb("#f7b955"),
    argb("#9fb0c8"),
    argb("#b690ff")
)

@Composable
internal fun FifthWallScene(
    level: FifthWallLevel,
    state: FifthWallUiState,
    focusedPackage: FifthWallPackage?
) {
    MateriaCanvas(
        id = "fifth-wall-scene",
        width = "100%",
        height = "100%",
        backgroundColor = argb(FifthWallTheme.BASE)
    ) {
        SceneConfig(
            backgroundColor = argb(FifthWallTheme.BASE),
            fogEnabled = true,
            fogColor = argb("#061018"),
            fogNear = 20f,
            fogFar = 52f,
            shadowsEnabled = true
        )
        SigilCamera(
            position = listOf(0f, 9.35f, 15.2f),
            lookAt = listOf(0f, 1.8f, 1.65f),
            fov = 40f,
            near = 0.1f,
            far = 100f,
            name = "dispatch-camera"
        )
        SigilOrbitControls(
            target = listOf(0f, 1.9f, 1.7f),
            minDistance = 12.5f,
            maxDistance = 20f,
            minPolarAngle = 0.72f,
            maxPolarAngle = 1.3f,
            enablePan = false,
            autoRotate = focusedPackage?.geometry != null || state.prompt == FifthWallPrompt.Intro,
            autoRotateSpeed = 0.55f,
            rotateSpeed = 0.7f,
            zoomSpeed = 0.9f,
            name = "dispatch-orbit"
        )

        SigilAmbientLight(color = SCENE_TEXT, intensity = 0.72f, name = "ambient-fill")
        SigilDirectionalLight(
            position = listOf(5f, 14f, 9f),
            target = listOf(0f, 0f, 1f),
            intensity = 1.7f,
            castShadow = true,
            name = "bay-sun"
        )
        SigilPointLight(
            position = listOf(-3f, 5.5f, -1f),
            color = SCENE_ACCENT,
            intensity = 2.6f,
            distance = 24f,
            decay = 1.6f,
            name = "belt-glow"
        )
        SigilPointLight(
            position = listOf(7f, 6.5f, -5.5f),
            color = if (focusedPackage?.validGeometry == false) SCENE_DANGER else SCENE_WARM,
            intensity = 2.2f,
            distance = 20f,
            decay = 1.7f,
            name = "inspection-glow"
        )
        SigilPointLight(
            position = listOf(0f, 5f, 8.4f),
            color = SCENE_WARM,
            intensity = 1.7f,
            distance = 18f,
            decay = 1.8f,
            name = "route-glow"
        )
        if (state.glitchActive) {
            SigilPointLight(
                position = listOf(0f, 8f, 5f),
                color = SCENE_DANGER,
                intensity = 3.4f,
                distance = 28f,
                decay = 1.2f,
                name = "glitch-alert"
            )
        }

        WarehouseShell()
        RoutingGuides(level = level, state = state)
        ConveyorDeck(level = level, state = state)
        TruckBay(level = level, state = state)
        ReturnBinBay(
            selected = state.lastRouteTarget == "Return Bin",
            routeAccepted = if (state.lastRouteTarget == "Return Bin") state.lastRouteAccepted else null,
            glitchActive = state.glitchActive
        )
        InspectionDock(pkg = focusedPackage)
        ""
    }
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
    SigilBox(
        width = 34f,
        height = 8f,
        depth = 0.6f,
        position = listOf(0f, 4f, -9.5f),
        color = WAREHOUSE_WALL,
        metalness = 0.08f,
        roughness = 0.94f,
        receiveShadow = true,
        castShadow = false,
        name = "back-wall"
    )
    SigilBox(
        width = 0.6f,
        height = 8f,
        depth = 26f,
        position = listOf(-16.5f, 4f, 0f),
        color = WAREHOUSE_WALL,
        metalness = 0.08f,
        roughness = 0.94f,
        receiveShadow = true,
        castShadow = false,
        name = "left-wall"
    )
    SigilBox(
        width = 0.6f,
        height = 8f,
        depth = 26f,
        position = listOf(16.5f, 4f, 0f),
        color = WAREHOUSE_WALL,
        metalness = 0.08f,
        roughness = 0.94f,
        receiveShadow = true,
        castShadow = false,
        name = "right-wall"
    )
    SigilBox(
        width = 34f,
        height = 0.28f,
        depth = 5f,
        position = listOf(0f, 7.8f, -3f),
        color = argb("#132131"),
        metalness = 0.15f,
        roughness = 0.72f,
        castShadow = false,
        receiveShadow = true,
        name = "light-rig"
    )
    SigilBox(
        width = 25f,
        height = 0.18f,
        depth = 0.14f,
        position = listOf(0f, 2.35f, -9.14f),
        color = argb("#17314a"),
        metalness = 0.62f,
        roughness = 0.16f,
        castShadow = false,
        receiveShadow = false,
        name = "back-wall-guide-band"
    )
    SigilBox(
        width = 13f,
        height = 0.14f,
        depth = 0.12f,
        position = listOf(7.8f, 3.2f, -9.16f),
        color = argb("#254761"),
        metalness = 0.64f,
        roughness = 0.18f,
        castShadow = false,
        receiveShadow = false,
        name = "inspection-wall-band"
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
            position = listOf(x, 0f, 6.55f),
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
            endZ = 5.35f,
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
        position = listOf(9.2f, 0f, 6.3f),
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
        endZ = 5.2f,
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
            height = 0.06f,
            depth = depth,
            position = listOf(0f, 0.03f, 0f),
            color = argb("#101827"),
            metalness = 0.22f,
            roughness = 0.9f,
            castShadow = false,
            receiveShadow = true,
            name = "$name-base"
        )
        SigilBox(
            width = width - 0.38f,
            height = 0.03f,
            depth = 0.12f,
            position = listOf(0f, 0.07f, (depth / 2f) - 0.2f),
            color = color,
            metalness = 0.72f,
            roughness = 0.16f,
            castShadow = false,
            receiveShadow = false,
            name = "$name-front-edge"
        )
        SigilBox(
            width = width - 0.38f,
            height = 0.03f,
            depth = 0.12f,
            position = listOf(0f, 0.07f, -(depth / 2f) + 0.2f),
            color = color,
            metalness = 0.72f,
            roughness = 0.16f,
            castShadow = false,
            receiveShadow = false,
            name = "$name-back-edge"
        )
        SigilBox(
            width = 0.12f,
            height = 0.03f,
            depth = depth - 0.38f,
            position = listOf((width / 2f) - 0.2f, 0.07f, 0f),
            color = color,
            metalness = 0.72f,
            roughness = 0.16f,
            castShadow = false,
            receiveShadow = false,
            name = "$name-right-edge"
        )
        SigilBox(
            width = 0.12f,
            height = 0.03f,
            depth = depth - 0.38f,
            position = listOf(-(width / 2f) + 0.2f, 0.07f, 0f),
            color = color,
            metalness = 0.72f,
            roughness = 0.16f,
            castShadow = false,
            receiveShadow = false,
            name = "$name-left-edge"
        )
        if (selected) {
            SigilPointLight(
                position = listOf(0f, 0.9f, 0f),
                color = color,
                intensity = 1.2f,
                distance = 6f,
                decay = 1.8f,
                name = "$name-light"
            )
        }
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
    val headBackX = endX - (cos(angle.toDouble()).toFloat() * 0.28f)
    val headBackZ = endZ - (sin(angle.toDouble()).toFloat() * 0.28f)

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
    SigilBox(
        width = 0.44f,
        height = 0.045f,
        depth = 0.09f,
        position = listOf(headBackX, 0.04f, headBackZ),
        rotation = listOf(0f, angle + 0.62f, 0f),
        color = color,
        metalness = 0.64f,
        roughness = 0.2f,
        castShadow = false,
        receiveShadow = false,
        name = "$name-head-a"
    )
    SigilBox(
        width = 0.44f,
        height = 0.045f,
        depth = 0.09f,
        position = listOf(headBackX, 0.04f, headBackZ),
        rotation = listOf(0f, angle - 0.62f, 0f),
        color = color,
        metalness = 0.64f,
        roughness = 0.2f,
        castShadow = false,
        receiveShadow = false,
        name = "$name-head-b"
    )
}

@Composable
private fun ConveyorDeck(
    level: FifthWallLevel,
    state: FifthWallUiState
) {
    SigilGroup(position = listOf(-2.2f, 0f, 0.5f), name = "conveyor-group") {
        SigilBox(
            width = 13.6f,
            height = 0.58f,
            depth = 3f,
            position = listOf(0f, 0.3f, 0f),
            color = CONVEYOR_FRAME,
            metalness = 0.38f,
            roughness = 0.72f,
            name = "conveyor-body"
        )
        SigilBox(
            width = 13.1f,
            height = 0.18f,
            depth = 2.32f,
            position = listOf(0f, 0.68f, 0f),
            color = if (state.glitchActive) argb("#3a1519") else argb(FifthWallTheme.BELT),
            metalness = 0.2f,
            roughness = 0.86f,
            name = "conveyor-belt"
        )
        SigilBox(
            width = 13f,
            height = 0.03f,
            depth = 0.12f,
            position = listOf(0f, 0.78f, -0.72f),
            color = CONVEYOR_STRIP,
            metalness = 0.55f,
            roughness = 0.22f,
            name = "belt-lane-left"
        )
        SigilBox(
            width = 13f,
            height = 0.03f,
            depth = 0.12f,
            position = listOf(0f, 0.78f, 0.72f),
            color = CONVEYOR_STRIP,
            metalness = 0.55f,
            roughness = 0.22f,
            name = "belt-lane-right"
        )
        SigilBox(
            width = 0.26f,
            height = 1.4f,
            depth = 0.26f,
            position = listOf(-6.3f, 0.7f, -1.05f),
            color = CONVEYOR_FRAME,
            metalness = 0.36f,
            roughness = 0.8f,
            name = "belt-leg-1"
        )
        SigilBox(
            width = 0.26f,
            height = 1.4f,
            depth = 0.26f,
            position = listOf(6.3f, 0.7f, 1.05f),
            color = CONVEYOR_FRAME,
            metalness = 0.36f,
            roughness = 0.8f,
            name = "belt-leg-2"
        )
        state.visiblePackages().forEachIndexed { index, pkg ->
            val selected = state.selectedPackageId == pkg.id || (state.selectedPackageId == null && index == 0)
            PackageMesh(
                pkg = pkg,
                position = listOf(-4.9f + (index * 3.2f), 1.36f, 0f),
                selected = selected,
                emphasized = index == 0,
                beltIndex = index,
                level = level
            )
        }
        ""
    }
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
            label = routeLabel,
            position = listOf(x, 0f, 6.6f),
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
        name = label.lowercase().replace(" ", "-")
    ) {
        SigilBox(
            width = 3.2f,
            height = 1.4f,
            depth = 1.5f,
            position = listOf(-0.35f, 1.1f, 0f),
            color = accent,
            metalness = 0.34f,
            roughness = 0.46f,
            name = "$label-trailer"
        )
        SigilBox(
            width = 1.15f,
            height = 1f,
            depth = 1.4f,
            position = listOf(1.68f, 0.9f, 0f),
            color = argb("#e6eef8"),
            metalness = 0.18f,
            roughness = 0.62f,
            name = "$label-cab"
        )
        SigilBox(
            width = 1.3f,
            height = 0.14f,
            depth = 0.92f,
            position = listOf(1.78f, 1.46f, 0f),
            color = accent,
            metalness = 0.62f,
            roughness = 0.18f,
            name = "$label-sign"
        )
        SigilBox(
            width = 3.4f,
            height = 0.24f,
            depth = 1.72f,
            position = listOf(0f, 0.22f, 0f),
            color = argb("#0c131c"),
            metalness = 0.24f,
            roughness = 0.92f,
            name = "$label-shadow"
        )
        wheel(position = listOf(-1.25f, 0.42f, -0.82f), name = "$label-wheel-a")
        wheel(position = listOf(-1.25f, 0.42f, 0.82f), name = "$label-wheel-b")
        wheel(position = listOf(0.15f, 0.42f, -0.82f), name = "$label-wheel-c")
        wheel(position = listOf(0.15f, 0.42f, 0.82f), name = "$label-wheel-d")
        wheel(position = listOf(1.82f, 0.42f, -0.82f), name = "$label-wheel-e")
        wheel(position = listOf(1.82f, 0.42f, 0.82f), name = "$label-wheel-f")
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
private fun ReturnBinBay(
    selected: Boolean,
    routeAccepted: Boolean?,
    glitchActive: Boolean
) {
    SigilGroup(
        position = listOf(9.2f, 0f, 6.3f),
        scale = listOf(1.08f, 1.08f, 1.08f),
        name = "return-bin"
    ) {
        SigilBox(
            width = 2.2f,
            height = 1.05f,
            depth = 2.2f,
            position = listOf(0f, 0.56f, 0f),
            color = if (glitchActive) argb("#30141a") else argb("#142233"),
            metalness = 0.32f,
            roughness = 0.52f,
            name = "return-base"
        )
        SigilBox(
            width = 1.75f,
            height = 0.42f,
            depth = 1.75f,
            position = listOf(0f, 0.98f, 0f),
            color = argb("#091018"),
            metalness = 0.08f,
            roughness = 0.96f,
            name = "return-mouth"
        )
        for (offset in listOf(-0.48f, -0.14f, 0.2f, 0.54f)) {
            SigilBox(
                width = 0.22f,
                height = 0.04f,
                depth = 1.84f,
                position = listOf(offset, 1.08f, 0f),
                rotation = listOf(0f, 0.32f, 0f),
                color = SCENE_WARM,
                metalness = 0.68f,
                roughness = 0.18f,
                castShadow = false,
                receiveShadow = false,
                name = "return-stripe-$offset"
            )
        }
        SigilMesh(
            geometryType = GeometryType.TORUS,
            geometryParams = GeometryParams(radius = 0.96f, tube = 0.08f, radialSegments = 16, tubularSegments = 48),
            position = listOf(0f, 1.08f, 0f),
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
        SigilPointLight(
            position = listOf(0f, 2.1f, 0f),
            color = when {
                !selected -> SCENE_WARM
                routeAccepted == true -> SCENE_SUCCESS
                routeAccepted == false -> SCENE_DANGER
                else -> SCENE_ACCENT
            },
            intensity = 1.3f,
            distance = 8f,
            decay = 1.7f,
            name = "return-light"
        )
        ""
    }
}

@Composable
private fun InspectionDock(pkg: FifthWallPackage?) {
    SigilGroup(position = listOf(8.2f, 0f, -5.5f), name = "inspection-dock") {
        SigilBox(
            width = 4.8f,
            height = 0.44f,
            depth = 4.3f,
            position = listOf(0f, 0.22f, 0f),
            color = argb("#141e2d"),
            metalness = 0.28f,
            roughness = 0.68f,
            name = "inspection-floor"
        )
        SigilBox(
            width = 1.95f,
            height = 0.28f,
            depth = 1.95f,
            position = listOf(0f, 0.56f, 0f),
            color = argb("#243549"),
            metalness = 0.42f,
            roughness = 0.38f,
            name = "inspection-plinth"
        )
        SigilMesh(
            geometryType = GeometryType.RING,
            geometryParams = GeometryParams(innerRadius = 0.95f, outerRadius = 1.22f, radialSegments = 48),
            position = listOf(0f, 0.74f, 0f),
            rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
            color = if (pkg?.validGeometry == false) SCENE_DANGER else SCENE_ACCENT,
            metalness = 0.74f,
            roughness = 0.14f,
            castShadow = false,
            receiveShadow = false,
            name = "inspection-ring"
        )
        if (pkg != null) {
            PackageMesh(
                pkg = pkg,
                position = listOf(0f, 1.65f, 0f),
                selected = true,
                emphasized = true,
                beltIndex = -1,
                level = null,
                enlarged = true
            )
        } else {
            SigilMesh(
                geometryType = GeometryType.DODECAHEDRON,
                geometryParams = GeometryParams(radius = 0.72f, detail = 0),
                position = listOf(0f, 1.45f, 0f),
                rotation = listOf(0.35f, 0.35f, 0f),
                color = SCENE_WARM,
                metalness = 0.7f,
                roughness = 0.12f,
                castShadow = true,
                receiveShadow = true,
                name = "inspection-idle"
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
    enlarged: Boolean = false
) {
    val baseColor = argb(pkg.color.hex)
    val scale = when {
        enlarged -> 1.38f
        emphasized -> 1.08f
        else -> 1f
    }
    val yaw = when (pkg.shape) {
        "rect" -> 0.18f
        "cylinder" -> 0.08f
        "sphere" -> 0.22f
        else -> -0.12f
    }
    val hover = if (enlarged) 0.18f else if (emphasized) 0.1f else 0f

    SigilGroup(
        position = position,
        rotation = listOf(0f, yaw + (beltIndex.coerceAtLeast(0) * 0.08f), 0f),
        scale = listOf(scale, scale, scale),
        name = "package-${pkg.id}"
    ) {
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
        when (pkg.shape) {
            "cube" -> CubeCrate(baseColor = baseColor, hover = hover)
            "rect" -> RectParcel(baseColor = baseColor, hover = hover)
            "sphere" -> SphereOrb(baseColor = baseColor, hover = hover)
            else -> CylinderDrum(baseColor = baseColor, hover = hover)
        }

        PackageBadge(pkg = pkg, hover = hover)
        GeometryAura(pkg = pkg, level = level, elevated = enlarged)
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
private fun CubeCrate(
    baseColor: Int,
    hover: Float
) {
    SigilBox(
        width = 1.2f,
        height = 0.12f,
        depth = 1.2f,
        position = listOf(0f, 0.12f + hover, 0f),
        color = PACKAGE_SHADE,
        metalness = 0.08f,
        roughness = 0.94f,
        name = "pkg-cube-pallet"
    )
    SigilBox(
        width = 1.14f,
        height = 1.02f,
        depth = 1.14f,
        position = listOf(0f, 0.6f + hover, 0f),
        color = baseColor,
        metalness = 0.16f,
        roughness = 0.44f,
        name = "pkg-cube-body"
    )
    for (x in listOf(-0.45f, 0.45f)) {
        SigilBox(
            width = 0.12f,
            height = 1.08f,
            depth = 1.18f,
            position = listOf(x, 0.6f + hover, 0f),
            color = PACKAGE_TAPE,
            metalness = 0.08f,
            roughness = 0.82f,
            castShadow = false,
            receiveShadow = false,
            name = "pkg-cube-rail-x-$x"
        )
    }
    for (z in listOf(-0.45f, 0.45f)) {
        SigilBox(
            width = 1.18f,
            height = 0.1f,
            depth = 0.12f,
            position = listOf(0f, 1.1f + hover, z),
            color = PACKAGE_TAPE,
            metalness = 0.08f,
            roughness = 0.82f,
            castShadow = false,
            receiveShadow = false,
            name = "pkg-cube-rail-z-$z"
        )
    }
    SigilBox(
        width = 0.16f,
        height = 1.04f,
        depth = 1.16f,
        position = listOf(0f, 0.6f + hover, 0f),
        color = PACKAGE_TAPE,
        metalness = 0.08f,
        roughness = 0.84f,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-cube-band-vertical"
    )
    SigilBox(
        width = 1.16f,
        height = 0.08f,
        depth = 0.16f,
        position = listOf(0f, 1.14f + hover, 0f),
        color = PACKAGE_TAPE,
        metalness = 0.08f,
        roughness = 0.84f,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-cube-band-top"
    )
}

@Composable
private fun RectParcel(
    baseColor: Int,
    hover: Float
) {
    SigilBox(
        width = 2f,
        height = 0.76f,
        depth = 1.02f,
        position = listOf(0f, 0.38f + hover, 0f),
        color = baseColor,
        metalness = 0.14f,
        roughness = 0.5f,
        name = "pkg-rect-body"
    )
    SigilBox(
        width = 1.84f,
        height = 0.08f,
        depth = 1f,
        position = listOf(0f, 0.78f + hover, 0f),
        color = PACKAGE_TRIM,
        metalness = 0.08f,
        roughness = 0.88f,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-rect-top"
    )
    SigilBox(
        width = 1.56f,
        height = 0.05f,
        depth = 0.14f,
        position = listOf(0f, 0.83f + hover, 0f),
        color = PACKAGE_TAPE,
        metalness = 0.08f,
        roughness = 0.88f,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-rect-seam"
    )
    SigilBox(
        width = 0.16f,
        height = 0.74f,
        depth = 1.06f,
        position = listOf(0f, 0.39f + hover, 0f),
        color = PACKAGE_TAPE,
        metalness = 0.08f,
        roughness = 0.84f,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-rect-band"
    )
    SigilBox(
        width = 0.42f,
        height = 0.18f,
        depth = 0.04f,
        position = listOf(0.58f, 0.56f + hover, 0.54f),
        color = PACKAGE_TAPE,
        metalness = 0.08f,
        roughness = 0.86f,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-rect-label"
    )
}

@Composable
private fun SphereOrb(
    baseColor: Int,
    hover: Float
) {
    SigilMesh(
        geometryType = GeometryType.RING,
        geometryParams = GeometryParams(innerRadius = 0.5f, outerRadius = 0.68f, radialSegments = 40),
        position = listOf(0f, 0.3f + hover, 0f),
        rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
        color = PACKAGE_TRIM,
        metalness = 0.22f,
        roughness = 0.54f,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-sphere-cradle"
    )
    for (x in listOf(-0.3f, 0.3f)) {
        SigilBox(
            width = 0.1f,
            height = 0.48f,
            depth = 0.1f,
            position = listOf(x, 0.42f + hover, 0f),
            rotation = listOf(0f, 0f, if (x < 0f) -0.34f else 0.34f),
            color = PACKAGE_TRIM,
            metalness = 0.18f,
            roughness = 0.72f,
            castShadow = false,
            receiveShadow = false,
            name = "pkg-sphere-strut-$x"
        )
    }
    SigilSphere(
        radius = 0.62f,
        widthSegments = 28,
        heightSegments = 18,
        position = listOf(0f, 0.82f + hover, 0f),
        color = baseColor,
        metalness = 0.22f,
        roughness = 0.34f,
        name = "pkg-sphere-body"
    )
    SigilMesh(
        geometryType = GeometryType.TORUS,
        geometryParams = GeometryParams(radius = 0.44f, tube = 0.045f, radialSegments = 18, tubularSegments = 40),
        position = listOf(0f, 0.82f + hover, 0f),
        rotation = listOf(-(PI.toFloat() / 2f), 0f, 0f),
        color = PACKAGE_TRIM,
        metalness = 0.22f,
        roughness = 0.56f,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-sphere-band"
    )
}

@Composable
private fun CylinderDrum(
    baseColor: Int,
    hover: Float
) {
    SigilMesh(
        geometryType = GeometryType.CYLINDER,
        geometryParams = GeometryParams(
            radiusTop = 0.48f,
            radiusBottom = 0.48f,
            height = 1.12f,
            radialSegments = 28
        ),
        position = listOf(0f, 0.6f + hover, 0f),
        color = baseColor,
        metalness = 0.18f,
        roughness = 0.42f,
        name = "pkg-cylinder-body"
    )
    for (y in listOf(0.14f, 1.06f)) {
        SigilMesh(
            geometryType = GeometryType.CYLINDER,
            geometryParams = GeometryParams(
                radiusTop = 0.54f,
                radiusBottom = 0.54f,
                height = 0.08f,
                radialSegments = 28
            ),
            position = listOf(0f, y + hover, 0f),
            color = PACKAGE_TRIM,
            metalness = 0.16f,
            roughness = 0.72f,
            castShadow = false,
            receiveShadow = false,
            name = "pkg-cylinder-ring-$y"
        )
    }
    SigilMesh(
        geometryType = GeometryType.CYLINDER,
        geometryParams = GeometryParams(
            radiusTop = 0.5f,
            radiusBottom = 0.5f,
            height = 0.14f,
            radialSegments = 28
        ),
        position = listOf(0f, 0.6f + hover, 0f),
        color = PACKAGE_TAPE,
        metalness = 0.12f,
        roughness = 0.8f,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-cylinder-mid-band"
    )
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

@Composable
private fun wheel(position: List<Float>, name: String) {
    SigilMesh(
        geometryType = GeometryType.CYLINDER,
        geometryParams = GeometryParams(
            radiusTop = 0.34f,
            radiusBottom = 0.34f,
            height = 0.26f,
            radialSegments = 20
        ),
        position = position,
        rotation = listOf(0f, 0f, PI.toFloat() / 2f),
        color = argb("#090e13"),
        metalness = 0.2f,
        roughness = 0.88f,
        name = name
    )
}

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
