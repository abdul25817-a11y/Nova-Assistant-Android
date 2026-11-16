package com.nova.assistant.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.sin

/** Lightweight animated gradient wallpaper with drifting orbs. */
class NovaWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = NovaEngine()

    inner class NovaEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var surfaceReady = false
        private var width = 0
        private var height = 0
        private var time = 0f

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val drawRunnable = object : Runnable {
            override fun run() {
                if (drawFrame()) {
                    time += 0.02f
                    handler.postDelayed(this, FRAME_MS)
                }
            }
        }

        private val holderCallback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                width = holder.surfaceFrame.width()
                height = holder.surfaceFrame.height()
                surfaceReady = true
                start()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
                width = w
                height = h
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceReady = false
                stop()
            }
        }

        init {
            surfaceHolder.addCallback(holderCallback)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) start() else stop()
        }

        override fun onDestroy() {
            stop()
            surfaceHolder.removeCallback(holderCallback)
            super.onDestroy()
        }

        private fun start() {
            if (!visible || !surfaceReady) return
            handler.removeCallbacks(drawRunnable)
            handler.post(drawRunnable)
        }

        private fun stop() {
            handler.removeCallbacks(drawRunnable)
        }

        private fun drawFrame(): Boolean {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return false
                canvas.drawColor(Color.parseColor("#0F1020"))

                drawOrb(canvas, width * 0.30f + sin(time) * width * 0.10f,
                    height * 0.35f + sin(time * 0.8f) * height * 0.08f,
                    kotlin.math.min(width, height) * 0.32f, Color.parseColor("#6C5CE7"))

                drawOrb(canvas, width * 0.72f + sin(time * 0.7f + 2f) * width * 0.09f,
                    height * 0.68f + sin(time * 1.1f + 1f) * height * 0.10f,
                    kotlin.math.min(width, height) * 0.26f, Color.parseColor("#00CEC9"))

                drawOrb(canvas, width * 0.50f + sin(time * 0.4f + 4f) * width * 0.06f,
                    height * 0.20f + sin(time * 0.9f + 3f) * height * 0.05f,
                    kotlin.math.min(width, height) * 0.14f, Color.parseColor("#4834D4"))
            } catch (e: Exception) {
                return false
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                    }
                }
            }
            return true
        }

        private fun drawOrb(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
            paint.color = color
            paint.alpha = 55
            canvas.drawCircle(cx, cy, radius, paint)
            paint.alpha = 110
            canvas.drawCircle(cx, cy, radius * 0.6f, paint)
            paint.alpha = 170
            canvas.drawCircle(cx, cy, radius * 0.25f, paint)
        }
    }

    companion object {
        private const val FRAME_MS = 45L
    }
}
