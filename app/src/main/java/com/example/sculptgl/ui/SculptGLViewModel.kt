package com.example.sculptgl.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sculptgl.engine.CameraPreset
import com.example.sculptgl.engine.SculptEngine
import com.example.sculptgl.engine.SculptRenderer
import com.example.sculptgl.engine.SculptTool
import com.example.sculptgl.engine.ShadingMode
import com.example.sculptgl.model.HistoryManager
import com.example.sculptgl.model.MeshPrimitives
import com.example.sculptgl.model.SculptMesh
import com.example.sculptgl.model.Vec3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class CanvasInteractionMode {
    SCULPT, // 1 finger sculpts on mesh, orbits on empty canvas
    CAMERA  // 1 finger always orbits camera
}

data class SculptUIState(
    val currentTool: SculptTool = SculptTool.BRUSH,
    val brushRadius: Float = 0.35f,
    val brushIntensity: Float = 0.5f,
    val isNegative: Boolean = false,
    val symmetryX: Boolean = true,
    val shadingMode: ShadingMode = ShadingMode.CLAY_MATCAP,
    val showWireframe: Boolean = false,
    val showGrid: Boolean = true,
    val interactionMode: CanvasInteractionMode = CanvasInteractionMode.SCULPT,
    val vertexCount: Int = 0,
    val triangleCount: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val toastMessage: String? = null
)

class SculptGLViewModel(application: Application) : AndroidViewModel(application) {

    val engine = SculptEngine()
    val history = HistoryManager()
    lateinit var renderer: SculptRenderer

    private val _uiState = MutableStateFlow(SculptUIState())
    val uiState: StateFlow<SculptUIState> = _uiState.asStateFlow()

    private var activeMesh: SculptMesh = MeshPrimitives.createSphere(24, 1.0f)

    init {
        updateMeshStats()
    }

    fun initRenderer(context: Context): SculptRenderer {
        renderer = SculptRenderer(context)
        renderer.mesh = activeMesh
        renderer.cursorRadius = engine.radius
        renderer.symmetryX = engine.symmetryX
        renderer.shadingMode = _uiState.value.shadingMode
        renderer.showWireframe = _uiState.value.showWireframe
        renderer.showGrid = _uiState.value.showGrid
        return renderer
    }

    fun selectTool(tool: SculptTool) {
        engine.currentTool = tool
        _uiState.value = _uiState.value.copy(currentTool = tool)
        val msg = if (tool == SculptTool.PLANAR) {
            "Planar Brush: Sculpt flat facets"
        } else {
            "${tool.displayName} selected"
        }
        showToast(msg)
    }

    fun setRadius(radius: Float) {
        engine.radius = radius
        if (::renderer.isInitialized) {
            renderer.cursorRadius = radius
        }
        _uiState.value = _uiState.value.copy(brushRadius = radius)
    }

    fun setIntensity(intensity: Float) {
        engine.intensity = intensity
        _uiState.value = _uiState.value.copy(brushIntensity = intensity)
    }

    fun toggleNegative() {
        val next = !engine.isNegative
        engine.isNegative = next
        _uiState.value = _uiState.value.copy(isNegative = next)
        val msg = if (engine.currentTool == SculptTool.PLANAR) {
            if (next) "Planar: Trim / Subtract mode (-)" else "Planar: Build / Add mode (+)"
        } else {
            if (next) "Sub / Carve mode (-)" else "Add / Build mode (+)"
        }
        showToast(msg)
    }

    fun toggleSymmetry() {
        val next = !engine.symmetryX
        engine.symmetryX = next
        if (::renderer.isInitialized) {
            renderer.symmetryX = next
        }
        _uiState.value = _uiState.value.copy(symmetryX = next)
        showToast(if (next) "Symmetry: ON (Mirror X)" else "Symmetry: OFF")
    }

    fun setShadingMode(mode: ShadingMode) {
        if (::renderer.isInitialized) {
            renderer.shadingMode = mode
        }
        _uiState.value = _uiState.value.copy(shadingMode = mode)
    }

    fun toggleWireframe() {
        val next = !_uiState.value.showWireframe
        if (::renderer.isInitialized) {
            renderer.showWireframe = next
        }
        _uiState.value = _uiState.value.copy(showWireframe = next)
    }

    fun toggleGrid() {
        val next = !_uiState.value.showGrid
        if (::renderer.isInitialized) {
            renderer.showGrid = next
        }
        _uiState.value = _uiState.value.copy(showGrid = next)
    }

    fun toggleInteractionMode() {
        val next = if (_uiState.value.interactionMode == CanvasInteractionMode.SCULPT) {
            CanvasInteractionMode.CAMERA
        } else {
            CanvasInteractionMode.SCULPT
        }
        _uiState.value = _uiState.value.copy(interactionMode = next)
        showToast(if (next == CanvasInteractionMode.CAMERA) "Camera View Mode" else "Sculpt Touch Mode")
    }

    fun setCameraPreset(preset: CameraPreset) {
        if (::renderer.isInitialized) {
            renderer.camera.setPreset(preset)
        }
        showToast("View: ${preset.name}")
    }

    fun resetCamera() {
        if (::renderer.isInitialized) {
            renderer.camera.resetView(activeMesh.center, activeMesh.radius)
        }
        showToast("Camera reset to center")
    }

    fun triggerUndo() {
        if (history.undo(activeMesh)) {
            if (::renderer.isInitialized) {
                renderer.needsMeshUpdate = true
            }
            updateMeshStats()
            showToast("Undo ↺ (2-Finger Tap)")
        }
    }

    fun triggerRedo() {
        if (history.redo(activeMesh)) {
            if (::renderer.isInitialized) {
                renderer.needsMeshUpdate = true
            }
            updateMeshStats()
            showToast("Redo ↻ (3-Finger Tap)")
        }
    }

    fun loadPrimitive(type: String) {
        val newMesh = when (type) {
            "Sphere" -> MeshPrimitives.createSphere(24, 1.0f)
            "Cube" -> MeshPrimitives.createCube(18, 1.3f)
            "Cylinder" -> MeshPrimitives.createCylinder(0.75f, 1.6f, 28, 20)
            "Torus" -> MeshPrimitives.createTorus(0.9f, 0.35f, 24, 40)
            "Plane" -> MeshPrimitives.createPlane(1.8f, 30)
            else -> MeshPrimitives.createSphere(24, 1.0f)
        }
        activeMesh = newMesh
        history.clear()
        if (::renderer.isInitialized) {
            renderer.mesh = activeMesh
            renderer.camera.resetView(activeMesh.center, activeMesh.radius)
        }
        updateMeshStats()
        showToast("$type loaded")
    }

    fun subdivideMesh() {
        viewModelScope.launch {
            // Linear 1-to-4 triangle subdivision
            val triCount = activeMesh.triangleCount
            if (triCount > 40000) {
                showToast("Mesh resolution already high")
                return@launch
            }

            val oldVerts = activeMesh.vertices
            val oldIndices = activeMesh.indices
            val newVertsList = ArrayList<Float>()
            for (f in oldVerts) newVertsList.add(f)

            val edgeMidMap = HashMap<Long, Int>()

            fun getMidpoint(iA: Int, iB: Int): Int {
                val minIdx = Math.min(iA, iB)
                val maxIdx = Math.max(iA, iB)
                val key = (minIdx.toLong() shl 32) or (maxIdx.toLong() and 0xFFFFFFFFL)
                return edgeMidMap.getOrPut(key) {
                    val midIdx = newVertsList.size / 3
                    val mx = (oldVerts[iA * 3] + oldVerts[iB * 3]) * 0.5f
                    val my = (oldVerts[iA * 3 + 1] + oldVerts[iB * 3 + 1]) * 0.5f
                    val mz = (oldVerts[iA * 3 + 2] + oldVerts[iB * 3 + 2]) * 0.5f
                    newVertsList.add(mx); newVertsList.add(my); newVertsList.add(mz)
                    midIdx
                }
            }

            val newIndsList = ArrayList<Int>()
            for (t in 0 until triCount) {
                val i0 = oldIndices[t * 3]
                val i1 = oldIndices[t * 3 + 1]
                val i2 = oldIndices[t * 3 + 2]

                val m01 = getMidpoint(i0, i1)
                val m12 = getMidpoint(i1, i2)
                val m20 = getMidpoint(i2, i0)

                newIndsList.add(i0); newIndsList.add(m01); newIndsList.add(m20)
                newIndsList.add(i1); newIndsList.add(m12); newIndsList.add(m01)
                newIndsList.add(i2); newIndsList.add(m20); newIndsList.add(m12)
                newIndsList.add(m01); newIndsList.add(m12); newIndsList.add(m20)
            }

            history.pushState(activeMesh)
            activeMesh = SculptMesh(newVertsList.toFloatArray(), newIndsList.toIntArray())
            if (::renderer.isInitialized) {
                renderer.mesh = activeMesh
            }
            updateMeshStats()
            showToast("Subdivided: ${activeMesh.vertexCount} vertices")
        }
    }

    fun exportModel(context: Context, format: String) {
        viewModelScope.launch {
            try {
                val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = if (format == "OBJ") {
                    val objContent = activeMesh.exportObj()
                    File(cacheDir, "sculpt_model.obj").apply { writeText(objContent) }
                } else {
                    val stlBytes = activeMesh.exportStlBinary()
                    File(cacheDir, "sculpt_model.stl").apply {
                        FileOutputStream(this).use { it.write(stlBytes) }
                    }
                }

                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (format == "OBJ") "text/plain" else "application/sla"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share $format Model"))
                showToast("Exported $format successfully!")
            } catch (e: Exception) {
                showToast("Export saved to cache: ${e.message}")
            }
        }
    }

    fun getMesh(): SculptMesh = activeMesh

    fun updateMeshStats() {
        _uiState.value = _uiState.value.copy(
            vertexCount = activeMesh.vertexCount,
            triangleCount = activeMesh.triangleCount,
            canUndo = history.canUndo,
            canRedo = history.canRedo
        )
    }

    fun showToast(msg: String) {
        _uiState.value = _uiState.value.copy(toastMessage = msg)
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
