package io.codecks.ui.theme

import android.app.KeyguardManager
import android.content.Context
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

@Composable
fun ThemeStudioPanel(
    settings: CodecksThemeSettings,
    modifier: Modifier = Modifier,
    environmentOverride: ThemeUiEnvironment? = null,
) {
    val context = LocalContext.current.applicationContext
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyguard = remember(context) { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    var locked by remember { mutableStateOf(keyguard.isDeviceLocked) }
    DisposableEffect(lifecycleOwner, keyguard) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) locked = keyguard.isDeviceLocked
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val overlay = (view.rootView.layoutParams as? WindowManager.LayoutParams)?.type.let { type ->
        type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY ||
            type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val darkPalette = MaterialTheme.colorScheme.background.luminance() < .5f
    val observedEnvironment = ThemeUiEnvironment(
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp,
            fontScale = configuration.fontScale,
            reducedMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f,
            overlay = overlay,
            locked = locked,
    )
    val adaptation = ThemeUiPolicy.resolve(environmentOverride ?: observedEnvironment)
    val repository = remember(context) { ThemeSettingsRepository(context) }
    val scope = rememberCoroutineScope()
    var savedDraft by rememberSaveable { mutableStateOf(ThemeSchemeCodec.encode(settings.themeBundle)) }
    var editor by remember(settings.themeBundle) {
        mutableStateOf(ThemeEditorState.restore(settings.themeBundle, savedDraft))
    }
    var target by rememberSaveable { mutableStateOf(ThemeTarget.Global) }
    var transferOpen by rememberSaveable { mutableStateOf(false) }
    var transferText by rememberSaveable { mutableStateOf("") }
    var transferError by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var duplicateCounter by rememberSaveable { mutableStateOf(0) }
    var seedText by rememberSaveable { mutableStateOf("#FF3DDC84") }

    fun update(next: ThemeEditorState) {
        editor = next
        savedDraft = next.save()
    }

    Surface(modifier = modifier.fillMaxWidth().testTag("theme-studio"), tonalElevation = 1.dp, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Theme studio", style = MaterialTheme.typography.titleMedium)
            Text("Eight offline presets or edit every color. Preview first; Apply is atomic.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            settings.themeLoadIssue?.let {
                Text(
                    "Saved theme rejected: ${it.name}. Safe Codecks Green loaded.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }

            val active = editor.draft.resolve(target)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemePresetCatalog.presets.forEach { preset ->
                    val selected = active.preset == preset.preset
                    FilterChip(
                        selected = selected,
                        onClick = {
                            update(editor.preview(editor.draft.withScheme(target, preset)))
                        },
                        label = { Text(preset.label) },
                        modifier = Modifier.testTag("theme-preset-${preset.id}").semantics { contentDescription = "${preset.label} theme${if (selected) ", selected" else ""}" },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeTarget.entries.forEach { item ->
                    FilterChip(
                        selected = target == item,
                        onClick = { target = item },
                        label = { Text(item.name) },
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = seedText,
                    onValueChange = { seedText = it.take(9) },
                    label = { Text("Seed #FFRRGGBB") },
                    singleLine = true,
                    isError = ThemeArgb.parse(seedText) == null,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val seed = ThemeArgb.parse(seedText)
                        if (seed == null) {
                            message = "Invalid seed: use opaque #FFRRGGBB"
                        } else {
                            duplicateCounter += 1
                            val generated = ThemeTonalGenerator.generate(
                                seed = seed,
                                id = "custom-seed-${System.currentTimeMillis().toString(36)}-$duplicateCounter",
                                label = "Seed ${seed.toHex().takeLast(6)}",
                                dark = darkPalette,
                            )
                            update(editor.preview(editor.draft.withScheme(target, generated)))
                            message = "Accessible tonal roles generated"
                        }
                    },
                    enabled = ThemeArgb.parse(seedText) != null,
                    modifier = Modifier.testTag("theme-generate-seed"),
                ) { Text("Generate") }
            }

            ThemeColorRole.entries.chunked(adaptation.editorColumns).forEach { roles ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    roles.forEach { role ->
                        var text by remember(active.id, role, active[role]) { mutableStateOf(active[role].toHex()) }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { candidate ->
                                text = candidate.take(9)
                                val parsed = ThemeArgb.parse(text)
                                if (parsed != null) {
                                    update(editor.edit(target, role, parsed))
                                    message = null
                                } else {
                                    message = "Invalid ${role.name}: use opaque #FFRRGGBB"
                                }
                            },
                            label = { Text(role.name) },
                            supportingText = {
                                val parsed = ThemeArgb.parse(text)
                                if (parsed == null) Text("Use opaque #FFRRGGBB") else {
                                    val ratio = ThemeContrast.ratio(ThemeContrast.readableForeground(parsed), parsed)
                                    Text("${"%.1f".format(ratio)}:1")
                                }
                            },
                            isError = ThemeArgb.parse(text) == null,
                            singleLine = true,
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                        )
                    }
                }
            }

            ThemeOpacityRole.entries.forEach { role ->
                Text("${role.name} strength ${"%.0f".format(active.opacity[role] * 100)}%")
                Slider(
                    value = active.opacity[role],
                    onValueChange = { update(editor.setOpacity(target, role, it)) },
                    valueRange = .2f..1f,
                    modifier = Modifier.semantics { contentDescription = "${target.name} ${role.name} strength" },
                )
            }

            ThemePreview(editor.draft.resolve(target), target, adaptation, rtl)

            val borderRatio = ThemeContrast.ratio(active[ThemeColorRole.Border], active[ThemeColorRole.Background])
            Text(
                "Border/background ${"%.1f".format(borderRatio)}:1${if (borderRatio >= ThemeContrast.LARGE_TEXT_MIN) " · Pass" else " · Fix contrast"}",
                color = if (borderRatio >= ThemeContrast.LARGE_TEXT_MIN) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )

            if (!ThemeContrast.isCriticalReadable(editor.draft.global)) {
                Text("Unreadable critical colors. Apply is blocked.", color = MaterialTheme.colorScheme.error)
            }
            message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { update(editor.undo()) }, enabled = editor.canUndo) { Text("Undo") }
                    TextButton(onClick = { update(editor.reset()) }) { Text("Reset") }
                    TextButton(onClick = {
                        duplicateCounter += 1
                        val id = "custom-${target.name.lowercase()}-${System.currentTimeMillis().toString(36)}-$duplicateCounter"
                        update(editor.duplicate(target, id, "${active.label} copy".take(48)))
                        message = "Independent copy ready"
                    }) { Text("Duplicate") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        transferText = ThemeSchemeCodec.encode(editor.draft)
                        transferError = null
                        transferOpen = true
                    }, modifier = Modifier.testTag("theme-transfer-open")) { Text("Export / import") }
                    Button(
                        onClick = {
                            scope.launch {
                                message = if (repository.applyThemeBundle(editor.draft)) "Theme applied" else "Theme rejected"
                                if (message == "Theme applied") update(editor.applied())
                            }
                        },
                        enabled = editor.canApply && adaptation.editingAllowed,
                    ) { Text("Apply") }
                }
            }
        }
    }

    if (transferOpen) {
        AlertDialog(
            onDismissRequest = { transferOpen = false },
            title = { Text("Theme export / import", modifier = Modifier.testTag("theme-transfer-dialog")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = transferText,
                        onValueChange = {
                            transferText = it.take(ThemeSchemeCodec.MAX_BYTES)
                            transferError = null
                        },
                        label = { Text("Bounded theme JSON") },
                        minLines = 5,
                        isError = transferError != null,
                    )
                    transferError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (val result = ThemeSchemeCodec.decode(transferText)) {
                        is ThemeImportResult.Success -> {
                            update(editor.preview(result.bundle))
                            message = "Import preview ready"
                            transferOpen = false
                        }
                        is ThemeImportResult.Rejected -> transferError = "Import rejected: ${result.reason.name}"
                    }
                }) { Text("Preview import") }
            },
            dismissButton = { TextButton(onClick = { transferOpen = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun ThemePreview(scheme: ThemeScheme, target: ThemeTarget, adaptation: ThemeUiAdaptation, rtl: Boolean) {
    val base = MaterialTheme.colorScheme.background
    val effectiveBackground = ThemeColorMath.composite(
        scheme[ThemeColorRole.Background],
        ThemeArgb.fromRgb(base.red, base.green, base.blue),
        scheme.opacity.surface,
    )
    val targetBackground = Color(effectiveBackground.value.toInt())
    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = if (adaptation.animationsEnabled) tween(themeAnimationDurationMillis(adaptation)) else snap(),
        label = "theme-preview-background",
    )
    val foreground = Color(ThemeContrast.readableForeground(effectiveBackground).value.toInt())
    Surface(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(themePreviewWidthFraction(adaptation)).testTag("theme-preview"),
    ) {
        Column(
            Modifier.background(background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Live ${target.name.lowercase()} preview", color = foreground)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (rtl) Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End) else Arrangement.spacedBy(8.dp),
            ) {
                listOf(ThemeColorRole.Primary, ThemeColorRole.Secondary, ThemeColorRole.Tertiary, ThemeColorRole.Button).forEach { role ->
                    Box(
                        Modifier.size(48.dp).background(Color(scheme[role].value.toInt()), MaterialTheme.shapes.small)
                            .semantics { contentDescription = "${role.name} color swatch" },
                    )
                }
            }
        }
    }
}
