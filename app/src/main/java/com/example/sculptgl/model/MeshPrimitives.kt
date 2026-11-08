package com.example.sculptgl.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object MeshPrimitives {

    /**
     * Creates a normalized cube sphere (like SculptGL's subdivided cube)
     * which provides uniform vertex distribution without polar pinching.
     */
    fun createSphere(resolution: Int = 24, radius: Float = 1.0f): SculptMesh {
        val n = resolution
        val vertsList = ArrayList<Float>()
        val indsList = ArrayList<Int>()

        // 6 faces of the cube
        val faceDirs = arrayOf(
            arrayOf(Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f)), // +Z
            arrayOf(Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, -1f)), // -Z
            arrayOf(Vec3(0f, 0f, -1f), Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f)), // +X
            arrayOf(Vec3(0f, 0f, 1f), Vec3(0f, 1f, 0f), Vec3(-1f, 0f, 0f)), // -X
            arrayOf(Vec3(1f, 0f, 0f), Vec3(0f, 0f, -1f), Vec3(0f, 1f, 0f)), // +Y
            arrayOf(Vec3(1f, 0f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, -1f, 0f))  // -Y
        )

        val vertexMap = HashMap<Long, Int>()

        fun getOrAddVertex(x: Float, y: Float, z: Float): Int {
            // Spherify cube point:
            val len = sqrt(x * x + y * y + z * z)
            val sx = (x / len) * radius
            val sy = (y / len) * radius
            val sz = (z / len) * radius

            val qx = (sx * 10000).toInt()
            val qy = (sy * 10000).toInt()
            val qz = (sz * 10000).toInt()
            val key = ((qx.toLong() and 0x1FFFFF) shl 42) or ((qy.toLong() and 0x1FFFFF) shl 21) or (qz.toLong() and 0x1FFFFF)

            return vertexMap.getOrPut(key) {
                val index = vertsList.size / 3
                vertsList.add(sx)
                vertsList.add(sy)
                vertsList.add(sz)
                index
            }
        }

        for (face in faceDirs) {
            val right = face[0]
            val up = face[1]
            val normal = face[2]

            for (j in 0 until n) {
                for (i in 0 until n) {
                    val u0 = -1f + 2f * i / n
                    val u1 = -1f + 2f * (i + 1) / n
                    val v0 = -1f + 2f * j / n
                    val v1 = -1f + 2f * (j + 1) / n

                    fun point(u: Float, v: Float): Vec3 {
                        return normal + (right * u) + (up * v)
                    }

                    val p00 = point(u0, v0)
                    val p10 = point(u1, v0)
                    val p11 = point(u1, v1)
                    val p01 = point(u0, v1)

                    val idx00 = getOrAddVertex(p00.x, p00.y, p00.z)
                    val idx10 = getOrAddVertex(p10.x, p10.y, p10.z)
                    val idx11 = getOrAddVertex(p11.x, p11.y, p11.z)
                    val idx01 = getOrAddVertex(p01.x, p01.y, p01.z)

                    indsList.add(idx00)
                    indsList.add(idx10)
                    indsList.add(idx11)

                    indsList.add(idx00)
                    indsList.add(idx11)
                    indsList.add(idx01)
                }
            }
        }

        return SculptMesh(
            vertices = vertsList.toFloatArray(),
            indices = indsList.toIntArray()
        )
    }

    /**
     * Creates a subdivided cube for sculpting.
     */
    fun createCube(resolution: Int = 18, size: Float = 1.3f): SculptMesh {
        val n = resolution
        val half = size * 0.5f
        val vertsList = ArrayList<Float>()
        val indsList = ArrayList<Int>()

        val faceDirs = arrayOf(
            arrayOf(Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f)), // +Z
            arrayOf(Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, -1f)), // -Z
            arrayOf(Vec3(0f, 0f, -1f), Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f)), // +X
            arrayOf(Vec3(0f, 0f, 1f), Vec3(0f, 1f, 0f), Vec3(-1f, 0f, 0f)), // -X
            arrayOf(Vec3(1f, 0f, 0f), Vec3(0f, 0f, -1f), Vec3(0f, 1f, 0f)), // +Y
            arrayOf(Vec3(1f, 0f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, -1f, 0f))  // -Y
        )

        val vertexMap = HashMap<Long, Int>()

        fun getOrAddVertex(x: Float, y: Float, z: Float): Int {
            val qx = (x * 10000).toInt()
            val qy = (y * 10000).toInt()
            val qz = (z * 10000).toInt()
            val key = ((qx.toLong() and 0x1FFFFF) shl 42) or ((qy.toLong() and 0x1FFFFF) shl 21) or (qz.toLong() and 0x1FFFFF)

            return vertexMap.getOrPut(key) {
                val index = vertsList.size / 3
                vertsList.add(x)
                vertsList.add(y)
                vertsList.add(z)
                index
            }
        }

        for (face in faceDirs) {
            val right = face[0]
            val up = face[1]
            val normal = face[2]

            for (j in 0 until n) {
                for (i in 0 until n) {
                    val u0 = -half + (2f * half * i / n)
                    val u1 = -half + (2f * half * (i + 1) / n)
                    val v0 = -half + (2f * half * j / n)
                    val v1 = -half + (2f * half * (j + 1) / n)

                    val p00 = normal * half + (right * (u0 / half)) + (up * (v0 / half))
                    val p10 = normal * half + (right * (u1 / half)) + (up * (v0 / half))
                    val p11 = normal * half + (right * (u1 / half)) + (up * (v1 / half))
                    val p01 = normal * half + (right * (u0 / half)) + (up * (v1 / half))

                    val idx00 = getOrAddVertex(p00.x, p00.y, p00.z)
                    val idx10 = getOrAddVertex(p10.x, p10.y, p10.z)
                    val idx11 = getOrAddVertex(p11.x, p11.y, p11.z)
                    val idx01 = getOrAddVertex(p01.x, p01.y, p01.z)

                    indsList.add(idx00)
                    indsList.add(idx10)
                    indsList.add(idx11)

                    indsList.add(idx00)
                    indsList.add(idx11)
                    indsList.add(idx01)
                }
            }
        }

        return SculptMesh(
            vertices = vertsList.toFloatArray(),
            indices = indsList.toIntArray()
        )
    }

    /**
     * Creates a cylinder mesh with caps.
     */
    fun createCylinder(radius: Float = 0.75f, height: Float = 1.6f, radSegments: Int = 28, heightSegments: Int = 20): SculptMesh {
        val verts = ArrayList<Float>()
        val inds = ArrayList<Int>()
        val halfH = height * 0.5f

        for (yIdx in 0..heightSegments) {
            val y = -halfH + height * (yIdx.toFloat() / heightSegments)
            for (rIdx in 0 until radSegments) {
                val angle = (2.0 * PI * rIdx / radSegments).toFloat()
                val x = radius * cos(angle)
                val z = radius * sin(angle)
                verts.add(x); verts.add(y); verts.add(z)
            }
        }

        // Side faces
        for (yIdx in 0 until heightSegments) {
            val ring0 = yIdx * radSegments
            val ring1 = (yIdx + 1) * radSegments
            for (rIdx in 0 until radSegments) {
                val nextR = (rIdx + 1) % radSegments
                val i00 = ring0 + rIdx
                val i10 = ring0 + nextR
                val i11 = ring1 + nextR
                val i01 = ring1 + rIdx

                inds.add(i00); inds.add(i10); inds.add(i11)
                inds.add(i00); inds.add(i11); inds.add(i01)
            }
        }

        // Top and bottom center verts
        val topCenterIdx = verts.size / 3
        verts.add(0f); verts.add(halfH); verts.add(0f)
        val botCenterIdx = verts.size / 3
        verts.add(0f); verts.add(-halfH); verts.add(0f)

        val topRing = heightSegments * radSegments
        val botRing = 0

        for (rIdx in 0 until radSegments) {
            val nextR = (rIdx + 1) % radSegments
            // Top cap
            inds.add(topCenterIdx)
            inds.add(topRing + rIdx)
            inds.add(topRing + nextR)
            // Bottom cap
            inds.add(botCenterIdx)
            inds.add(botRing + nextR)
            inds.add(botRing + rIdx)
        }

        return SculptMesh(verts.toFloatArray(), inds.toIntArray())
    }

    /**
     * Creates a torus mesh.
     */
    fun createTorus(radiusOut: Float = 0.9f, tubeRadius: Float = 0.35f, radialSegs: Int = 24, tubularSegs: Int = 40): SculptMesh {
        val verts = ArrayList<Float>()
        val inds = ArrayList<Int>()

        for (i in 0 until tubularSegs) {
            val u = (2.0 * PI * i / tubularSegs).toFloat()
            val cosU = cos(u)
            val sinU = sin(u)

            for (j in 0 until radialSegs) {
                val v = (2.0 * PI * j / radialSegs).toFloat()
                val cosV = cos(v)
                val sinV = sin(v)

                val x = (radiusOut + tubeRadius * cosV) * cosU
                val y = tubeRadius * sinV
                val z = (radiusOut + tubeRadius * cosV) * sinU

                verts.add(x); verts.add(y); verts.add(z)
            }
        }

        for (i in 0 until tubularSegs) {
            val nextI = (i + 1) % tubularSegs
            val ring0 = i * radialSegs
            val ring1 = nextI * radialSegs

            for (j in 0 until radialSegs) {
                val nextJ = (j + 1) % radialSegs
                val i00 = ring0 + j
                val i10 = ring0 + nextJ
                val i11 = ring1 + nextJ
                val i01 = ring1 + j

                inds.add(i00); inds.add(i10); inds.add(i11)
                inds.add(i00); inds.add(i11); inds.add(i01)
            }
        }

        return SculptMesh(verts.toFloatArray(), inds.toIntArray())
    }

    /**
     * Creates a plane mesh for relief sculpting.
     */
    fun createPlane(size: Float = 1.8f, subdivisions: Int = 30): SculptMesh {
        val n = subdivisions
        val half = size * 0.5f
        val verts = ArrayList<Float>()
        val inds = ArrayList<Int>()

        for (j in 0..n) {
            val z = -half + size * (j.toFloat() / n)
            for (i in 0..n) {
                val x = -half + size * (i.toFloat() / n)
                verts.add(x); verts.add(0f); verts.add(z)
            }
        }

        val rowSize = n + 1
        for (j in 0 until n) {
            for (i in 0 until n) {
                val i00 = j * rowSize + i
                val i10 = j * rowSize + (i + 1)
                val i11 = (j + 1) * rowSize + (i + 1)
                val i01 = (j + 1) * rowSize + i

                inds.add(i00); inds.add(i11); inds.add(i10)
                inds.add(i00); inds.add(i01); inds.add(i11)
            }
        }

        return SculptMesh(verts.toFloatArray(), inds.toIntArray())
    }
}
