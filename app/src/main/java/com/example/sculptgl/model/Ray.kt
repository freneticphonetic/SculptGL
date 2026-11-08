package com.example.sculptgl.model

import kotlin.math.sqrt

data class Ray(val origin: Vec3, val direction: Vec3) {

    /**
     * Möller–Trumbore ray-triangle intersection algorithm.
     * Returns distance t along ray to intersection, or null if no hit or hit is behind origin.
     */
    fun intersectTriangle(v0: Vec3, v1: Vec3, v2: Vec3): Float? {
        val edge1 = v1 - v0
        val edge2 = v2 - v0
        val h = direction.cross(edge2)
        val a = edge1.dot(h)

        if (a > -1e-6f && a < 1e-6f) return null // Parallel

        val f = 1.0f / a
        val s = origin - v0
        val u = f * s.dot(h)
        if (u < 0.0f || u > 1.0f) return null

        val q = s.cross(edge1)
        val v = f * direction.dot(q)
        if (v < 0.0f || u + v > 1.0f) return null

        val t = f * edge2.dot(q)
        return if (t > 1e-5f) t else null
    }

    /**
     * Intersects ray with sphere.
     */
    fun intersectSphere(center: Vec3, radius: Float): Float? {
        val oc = origin - center
        val a = direction.dot(direction)
        val b = 2.0f * oc.dot(direction)
        val c = oc.dot(oc) - radius * radius
        val discriminant = b * b - 4 * a * c
        if (discriminant < 0) return null
        val t = (-b - sqrt(discriminant)) / (2.0f * a)
        return if (t > 0) t else null
    }

    fun pointAt(t: Float): Vec3 {
        return origin + direction * t
    }
}
