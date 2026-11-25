package com.cyron.manager.presentation

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cyron.manager.R

/** Vazirmatn (SIL OFL 1.1) gives Persian copy a native, readable voice. */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1.2).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 33.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = .5.sp)
)

private fun TextStyle.forPersian(family: FontFamily): TextStyle {
    // Vazirmatn needs a little more vertical room than the Latin default for
    // Persian descenders and diacritics. Never shrink an existing line height.
    val comfortable = (fontSize.value * 1.45f).sp
    val existing = lineHeight.value
    val line = if (!existing.isNaN() && existing > comfortable.value) lineHeight else comfortable
    // Negative tracking is a Latin display convention. On RTL Persian it can make
    // the final letter overflow the measured line and break onto its own line.
    val tracking = if (letterSpacing.value < 0f) 0.sp else letterSpacing
    return copy(fontFamily = family, lineHeight = line, letterSpacing = tracking)
}

private fun Typography.forPersian(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.forPersian(family),
    displayMedium = displayMedium.forPersian(family),
    displaySmall = displaySmall.forPersian(family),
    headlineLarge = headlineLarge.forPersian(family),
    headlineMedium = headlineMedium.forPersian(family),
    headlineSmall = headlineSmall.forPersian(family),
    titleLarge = titleLarge.forPersian(family),
    titleMedium = titleMedium.forPersian(family),
    titleSmall = titleSmall.forPersian(family),
    bodyLarge = bodyLarge.forPersian(family),
    bodyMedium = bodyMedium.forPersian(family),
    bodySmall = bodySmall.forPersian(family),
    labelLarge = labelLarge.forPersian(family),
    labelMedium = labelMedium.forPersian(family),
    labelSmall = labelSmall.forPersian(family)
)

val PersianTypography = Typography.forPersian(Vazirmatn)
