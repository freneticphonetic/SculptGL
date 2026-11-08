package com.example.sculptgl.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.sculptgl.model.HitResult
import com.example.sculptgl.model.SculptMesh
import com.example.sculptgl.model.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class ShadingMode(val displayName: String) {
    CLAY_MATCAP("Clay"),
    RED_CLAY_MATCAP("Red Clay"),
    PEARL_MATCAP("Pearl"),
    GREEN_MATCAP("Green"),
    SKIN_MATCAP("Skin"),
    WHITE_MATCAP("White"),
    PBR("Studio Light"),
    NORMALS("Normals")
}

class SculptRenderer(private val context: Context) : GLSurfaceView.Renderer {

    val camera = SculptCamera()
    var mesh: SculptMesh? = null
        set(value) {
            field = value
            needsBufferRebuild = true
        }

    var shadingMode: ShadingMode = ShadingMode.CLAY_MATCAP
    var showWireframe: Boolean = false
    var showGrid: Boolean = true
    var showCursor: Boolean = true

    // Interactive cursor state
    var currentHit: HitResult? = null
    var cursorRadius: Float = 0.35f
    var symmetryX: Boolean = true

    @Volatile
    var needsMeshUpdate: Boolean = false
    @Volatile
    private var needsBufferRebuild: Boolean = true

    // OpenGL Buffers
    private var vertexBufferId = 0
    private var normalBufferId = 0
    private var colorBufferId = 0
    private var indexBufferId = 0
    private var wireframeBufferId = 0

    private var vertexCount = 0
    private var triangleCount = 0
    private var wireframeIndexCount = 0

    // Grid buffers
    private var gridVboId = 0
    private var gridVertexCount = 0

    // Cursor ring buffer
    private var cursorVboId = 0
    private val cursorRingSegments = 48

    // Shaders
    private var meshShaderProgram = 0
    private var flatShaderProgram = 0

    // Textures
    private val matcapTextures = HashMap<ShadingMode, Int>()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.11f, 0.12f, 0.14f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        initShaders()
        loadMatcapTextures()
        initGrid()
        initCursorRing()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        camera.setViewport(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val currentMesh = mesh ?: return

        if (needsBufferRebuild) {
            rebuildMeshBuffers(currentMesh)
            needsBufferRebuild = false
            needsMeshUpdate = false
        } else if (needsMeshUpdate) {
            updateMeshBuffers(currentMesh)
            needsMeshUpdate = false
        }

        // Draw grid
        if (showGrid) {
            drawGrid()
        }

        // Draw mesh
        drawMesh(currentMesh)

        // Draw wireframe
        if (showWireframe) {
            drawWireframe()
        }

        // Draw brush cursor ring
        val hit = currentHit
        if (showCursor && hit != null) {
            drawCursor(hit.point, hit.normal)
            if (symmetryX) {
                val symPt = Vec3(-hit.point.x, hit.point.y, hit.point.z)
                val symNorm = Vec3(-hit.normal.x, hit.normal.y, hit.normal.z)
                drawCursor(symPt, symNorm)
            }
        }
    }

    private fun initShaders() {
        val meshVS = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uMVMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            varying vec3 vNormal;
            varying vec3 vViewNormal;
            varying vec3 vColor;
            varying vec3 vPosition;
            void main() {
                vPosition = aPosition;
                vNormal = aNormal;
                vColor = aColor;
                vViewNormal = normalize((uMVMatrix * vec4(aNormal, 0.0)).xyz);
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        val meshFS = """
            precision mediump float;
            uniform int uShadingMode;
            uniform sampler2D uMatcapTexture;
            uniform vec3 uLightDir;
            varying vec3 vNormal;
            varying vec3 vViewNormal;
            varying vec3 vColor;
            varying vec3 vPosition;
            void main() {
                if (uShadingMode == 0) { // Matcap
                    vec2 uv = vViewNormal.xy * 0.495 + 0.5;
                    uv.y = 1.0 - uv.y;
                    vec4 matcap = texture2D(uMatcapTexture, uv);
                    gl_FragColor = vec4(matcap.rgb * vColor, 1.0);
                } else if (uShadingMode == 1) { // Studio Light
                    vec3 N = normalize(vNormal);
                    vec3 L = normalize(uLightDir);
                    float diff = max(dot(N, L), 0.0);
                    float ambient = 0.3;
                    vec3 V = normalize(vec3(0.0, 0.0, 1.0));
                    vec3 H = normalize(L + V);
                    float spec = pow(max(dot(N, H), 0.0), 32.0) * 0.4;
                    vec3 lit = vColor * (ambient + diff * 0.7) + vec3(spec);
                    gl_FragColor = vec4(lit, 1.0);
                } else if (uShadingMode == 2) { // Normals
                    gl_FragColor = vec4(normalize(vNormal) * 0.5 + 0.5, 1.0);
                } else {
                    gl_FragColor = vec4(vColor, 1.0);
                }
            }
        """.trimIndent()

        meshShaderProgram = createProgram(meshVS, meshFS)

        val flatVS = """
            uniform mat4 uMVPMatrix;
            attribute vec3 aPosition;
            void main() {
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        val flatFS = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """.trimIndent()

        flatShaderProgram = createProgram(flatVS, flatFS)
    }

    private fun loadMatcapTextures() {
        val mapping = mapOf(
            ShadingMode.CLAY_MATCAP to "matcaps/clay.jpg",
            ShadingMode.RED_CLAY_MATCAP to "matcaps/redClay.jpg",
            ShadingMode.PEARL_MATCAP to "matcaps/pearl.jpg",
            ShadingMode.GREEN_MATCAP to "matcaps/green.jpg",
            ShadingMode.SKIN_MATCAP to "matcaps/skin.jpg",
            ShadingMode.WHITE_MATCAP to "matcaps/white.jpg"
        )

        for ((mode, assetPath) in mapping) {
            try {
                context.assets.open(assetPath).use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    val texIds = IntArray(1)
                    GLES20.glGenTextures(1, texIds, 0)
                    val texId = texIds[0]
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                    bitmap.recycle()
                    matcapTextures[mode] = texId
                }
            } catch (e: Exception) {
                // Ignore if asset missing
            }
        }
    }

    private fun initGrid() {
        val size = 4.0f
        val steps = 20
        val step = (size * 2f) / steps
        val linesList = ArrayList<Float>()
        val y = -1.2f

        for (i in 0..steps) {
            val coord = -size + i * step
            // Line along X
            linesList.add(-size); linesList.add(y); linesList.add(coord)
            linesList.add(size); linesList.add(y); linesList.add(coord)
            // Line along Z
            linesList.add(coord); linesList.add(y); linesList.add(-size)
            linesList.add(coord); linesList.add(y); linesList.add(size)
        }

        gridVertexCount = linesList.size / 3
        val fb = ByteBuffer.allocateDirect(linesList.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (f in linesList) fb.put(f)
        fb.position(0)

        val vbos = IntArray(1)
        GLES20.glGenBuffers(1, vbos, 0)
        gridVboId = vbos[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gridVboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, fb.capacity() * 4, fb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun initCursorRing() {
        val points = FloatArray((cursorRingSegments + 1) * 3)
        for (i in 0..cursorRingSegments) {
            val angle = (2.0 * PI * i / cursorRingSegments).toFloat()
            points[i * 3] = cos(angle)
            points[i * 3 + 1] = sin(angle)
            points[i * 3 + 2] = 0f
        }

        val fb = ByteBuffer.allocateDirect(points.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(points)
        fb.position(0)

        val vbos = IntArray(1)
        GLES20.glGenBuffers(1, vbos, 0)
        cursorVboId = vbos[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, cursorVboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, fb.capacity() * 4, fb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun rebuildMeshBuffers(mesh: SculptMesh) {
        val vbos = IntArray(5)
        if (vertexBufferId != 0) {
            GLES20.glDeleteBuffers(5, intArrayOf(vertexBufferId, normalBufferId, colorBufferId, indexBufferId, wireframeBufferId), 0)
        }
        GLES20.glGenBuffers(5, vbos, 0)
        vertexBufferId = vbos[0]
        normalBufferId = vbos[1]
        colorBufferId = vbos[2]
        indexBufferId = vbos[3]
        wireframeBufferId = vbos[4]

        vertexCount = mesh.vertexCount
        triangleCount = mesh.triangleCount

        // 1. Vertex buffer
        val vb = ByteBuffer.allocateDirect(mesh.vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(mesh.vertices).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vb.capacity() * 4, vb, GLES20.GL_DYNAMIC_DRAW)

        // 2. Normal buffer
        val nb = ByteBuffer.allocateDirect(mesh.normals.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        nb.put(mesh.normals).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, normalBufferId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, nb.capacity() * 4, nb, GLES20.GL_DYNAMIC_DRAW)

        // 3. Color buffer
        val colors = mesh.colors ?: FloatArray(mesh.vertices.size) { 1f }
        val cb = ByteBuffer.allocateDirect(colors.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        cb.put(colors).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorBufferId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, cb.capacity() * 4, cb, GLES20.GL_DYNAMIC_DRAW)

        // 4. Index buffer
        val ib = ByteBuffer.allocateDirect(mesh.indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        ib.put(mesh.indices).position(0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, ib.capacity() * 4, ib, GLES20.GL_STATIC_DRAW)

        // 5. Wireframe line indices
        val wireLines = IntArray(triangleCount * 6)
        var wIdx = 0
        for (t in 0 until triangleCount) {
            val i0 = mesh.indices[t * 3]
            val i1 = mesh.indices[t * 3 + 1]
            val i2 = mesh.indices[t * 3 + 2]
            wireLines[wIdx++] = i0; wireLines[wIdx++] = i1
            wireLines[wIdx++] = i1; wireLines[wIdx++] = i2
            wireLines[wIdx++] = i2; wireLines[wIdx++] = i0
        }
        wireframeIndexCount = wireLines.size
        val wb = ByteBuffer.allocateDirect(wireLines.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        wb.put(wireLines).position(0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, wireframeBufferId)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, wb.capacity() * 4, wb, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun updateMeshBuffers(mesh: SculptMesh) {
        if (vertexBufferId == 0) return

        val vb = ByteBuffer.allocateDirect(mesh.vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(mesh.vertices).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vb.capacity() * 4, vb)

        val nb = ByteBuffer.allocateDirect(mesh.normals.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        nb.put(mesh.normals).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, normalBufferId)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, nb.capacity() * 4, nb)

        mesh.colors?.let { colors ->
            val cb = ByteBuffer.allocateDirect(colors.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            cb.put(colors).position(0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorBufferId)
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, cb.capacity() * 4, cb)
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawGrid() {
        GLES20.glUseProgram(flatShaderProgram)
        val mvpLoc = GLES20.glGetUniformLocation(flatShaderProgram, "uMVPMatrix")
        val colLoc = GLES20.glGetUniformLocation(flatShaderProgram, "uColor")
        val posLoc = GLES20.glGetAttribLocation(flatShaderProgram, "aPosition")

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, camera.viewProjMatrix, 0)
        GLES20.glUniform4f(colLoc, 0.22f, 0.24f, 0.28f, 0.8f)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gridVboId)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, gridVertexCount)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawMesh(mesh: SculptMesh) {
        GLES20.glUseProgram(meshShaderProgram)

        val mvpLoc = GLES20.glGetUniformLocation(meshShaderProgram, "uMVPMatrix")
        val mvLoc = GLES20.glGetUniformLocation(meshShaderProgram, "uMVMatrix")
        val modeLoc = GLES20.glGetUniformLocation(meshShaderProgram, "uShadingMode")
        val lightLoc = GLES20.glGetUniformLocation(meshShaderProgram, "uLightDir")
        val texLoc = GLES20.glGetUniformLocation(meshShaderProgram, "uMatcapTexture")

        val posLoc = GLES20.glGetAttribLocation(meshShaderProgram, "aPosition")
        val normLoc = GLES20.glGetAttribLocation(meshShaderProgram, "aNormal")
        val colLoc = GLES20.glGetAttribLocation(meshShaderProgram, "aColor")

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, camera.viewProjMatrix, 0)
        GLES20.glUniformMatrix4fv(mvLoc, 1, false, camera.viewMatrix, 0)
        GLES20.glUniform3f(lightLoc, 0.6f, 0.8f, 0.6f)

        val isMatcap = shadingMode != ShadingMode.PBR && shadingMode != ShadingMode.NORMALS
        val texId = matcapTextures[shadingMode] ?: matcapTextures[ShadingMode.CLAY_MATCAP] ?: 0

        if (isMatcap && texId != 0) {
            GLES20.glUniform1i(modeLoc, 0) // Matcap
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glUniform1i(texLoc, 0)
        } else if (shadingMode == ShadingMode.PBR) {
            GLES20.glUniform1i(modeLoc, 1) // PBR
        } else {
            GLES20.glUniform1i(modeLoc, 2) // Normals
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, normalBufferId)
        GLES20.glEnableVertexAttribArray(normLoc)
        GLES20.glVertexAttribPointer(normLoc, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorBufferId)
        GLES20.glEnableVertexAttribArray(colLoc)
        GLES20.glVertexAttribPointer(colLoc, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, triangleCount * 3, GLES20.GL_UNSIGNED_INT, 0)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(normLoc)
        GLES20.glDisableVertexAttribArray(colLoc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun drawWireframe() {
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-1.0f, -1.0f)

        GLES20.glUseProgram(flatShaderProgram)
        val mvpLoc = GLES20.glGetUniformLocation(flatShaderProgram, "uMVPMatrix")
        val colLoc = GLES20.glGetUniformLocation(flatShaderProgram, "uColor")
        val posLoc = GLES20.glGetAttribLocation(flatShaderProgram, "aPosition")

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, camera.viewProjMatrix, 0)
        GLES20.glUniform4f(colLoc, 0.15f, 0.15f, 0.15f, 0.6f)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, wireframeBufferId)
        GLES20.glDrawElements(GLES20.GL_LINES, wireframeIndexCount, GLES20.GL_UNSIGNED_INT, 0)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
    }

    private fun drawCursor(center: Vec3, normal: Vec3) {
        GLES20.glUseProgram(flatShaderProgram)
        val mvpLoc = GLES20.glGetUniformLocation(flatShaderProgram, "uMVPMatrix")
        val colLoc = GLES20.glGetUniformLocation(flatShaderProgram, "uColor")
        val posLoc = GLES20.glGetAttribLocation(flatShaderProgram, "aPosition")

        // Build model matrix for cursor ring
        val modelMat = FloatArray(16)
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, center.x, center.y, center.z)

        // Rotate ring to align with hit normal
        val up = Vec3(0f, 0f, 1f)
        val rotAxis = up.cross(normal)
        val angle = Math.toDegrees(kotlin.math.acos(up.dot(normal).coerceIn(-1f, 1f).toDouble())).toFloat()
        if (rotAxis.lengthSquared() > 1e-5f) {
            val aNorm = rotAxis.normalized()
            Matrix.rotateM(modelMat, 0, angle, aNorm.x, aNorm.y, aNorm.z)
        }
        Matrix.scaleM(modelMat, 0, cursorRadius, cursorRadius, cursorRadius)

        val mvpMat = FloatArray(16)
        Matrix.multiplyMM(mvpMat, 0, camera.viewProjMatrix, 0, modelMat, 0)

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMat, 0)
        GLES20.glUniform4f(colLoc, 1.0f, 0.45f, 0.2f, 0.95f) // Vibrant orange cursor

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, cursorVboId)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glLineWidth(3.0f)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, cursorRingSegments + 1)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }
}
