package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// --- Sleek Plain Minimal Theme (Eye-Safe Muted Deep Grey & Metallic Silver) ---
val CozyDarkSkyBackground = Color(0xFF1E2022)     // Soothing dark slate charcoal background (not pure black)
val CozySandSurface = Color(0xFF2B2E31)          // Calming soft granite graphite card surface
val OffWhiteText = Color(0xFFE3E5E8)             // Eye-safe comfortable off-white text
val PureWhite = Color(0xFFE3E5E8)                // Muted off-white/light-grey replacing blinding pure white

// Soft shades of moderate grey for text fields (making ALL white boxes/containers a sleek dark grey!)
val MinimalTextFieldContainer = Color(0xFF333639) // True cozy charcoal grey box (completely eye-safe, NO glaring white boxes!)
val MinimalTextFieldText = Color(0xFFE3E5E8)      // Sleek off-white text inside fields
val MinimalBorderColor = Color(0xFF43464B)        // Soft slate-dark border framing lines

// Elegant muted colors for secondary elements
val MutedGrayText = Color(0xFF909499)             // Cozy iOS/material gray
val PlainPrimaryAccent = Color(0xFFB0B3B8)        // Cozy anodized metallic slate light gray
val PlainSecondaryAccent = Color(0xFF8F9398)      // Plain warm pewter silver accents

// --- Quiet Eye-Safe Badges & Alerts (Muted Warm Crimson & Sage/Pine Green) ---
val SoftAlertBg = Color(0xFF4A2F2F)               // Quiet dark crimson-rose warning container (instead of harsh bright red)
val SoftAlertText = Color(0xFFF99B9B)             // Deep cozy pastel rose text

val CompactActiveBg = Color(0xFF283A31)           // Cozy quiet emerald pine container
val CompactActiveText = Color(0xFF9EE4C4)         // Soothing pale jade/mint text

// Compatibility Aliases for Minimalist Dark Look
val ExpiringBackground = SoftAlertBg
val ExpiringText = SoftAlertText
val ExpiringBadgeBg = SoftAlertBg
val ExpiringBadgeText = SoftAlertText
val ActiveBadgeBg = CompactActiveBg
val ActiveBadgeText = CompactActiveText
