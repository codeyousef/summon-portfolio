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
import codes.yousef.sigil.summon.components.SigilModel
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

private val WAREHOUSE_FLOOR = argb("#0d1721")
private val SCENE_ACCENT = argb(FifthWallTheme.ACCENT)
private val SCENE_WARM = argb(FifthWallTheme.ACCENT_WARM)
private val SCENE_SUCCESS = argb(FifthWallTheme.SUCCESS)
private val SCENE_DANGER = argb(FifthWallTheme.DANGER)
private val SCENE_NEUTRAL_LIGHT = argb("#f6fbff")
private val PACKAGE_TAPE = argb("#eef4fb")
private val PACKAGE_SHADE = argb("#0b1219")
private val TRUCK_COLOR_FALLBACKS = listOf(
    argb("#ff6b6b"),
    argb("#5aa9ff"),
    argb("#45e0a8"),
    argb("#f7b955"),
    argb("#9fb0c8"),
    argb("#b690ff")
)

private data class FifthWallModelSpec(
    val url: String,
    val position: List<Float> = listOf(0f, 0f, 0f),
    val rotation: List<Float> = listOf(0f, 0f, 0f),
    val scale: List<Float> = listOf(1f, 1f, 1f)
)

private fun uniformScale(value: Float): List<Float> = listOf(value, value, value)

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
            fogEnabled = false,
            fogColor = argb("#0d1620"),
            fogNear = 36f,
            fogFar = 82f,
            shadowsEnabled = true
        )
        SigilCamera(
            position = listOf(0f, 6.7f, 16.4f),
            lookAt = listOf(0f, 1.85f, 2.35f),
            fov = 44f,
            near = 0.1f,
            far = 100f,
            name = "dispatch-camera"
        )
        SigilOrbitControls(
            target = listOf(0f, 1.85f, 2.35f),
            minDistance = 12.5f,
            maxDistance = 20f,
            minPolarAngle = 0.9f,
            maxPolarAngle = 1.36f,
            enablePan = false,
            autoRotate = focusedPackage?.geometry != null || state.prompt == FifthWallPrompt.Intro,
            autoRotateSpeed = 0.55f,
            rotateSpeed = 0.7f,
            zoomSpeed = 0.9f,
            name = "dispatch-orbit"
        )

        SigilAmbientLight(color = SCENE_NEUTRAL_LIGHT, intensity = 1.18f, name = "ambient-fill")
        SigilDirectionalLight(
            position = listOf(4f, 12f, 9f),
            target = listOf(0f, 0f, 1f),
            intensity = 0.62f,
            castShadow = false,
            name = "bay-sun"
        )
        SigilPointLight(
            position = listOf(-3f, 5.5f, -1f),
            color = SCENE_ACCENT,
            intensity = 0.28f,
            distance = 24f,
            decay = 1.6f,
            name = "belt-glow"
        )
        SigilPointLight(
            position = listOf(7f, 6.5f, -5.5f),
            color = if (focusedPackage?.validGeometry == false) SCENE_DANGER else SCENE_WARM,
            intensity = 0.26f,
            distance = 20f,
            decay = 1.7f,
            name = "inspection-glow"
        )
        SigilPointLight(
            position = listOf(0f, 5f, 8.4f),
            color = SCENE_WARM,
            intensity = 0.2f,
            distance = 18f,
            decay = 1.8f,
            name = "route-glow"
        )
        if (state.glitchActive) {
            SigilPointLight(
                position = listOf(0f, 8f, 5f),
                color = SCENE_DANGER,
                intensity = 1.8f,
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
        WrenchProp(visible = state.wrenchVisible)
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
    SigilModel(
        url = fifthWallModelUrl("warehouse-bay-shell-kit.glb"),
        position = listOf(0f, 0.02f, -7f),
        scale = uniformScale(17f),
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
        SigilModel(
            url = fifthWallModelUrl("conveyor-deck.glb"),
            scale = uniformScale(10.6f),
            castShadow = false,
            receiveShadow = false,
            name = "conveyor-model"
        )
        if (state.glitchActive) {
            SigilPointLight(
                position = listOf(0f, 1.4f, 0f),
                color = argb("#45121a"),
                intensity = 1.3f,
                distance = 8f,
                decay = 2f,
                name = "conveyor-glitch-light"
            )
        }
        state.visiblePackages().forEachIndexed { index, pkg ->
            val selected = state.selectedPackageId == pkg.id || (state.selectedPackageId == null && index == 0)
            PackageMesh(
                pkg = pkg,
                position = listOf(-4.9f + (index * 3.2f), 2.34f, 0f),
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
    val glow = if (hidden) 0.16f else 0.34f
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
    SigilPointLight(
        position = listOf(-1.1f, 3.2f, 0f),
        color = panelColor,
        intensity = glow,
        distance = 5.5f,
        decay = 1.9f,
        name = "$name-accent-light"
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
        name = "return-bin"
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
        SigilPointLight(
            position = listOf(0f, 2.8f, 0f),
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
        if (glitchActive) {
            SigilPointLight(
                position = listOf(0f, 2.3f, 0f),
                color = argb("#5c1f2a"),
                intensity = 1f,
                distance = 5.5f,
                decay = 1.8f,
                name = "return-glitch-light"
            )
        }
        ""
    }
}

@Composable
private fun InspectionDock(pkg: FifthWallPackage?) {
    SigilGroup(position = listOf(8.2f, 0f, -5.5f), name = "inspection-dock") {
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
        if (pkg != null) {
            PackageMesh(
                pkg = pkg,
                position = listOf(0f, 3.18f, 0f),
                selected = true,
                emphasized = true,
                beltIndex = -1,
                level = null,
                enlarged = true
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
        val modelSpec = packageModelSpec(pkg)
        SigilModel(
            url = modelSpec.url,
            position = listOf(
                modelSpec.position[0],
                modelSpec.position[1] + hover,
                modelSpec.position[2]
            ),
            rotation = modelSpec.rotation,
            scale = modelSpec.scale,
            castShadow = false,
            receiveShadow = false,
            name = "pkg-body"
        )
        PackageColorAccent(
            shape = pkg.shape,
            color = baseColor,
            hover = hover
        )
        PackageGeometryModel(pkg = pkg, hover = hover, elevated = enlarged)

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
private fun PackageGeometryModel(
    pkg: FifthWallPackage,
    hover: Float,
    elevated: Boolean
) {
    val modelSpec = geometryModelSpec(pkg, elevated) ?: return
    SigilModel(
        url = modelSpec.url,
        position = listOf(
            modelSpec.position[0],
            modelSpec.position[1] + hover,
            modelSpec.position[2]
        ),
        rotation = modelSpec.rotation,
        scale = modelSpec.scale,
        castShadow = false,
        receiveShadow = false,
        name = "pkg-geometry-model"
    )
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

    SigilPointLight(
        position = listOf(-0.72f, markerY + 0.18f, 0.56f),
        color = color,
        intensity = 0.24f,
        distance = 2.25f,
        decay = 2.1f,
        name = "pkg-color-light"
    )
}

@Composable
private fun WrenchProp(visible: Boolean) {
    if (!visible) return
    SigilGroup(
        position = listOf(13.1f, 0.06f, 9.2f),
        rotation = listOf(0f, -0.42f, 0f),
        name = "repair-wrench-prop"
    ) {
        SigilModel(
            url = fifthWallModelUrl("repair-wrench.glb"),
            scale = listOf(1.2f, 1.2f, 1.2f),
            rotation = listOf(0f, 0.72f, 0f),
            castShadow = false,
            receiveShadow = false,
            name = "repair-wrench-model"
        )
        SigilPointLight(
            position = listOf(0f, 0.8f, 0f),
            color = SCENE_ACCENT,
            intensity = 1.2f,
            distance = 6f,
            decay = 1.9f,
            name = "repair-wrench-light"
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
    val baseName = fileName.removeSuffix(".glb")
    return "/static/models/fifth-wall/$baseName/$baseName.gltf"
}

private fun packageModelSpec(pkg: FifthWallPackage): FifthWallModelSpec = when {
    pkg.labelText?.contains("SPECIAL DELIVERY", ignoreCase = true) == true -> FifthWallModelSpec(
        url = fifthWallModelUrl("special-delivery-package.glb"),
        position = listOf(0f, 0.1f, 0f),
        scale = uniformScale(1.18f)
    )
    pkg.shape == "rect" -> FifthWallModelSpec(
        url = fifthWallModelUrl("rectangular-parcel.glb"),
        position = listOf(0f, 0.12f, 0f),
        scale = uniformScale(1.36f)
    )
    pkg.shape == "sphere" -> FifthWallModelSpec(
        url = fifthWallModelUrl("sphere-package-with-cradle.glb"),
        position = listOf(0f, 0.08f, 0f),
        scale = uniformScale(1.16f)
    )
    pkg.shape == "cylinder" -> FifthWallModelSpec(
        url = fifthWallModelUrl("cylinder-drum.glb"),
        position = listOf(0f, 0.1f, 0f),
        scale = uniformScale(1.24f)
    )
    else -> FifthWallModelSpec(
        url = fifthWallModelUrl("cube-crate.glb"),
        position = listOf(0f, 0.12f, 0f),
        scale = uniformScale(1.18f)
    )
}

private fun geometryModelSpec(
    pkg: FifthWallPackage,
    elevated: Boolean
): FifthWallModelSpec? {
    if (pkg.geometry == null) return null

    val lift = if (elevated) 1.3f else 1.08f
    return when {
        pkg.validGeometry -> FifthWallModelSpec(
            url = fifthWallModelUrl("valid-geometry-insert-set.glb"),
            position = listOf(0f, lift, 0f),
            rotation = listOf(0f, 0.38f, 0f),
            scale = uniformScale(if (elevated) 1.05f else 0.82f)
        )
        pkg.geometry == "Penrose loop" -> FifthWallModelSpec(
            url = fifthWallModelUrl("penrose-loop.glb"),
            position = listOf(0f, lift, 0f),
            rotation = listOf(0.18f, 0.52f, 0f),
            scale = uniformScale(if (elevated) 0.9f else 0.66f)
        )
        pkg.geometry == "Escher stair" -> FifthWallModelSpec(
            url = fifthWallModelUrl("escher-stair.glb"),
            position = listOf(0f, lift, 0f),
            rotation = listOf(0.12f, 0.62f, 0f),
            scale = uniformScale(if (elevated) 1f else 0.78f)
        )
        else -> FifthWallModelSpec(
            url = fifthWallModelUrl("impossible-trident.glb"),
            position = listOf(0f, lift, 0f),
            rotation = listOf(0.14f, 0.48f, 0f),
            scale = uniformScale(if (elevated) 0.92f else 0.72f)
        )
    }
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
