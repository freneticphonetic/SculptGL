package com.example.sculptgl.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.sculptgl.engine.CameraPreset
import com.example.sculptgl.engine.SculptTool
import com.example.sculptgl.engine.ShadingMode
import com.example.sculptgl.ui.theme.DarkBorder
import com.example.sculptgl.ui.theme.DarkSurface
import com.example.sculptgl.ui.theme.DarkSurfaceVariant
import com.example.sculptgl.ui.theme.PrimaryClay
import com.example.sculptgl.ui.theme.PrimaryClayLight
import com.example.sculptgl.ui.theme.TextMuted
import com.example.sculptgl.ui.theme.TextPrimary
import com.example.sculptgl.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SculptGLApp(viewModel: SculptGLViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showPrimitiveDialog by remember { mutableStateOf(false) }
    var showShadingDialog by remember { mutableStateOf(false) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Auto-clear toast banner
    LaunchedEffect(uiState.toastMessage) {
        if (uiState.toastMessage != null) {
            delay(2200)
            viewModel.clearToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141518))
    ) {
        // 1. OpenGL 3D Sculpting Canvas
        AndroidView(
            factory = { ctx ->
                val r = viewModel.initRenderer(ctx)
                SculptGLSurfaceView(ctx, viewModel, r)
            },
            onRelease = { view ->
                view.onPause()
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("sculpt_canvas")
        )

        // 2. Top Header Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .testTag("top_bar"),
            color = DarkSurface.copy(alpha = 0.92f),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and Mesh stats
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PrimaryClay),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewInAr,
                            contentDescription = "SculptGL",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SculptGL 3D",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${uiState.vertexCount} verts • ${uiState.triangleCount} tris",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                // Quick Action Bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Undo button
                    IconButton(
                        onClick = { viewModel.triggerUndo() },
                        enabled = uiState.canUndo,
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo (2-finger tap)",
                            tint = if (uiState.canUndo) TextPrimary else TextMuted
                        )
                    }

                    // Redo button
                    IconButton(
                        onClick = { viewModel.triggerRedo() },
                        enabled = uiState.canRedo,
                        modifier = Modifier.testTag("redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo (3-finger tap)",
                            tint = if (uiState.canRedo) TextPrimary else TextMuted
                        )
                    }

                    // Symmetry Toggle
                    IconButton(
                        onClick = { viewModel.toggleSymmetry() },
                        modifier = Modifier.testTag("symmetry_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (uiState.symmetryX) PrimaryClay.copy(alpha = 0.25f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (uiState.symmetryX) PrimaryClay else DarkBorder,
                                    RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SYM",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.symmetryX) PrimaryClayLight else TextSecondary
                            )
                        }
                    }

                    // Interaction mode (Sculpt vs Camera)
                    IconButton(
                        onClick = { viewModel.toggleInteractionMode() },
                        modifier = Modifier.testTag("mode_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.interactionMode == CanvasInteractionMode.SCULPT) Icons.Default.Brush else Icons.Default.CameraAlt,
                            contentDescription = "Toggle Sculpt/Camera Mode",
                            tint = if (uiState.interactionMode == CanvasInteractionMode.SCULPT) PrimaryClay else TextSecondary
                        )
                    }

                    // Wireframe Toggle
                    IconButton(
                        onClick = { viewModel.toggleWireframe() },
                        modifier = Modifier.testTag("wireframe_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Toggle Wireframe",
                            tint = if (uiState.showWireframe) PrimaryClay else TextSecondary
                        )
                    }

                    // Overflow Menu
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = TextPrimary
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(DarkSurfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Primitive Mesh...", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.CropLandscape, null, tint = PrimaryClay) },
                                onClick = {
                                    showMenu = false
                                    showPrimitiveDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Subdivide Mesh (Detail)", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Layers, null, tint = PrimaryClay) },
                                onClick = {
                                    showMenu = false
                                    viewModel.subdivideMesh()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Camera Views...", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.CenterFocusStrong, null, tint = PrimaryClay) },
                                onClick = {
                                    showMenu = false
                                    showCameraDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Shading & Matcaps...", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Texture, null, tint = PrimaryClay) },
                                onClick = {
                                    showMenu = false
                                    showShadingDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Toggle Grid Floor", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.GridOn, null, tint = PrimaryClay) },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleGrid()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Wavefront (.OBJ)", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Share, null, tint = PrimaryClay) },
                                onClick = {
                                    showMenu = false
                                    viewModel.exportModel(context, "OBJ")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export 3D Print (.STL)", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Share, null, tint = PrimaryClay) },
                                onClick = {
                                    showMenu = false
                                    viewModel.exportModel(context, "STL")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Touch Gestures Guide", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.PanTool, null, tint = PrimaryClay) },
                                onClick = {
                                    showMenu = false
                                    showHelpDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. Toast / Banner Notification
        AnimatedVisibility(
            visible = uiState.toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 68.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryClay.copy(alpha = 0.5f))
            ) {
                Text(
                    text = uiState.toastMessage ?: "",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // 4. Floating Quick Adjustments (Radius & Intensity & Carve Toggle)
        Card(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 118.dp)
                .widthIn(max = 210.dp)
                .testTag("brush_controls_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.88f)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Tool Name + Mode subtitle + Add/Sub toggle
                val isPlanar = uiState.currentTool == SculptTool.PLANAR
                val isRemoving = uiState.isNegative
                val modeColor = if (isRemoving) Color(0xFFEF4444) else PrimaryClay

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = uiState.currentTool.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryClayLight
                        )
                        Text(
                            text = if (isPlanar) {
                                if (isRemoving) "Trim (-)" else "Build (+)"
                            } else {
                                if (isRemoving) "Carve (-)" else "Build (+)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = modeColor
                        )
                    }

                    // Add / Sub Toggle button
                    FilledIconButton(
                        onClick = { viewModel.toggleNegative() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = modeColor
                        ),
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("sub_add_toggle")
                    ) {
                        Icon(
                            imageVector = if (isRemoving) Icons.Default.Remove else Icons.Default.Add,
                            contentDescription = if (isRemoving) "Subtract Mode" else "Add Mode",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Radius slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(36.dp))
                    Slider(
                        value = uiState.brushRadius,
                        onValueChange = { viewModel.setRadius(it) },
                        valueRange = 0.05f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryClay, activeTrackColor = PrimaryClay),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Intensity slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Str", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(36.dp))
                    Slider(
                        value = uiState.brushIntensity,
                        onValueChange = { viewModel.setIntensity(it) },
                        valueRange = 0.05f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryClay, activeTrackColor = PrimaryClay),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 5. Floating Gesture Hint Banner
        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 118.dp)
                .testTag("gesture_hint_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.78f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = "✌️ 2-finger tap: Undo",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "🖐️ 3-finger tap: Redo",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "🤏 Pinch: Zoom & Pan",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                if (uiState.currentTool == SculptTool.PLANAR) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "📐 Planar: Locks plane to sculpt flat facets",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryClayLight,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 6. Bottom Dock: Tool Selection Chips
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .testTag("bottom_tool_dock"),
            color = DarkSurface.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SculptTool.values().forEach { tool ->
                        val isSelected = uiState.currentTool == tool
                        val isPlanar = tool == SculptTool.PLANAR

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.selectTool(tool)
                                if (tool == SculptTool.PAINT) {
                                    showColorDialog = true
                                }
                            },
                            label = {
                                Text(
                                    text = if (isPlanar) "★ Planar" else tool.displayName,
                                    fontWeight = if (isSelected || isPlanar) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = getToolIcon(tool),
                                    contentDescription = tool.displayName,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryClay,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                containerColor = if (isPlanar) PrimaryClay.copy(alpha = 0.15f) else DarkSurfaceVariant,
                                labelColor = if (isPlanar) PrimaryClayLight else TextPrimary,
                                iconColor = if (isPlanar) PrimaryClayLight else TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isPlanar) PrimaryClay else DarkBorder,
                                selectedBorderColor = PrimaryClayLight
                            ),
                            modifier = Modifier.testTag("tool_${tool.name.lowercase()}")
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showPrimitiveDialog) {
        AlertDialog(
            onDismissRequest = { showPrimitiveDialog = false },
            title = { Text("Choose 3D Primitive", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Sphere", "Cube", "Cylinder", "Torus", "Plane").forEach { prim ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPrimitiveDialog = false
                                    viewModel.loadPrimitive(prim)
                                },
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(prim, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Icon(Icons.Default.ViewInAr, null, tint = PrimaryClay)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrimitiveDialog = false }) {
                    Text("Cancel", color = PrimaryClay)
                }
            },
            containerColor = DarkSurface
        )
    }

    if (showShadingDialog) {
        AlertDialog(
            onDismissRequest = { showShadingDialog = false },
            title = { Text("Shading & Matcaps", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShadingMode.values().forEach { mode ->
                        val isSelected = uiState.shadingMode == mode
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showShadingDialog = false
                                    viewModel.setShadingMode(mode)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) PrimaryClay.copy(alpha = 0.25f) else DarkSurfaceVariant
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) PrimaryClay else DarkBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(mode.displayName, color = if (isSelected) PrimaryClayLight else TextPrimary)
                                if (isSelected) {
                                    Text("✓", color = PrimaryClay, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShadingDialog = false }) {
                    Text("Close", color = PrimaryClay)
                }
            },
            containerColor = DarkSurface
        )
    }

    if (showCameraDialog) {
        AlertDialog(
            onDismissRequest = { showCameraDialog = false },
            title = { Text("Camera Views", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ButtonView("Front", Modifier.weight(1f)) { viewModel.setCameraPreset(CameraPreset.FRONT); showCameraDialog = false }
                        ButtonView("Back", Modifier.weight(1f)) { viewModel.setCameraPreset(CameraPreset.BACK); showCameraDialog = false }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ButtonView("Left", Modifier.weight(1f)) { viewModel.setCameraPreset(CameraPreset.LEFT); showCameraDialog = false }
                        ButtonView("Right", Modifier.weight(1f)) { viewModel.setCameraPreset(CameraPreset.RIGHT); showCameraDialog = false }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ButtonView("Top", Modifier.weight(1f)) { viewModel.setCameraPreset(CameraPreset.TOP); showCameraDialog = false }
                        ButtonView("Isometric", Modifier.weight(1f)) { viewModel.setCameraPreset(CameraPreset.ISO); showCameraDialog = false }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ButtonView("Reset & Frame Model", Modifier.fillMaxWidth()) {
                        viewModel.resetCamera()
                        showCameraDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCameraDialog = false }) {
                    Text("Close", color = PrimaryClay)
                }
            },
            containerColor = DarkSurface
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Mobile Sculpting Gestures", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GestureRow("✌️ 2-Finger Quick Tap", "Undo the last sculpting stroke (standard Android art gesture)")
                    GestureRow("🖐️ 3-Finger Quick Tap", "Redo previously undone stroke")
                    GestureRow("☝️ 1-Finger on Model", "Deforms and sculpts mesh with selected tool")
                    GestureRow("☝️ 1-Finger on Canvas", "Smoothly orbits and rotates camera around model")
                    GestureRow("🤏 2-Finger Pinch", "Zooms camera closer or further away")
                    GestureRow("✌️ 2-Finger Drag", "Pans camera position horizontally & vertically")
                    GestureRow("★ Planar Brush", "Flattens geometry into crisp planar facets & hard surfaces")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it!", color = PrimaryClay)
                }
            },
            containerColor = DarkSurface
        )
    }

    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("Paint Tool Color", color = TextPrimary) },
            text = {
                val palette = listOf(
                    com.example.sculptgl.model.Vec3(0.9f, 0.25f, 0.2f), // Red/Clay
                    com.example.sculptgl.model.Vec3(0.95f, 0.55f, 0.15f), // Orange
                    com.example.sculptgl.model.Vec3(0.95f, 0.85f, 0.2f), // Yellow
                    com.example.sculptgl.model.Vec3(0.2f, 0.8f, 0.35f), // Green
                    com.example.sculptgl.model.Vec3(0.2f, 0.6f, 0.95f), // Blue
                    com.example.sculptgl.model.Vec3(0.7f, 0.35f, 0.95f), // Purple
                    com.example.sculptgl.model.Vec3(0.95f, 0.75f, 0.65f), // Skin
                    com.example.sculptgl.model.Vec3(0.15f, 0.15f, 0.15f), // Charcoal
                    com.example.sculptgl.model.Vec3(0.95f, 0.95f, 0.95f) // White
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    palette.forEach { vColor ->
                        val composeColor = Color(vColor.x, vColor.y, vColor.z)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(composeColor)
                                .border(2.dp, Color.White, CircleShape)
                                .clickable {
                                    viewModel.engine.paintColor.set(vColor)
                                    showColorDialog = false
                                    viewModel.showToast("Paint color set")
                                }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorDialog = false }) {
                    Text("Done", color = PrimaryClay)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun ButtonView(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GestureRow(gesture: String, desc: String) {
    Column {
        Text(gesture, fontWeight = FontWeight.Bold, color = PrimaryClayLight, fontSize = 14.sp)
        Text(desc, color = TextSecondary, fontSize = 12.sp)
    }
}

private fun getToolIcon(tool: SculptTool): ImageVector {
    return when (tool) {
        SculptTool.BRUSH -> Icons.Default.Brush
        SculptTool.PLANAR -> Icons.Default.CropLandscape
        SculptTool.INFLATE -> Icons.Default.ViewInAr
        SculptTool.TWIST -> Icons.Default.Transform
        SculptTool.SMOOTH -> Icons.Default.ColorLens
        SculptTool.FLATTEN -> Icons.Default.Layers
        SculptTool.PINCH -> Icons.Default.CenterFocusStrong
        SculptTool.CREASE -> Icons.Default.CropLandscape
        SculptTool.DRAG -> Icons.Default.PanTool
        SculptTool.PAINT -> Icons.Default.FormatPaint
        SculptTool.MOVE -> Icons.Default.PanTool
        SculptTool.MASK -> Icons.Default.Layers
    }
}
