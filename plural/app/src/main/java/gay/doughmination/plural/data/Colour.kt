package gay.doughmination.plural.data

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// Member colours, as system/src/lib/colour.ts handles them: PluralKit stores
// hex without the "#", and a name drawn in a very dark colour gets lifted
// until it reads on the dark theme, without washing out to white.

/** A member's colour, or null when they have none (or it isn't hex). */
fun memberColour(hex: String?): Color? {
    val rgb = hexToRgb(hex ?: return null) ?: return null
    return Color(rgb[0], rgb[1], rgb[2])
}

/** The member's colour, lifted if needed so it reads on the dark backgrounds. */
fun readableOnDark(hex: String?, fallback: Color): Color {
    val rgb = hexToRgb(hex ?: return fallback) ?: return fallback
    if (luminance(rgb) >= MIN_LUMINANCE) return Color(rgb[0], rgb[1], rgb[2])

    val (h, s, startL) = rgbToHsl(rgb)
    var l = startL
    var out = rgb
    // Walk lightness up until it clears the floor, capped well short of white.
    while (l < 0.85 && luminance(out) < MIN_LUMINANCE) {
        l = min(0.85, l + 0.04)
        out = hslToRgb(h, s, l)
    }
    return Color(out[0], out[1], out[2])
}

private const val MIN_LUMINANCE = 0.18

private fun hexToRgb(raw: String): IntArray? {
    var hex = raw.trim().removePrefix("#")
    if (hex.length == 3) hex = hex.map { "$it$it" }.joinToString("")
    if (hex.length != 6 || hex.any { it.digitToIntOrNull(16) == null }) return null
    return intArrayOf(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16))
}

/** WCAG relative luminance, 0 (black) to 1 (white). */
private fun luminance(rgb: IntArray): Double {
    fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1]) + 0.0722 * channel(rgb[2])
}

private fun rgbToHsl(rgb: IntArray): Triple<Double, Double, Double> {
    val r = rgb[0] / 255.0
    val g = rgb[1] / 255.0
    val b = rgb[2] / 255.0
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2
    if (mx == mn) return Triple(0.0, 0.0, l)
    val d = mx - mn
    val s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        r -> ((g - b) / d + if (g < b) 6 else 0) / 6
        g -> ((b - r) / d + 2) / 6
        else -> ((r - g) / d + 4) / 6
    }
    return Triple(h, s, l)
}

private fun hslToRgb(h: Double, s: Double, l: Double): IntArray {
    if (s == 0.0) {
        val v = (l * 255).roundToInt()
        return intArrayOf(v, v, v)
    }
    fun hue(p: Double, q: Double, t0: Double): Double {
        var t = t0
        if (t < 0) t += 1
        if (t > 1) t -= 1
        return when {
            t < 1.0 / 6 -> p + (q - p) * 6 * t
            t < 1.0 / 2 -> q
            t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
            else -> p
        }
    }
    val q = if (l < 0.5) l * (1 + s) else l + s - l * s
    val p = 2 * l - q
    return intArrayOf(
        (hue(p, q, h + 1.0 / 3) * 255).roundToInt(),
        (hue(p, q, h) * 255).roundToInt(),
        (hue(p, q, h - 1.0 / 3) * 255).roundToInt(),
    )
}
