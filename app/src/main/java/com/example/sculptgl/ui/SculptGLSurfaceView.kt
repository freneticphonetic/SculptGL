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
        setZOrderMediaOverlay(true)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
    }

    override fun onDetachedFromWindow() {
        onPause()
        super.onDetachedFromWindow()
    }

    // Touch gesture tracking
    private var isSculpting = false
    private var strokeStarted = false
    private var pendingHit: HitResult? = null
    private var prevHitPoint: Vec3? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f

    // Multi-touch tracking
    private var maxPointersInGesture = 0
    private var gestureStartTime = 0L
    private var gestureMoved = false
    private var gestureHandled = false
    private var prevPinchDistance = 0f
    private var prevMidX = 0f
    private var prevMidY = 0f

    private val touchSlop = 12f // pixels threshold to differentiate tap vs drag

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerCount = event.pointerCount
        val action = event.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                maxPointersInGesture = 1
                gestureStartTime = System.currentTimeMillis()
                gestureMoved = false
                gestureHandled = false
                strokeStarted = false
                isSculpting = false
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y

                val mode = viewModel.uiState.value.interactionMode
                if (mode == CanvasInteractionMode.SCULPT) {
                    val ray = renderer.camera.screenToRay(event.x, event.y)
                    val mesh = viewModel.getMesh()
                    pendingHit = mesh.pick(ray)
                    renderer.currentHit = pendingHit
                } else {
                    pendingHit = null
                    renderer.currentHit = null
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val count = event.pointerCount
                if (count > maxPointersInGesture) {
                    maxPointersInGesture = count
                }
                // Multi-finger detected: this is not a single-finger sculpt stroke
                isSculpting = false
                strokeStarted = false
                renderer.currentHit = null

                if (count == 2) {
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
                val distFromDown = hypot(event.x - downX, event.y - downY)
                if (distFromDown > touchSlop) {
                    gestureMoved = true
                }

                if (pointerCount == 1 && maxPointersInGesture == 1) {
                    val mode = viewModel.uiState.value.interactionMode
                    if (mode == CanvasInteractionMode.SCULPT) {
                        val ray = renderer.camera.screenToRay(event.x, event.y)
                        val mesh = viewModel.getMesh()
                        val currentHit = mesh.pick(ray)

                        if (!strokeStarted && gestureMoved) {
                            val initialHit = currentHit ?: pendingHit
                            if (initialHit != null) {
                                strokeStarted = true
                                isSculpting = true
                                viewModel.history.pushState(mesh)
                                viewModel.engine.beginStroke(mesh, initialHit)
                                prevHitPoint = initialHit.point.copy()
                                renderer.currentHit = initialHit
                                viewModel.engine.sculpt(mesh, initialHit)
                                renderer.needsMeshUpdate = true
                            }
                        } else if (isSculpting && currentHit != null) {
                            val dragDelta = prevHitPoint?.let { currentHit.point - it }
                            prevHitPoint = currentHit.point.copy()
                            renderer.currentHit = currentHit
                            viewModel.engine.sculpt(mesh, currentHit, dragDelta)
                            renderer.needsMeshUpdate = true
                        } else if (!isSculpting && gestureMoved) {
                            // Empty canvas single-finger orbit
                            renderer.camera.rotate(-dx * 0.005f, -dy * 0.005f)
                        }
                    } else {
                        // Camera mode: 1-finger orbit
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

                    val pinchDiff = Math.abs(currentDist - prevPinchDistance)
                    val panDiff = hypot(midX - prevMidX, midY - prevMidY)
                    if (pinchDiff > 8f || panDiff > 8f) {
                        gestureMoved = true
                    }

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
                // When first finger lifts during multi-touch tap
                val duration = System.currentTimeMillis() - gestureStartTime
                if (!gestureMoved && !gestureHandled && duration < 400) {
                    if (maxPointersInGesture == 2) {
                        viewModel.triggerUndo()
                        gestureHandled = true
                    } else if (maxPointersInGesture == 3) {
                        viewModel.triggerRedo()
                        gestureHandled = true
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val duration = System.currentTimeMillis() - gestureStartTime

                if (!gestureMoved && !gestureHandled && duration < 400) {
                    if (maxPointersInGesture == 2) {
                        viewModel.triggerUndo()
                        gestureHandled = true
                    } else if (maxPointersInGesture == 3) {
                        viewModel.triggerRedo()
                        gestureHandled = true
                    } else if (maxPointersInGesture == 1 && !strokeStarted) {
                        // Single tap without dragging: single dab on the model
                        val mode = viewModel.uiState.value.interactionMode
                        if (mode == CanvasInteractionMode.SCULPT && pendingHit != null) {
                            val mesh = viewModel.getMesh()
                            viewModel.history.pushState(mesh)
                            viewModel.engine.beginStroke(mesh, pendingHit!!)
                            viewModel.engine.sculpt(mesh, pendingHit!!)
                            renderer.needsMeshUpdate = true
                            viewModel.updateMeshStats()
                        }
                    }
                }

                if (isSculpting) {
                    viewModel.updateMeshStats()
                }

                isSculpting = false
                strokeStarted = false
                prevHitPoint = null
                pendingHit = null
                renderer.currentHit = null
                maxPointersInGesture = 0
                gestureHandled = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isSculpting = false
                strokeStarted = false
                renderer.currentHit = null
                maxPointersInGesture = 0
                gestureHandled = false
                return true
            }
        }

        return super.onTouchEvent(event)
    }
}
