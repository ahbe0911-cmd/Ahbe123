package ir.dasteyar.app

import androidx.compose.ui.text.font.Font as ComposeFont
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont

/**
 * Resolves the Google Fonts Font overload alongside Compose's resource Font constructor.
 */
fun Font(
    googleFont: GoogleFont,
    fontProvider: GoogleFont.Provider,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal
): ComposeFont = androidx.compose.ui.text.googlefonts.Font(
    googleFont = googleFont,
    fontProvider = fontProvider,
    weight = weight,
    style = style
)
