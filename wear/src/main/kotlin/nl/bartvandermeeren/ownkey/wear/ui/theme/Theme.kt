package nl.bartvandermeeren.ownkey.wear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun OwnkeyWearTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWearSpacing provides WearSpacing()) {
        MaterialTheme(
            colorScheme = OwnkeyWearColorScheme,
            typography = OwnkeyWearTypography,
            shapes = OwnkeyWearShapes,
            content = content,
        )
    }
}
