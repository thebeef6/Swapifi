package swapifi.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SwapifiRedBright,
    onPrimary = Color.White,
    primaryContainer = SwapifiRedDeep,
    onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = SwapifiSilver,
    onSecondary = Color(0xFF2A2D31),
    tertiary = SwapifiRed,
    background = DarkBackground,
    onBackground = Color(0xFFF2E7E9),
    surface = DarkSurface,
    onSurface = Color(0xFFF2E7E9),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFD8C2C7)
)

private val LightColorScheme = lightColorScheme(
    primary = SwapifiRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DE),
    onPrimaryContainer = Color(0xFF40000D),
    secondary = Color(0xFF75565C),
    tertiary = SwapifiRedDeep,
    background = LightBackground,
    onBackground = Color(0xFF201A1B),
    surface = LightSurface,
    onSurface = Color(0xFF201A1B),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF514347)
)

@Composable
fun SwapifiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Desactivado por defecto: el color dinámico de Android 12+ pisaría el
    // rojo de marca del icono con los colores del fondo de pantalla del usuario.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
