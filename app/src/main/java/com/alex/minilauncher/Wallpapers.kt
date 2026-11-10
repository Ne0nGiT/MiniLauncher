package com.alex.minilauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** Hình nền có sẵn trong app: tất cả được vẽ bằng code, không cần file ảnh. */
object Wall {
    class Opt(val id: String, val name: String)

    val opts = listOf(
        Opt("system", "Hệ thống"), Opt("black", "Đen tuyền"), Opt("graphite", "Than"),
        Opt("navy", "Xanh đêm"), Opt("white", "Trắng"), Opt("mountain", "Núi"),
        Opt("forest", "Rừng"), Opt("sunset", "Hoàng hôn"), Opt("stars", "Sao đêm")
    )

    fun scenic(id: String) = id == "mountain" || id == "forest" || id == "sunset" || id == "stars"
    fun isLight(id: String) = id == "white"

    private var cId = ""
    private var cBmp: Bitmap? = null

    /** Nền cho cửa sổ. dim = 30..100 (chỉ ảnh phong cảnh dùng lớp phủ tối). */
    fun window(ctx: Context, id: String, dim: Int): Drawable {
        val base: Drawable = when (id) {
            "black" -> GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF000000.toInt(), 0xFF0B0B0D.toInt(), 0xFF26262B.toInt()))
            "graphite" -> ColorDrawable(0xFF1C1C1E.toInt())
            "navy" -> ColorDrawable(0xFF0D1B2A.toInt())
            "white" -> ColorDrawable(0xFFF2F2F2.toInt())
            else -> BitmapDrawable(ctx.resources, scene(ctx, id))
        }
        if (!scenic(id)) return base
        val a = dim * 255 / 100 * 55 / 100
        return LayerDrawable(arrayOf(base, ColorDrawable(android.graphics.Color.argb(a, 0, 0, 0))))
    }

    private fun scene(ctx: Context, id: String): Bitmap {
        if (id == cId && cBmp != null) return cBmp!!
        val dm = ctx.resources.displayMetrics
        val b = render(id, dm.widthPixels / 2, dm.heightPixels / 2)
        cId = id; cBmp = b
        return b
    }

    fun thumb(id: String): Bitmap = render(id, 90, 160)

    fun full(ctx: Context, id: String): Bitmap {
        val dm = ctx.resources.displayMetrics
        return render(id, dm.widthPixels, dm.heightPixels)
    }

    private fun grad(x0: Float, y0: Float, x1: Float, y1: Float, vararg colors: Int) =
        Paint().apply { shader = LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP) }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun render(id: String, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val W = w.toFloat(); val H = h.toFloat()
        when (id) {
            "system" -> c.drawPaint(grad(0f, 0f, W, H, 0xFF4A4A50.toInt(), 0xFF9A9AA2.toInt()))
            "black" -> c.drawPaint(grad(0f, 0f, 0f, H, 0xFF000000.toInt(), 0xFF0B0B0D.toInt(), 0xFF26262B.toInt()))
            "graphite" -> c.drawColor(0xFF1C1C1E.toInt())
            "navy" -> c.drawColor(0xFF0D1B2A.toInt())
            "white" -> c.drawColor(0xFFF2F2F2.toInt())
            "mountain" -> mountain(c, W, H)
            "forest" -> forest(c, W, H)
            "sunset" -> sunset(c, W, H)
            "stars" -> stars(c, W, H)
        }
        return bmp
    }

    private fun ridge(c: Canvas, W: Float, H: Float, baseY: Float, amp: Float, seed: Int, color: Int) {
        val r = Random(seed.toLong())
        val p1 = r.nextFloat() * 6.28f; val p2 = r.nextFloat() * 6.28f; val p3 = r.nextFloat() * 6.28f
        val path = Path(); path.moveTo(0f, H)
        val n = 40
        for (i in 0..n) {
            val x = W * i / n
            val t = x / W * 6.28f
            val v = 0.55f * (1f - abs(sin(t * 1.3f + p1))) +
                    0.30f * (1f - abs(sin(t * 2.9f + p2))) +
                    0.15f * (0.5f + 0.5f * sin(t * 6.1f + p3))
            path.lineTo(x, baseY - amp * v)
        }
        path.lineTo(W, H); path.close()
        c.drawPath(path, fillPaint(color))
    }

    private fun mist(c: Canvas, W: Float, H: Float, y: Float, tint: Int) {
        val p = Paint()
        p.shader = LinearGradient(0f, y - H * 0.12f, 0f, y + H * 0.03f,
            intArrayOf(tint and 0xFFFFFF, tint), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, y - H * 0.12f, W, y + H * 0.03f, p)
    }

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        val p = Paint()
        p.shader = RadialGradient(x, y, r, intArrayOf(color, color and 0xFFFFFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
    }

    private fun mountain(c: Canvas, W: Float, H: Float) {
        c.drawPaint(grad(0f, 0f, 0f, H * 0.7f, 0xFF0A101C.toInt(), 0xFF2A3A55.toInt(), 0xFF6E829E.toInt()))
        glow(c, W * 0.72f, H * 0.15f, W * 0.24f, 0x55FFFFFF)
        c.drawCircle(W * 0.72f, H * 0.15f, W * 0.05f, fillPaint(0xFFEDEFF5.toInt()))
        ridge(c, W, H, H * 0.60f, H * 0.14f, 1, 0xFF4A5B78.toInt())
        mist(c, W, H, H * 0.66f, 0x40B8C8E0)
        ridge(c, W, H, H * 0.68f, H * 0.13f, 2, 0xFF33425E.toInt())
        mist(c, W, H, H * 0.76f, 0x40B8C8E0)
        ridge(c, W, H, H * 0.78f, H * 0.11f, 3, 0xFF222D44.toInt())
        mist(c, W, H, H * 0.86f, 0x30B8C8E0)
        ridge(c, W, H, H * 0.88f, H * 0.08f, 4, 0xFF121A2B.toInt())
    }

    private fun pines(c: Canvas, W: Float, H: Float, baseY: Float, th: Float, count: Int, color: Int, seed: Int) {
        val r = Random(seed.toLong()); val p = fillPaint(color)
        c.drawRect(0f, baseY, W, H, p)
        for (i in 0..count) {
            val x = W * (i + r.nextFloat() * 0.8f - 0.2f) / count
            val t = th * (0.8f + r.nextFloat() * 0.4f)
            val hw = t * 0.28f
            for (k in 0..2) {
                val top = baseY - t + k * t * 0.27f
                val bot = top + t * 0.42f
                val half = hw * (0.55f + 0.225f * k)
                val path = Path()
                path.moveTo(x, top); path.lineTo(x - half, bot); path.lineTo(x + half, bot); path.close()
                c.drawPath(path, p)
            }
        }
    }

    private fun forest(c: Canvas, W: Float, H: Float) {
        c.drawPaint(grad(0f, 0f, 0f, H * 0.65f, 0xFF08110E.toInt(), 0xFF1E3A2E.toInt(), 0xFF5E8068.toInt()))
        glow(c, W * 0.3f, H * 0.18f, W * 0.35f, 0x30DFF5E4)
        pines(c, W, H, H * 0.62f, H * 0.10f, 16, 0xFF3A5A45.toInt(), 11)
        mist(c, W, H, H * 0.70f, 0x40C8E0CC)
        pines(c, W, H, H * 0.72f, H * 0.14f, 11, 0xFF254033.toInt(), 12)
        mist(c, W, H, H * 0.82f, 0x30C8E0CC)
        pines(c, W, H, H * 0.84f, H * 0.20f, 8, 0xFF14261D.toInt(), 13)
        pines(c, W, H, H * 0.97f, H * 0.28f, 5, 0xFF08130E.toInt(), 14)
    }

    private fun sunset(c: Canvas, W: Float, H: Float) {
        c.drawPaint(grad(0f, 0f, 0f, H * 0.72f, 0xFF14122B.toInt(), 0xFF5B2E5E.toInt(), 0xFFE2766A.toInt(), 0xFFF6B36B.toInt()))
        glow(c, W * 0.5f, H * 0.62f, W * 0.55f, 0x66FFD08A)
        c.drawCircle(W * 0.5f, H * 0.62f, W * 0.11f, fillPaint(0xFFFFE2A8.toInt()))
        ridge(c, W, H, H * 0.66f, H * 0.10f, 5, 0xFF5A2F55.toInt())
        ridge(c, W, H, H * 0.74f, H * 0.10f, 6, 0xFF3A1F44.toInt())
        ridge(c, W, H, H * 0.84f, H * 0.09f, 7, 0xFF22122B.toInt())
        ridge(c, W, H, H * 0.93f, H * 0.06f, 8, 0xFF100912.toInt())
    }

    private fun stars(c: Canvas, W: Float, H: Float) {
        c.drawPaint(grad(0f, 0f, 0f, H, 0xFF02030A.toInt(), 0xFF0A1128.toInt(), 0xFF1C2447.toInt()))
        glow(c, W * 0.3f, H * 0.25f, W * 0.8f, 0x2A9BB4FF)
        val r = Random(7); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 140) {
            p.color = android.graphics.Color.argb(90 + r.nextInt(166), 255, 255, 255)
            c.drawCircle(r.nextFloat() * W, r.nextFloat() * H * 0.78f, max(0.5f, (0.4f + r.nextFloat() * 1.1f) * (W / 360f)), p)
        }
        ridge(c, W, H, H * 0.90f, H * 0.07f, 9, 0xFF05070D.toInt())
        ridge(c, W, H, H * 0.96f, H * 0.05f, 10, 0xFF000000.toInt())
    }
}
