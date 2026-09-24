package dev.mewdeko.mobile.feature.xp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.GuildPalette
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.TabLevel
import dev.mewdeko.mobile.core.ui.StatusMessage

/**
 * The full-screen rank card designer: a touch canvas that draws the card
 * exactly as the bot will, with drag to move, pinch to zoom, pan, tap to
 * select, and a bottom sheet for layers, properties, and tools.
 *
 * Staged edits live in [XpViewModel] and survive leaving the designer; the
 * Save action writes the template (and the background URL when it
 * changed). Errors surface through [status] in this screen's snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XpCardDesigner(
    state: XpState,
    viewModel: XpViewModel,
    status: StatusMessage?,
    onStatusShown: () -> Unit,
    onClose: () -> Unit,
) {
    val controller = viewModel.designer
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assets = remember { XpCardAssets.get(context) }
    val images = remember { XpImageCache(context, scope) }
    val renderer = remember { XpCardRenderer(assets) }
    val ink = rememberXpCardInk()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(status) {
        val current = status ?: return@LaunchedEffect
        snackbar.showSnackbar(current.text, duration = SnackbarDuration.Short)
        onStatusShown()
    }

    val backgroundUrl = state.settings.customXpImageUrl
    val (background, isDefaultBackground) = xpBackground(backgroundUrl, assets, images)
    LaunchedEffect(background, state.template.id) {
        background?.let { viewModel.syncOutputSizeToBackground(it.width, it.height) }
    }

    val sample = XpCardData.sample(viewModel.guildName, viewModel.guildId, viewModel.userId)
    val real = state.realCardData(viewModel.guildName, viewModel.guildId)
    val data = if (controller.useRealData && real != null) real else sample
    val scene = XpCardScene(
        template = state.template,
        customElements = state.customElements,
        builtInOrder = sanitizeBuiltInOrder(state.builtInOrder),
        data = data,
        background = background,
        defaultBackground = isDefaultBackground,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Card Designer")
                        if (state.hasUnsavedTemplate) {
                            Text(
                                "Unsaved changes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undoTemplate, enabled = state.templateUndoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = viewModel::redoTemplate, enabled = state.templateRedoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    TextButton(onClick = viewModel::saveTemplate, enabled = state.hasUnsavedTemplate) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            val sheetMax = maxHeight * 0.7f
            Column(modifier = Modifier.fillMaxSize()) {
                DesignerToolbar(controller = controller, realAvailable = real != null)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    DesignerCanvas(
                        scene = scene,
                        controller = controller,
                        viewModel = viewModel,
                        renderer = renderer,
                        ink = ink,
                        images = images,
                    )
                    CanvasOverlay(controller = controller)
                }
                if (controller.sheetOpen && controller.mode == XpDesignerMode.EDIT) {
                    DesignerSheet(
                        state = state,
                        viewModel = viewModel,
                        maxHeight = sheetMax,
                        backgroundFailed = images.failed(backgroundUrl),
                    )
                }
            }
        }
    }
}

/**
 * Mode, data source, zoom, snap, and grid controls above the canvas. Mode and
 * data source only change what the canvas shows, so they are secondary
 * [SectionTabs]; the data source switch is left out until real member data
 * has loaded.
 */
@Composable
private fun DesignerToolbar(controller: XpDesignerController, realAvailable: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(top = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTabs(
                tabs = DesignerModeTabs,
                selectedId = controller.mode.name,
                onSelect = { id ->
                    val mode = XpDesignerMode.valueOf(id)
                    controller.mode = mode
                    if (mode == XpDesignerMode.PREVIEW) controller.hoveredId = null
                },
                level = TabLevel.Secondary,
                modifier = Modifier.weight(1f),
            )
            if (realAvailable) {
                SectionTabs(
                    tabs = DesignerDataTabs,
                    selectedId = if (controller.useRealData) DATA_REAL else DATA_SAMPLE,
                    onSelect = { id -> controller.useRealData = id == DATA_REAL },
                    level = TabLevel.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        DesignerViewControls(controller)
    }
}

/** Tab id for the sample data source in [DesignerToolbar]. */
private const val DATA_SAMPLE = "sample"

/** Tab id for the real member data source in [DesignerToolbar]. */
private const val DATA_REAL = "real"

/** Edit and Preview modes for the designer canvas. */
private val DesignerModeTabs = listOf(
    SectionTab(XpDesignerMode.EDIT.name, "Edit"),
    SectionTab(XpDesignerMode.PREVIEW.name, "Preview"),
)

/** Sample and real data sources for the designer canvas. */
private val DesignerDataTabs = listOf(
    SectionTab(DATA_SAMPLE, "Sample"),
    SectionTab(DATA_REAL, "Real"),
)

/** Zoom, snap, and grid controls, scrolling sideways on narrow screens. */
@Composable
private fun DesignerViewControls(controller: XpDesignerController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { controller.stepZoom(-0.1f) }, enabled = controller.zoom > XpMinZoom) {
            Icon(Icons.Default.Remove, contentDescription = "Zoom out")
        }
        Text(
            "${Math.round(controller.zoom * 100)}%",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onClick = { controller.stepZoom(0.1f) }, enabled = controller.zoom < XpMaxZoom) {
            Icon(Icons.Default.Add, contentDescription = "Zoom in")
        }
        FilterChip(
            selected = controller.snapToGrid,
            onClick = { controller.snapToGrid = !controller.snapToGrid },
            label = { Text("Snap") },
        )
        FilterChip(
            selected = controller.showGrid,
            onClick = { controller.showGrid = !controller.showGrid },
            label = { Text("Grid") },
            leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
}

/**
 * The card canvas. Draws the scene through [XpCardRenderer] under the
 * controller's pan and zoom, and maps touches: one finger on an element
 * drags it (one undo entry per drag), one finger elsewhere pans, two
 * fingers pinch about their centroid and pan, a tap selects or clears, and
 * a double tap opens the element's properties. Pointer devices also get
 * hover chrome and wheel zoom and pan.
 */
@Composable
private fun DesignerCanvas(
    scene: XpCardScene,
    controller: XpDesignerController,
    viewModel: XpViewModel,
    renderer: XpCardRenderer,
    ink: XpCardInk,
    images: XpImageCache,
) {
    val palette = LocalGuildPalette.current
    val density = LocalDensity.current.density
    val currentScene by rememberUpdatedState(scene)

    LaunchedEffect(scene.cardWidth, scene.cardHeight) {
        controller.resetView(scene.cardWidth, scene.cardHeight)
    }

    fun options() = XpRenderOptions(
        mode = controller.mode,
        selectedId = controller.selectedId,
        hoveredId = controller.hoveredId,
        showGrid = controller.showGrid,
        gridSize = controller.gridSize,
        showRulers = controller.showRulers,
        unit = 1f / controller.zoom,
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(GuildPalette.SlateSidebar)
            .background(xpEditorBackdrop(palette))
            .onSizeChanged {
                controller.onViewSize(
                    it.width.toFloat(),
                    it.height.toFloat(),
                    density,
                    currentScene.cardWidth,
                    currentScene.cardHeight,
                )
            }
            .pointerInput(controller, viewModel, renderer) {
                val slop = viewConfiguration.touchSlop
                val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                var lastTapTime = 0L
                var lastTapId: String? = null
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val edit = controller.mode == XpDesignerMode.EDIT
                    val start = controller.toCard(down.position.x, down.position.y)
                    val hitId = if (edit) renderer.hitTest(currentScene, options(), start.x, start.y) else null
                    val origin = hitId?.let { viewModel.elementOrigin(it) }
                    var moved = false
                    var dragging = false
                    var multiTouch = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            multiTouch = true
                            moved = true
                            dragging = false
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (zoom != 1f) controller.zoomAbout(centroid.x, centroid.y, zoom)
                            controller.panBy(pan.x, pan.y)
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val change = pressed.first()
                        if (!moved && (change.position - down.position).getDistance() > slop) {
                            moved = true
                            if (hitId != null && origin != null && !multiTouch) {
                                viewModel.beginDrag(hitId)
                                dragging = true
                            }
                        }
                        if (moved) {
                            if (dragging && hitId != null && origin != null) {
                                val now = controller.toCard(change.position.x, change.position.y)
                                viewModel.setElementPosition(
                                    hitId,
                                    origin.x + (now.x - start.x),
                                    origin.y + (now.y - start.y),
                                )
                            } else {
                                val delta = change.position - change.previousPosition
                                controller.panBy(delta.x, delta.y)
                            }
                            change.consume()
                        }
                    }
                    if (!moved && edit) {
                        if (hitId != null) {
                            controller.selectedId = hitId
                            val isDouble = lastTapId == hitId && down.uptimeMillis - lastTapTime < doubleTapTimeout
                            if (isDouble) {
                                controller.openSheet(XpSheetTab.PROPERTIES)
                                lastTapTime = 0L
                                lastTapId = null
                            } else {
                                lastTapTime = down.uptimeMillis
                                lastTapId = hitId
                            }
                        } else {
                            controller.clearSelection()
                            lastTapId = null
                        }
                    }
                }
            }
            .pointerInput(controller, renderer) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Move -> if (!change.pressed && controller.mode == XpDesignerMode.EDIT) {
                                val point = controller.toCard(change.position.x, change.position.y)
                                controller.hoveredId = renderer.hitTest(currentScene, options(), point.x, point.y)
                            }
                            PointerEventType.Exit -> controller.hoveredId = null
                            PointerEventType.Scroll -> {
                                val delta = change.scrollDelta
                                val modifiers = event.keyboardModifiers
                                if (modifiers.isCtrlPressed || modifiers.isMetaPressed) {
                                    controller.zoomAbout(change.position.x, change.position.y, if (delta.y < 0) 1.1f else 0.9f)
                                } else {
                                    controller.panBy(-delta.x * 40f, -delta.y * 40f)
                                }
                                change.consume()
                            }
                        }
                    }
                }
            },
    ) {
        val renderOptions = options()
        val scale = controller.scale
        val panX = controller.panX
        val panY = controller.panY
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.withSave {
                translate(panX, panY)
                scale(scale, scale)
                renderer.draw(this, currentScene, renderOptions, ink) { images.get(it) }
            }
        }
    }
}

/** The floating Layers and Tools buttons and the zoom readout over the canvas. */
@Composable
private fun CanvasOverlay(controller: XpDesignerController) {
    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        if (controller.mode == XpDesignerMode.EDIT) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilledTonalButton(onClick = { controller.openSheet(XpSheetTab.LAYERS) }) {
                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Layers", modifier = Modifier.padding(start = 6.dp))
                }
                FilledTonalButton(onClick = { controller.openSheet(XpSheetTab.TOOLS) }) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Tools", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (Math.round(controller.zoom * 100) != 100) {
            Text(
                text = "${Math.round(controller.zoom * 100)}%",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

/**
 * The bottom sheet under the canvas: a drag handle that resizes it (up to
 * [maxHeight]), the Layers, Properties (while something is selected), and
 * Tools tabs, and a close button. It sits in the layout rather than over
 * the canvas, so the card stays fully visible and re-centres above it.
 */
@Composable
private fun DesignerSheet(
    state: XpState,
    viewModel: XpViewModel,
    maxHeight: Dp,
    backgroundFailed: Boolean,
) {
    val controller = viewModel.designer
    val palette = LocalGuildPalette.current
    val density = LocalDensity.current
    var fraction by remember { mutableFloatStateOf(0.75f) }
    val maxHeightPx = with(density) { maxHeight.toPx() }
    val dragState = rememberDraggableState { delta ->
        fraction = (fraction - delta / maxHeightPx).coerceIn(0.4f, 1f)
    }
    val selectedId = controller.selectedId
    val tabs = buildList {
        add(SectionTab(XpSheetTab.LAYERS.name, "Layers", Icons.Default.Layers))
        if (selectedId != null) add(SectionTab(XpSheetTab.PROPERTIES.name, "Properties", Icons.Default.Edit))
        add(SectionTab(XpSheetTab.TOOLS.name, "Tools", Icons.Default.Tune))
    }
    val activeTab = if (controller.sheetTab == XpSheetTab.PROPERTIES && selectedId == null) {
        XpSheetTab.LAYERS
    } else {
        controller.sheetTab
    }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(maxHeight * fraction)
            .border(1.dp, palette.primary.color.copy(alpha = DashAlpha.Hex30), shape),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(dragState, Orientation.Vertical)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.primary.color.copy(alpha = DashAlpha.Hex40)),
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTabs(
                    tabs = tabs,
                    selectedId = activeTab.name,
                    onSelect = { controller.sheetTab = XpSheetTab.valueOf(it) },
                    level = TabLevel.Secondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { controller.sheetOpen = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Close panel")
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (activeTab) {
                    XpSheetTab.LAYERS -> XpLayersPanel(state, viewModel)
                    XpSheetTab.PROPERTIES -> selectedId?.let { XpPropertiesPanel(state, viewModel, it) }
                    XpSheetTab.TOOLS -> XpToolsPanel(state, viewModel, backgroundFailed)
                }
            }
        }
    }
}
