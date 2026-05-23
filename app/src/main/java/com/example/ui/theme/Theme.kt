package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = PlainPrimaryAccent,
    onPrimary = CozyDarkSkyBackground,
    primaryContainer = MinimalTextFieldContainer,
    onPrimaryContainer = MinimalTextFieldText,
    secondary = PlainSecondaryAccent,
    secondaryContainer = CozySandSurface,
    onSecondaryContainer = OffWhiteText,
    background = CozyDarkSkyBackground,
    surface = CozySandSurface,
    onBackground = OffWhiteText,
    onSurface = OffWhiteText,
    outline = MinimalBorderColor
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PlainPrimaryAccent,
    onPrimary = CozyDarkSkyBackground,
    primaryContainer = MinimalTextFieldContainer,
    onPrimaryContainer = MinimalTextFieldText,
    secondary = PlainSecondaryAccent,
    secondaryContainer = CozySandSurface,
    onSecondaryContainer = OffWhiteText,
    background = CozyDarkSkyBackground,
    surface = CozySandSurface,
    onBackground = OffWhiteText,
    onSurface = OffWhiteText,
    outline = MinimalBorderColor
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors by default so that our precise Clean Minimalism palette is shown
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
