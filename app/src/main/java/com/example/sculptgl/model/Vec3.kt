package com.example.sculptgl.model

import kotlin.math.sqrt

data class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {

    fun set(nx: Float, ny: Float, nz: Float): Vec3 {
        x = nx
        y = ny
        z = nz
        return this
    }

    fun set(v: Vec3): Vec3 {
        x = v.x
        y = v.y
        z = v.z
        return this
    }

    operator fun plus(v: Vec3) = Vec3(x + v.x, y + v.y, z + v.z)
    operator fun minus(v: Vec3) = Vec3(x - v.x, y - v.y, z - v.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float) = if (s != 0f) Vec3(x / s, y / s, z / s) else Vec3()

    fun add(v: Vec3): Vec3 {
        x += v.x
        y += v.y
        z += v.z
        return this
    }

    fun sub(v: Vec3): Vec3 {
        x -= v.x
        y -= v.y
        z -= v.z
        return this
    }

    fun scale(s: Float): Vec3 {
        x *= s
        y *= s
        z *= s
        return this
    }

    fun dot(v: Vec3): Float = x * v.x + y * v.y + z * v.z

    fun cross(v: Vec3): Vec3 = Vec3(
        y * v.z - z * v.y,
        z * v.x - x * v.z,
        x * v.y - y * v.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun lengthSquared(): Float = x * x + y * y + z * z

    fun distance(v: Vec3): Float = (this - v).length()

    fun distanceSquared(v: Vec3): Float = (this - v).lengthSquared()

    fun normalize(): Vec3 {
        val len = length()
        if (len > 1e-6f) {
            x /= len
            y /= len
            z /= len
        }
        return this
    }

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 0f, 0f)
    }

    fun copy() = Vec3(x, y, z)
}
