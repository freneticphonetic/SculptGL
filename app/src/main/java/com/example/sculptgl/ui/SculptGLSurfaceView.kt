package com.example.sculptgl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.example.sculptgl.engine.SculptRenderer
import com.example.sculptgl.model.HitResult
import com.example.sculptgl.model.Vec3
import kotlin.math.hypot

@SuppressLint("ViewConstructor")
class SculptGLSurfaceView(
    context: Context,
    private val viewModel: SculptGLViewModel,
    private val renderer: SculptRenderer
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    // Touch gesture tracking
    private var isSculpting = false
    private var prevHitPoint: Vec3? = null
    private var lastX = 0f
    private var lastY = 0f

    // Multi-touch tracking
    private var maxPointersInGesture = 0
    private var gestureStartTime = 0L
    private var gestureMoved = false
    private var prevPinchDistance = 0f
    private var prevMidX = 0f
    private var prevMidY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerCount = event.pointerCount
        val action = event.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                maxPointersInGesture = 1
                gestureStartTime = System.currentTimeMillis()
                gestureMoved = false
                lastX = event.x
                lastY = event.y

                val mode = viewModel.uiState.value.interactionMode
                if (mode == CanvasInteractionMode.SCULPT) {
                    val ray = renderer.camera.screenToRay(event.x, event.y)
                    val mesh = viewModel.getMesh()
                    val hit = mesh.pick(ray)

                    if (hit != null) {
                        isSculpting = true
                        viewModel.history.pushState(mesh)
                        viewModel.engine.beginStroke(hit)
                        prevHitPoint = hit.point.copy()
                        renderer.currentHit = hit
                        viewModel.engine.sculpt(mesh, hit)
                        renderer.needsMeshUpdate = true
                    } else {
                        isSculpting = false
                        renderer.currentHit = null
                    }
                } else {
                    isSculpting = false
                    renderer.currentHit = null
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerCount > maxPointersInGesture) {
                    maxPointersInGesture = pointerCount
                }
                isSculpting = false
                renderer.currentHit = null

                if (pointerCount == 2) {
                    val x0 = event.getX(0); val y0 = event.getY(0)
                    val x1 = event.getX(1); val y1 = event.getY(1)
                    prevPinchDistance = hypot(x1 - x0, y1 - y0)
                    prevMidX = (x0 + x1) * 0.5f
                    prevMidY = (y0 + y1) * 0.5f
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (hypot(dx, dy) > 12f) {
                    gestureMoved = true
                }

                if (pointerCount == 1) {
                    if (isSculpting) {
                        val ray = renderer.camera.screenToRay(event.x, event.y)
                        val mesh = viewModel.getMesh()
                        val hit = mesh.pick(ray)

                        if (hit != null) {
                            val dragDelta = prevHitPoint?.let { hit.point - it }
                            prevHitPoint = hit.point.copy()
                            renderer.currentHit = hit
                            viewModel.engine.sculpt(mesh, hit, dragDelta)
                            renderer.needsMeshUpdate = true
                        }
                    } else {
                        // 1-finger orbit on canvas
                        renderer.camera.rotate(-dx * 0.005f, -dy * 0.005f)
                    }
                    lastX = event.x
                    lastY = event.y
                } else if (pointerCount >= 2) {
                    // Two finger pinch-to-zoom and pan
                    val x0 = event.getX(0); val y0 = event.getY(0)
                    val x1 = event.getX(1); val y1 = event.getY(1)
                    val currentDist = hypot(x1 - x0, y1 - y0)
                    val midX = (x0 + x1) * 0.5f
                    val midY = (y0 + y1) * 0.5f

                    if (prevPinchDistance > 10f && currentDist > 10f) {
                        val zoomFactor = prevPinchDistance / currentDist
                        renderer.camera.zoom(zoomFactor)
                    }

                    val panDx = midX - prevMidX
                    val panDy = midY - prevMidY
                    renderer.camera.pan(panDx, panDy)

                    prevPinchDistance = currentDist
                    prevMidX = midX
                    prevMidY = midY
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // If fingers are lifting, check if tap happened
                return true
            }

            MotionEvent.ACTION_UP -> {
                val duration = System.currentTimeMillis() - gestureStartTime

                // Standard art app gestures:
                // 2-finger tap = Undo!
                // 3-finger tap = Redo!
                if (!gestureMoved && duration < 350) {
                    if (maxPointersInGesture == 2) {
                        viewModel.triggerUndo()
                    } else if (maxPointersInGesture == 3) {
                        viewModel.triggerRedo()
                    }
                }

                if (isSculpting) {
                    isSculpting = false
                    prevHitPoint = null
                    renderer.currentHit = null
                    viewModel.updateMeshStats()
                }

                maxPointersInGesture = 0
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isSculpting = false
                renderer.currentHit = null
                maxPointersInGesture = 0
                return true
            }
        }

        return super.onTouchEvent(event)
    }
}
