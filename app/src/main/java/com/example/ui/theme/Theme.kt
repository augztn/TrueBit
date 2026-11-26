package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class TrueBitExtendedColors(
    val isObsidianDark: Boolean,
    val cardElevated: Color,
    val cardHigh: Color,
    val glassBorder: Color,
    val glassHeader: Color,
    val cyanAccent: Color,
    val emeraldVerdict: Color,
    val emeraldVerdictBg: Color,
    val amberVerdict: Color,
    val amberVerdictBg: Color,
    val crimsonVerdict: Color,
    val crimsonVerdictBg: Color,
    val textSecondary: Color,
    val textMuted: Color
)

val LocalTrueBitColors = staticCompositionLocalOf {
    TrueBitExtendedColors(
        isObsidianDark = true,
        cardElevated = ObsidianCardElevated,
        cardHigh = ObsidianCardHigh,
        glassBorder = ObsidianBorder,
        glassHeader = ObsidianGlassHeader,
        cyanAccent = CyanSpectrum,
        emeraldVerdict = EmeraldLossless,
        emeraldVerdictBg = EmeraldLosslessBg,
        amberVerdict = AmberUpsampled,
        amberVerdictBg = AmberUpsampledBg,
        crimsonVerdict = CrimsonTranscode,
        crimsonVerdictBg = CrimsonTranscodeBg,
        textSecondary = TextSecondaryDark,
        textMuted = TextMutedDark
    )
}

private val ObsidianDarkColorScheme = darkColorScheme(
    primary = CyanSpectrum,
    onPrimary = ObsidianBackground,
    primaryContainer = ObsidianCardHigh,
    onPrimaryContainer = CyanSpectrum,
    secondary = EmeraldLossless,
    onSecondary = ObsidianBackground,
    secondaryContainer = EmeraldLosslessBg,
    onSecondaryContainer = EmeraldLossless,
    tertiary = AmberUpsampled,
    tertiaryContainer = AmberUpsampledBg,
    onTertiaryContainer = AmberUpsampled,
    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianCardElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianCardElevated,
    surfaceContainerHigh = ObsidianCardHigh,
    surfaceContainerHighest = Color(0xFF222738),
    error = CrimsonTranscode,
    errorContainer = CrimsonTranscodeBg,
    onErrorContainer = CrimsonTranscode,
    outline = ObsidianBorder,
    outlineVariant = Color(0x14FFFFFF)
)

private val LiquidGlassLightColorScheme = lightColorScheme(
    primary = CyanSpectrumDeep,
    onPrimary = Color.White,
    primaryContainer = LiquidLightCardHigh,
    onPrimaryContainer = CyanSpectrumDeep,
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0x1F10B981),
    onSecondaryContainer = Color(0xFF059669),
    tertiary = Color(0xFFD97706),
    tertiaryContainer = Color(0x1FF59E0B),
    onTertiaryContainer = Color(0xFFD97706),
    background = LiquidLightBackground,
    onBackground = TextPrimaryLight,
    surface = LiquidLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LiquidLightCardElevated,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = LiquidLightBackground,
    surfaceContainerLow = LiquidLightSurface,
    surfaceContainer = LiquidLightCardElevated,
    surfaceContainerHigh = LiquidLightCardHigh,
    surfaceContainerHighest = Color(0xFFE2E8F0),
    error = Color(0xFFE11D48),
    errorContainer = Color(0x1FF43F5E),
    onErrorContainer = Color(0xFFE11D48),
    outline = LiquidLightBorder,
    outlineVariant = Color(0x120F172A)
)

@Composable
fun TrueBitTheme(
    isObsidianDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isObsidianDark) ObsidianDarkColorScheme else LiquidGlassLightColorScheme
    val extendedColors = if (isObsidianDark) {
        TrueBitExtendedColors(
            isObsidianDark = true,
            cardElevated = ObsidianCardElevated,
            cardHigh = ObsidianCardHigh,
            glassBorder = ObsidianBorder,
            glassHeader = ObsidianGlassHeader,
            cyanAccent = CyanSpectrum,
            emeraldVerdict = EmeraldLossless,
            emeraldVerdictBg = EmeraldLosslessBg,
            amberVerdict = AmberUpsampled,
            amberVerdictBg = AmberUpsampledBg,
            crimsonVerdict = CrimsonTranscode,
            crimsonVerdictBg = CrimsonTranscodeBg,
            textSecondary = TextSecondaryDark,
            textMuted = TextMutedDark
        )
    } else {
        TrueBitExtendedColors(
            isObsidianDark = false,
            cardElevated = LiquidLightCardElevated,
            cardHigh = LiquidLightCardHigh,
            glassBorder = LiquidLightBorder,
            glassHeader = LiquidLightGlassHeader,
            cyanAccent = CyanSpectrumDeep,
            emeraldVerdict = Color(0xFF059669),
            emeraldVerdictBg = Color(0x1F10B981),
            amberVerdict = Color(0xFFD97706),
            amberVerdictBg = Color(0x1FF59E0B),
            crimsonVerdict = Color(0xFFE11D48),
            crimsonVerdictBg = Color(0x1FF43F5E),
            textSecondary = TextSecondaryLight,
            textMuted = TextMutedLight
        )
    }

    CompositionLocalProvider(LocalTrueBitColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
