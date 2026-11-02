package gay.doughmination.plural.data

import androidx.compose.ui.graphics.Color

/** A pride flag: the label the API stores per member, and its stripes top to bottom. */
class PrideFlag(val label: String, val stripes: List<Color>)

private fun flag(label: String, vararg stripes: Long) = PrideFlag(label, stripes.map { Color(it) })

/** The website's curated catalogue (system/src/lib/pride.ts), in its order. */
val PrideFlags = listOf(
    flag("Lesbian", 0xFFD52D00, 0xFFFF9A56, 0xFFFFFFFF, 0xFFD362A4, 0xFFA30262),
    flag("Gay", 0xFF078D70, 0xFF98E8C1, 0xFFFFFFFF, 0xFF7BADE2, 0xFF3D1A78),
    flag("Bisexual", 0xFFD60270, 0xFF9B4F96, 0xFF0038A8),
    flag("Pansexual", 0xFFFF218C, 0xFFFFD800, 0xFF21B1FF),
    flag("Asexual", 0xFF000000, 0xFFA3A3A3, 0xFFFFFFFF, 0xFF800080),
    flag("Aromantic", 0xFF3DA542, 0xFFA7D379, 0xFFFFFFFF, 0xFFA9A9A9, 0xFF000000),
    flag("Transgender", 0xFF5BCEFA, 0xFFF5A9B8, 0xFFFFFFFF, 0xFFF5A9B8, 0xFF5BCEFA),
    flag("Nonbinary", 0xFFFCF434, 0xFFFFFFFF, 0xFF9C59D1, 0xFF2C2C2C),
    flag("Genderfluid", 0xFFFF75A2, 0xFFFFFFFF, 0xFFBE18D6, 0xFF000000, 0xFF333EBD),
    flag("Genderqueer", 0xFFB57EDC, 0xFFFFFFFF, 0xFF4A8123),
    flag("Agender", 0xFF000000, 0xFFBCC4C7, 0xFFFFFFFF, 0xFFB7F684, 0xFFFFFFFF, 0xFFBCC4C7, 0xFF000000),
    flag("Intersex", 0xFFFFD800, 0xFF7A00AC),
    flag("Demisexual", 0xFF000000, 0xFFFFFFFF, 0xFF6E0070, 0xFFD3D3D3),
    flag("Polyamory", 0xFF0000FF, 0xFFFF0000, 0xFF000000),
    flag("Queer", 0xFFB57EDC, 0xFFFFFFFF, 0xFF4A8123),
    flag("Rainbow", 0xFFE40303, 0xFFFF8C00, 0xFFFFED00, 0xFF008026, 0xFF004DFF, 0xFF750787),
)

fun findPrideFlag(label: String): PrideFlag? = PrideFlags.firstOrNull { it.label.equals(label, ignoreCase = true) }
