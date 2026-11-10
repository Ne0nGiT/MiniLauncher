package com.alex.minilauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/** shape: 1 = tròn, 2 = vuông bo (pct = % bo góc), 3 = squircle */
fun shapePath(w: Float, h: Float, shape: Int, pct: Int): Path {
    val p = Path()
    when (shape) {
        1 -> p.addOval(0f, 0f, w, h, Path.Direction.CW)
        2 -> { val r = w * pct.coerceIn(0, 50) / 100f; p.addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW) }
        else -> {
            val n = 4.0
            for (i in 0..120) {
                val t = 2 * Math.PI * i / 120
                val c = Math.cos(t); val s = Math.sin(t)
                val x = (w / 2 + (w / 2) * Math.signum(c) * Math.pow(Math.abs(c), 2.0 / n)).toFloat()
                val y = (h / 2 + (h / 2) * Math.signum(s) * Math.pow(Math.abs(s), 2.0 / n)).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
        }
    }
    return p
}

/** Vẽ lại icon vào khuôn mới (icon adaptive: ghép lớp nền + lớp trước rồi cắt theo khuôn, mép mịn). */
fun renderIcon(src: Drawable, size: Int, shape: Int, pct: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (Build.VERSION.SDK_INT >= 26 && src is AdaptiveIconDrawable) {
        val ex = size / 4
        src.background?.let { it.setBounds(-ex, -ex, size + ex, size + ex); it.draw(c) }
        src.foreground?.let { it.setBounds(-ex, -ex, size + ex, size + ex); it.draw(c) }
    } else {
        src.setBounds(0, 0, size, size); src.draw(c)
    }
    val mask = Paint(Paint.ANTI_ALIAS_FLAG)
    mask.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    c.drawPath(shapePath(size.toFloat(), size.toFloat(), shape, pct), mask)
    return bmp
}
