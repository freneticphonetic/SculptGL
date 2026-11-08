package com.example.sculptgl.model

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class HitResult(
    val distance: Float,
    val point: Vec3,
    val normal: Vec3,
    val triangleIndex: Int
)

class SculptMesh(
    var vertices: FloatArray,
    var indices: IntArray,
    var colors: FloatArray? = null,
    var masks: FloatArray? = null
) {
    val vertexCount: Int get() = vertices.size / 3
    val triangleCount: Int get() = indices.size / 3

    var normals: FloatArray = FloatArray(vertices.size)
    var neighbors: Array<IntArray> = emptyArray()
    var vertexTriangles: Array<IntArray> = emptyArray()

    // Bounding info
    val center = Vec3(0f, 0f, 0f)
    var radius: Float = 1.0f

    init {
        if (colors == null || colors!!.size != vertices.size) {
            colors = FloatArray(vertices.size) { 1.0f } // default white
        }
        if (masks == null || masks!!.size != vertexCount) {
            masks = FloatArray(vertexCount) { 0.0f } // default unmasked
        }
        rebuildTopology()
        computeNormals()
        computeBounding()
    }

    fun rebuildTopology() {
        val count = vertexCount
        val triCount = triangleCount
        val neighborSets = Array(count) { HashSet<Int>() }
        val vertTriLists = Array(count) { ArrayList<Int>() }

        for (t in 0 until triCount) {
            val i0 = indices[t * 3]
            val i1 = indices[t * 3 + 1]
            val i2 = indices[t * 3 + 2]

            vertTriLists[i0].add(t)
            vertTriLists[i1].add(t)
            vertTriLists[i2].add(t)

            neighborSets[i0].add(i1)
            neighborSets[i0].add(i2)

            neighborSets[i1].add(i0)
            neighborSets[i1].add(i2)

            neighborSets[i2].add(i0)
            neighborSets[i2].add(i1)
        }

        neighbors = Array(count) { i -> neighborSets[i].toIntArray() }
        vertexTriangles = Array(count) { i -> vertTriLists[i].toIntArray() }
    }

    fun computeBounding() {
        if (vertexCount == 0) return
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        for (i in 0 until vertexCount) {
            val x = vertices[i * 3]
            val y = vertices[i * 3 + 1]
            val z = vertices[i * 3 + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }

        center.set((minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f)
        var maxDistSq = 0f
        for (i in 0 until vertexCount) {
            val dx = vertices[i * 3] - center.x
            val dy = vertices[i * 3 + 1] - center.y
            val dz = vertices[i * 3 + 2] - center.z
            val dSq = dx * dx + dy * dy + dz * dz
            if (dSq > maxDistSq) maxDistSq = dSq
        }
        radius = sqrt(maxDistSq) * 1.05f
    }

    fun computeNormals() {
        if (normals.size != vertices.size) {
            normals = FloatArray(vertices.size)
        } else {
            normals.fill(0f)
        }

        val triCount = triangleCount
        for (t in 0 until triCount) {
            val i0 = indices[t * 3]
            val i1 = indices[t * 3 + 1]
            val i2 = indices[t * 3 + 2]

            val v0x = vertices[i0 * 3]; val v0y = vertices[i0 * 3 + 1]; val v0z = vertices[i0 * 3 + 2]
            val v1x = vertices[i1 * 3]; val v1y = vertices[i1 * 3 + 1]; val v1z = vertices[i1 * 3 + 2]
            val v2x = vertices[i2 * 3]; val v2y = vertices[i2 * 3 + 1]; val v2z = vertices[i2 * 3 + 2]

            val e1x = v1x - v0x; val e1y = v1y - v0y; val e1z = v1z - v0z
            val e2x = v2x - v0x; val e2y = v2y - v0y; val e2z = v2z - v0z

            // Cross product
            val nx = e1y * e2z - e1z * e2y
            val ny = e1z * e2x - e1x * e2z
            val nz = e1x * e2y - e1y * e2x

            normals[i0 * 3] += nx; normals[i0 * 3 + 1] += ny; normals[i0 * 3 + 2] += nz
            normals[i1 * 3] += nx; normals[i1 * 3 + 1] += ny; normals[i1 * 3 + 2] += nz
            normals[i2 * 3] += nx; normals[i2 * 3 + 1] += ny; normals[i2 * 3 + 2] += nz
        }

        for (i in 0 until vertexCount) {
            val nx = normals[i * 3]
            val ny = normals[i * 3 + 1]
            val nz = normals[i * 3 + 2]
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 1e-6f) {
                normals[i * 3] = nx / len
                normals[i * 3 + 1] = ny / len
                normals[i * 3 + 2] = nz / len
            } else {
                normals[i * 3] = 0f
                normals[i * 3 + 1] = 1f
                normals[i * 3 + 2] = 0f
            }
        }
    }

    fun pick(ray: Ray): HitResult? {
        // Quick bounding sphere reject
        if (ray.intersectSphere(center, radius) == null) {
            return null
        }

        var closestT = Float.MAX_VALUE
        var hitTri = -1
        val v0 = Vec3(); val v1 = Vec3(); val v2 = Vec3()

        val triCount = triangleCount
        for (t in 0 until triCount) {
            val i0 = indices[t * 3]
            val i1 = indices[t * 3 + 1]
            val i2 = indices[t * 3 + 2]

            v0.set(vertices[i0 * 3], vertices[i0 * 3 + 1], vertices[i0 * 3 + 2])
            v1.set(vertices[i1 * 3], vertices[i1 * 3 + 1], vertices[i1 * 3 + 2])
            v2.set(vertices[i2 * 3], vertices[i2 * 3 + 1], vertices[i2 * 3 + 2])

            val dist = ray.intersectTriangle(v0, v1, v2)
            if (dist != null && dist < closestT) {
                closestT = dist
                hitTri = t
            }
        }

        if (hitTri >= 0) {
            val hitPt = ray.pointAt(closestT)
            val i0 = indices[hitTri * 3]
            val i1 = indices[hitTri * 3 + 1]
            val i2 = indices[hitTri * 3 + 2]

            val avgNx = (normals[i0 * 3] + normals[i1 * 3] + normals[i2 * 3]) / 3f
            val avgNy = (normals[i0 * 3 + 1] + normals[i1 * 3 + 1] + normals[i2 * 3 + 1]) / 3f
            val avgNz = (normals[i0 * 3 + 2] + normals[i1 * 3 + 2] + normals[i2 * 3 + 2]) / 3f
            val hitNorm = Vec3(avgNx, avgNy, avgNz).normalized()

            return HitResult(closestT, hitPt, hitNorm, hitTri)
        }
        return null
    }

    fun copy(): SculptMesh {
        val mesh = SculptMesh(
            vertices = vertices.clone(),
            indices = indices.clone(),
            colors = colors?.clone(),
            masks = masks?.clone()
        )
        mesh.normals = normals.clone()
        mesh.neighbors = neighbors
        mesh.vertexTriangles = vertexTriangles
        mesh.center.set(center)
        mesh.radius = radius
        return mesh
    }

    fun exportObj(): String {
        val sb = StringBuilder()
        sb.append("# SculptGL 3D OBJ Export\n")
        sb.append("# Vertices: $vertexCount, Triangles: $triangleCount\n")
        for (i in 0 until vertexCount) {
            sb.append("v ")
                .append(vertices[i * 3]).append(" ")
                .append(vertices[i * 3 + 1]).append(" ")
                .append(vertices[i * 3 + 2])
            colors?.let { c ->
                sb.append(" ").append(c[i * 3]).append(" ")
                    .append(c[i * 3 + 1]).append(" ")
                    .append(c[i * 3 + 2])
            }
            sb.append("\n")
        }
        for (i in 0 until vertexCount) {
            sb.append("vn ")
                .append(normals[i * 3]).append(" ")
                .append(normals[i * 3 + 1]).append(" ")
                .append(normals[i * 3 + 2]).append("\n")
        }
        for (t in 0 until triangleCount) {
            val i0 = indices[t * 3] + 1
            val i1 = indices[t * 3 + 1] + 1
            val i2 = indices[t * 3 + 2] + 1
            sb.append("f ").append(i0).append("//").append(i0).append(" ")
                .append(i1).append("//").append(i1).append(" ")
                .append(i2).append("//").append(i2).append("\n")
        }
        return sb.toString()
    }

    fun exportStlBinary(): ByteArray {
        val bos = ByteArrayOutputStream()
        val header = ByteArray(80)
        "SculptGL 3D Binary STL Export".toByteArray().copyInto(header, 0, 0, Math.min(80, 29))
        bos.write(header)

        val triCount = triangleCount
        val countBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        countBuffer.putInt(triCount)
        bos.write(countBuffer.array())

        val triBuffer = ByteBuffer.allocate(50 * triCount).order(ByteOrder.LITTLE_ENDIAN)
        for (t in 0 until triCount) {
            val i0 = indices[t * 3]
            val i1 = indices[t * 3 + 1]
            val i2 = indices[t * 3 + 2]

            val avgNx = (normals[i0 * 3] + normals[i1 * 3] + normals[i2 * 3]) / 3f
            val avgNy = (normals[i0 * 3 + 1] + normals[i1 * 3 + 1] + normals[i2 * 3 + 1]) / 3f
            val avgNz = (normals[i0 * 3 + 2] + normals[i1 * 3 + 2] + normals[i2 * 3 + 2]) / 3f

            triBuffer.putFloat(avgNx)
            triBuffer.putFloat(avgNy)
            triBuffer.putFloat(avgNz)

            triBuffer.putFloat(vertices[i0 * 3]); triBuffer.putFloat(vertices[i0 * 3 + 1]); triBuffer.putFloat(vertices[i0 * 3 + 2])
            triBuffer.putFloat(vertices[i1 * 3]); triBuffer.putFloat(vertices[i1 * 3 + 1]); triBuffer.putFloat(vertices[i1 * 3 + 2])
            triBuffer.putFloat(vertices[i2 * 3]); triBuffer.putFloat(vertices[i2 * 3 + 1]); triBuffer.putFloat(vertices[i2 * 3 + 2])

            triBuffer.putShort(0) // attribute byte count
        }
        bos.write(triBuffer.array())
        return bos.toByteArray()
    }
}
