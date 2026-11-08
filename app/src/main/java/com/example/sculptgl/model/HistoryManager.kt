package com.example.sculptgl.model

class MeshSnapshot(
    val vertices: FloatArray,
    val colors: FloatArray?,
    val masks: FloatArray?
)

class HistoryManager(private val maxHistory: Int = 20) {

    private val undoStack = ArrayList<MeshSnapshot>()
    private val redoStack = ArrayList<MeshSnapshot>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun pushState(mesh: SculptMesh) {
        val snapshot = MeshSnapshot(
            vertices = mesh.vertices.clone(),
            colors = mesh.colors?.clone(),
            masks = mesh.masks?.clone()
        )
        undoStack.add(snapshot)
        if (undoStack.size > maxHistory) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo(currentMesh: SculptMesh): Boolean {
        if (undoStack.isEmpty()) return false

        // Save current to redo
        val currentSnapshot = MeshSnapshot(
            vertices = currentMesh.vertices.clone(),
            colors = currentMesh.colors?.clone(),
            masks = currentMesh.masks?.clone()
        )
        redoStack.add(currentSnapshot)

        // Restore last undo
        val state = undoStack.removeAt(undoStack.lastIndex)
        applySnapshot(currentMesh, state)
        return true
    }

    fun redo(currentMesh: SculptMesh): Boolean {
        if (redoStack.isEmpty()) return false

        // Save current to undo
        val currentSnapshot = MeshSnapshot(
            vertices = currentMesh.vertices.clone(),
            colors = currentMesh.colors?.clone(),
            masks = currentMesh.masks?.clone()
        )
        undoStack.add(currentSnapshot)

        // Restore last redo
        val state = redoStack.removeAt(redoStack.lastIndex)
        applySnapshot(currentMesh, state)
        return true
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    private fun applySnapshot(mesh: SculptMesh, snapshot: MeshSnapshot) {
        if (mesh.vertices.size == snapshot.vertices.size) {
            System.arraycopy(snapshot.vertices, 0, mesh.vertices, 0, mesh.vertices.size)
        } else {
            mesh.vertices = snapshot.vertices.clone()
        }
        if (snapshot.colors != null && mesh.colors != null && mesh.colors!!.size == snapshot.colors.size) {
            System.arraycopy(snapshot.colors, 0, mesh.colors!!, 0, mesh.colors!!.size)
        }
        if (snapshot.masks != null && mesh.masks != null && mesh.masks!!.size == snapshot.masks.size) {
            System.arraycopy(snapshot.masks, 0, mesh.masks!!, 0, mesh.masks!!.size)
        }
        mesh.computeNormals()
        mesh.computeBounding()
    }
}
