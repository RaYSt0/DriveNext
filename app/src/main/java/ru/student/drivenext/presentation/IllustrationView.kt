package ru.student.drivenext.presentation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Small scalable illustrations drawn in app code, so no external image assets are required. */
class IllustrationView(context: Context, attrs: android.util.AttributeSet? = null) : View(context, attrs) {
    var scene: Int = 0
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Color.rgb(35, 20, 63)
    private val purple = Color.rgb(109, 77, 218)
    private val lavender = Color.rgb(241, 233, 255)
    private val cyan = Color.rgb(99, 209, 227)
    private val coral = Color.rgb(244, 129, 116)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 360f, height / 320f)
        canvas.save()
        canvas.translate((width - 360f * scale) / 2f, (height - 320f * scale) / 2f)
        canvas.scale(scale, scale)
        when (scene) {
            0 -> splash(canvas)
            1 -> rental(canvas)
            2 -> safety(canvas)
            else -> offers(canvas)
        }
        canvas.restore()
    }

    private fun splash(c: Canvas) {
        circle(c, 180f, 156f, 128f, lavender)
        circle(c, 260f, 72f, 24f, cyan)
        circle(c, 86f, 95f, 14f, coral)
        car(c, 46f, 153f, 268f, purple)
        rounded(c, 112f, 241f, 248f, 251f, 5f, ink)
        rounded(c, 153f, 252f, 207f, 259f, 3f, cyan)
    }

    private fun rental(c: Canvas) {
        circle(c, 190f, 155f, 130f, Color.rgb(222, 249, 253))
        rounded(c, 34f, 36f, 174f, 260f, 20f, ink)
        rounded(c, 42f, 45f, 166f, 250f, 14f, Color.WHITE)
        rounded(c, 55f, 59f, 153f, 133f, 10f, lavender)
        car(c, 58f, 72f, 92f, coral)
        rounded(c, 58f, 160f, 150f, 168f, 4f, lavender)
        rounded(c, 58f, 180f, 132f, 188f, 4f, lavender)
        car(c, 151f, 174f, 188f, purple)
        circle(c, 256f, 62f, 24f, ink)
        circle(c, 256f, 62f, 9f, Color.WHITE)
        triangle(c, 243f, 77f, 269f, 77f, 256f, 101f, ink)
    }

    private fun safety(c: Canvas) {
        circle(c, 178f, 151f, 125f, Color.rgb(255, 232, 225))
        paint.color = coral
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        c.drawArc(38f, 70f, 324f, 258f, 12f, 235f, false, paint)
        paint.style = Paint.Style.FILL
        car(c, 50f, 181f, 223f, cyan)
        rounded(c, 205f, 44f, 305f, 122f, 13f, Color.WHITE)
        rounded(c, 216f, 57f, 294f, 65f, 4f, lavender)
        rounded(c, 216f, 76f, 278f, 84f, 4f, lavender)
        circle(c, 292f, 230f, 27f, ink)
        rounded(c, 280f, 226f, 304f, 238f, 5f, Color.WHITE)
    }

    private fun offers(c: Canvas) {
        circle(c, 180f, 158f, 137f, Color.rgb(225, 248, 251))
        rounded(c, 33f, 78f, 138f, 171f, 18f, Color.WHITE)
        rounded(c, 210f, 52f, 323f, 145f, 18f, Color.WHITE)
        rounded(c, 151f, 174f, 310f, 264f, 18f, Color.WHITE)
        car(c, 47f, 93f, 77f, coral)
        car(c, 222f, 65f, 86f, cyan)
        car(c, 165f, 181f, 130f, purple)
        rounded(c, 52f, 147f, 118f, 154f, 4f, lavender)
        rounded(c, 224f, 120f, 302f, 127f, 4f, lavender)
        rounded(c, 166f, 239f, 288f, 246f, 4f, lavender)
    }

    private fun car(c: Canvas, x: Float, y: Float, w: Float, color: Int) {
        c.save()
        c.translate(x, y)
        c.scale(w / 240f, w / 240f)
        rounded(c, 20f, 78f, 220f, 126f, 22f, color)
        val roof = Path().apply {
            moveTo(57f, 82f); lineTo(84f, 43f); quadTo(90f, 36f, 101f, 36f)
            lineTo(160f, 36f); quadTo(171f, 36f, 178f, 46f)
            lineTo(201f, 82f); close()
        }
        paint.color = color
        c.drawPath(roof, paint)
        rounded(c, 93f, 46f, 126f, 77f, 5f, Color.WHITE)
        rounded(c, 132f, 46f, 160f, 77f, 5f, Color.WHITE)
        circle(c, 68f, 121f, 24f, ink)
        circle(c, 177f, 121f, 24f, ink)
        circle(c, 68f, 121f, 11f, Color.WHITE)
        circle(c, 177f, 121f, 11f, Color.WHITE)
        rounded(c, 30f, 89f, 55f, 98f, 4f, Color.WHITE)
        rounded(c, 191f, 89f, 211f, 98f, 4f, Color.WHITE)
        c.restore()
    }

    private fun rounded(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        paint.color = color
        c.drawRoundRect(left, top, right, bottom, radius, radius, paint)
    }

    private fun circle(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.color = color
        c.drawCircle(x, y, radius, paint)
    }

    private fun triangle(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
        paint.color = color
        c.drawPath(Path().apply { moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close() }, paint)
    }
}
