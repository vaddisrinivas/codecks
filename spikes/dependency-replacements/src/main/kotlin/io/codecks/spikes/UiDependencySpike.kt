package io.codecks.spikes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.SettingsSlider
import com.alorma.compose.settings.ui.SettingsSwitch
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import compose.icons.FontAwesomeIcons
import compose.icons.SimpleIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bolt
import compose.icons.simpleicons.Github

@Composable
fun RepresentativeSettingsRows(
    haptics: Boolean,
    speed: Float,
    onHapticsChanged: (Boolean) -> Unit,
    onSpeedChanged: (Float) -> Unit,
) {
    Column {
        SettingsSwitch(
            state = haptics,
            modifier = Modifier.semantics { contentDescription = "Trackpad haptics" },
            title = { Text("Trackpad haptics", style = MaterialTheme.typography.titleMedium) },
            subtitle = { Text("Confirm gestures with vibration") },
            shape = MaterialTheme.shapes.medium,
            onCheckedChange = onHapticsChanged,
        )
        SettingsSlider(
            title = { Text("Pointer speed", style = MaterialTheme.typography.titleMedium) },
            value = speed,
            valueRange = 0.5f..2f,
            steps = 5,
            shape = MaterialTheme.shapes.medium,
            onValueChange = { onSpeedChanged(it.coerceIn(0.5f, 2f)) },
        )
    }
}

@Composable
fun RepresentativeColorPicker(color: Color, onColorChanged: (Color) -> Unit) {
    val controller = rememberColorPickerController()
    HsvColorPicker(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .semantics { contentDescription = "Deck key color" },
        controller = controller,
        initialColor = color,
        onColorChanged = { onColorChanged(it.color) },
    )
}

object SemanticSpikeIcons {
    val Automation: ImageVector = FontAwesomeIcons.Solid.Bolt
    val Source: ImageVector = SimpleIcons.Github
}

@Composable
fun RepresentativeIcons() {
    Icon(SemanticSpikeIcons.Automation, contentDescription = "Run automation")
    Icon(SemanticSpikeIcons.Source, contentDescription = "Open source repository")
}

object ColorSafety {
    fun boundedChannel(channel: Float): Float = channel.coerceIn(0f, 1f)

    fun contrastRatio(lighter: Float, darker: Float): Float {
        val high = maxOf(boundedChannel(lighter), boundedChannel(darker))
        val low = minOf(boundedChannel(lighter), boundedChannel(darker))
        return (high + 0.05f) / (low + 0.05f)
    }
}
