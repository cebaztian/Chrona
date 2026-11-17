package com.nutabe.chrona.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nutabe.chrona.R

// The fonts are variable: without variation settings every weight would draw at the default weight.
private fun newsreader(weight: Int) = Font(
    R.font.newsreader,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Newsreader = FontFamily(newsreader(400), newsreader(500))
val PlusJakartaSans = FontFamily(jakarta(300), jakarta(400), jakarta(500), jakarta(600))

private const val TABULAR = "tnum"

/** DESIGN.md §4. Times and counters use Plus Jakarta Sans with tabular figures; the rest is Newsreader. */
val ChronaTypography = Typography(
    displayLarge = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Light, fontSize = 88.sp, fontFeatureSettings = TABULAR),
    displayMedium = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Light, fontSize = 64.sp, fontFeatureSettings = TABULAR),
    displaySmall = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Light, fontSize = 48.sp, fontFeatureSettings = TABULAR),
    headlineLarge = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Normal, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Normal, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Normal, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Normal, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodySmall = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 18.sp),
    labelMedium = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.16.em,
    ),
)

/** Styles that Material's slots do not cover. */
object ChronaText {
    val counter = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 56.sp, fontFeatureSettings = TABULAR)
    val counterTotal = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 24.sp, fontFeatureSettings = TABULAR)
    val math = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 32.sp, fontFeatureSettings = TABULAR)
    val number = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, fontFeatureSettings = TABULAR)
}
