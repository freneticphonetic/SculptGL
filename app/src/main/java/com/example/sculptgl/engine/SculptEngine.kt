package com.example.sculptgl.engine

import com.example.sculptgl.model.HitResult
import com.example.sculptgl.model.SculptMesh
import com.example.sculptgl.model.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SculptEngine {

    var currentTool: SculptTool = SculptTool.BRUSH
    var radius: Float = 0.35f
    var intensity: Float = 0.5f
    var isNegative: Boolean = false // false = add/build, true = sub/carve
    var symmetryX: Boolean = true

    // Paint tool color (RGB in 0..1)
    var paintColor = Vec3(0.9f, 0.35f, 0.2f)

    // Planar brush plane cache
    private var planarOrigin = Vec3()
    private var planarNormal = Vec3()
    private var planarCutPoint = Vec3()

    /**
     * Planar Brush Reference Plane:
     * When you touch the mesh, sample the surrounding geometry within the brush radius
     * to calculate the reference average plane with position and orientation in 3D space.
     * Subtract (-) mode cuts a plane INTO the material by depth.
     * Add (+) mode builds a flat plateau plane OUTWARD from the material by depth.
     * The orientation is locked for the entire stroke.
     */
    fun beginStroke(mesh: SculptMesh, hit: HitResult) {
        val radSq = radius * radius
        val vertices = mesh.vertices
        val normals = mesh.normals
        val vertexCount = mesh.vertexCount

        var avgNx = 0f; var avgNy = 0f; var avgNz = 0f
        var count = 0

        for (i in 0 until vertexCount) {
            val dx = vertices[i * 3] - hit.point.x
            val dy = vertices[i * 3 + 1] - hit.point.y
            val dz = vertices[i * 3 + 2] - hit.point.z
            if (dx * dx + dy * dy + dz * dz < radSq) {
                avgNx += normals[i * 3]
                avgNy += normals[i * 3 + 1]
                avgNz += normals[i * 3 + 2]
                count++
            }
        }

        if (count > 0) {
            val nLen = sqrt(avgNx * avgNx + avgNy * avgNy + avgNz * avgNz)
            if (nLen > 1e-5f) {
                planarNormal.set(avgNx / nLen, avgNy / nLen, avgNz / nLen)
            } else {
                planarNormal.set(hit.normal).normalize()
            }
        } else {
            planarNormal.set(hit.normal).normalize()
        }

        if (planarNormal.dot(hit.normal) < 0f) {
            planarNormal.set(-planarNormal.x, -planarNormal.y, -planarNormal.z)
        }
        planarOrigin.set(hit.point)
    }

    /**
     * Applies sculpting deformation at hit location.
     */
    fun sculpt(mesh: SculptMesh, hit: HitResult, screenDragDelta: Vec3? = null) {
        applyTool(mesh, hit.point, hit.normal, screenDragDelta, isSymmetric = false)
        if (symmetryX) {
            val symPoint = Vec3(-hit.point.x, hit.point.y, hit.point.z)
            val symNormal = Vec3(-hit.normal.x, hit.normal.y, hit.normal.z)
            val symDrag = screenDragDelta?.let { Vec3(-it.x, it.y, it.z) }
            applyTool(mesh, symPoint, symNormal, symDrag, isSymmetric = true)
        }
    }

    private fun applyTool(mesh: SculptMesh, center: Vec3, normal: Vec3, dragDelta: Vec3?, isSymmetric: Boolean = false) {
        val radSq = radius * radius
        val dirSign = if (isNegative) -1f else 1f
        val vertices = mesh.vertices
        val normals = mesh.normals
        val colors = mesh.colors
        val masks = mesh.masks
        val neighbors = mesh.neighbors
        val vertexCount = mesh.vertexCount

        val affectedVertices = ArrayList<Int>()

        // 1. Gather affected vertices
        for (i in 0 until vertexCount) {
            val vx = vertices[i * 3]
            val vy = vertices[i * 3 + 1]
            val vz = vertices[i * 3 + 2]

            val dx = vx - center.x
            val dy = vy - center.y
            val dz = vz - center.z
            val distSq = dx * dx + dy * dy + dz * dz

            if (distSq < radSq) {
                affectedVertices.add(i)
            }
        }

        if (affectedVertices.isEmpty()) return

        // 2. Perform tool-specific deformation
        when (currentTool) {
            SculptTool.BRUSH -> {
                val offset = normal * (radius * 0.22f * intensity * dirSign)
                for (idx in affectedVertices) {
                    val falloff = computeFalloff(mesh, idx, center, radSq)
                    if (falloff <= 0f) continue
                    vertices[idx * 3] += offset.x * falloff
                    vertices[idx * 3 + 1] += offset.y * falloff
                    vertices[idx * 3 + 2] += offset.z * falloff
                }
            }

            SculptTool.PLANAR -> {
                // Nomad Sculpt Planar Brush:
                // 1. Locked plane normal (orientation) established at stroke start.
                // 2. Each dab evaluates local surface height under the brush along the locked normal,
                //    progressively ironing out grooves and ridges into a broad, continuous angled planar surface.
                val activeNorm = if (isSymmetric) Vec3(-planarNormal.x, planarNormal.y, planarNormal.z) else planarNormal

                // Calculate local average position of vertices under the brush
                var avgPx = 0f; var avgPy = 0f; var avgPz = 0f
                for (idx in affectedVertices) {
                    avgPx += vertices[idx * 3]
                    avgPy += vertices[idx * 3 + 1]
                    avgPz += vertices[idx * 3 + 2]
                }
                val invCount = 1f / affectedVertices.size
                avgPx *= invCount; avgPy *= invCount; avgPz *= invCount

                val depth = radius * 0.12f * intensity
                val pCutX = if (isNegative) avgPx - activeNorm.x * depth else avgPx + activeNorm.x * depth
                val pCutY = if (isNegative) avgPy - activeNorm.y * depth else avgPy + activeNorm.y * depth
                val pCutZ = if (isNegative) avgPz - activeNorm.z * depth else avgPz + activeNorm.z * depth

                for (idx in affectedVertices) {
                    val vx = vertices[idx * 3]
                    val vy = vertices[idx * 3 + 1]
                    val vz = vertices[idx * 3 + 2]

                    // Distance along the locked normal to the dab plane:
                    val d = (vx - pCutX) * activeNorm.x +
                            (vy - pCutY) * activeNorm.y +
                            (vz - pCutZ) * activeNorm.z

                    val dx = vx - center.x
                    val dy = vy - center.y
                    val dz = vz - center.z
                    val distSq = dx * dx + dy * dy + dz * dz

                    val r = sqrt(distSq / radSq)
                    val falloff = (1f - r * r).coerceIn(0f, 1f)
                    val maskVal = mesh.masks?.get(idx) ?: 0f
                    val factor = (falloff * intensity * 0.9f * (1f - maskVal)).coerceIn(0f, 1f)

                    val shift = if (isNegative) {
                        // Trim: push high material above the dab plane inward
                        if (d > 0f) d * factor else 0f
                    } else {
                        // Build: pull low material below the dab plane outward
                        if (d < 0f) d * factor else 0f
                    }

                    vertices[idx * 3] -= activeNorm.x * shift
                    vertices[idx * 3 + 1] -= activeNorm.y * shift
                    vertices[idx * 3 + 2] -= activeNorm.z * shift
                }
            }

            SculptTool.INFLATE -> {
                val scale = radius * 0.18f * intensity * dirSign
                for (idx in affectedVertices) {
                    val falloff = computeFalloff(mesh, idx, center, radSq)
                    if (falloff <= 0f) continue
                    val nx = normals[idx * 3]
                    val ny = normals[idx * 3 + 1]
                    val nz = normals[idx * 3 + 2]
                    vertices[idx * 3] += nx * scale * falloff
                    vertices[idx * 3 + 1] += ny * scale * falloff
                    vertices[idx * 3 + 2] += nz * scale * falloff
                }
            }

            SculptTool.TWIST -> {
                val axis = normal.normalized()
                for (idx in affectedVertices) {
                    val falloff = computeFalloff(mesh, idx, center, radSq)
                    if (falloff <= 0f) continue
                    val angle = falloff * intensity * 0.4f * dirSign
                    val p = Vec3(vertices[idx * 3] - center.x, vertices[idx * 3 + 1] - center.y, vertices[idx * 3 + 2] - center.z)
                    // Rodrigues rotation formula
                    val cosA = cos(angle)
                    val sinA = sin(angle)
                    val dot = p.dot(axis)
                    val cross = axis.cross(p)
                    val rotP = p * cosA + cross * sinA + axis * (dot * (1f - cosA))

                    vertices[idx * 3] = center.x + rotP.x
                    vertices[idx * 3 + 1] = center.y + rotP.y
                    vertices[idx * 3 + 2] = center.z + rotP.z
                }
            }

            SculptTool.SMOOTH -> {
                // Laplacian smoothing: relaxes vertices toward adjacent neighbors
                val newPos = FloatArray(affectedVertices.size * 3)
                for (i in affectedVertices.indices) {
                    val idx = affectedVertices[i]
                    val falloff = computeFalloff(mesh, idx, center, radSq)
                    val nbs = neighbors[idx]

                    if (falloff <= 0f || nbs.isEmpty()) {
                        newPos[i * 3] = vertices[idx * 3]
                        newPos[i * 3 + 1] = vertices[idx * 3 + 1]
                        newPos[i * 3 + 2] = vertices[idx * 3 + 2]
                        continue
                    }

                    var avgX = 0f; var avgY = 0f; var avgZ = 0f
                    for (nb in nbs) {
                        avgX += vertices[nb * 3]
                        avgY += vertices[nb * 3 + 1]
                        avgZ += vertices[nb * 3 + 2]
                    }
                    val invCount = 1.0f / nbs.size
                    avgX *= invCount; avgY *= invCount; avgZ *= invCount

                    val factor = falloff * intensity * 0.7f
                    newPos[i * 3] = vertices[idx * 3] + (avgX - vertices[idx * 3]) * factor
                    newPos[i * 3 + 1] = vertices[idx * 3 + 1] + (avgY - vertices[idx * 3 + 1]) * factor
                    newPos[i * 3 + 2] = vertices[idx * 3 + 2] + (avgZ - vertices[idx * 3 + 2]) * factor
                }

                for (i in affectedVertices.indices) {
                    val idx = affectedVertices[i]
                    vertices[idx * 3] = newPos[i * 3]
                    vertices[idx * 3 + 1] = newPos[i * 3 + 1]
                    vertices[idx * 3 + 2] = newPos[i * 3 + 2]
                }
            }

            SculptTool.FLATTEN -> {
                // Average position and normal of affected vertices
                var avgPx = 0f; var avgPy = 0f; var avgPz = 0f
                var avgNx = 0f; var avgNy = 0f; var avgNz = 0f
                for (idx in affectedVertices) {
                    avgPx += vertices[idx * 3]; avgPy += vertices[idx * 3 + 1]; avgPz += vertices[idx * 3 + 2]
                    avgNx += normals[idx * 3]; avgNy += normals[idx * 3 + 1]; avgNz += normals[idx * 3 + 2]
                }
                val invSize = 1f / affectedVertices.size
                avgPx *= invSize; avgPy *= invSize; avgPz *= invSize
                val normLen = sqrt(avgNx * avgNx + avgNy * avgNy + avgNz * avgNz)
                if (normLen > 1e-5f) {
                    avgNx /= normLen; avgNy /= normLen; avgNz /= normLen
                } else {
                    avgNx = normal.x; avgNy = normal.y; avgNz = normal.z
                }

                for (idx in affectedVertices) {
                    val falloff = computeFalloff(mesh, idx, center, radSq)
                    if (falloff <= 0f) continue
                    val vx = vertices[idx * 3]; val vy = vertices[idx * 3 + 1]; val vz = vertices[idx * 3 + 2]
                    val d = (vx - avgPx) * avgNx + (vy - avgPy) * avgNy + (vz - avgPz) * avgNz
                    val factor = falloff * intensity * 0.7f
                    vertices[idx * 3] -= avgNx * d * factor
                    vertices[idx * 3 + 1] -= avgNy * d * factor
                    vertices[idx * 3 + 2] -= avgNz * d * factor
                }
            }

            SculptTool.PINCH -> {
                for (idx in affectedVertices) {
                    val falloff = computeFalloff(mesh, idx, center, radSq)
                    if (falloff <= 0f) continue
                    val vx = vertices[idx * 3]; val vy = vertices[idx * 3 + 1]; val vz = vertices[idx * 3 + 2]
                    val factor = falloff * intensity * 0.35f * dirSign
                    vertices[idx * 3] += (center.x - vx) * factor
                    vertices[idx * 3 + 1] += (center.y - vy) * factor
                    vertices[idx * 3 + 2] += (center.z - vz) * factor
                }
            }

            SculptTool.CREASE -> {
                // Pinches towards center while carving deep groove along normal
                val carve = normal * (radius * 0.2f * intensity * dirSign)
                for (idx in affectedVertices) {
                    val falloff = computeFalloff(mesh, idx, center, radSq)
                    if (falloff <= 0f) continue
                    val vx = vertices[idx * 3]; val vy = vertices[idx * 3 + 1]; val vz = vertices[idx * 3 + 2]
                    val pinchFactor = falloff * intensity * 0.3f
                    vertices[idx * 3] += (center.x - vx) * pinchFactor - carve.x * falloff
                    vertices[idx * 3 + 1] += (center.y - vy) * pinchFactor - carve.y * falloff
                    vertices[idx * 3 + 2] += (center.z - vz) * pinchFactor - carve.z * falloff
                }
            }

            SculptTool.DRAG, SculptTool.MOVE -> {
                val delta = dragDelta ?: Vec3()
                val moveFactor = if (currentTool == SculptTool.MOVE) 1.2f else 0.8f
                for (idx in affectedVertices) {
                    val falloff = computeFalloff(mesh, idx, center, radSq)
                    if (falloff <= 0f) continue
                    val factor = falloff * intensity * moveFactor
                    vertices[idx * 3] += delta.x * factor
                    vertices[idx * 3 + 1] += delta.y * factor
                    vertices[idx * 3 + 2] += delta.z * factor
                }
            }

            SculptTool.PAINT -> {
                if (colors != null) {
                    for (idx in affectedVertices) {
                        val falloff = computeFalloff(mesh, idx, center, radSq)
                        if (falloff <= 0f) continue
                        val factor = falloff * intensity
                        colors[idx * 3] += (paintColor.x - colors[idx * 3]) * factor
                        colors[idx * 3 + 1] += (paintColor.y - colors[idx * 3 + 1]) * factor
                        colors[idx * 3 + 2] += (paintColor.z - colors[idx * 3 + 2]) * factor
                    }
                }
            }

            SculptTool.MASK -> {
                if (masks != null) {
                    for (idx in affectedVertices) {
                        val dSq = distanceSq(vertices, idx, center)
                        val r = sqrt(dSq / radSq)
                        val falloff = if (r < 1f) (1f - r * r).let { it * it } else 0f
                        val delta = falloff * intensity * dirSign * 0.25f
                        masks[idx] = (masks[idx] + delta).coerceIn(0f, 1f)
                    }
                }
            }
        }

        // Recompute normals for affected triangles
        recomputeAffectedNormals(mesh, affectedVertices)
        mesh.computeBounding()
    }

    private fun computeFalloff(mesh: SculptMesh, vIdx: Int, center: Vec3, radSq: Float): Float {
        val dSq = distanceSq(mesh.vertices, vIdx, center)
        if (dSq >= radSq) return 0f
        val r = sqrt(dSq / radSq)
        // Smooth cubic bell-curve
        val poly = 1f - r * r
        val falloff = poly * poly * poly
        val maskVal = mesh.masks?.get(vIdx) ?: 0f
        return falloff * (1f - maskVal)
    }

    private fun distanceSq(vertices: FloatArray, vIdx: Int, center: Vec3): Float {
        val dx = vertices[vIdx * 3] - center.x
        val dy = vertices[vIdx * 3 + 1] - center.y
        val dz = vertices[vIdx * 3 + 2] - center.z
        return dx * dx + dy * dy + dz * dz
    }

    private fun recomputeAffectedNormals(mesh: SculptMesh, affectedVertices: List<Int>) {
        val touchedTris = HashSet<Int>()
        for (v in affectedVertices) {
            val tris = mesh.vertexTriangles[v]
            for (t in tris) touchedTris.add(t)
        }

        val touchedVerts = HashSet<Int>()
        for (t in touchedTris) {
            touchedVerts.add(mesh.indices[t * 3])
            touchedVerts.add(mesh.indices[t * 3 + 1])
            touchedVerts.add(mesh.indices[t * 3 + 2])
        }

        // Zero out normals of touched vertices
        for (v in touchedVerts) {
            mesh.normals[v * 3] = 0f
            mesh.normals[v * 3 + 1] = 0f
            mesh.normals[v * 3 + 2] = 0f
        }

        // Accumulate normals from all triangles sharing touched vertices
        for (v in touchedVerts) {
            val tris = mesh.vertexTriangles[v]
            for (t in tris) {
                val i0 = mesh.indices[t * 3]
                val i1 = mesh.indices[t * 3 + 1]
                val i2 = mesh.indices[t * 3 + 2]

                val v0x = mesh.vertices[i0 * 3]; val v0y = mesh.vertices[i0 * 3 + 1]; val v0z = mesh.vertices[i0 * 3 + 2]
                val v1x = mesh.vertices[i1 * 3]; val v1y = mesh.vertices[i1 * 3 + 1]; val v1z = mesh.vertices[i1 * 3 + 2]
                val v2x = mesh.vertices[i2 * 3]; val v2y = mesh.vertices[i2 * 3 + 1]; val v2z = mesh.vertices[i2 * 3 + 2]

                val e1x = v1x - v0x; val e1y = v1y - v0y; val e1z = v1z - v0z
                val e2x = v2x - v0x; val e2y = v2y - v0y; val e2z = v2z - v0z

                val nx = e1y * e2z - e1z * e2y
                val ny = e1z * e2x - e1x * e2z
                val nz = e1x * e2y - e1y * e2x

                mesh.normals[v * 3] += nx
                mesh.normals[v * 3 + 1] += ny
                mesh.normals[v * 3 + 2] += nz
            }

            val nx = mesh.normals[v * 3]
            val ny = mesh.normals[v * 3 + 1]
            val nz = mesh.normals[v * 3 + 2]
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 1e-6f) {
                mesh.normals[v * 3] = nx / len
                mesh.normals[v * 3 + 1] = ny / len
                mesh.normals[v * 3 + 2] = nz / len
            } else {
                mesh.normals[v * 3 + 1] = 1f
            }
        }
    }
}
