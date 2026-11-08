package com.example.sculptgl.engine

import android.opengl.Matrix
import com.example.sculptgl.model.Ray
import com.example.sculptgl.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class CameraPreset {
    FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM, ISO
}

class SculptCamera {

    var target = Vec3(0f, 0f, 0f)
    var distance = 3.2f
    var yaw = 0.0f // horizontal angle in radians
    var pitch = 0.0f // vertical angle in radians

    var viewportWidth = 1080
    var viewportHeight = 1920

    val viewMatrix = FloatArray(16)
    val projMatrix = FloatArray(16)
    val viewProjMatrix = FloatArray(16)
    val invViewProjMatrix = FloatArray(16)

    val eye = Vec3()
    val right = Vec3()
    val up = Vec3(0f, 1f, 0f)
    val forward = Vec3()

    init {
        setPreset(CameraPreset.FRONT)
        updateMatrices()
    }

    fun setViewport(width: Int, height: Int) {
        viewportWidth = if (width > 0) width else 1
        viewportHeight = if (height > 0) height else 1
        updateMatrices()
    }

    fun rotate(deltaYaw: Float, deltaPitch: Float) {
        yaw += deltaYaw
        pitch = (pitch + deltaPitch).coerceIn(-1.5f, 1.5f)
        updateMatrices()
    }

    fun pan(deltaX: Float, deltaY: Float) {
        val factor = distance * 0.0015f
        target.x += (-right.x * deltaX + up.x * deltaY) * factor
        target.y += (-right.y * deltaX + up.y * deltaY) * factor
        target.z += (-right.z * deltaX + up.z * deltaY) * factor
        updateMatrices()
    }

    fun zoom(factor: Float) {
        distance = (distance * factor).coerceIn(0.5f, 25.0f)
        updateMatrices()
    }

    fun setPreset(preset: CameraPreset) {
        when (preset) {
            CameraPreset.FRONT -> { yaw = 0f; pitch = 0f }
            CameraPreset.BACK -> { yaw = PI.toFloat(); pitch = 0f }
            CameraPreset.LEFT -> { yaw = (PI * 0.5f).toFloat(); pitch = 0f }
            CameraPreset.RIGHT -> { yaw = (-PI * 0.5f).toFloat(); pitch = 0f }
            CameraPreset.TOP -> { yaw = 0f; pitch = 1.52f }
            CameraPreset.BOTTOM -> { yaw = 0f; pitch = -1.52f }
            CameraPreset.ISO -> { yaw = (PI * 0.25f).toFloat(); pitch = 0.5f }
        }
        updateMatrices()
    }

    fun resetView(center: Vec3 = Vec3(0f, 0f, 0f), radius: Float = 1.0f) {
        target.set(center)
        distance = radius * 2.8f
        yaw = 0f
        pitch = 0f
        updateMatrices()
    }

    fun updateMatrices() {
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)
        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)

        eye.x = target.x + distance * cosPitch * sinYaw
        eye.y = target.y + distance * sinPitch
        eye.z = target.z + distance * cosPitch * cosYaw

        forward.set((target - eye).normalized())
        right.set(forward.cross(Vec3(0f, 1f, 0f)).normalized())
        up.set(right.cross(forward).normalized())

        Matrix.setLookAtM(
            viewMatrix, 0,
            eye.x, eye.y, eye.z,
            target.x, target.y, target.z,
            up.x, up.y, up.z
        )

        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 45.0f, aspect, 0.1f, 100.0f)

        Matrix.multiplyMM(viewProjMatrix, 0, projMatrix, 0, viewMatrix, 0)
        Matrix.invertM(invViewProjMatrix, 0, viewProjMatrix, 0)
    }

    fun screenToRay(screenX: Float, screenY: Float): Ray {
        // Convert screen to Normalized Device Coordinates (NDC)
        val ndcX = (2.0f * screenX / viewportWidth) - 1.0f
        val ndcY = 1.0f - (2.0f * screenY / viewportHeight)

        val nearPoint = FloatArray(4)
        val farPoint = FloatArray(4)
        val ndcNear = floatArrayOf(ndcX, ndcY, -1.0f, 1.0f)
        val ndcFar = floatArrayOf(ndcX, ndcY, 1.0f, 1.0f)

        Matrix.multiplyMV(nearPoint, 0, invViewProjMatrix, 0, ndcNear, 0)
        Matrix.multiplyMV(farPoint, 0, invViewProjMatrix, 0, ndcFar, 0)

        val pNear = Vec3(nearPoint[0] / nearPoint[3], nearPoint[1] / nearPoint[3], nearPoint[2] / nearPoint[3])
        val pFar = Vec3(farPoint[0] / farPoint[3], farPoint[1] / farPoint[3], farPoint[2] / farPoint[3])

        return Ray(pNear, (pFar - pNear).normalized())
    }
}
