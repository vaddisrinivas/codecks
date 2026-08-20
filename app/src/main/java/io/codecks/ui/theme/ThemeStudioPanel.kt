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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.util.Locale

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
    val librarySnapshot by repository.themeLibrary.collectAsStateWithLifecycle(initialValue = ThemeLibrarySnapshot())
    val library = librarySnapshot.state
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
    var activeColorRoleName by rememberSaveable { mutableStateOf(ThemeColorRole.Primary.name) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var libraryName by rememberSaveable { mutableStateOf("") }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    fun update(next: ThemeEditorState) {
        editor = next
        savedDraft = next.save()
    }

    Surface(modifier = modifier.fillMaxWidth().testTag("theme-studio"), tonalElevation = 1.dp, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Theme studio", style = MaterialTheme.typography.titleMedium)
            Text("Twelve offline presets or build a named custom theme. Preview before applying.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            settings.themeLoadIssue?.let {
                Text(
                    "Saved theme rejected: ${it.name}. Safe Codecks Green loaded.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }

            val active = editor.draft.resolve(target)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemePresetCatalog.presets.forEach { preset ->
                    val selected = active.preset == preset.preset
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.width(152.dp).heightIn(min = 120.dp)
                            .testTag("theme-preset-${preset.id}")
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { update(editor.preview(editor.draft.withScheme(target, preset))) },
                            )
                            .semantics { contentDescription = "${preset.label} theme preview${if (selected) ", selected" else ""}" },
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(ThemeColorRole.Primary, ThemeColorRole.Secondary, ThemeColorRole.Tertiary).forEach { role ->
                                    Box(Modifier.size(32.dp).background(Color(preset[role].value.toInt()), MaterialTheme.shapes.small))
                                }
                            }
                            Text(preset.label, style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (preset.preset == ThemePresetId.HighContrast) "Maximum contrast" else "Accessible roles",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
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

            Text("Color", style = MaterialTheme.typography.titleSmall)
            val activeColorRole = ThemeColorRole.entries.firstOrNull { it.name == activeColorRoleName } ?: ThemeColorRole.Primary
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeColorRole.entries.forEach { role ->
                    FilterChip(
                        selected = role == activeColorRole,
                        onClick = { activeColorRoleName = role.name },
                        label = { Text(role.name) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            AccessibleColorPicker(
                selected = active[activeColorRole],
                target = target,
                role = activeColorRole,
                onSelect = { update(editor.edit(target, activeColorRole, it)); message = null },
            )

            TextButton(
                onClick = { advancedOpen = !advancedOpen },
                modifier = Modifier.heightIn(min = 48.dp).testTag("theme-advanced-toggle").semantics {
                    stateDescription = if (advancedOpen) "Expanded" else "Collapsed"
                },
            ) { Text(if (advancedOpen) "Hide advanced" else "Advanced: hex and opacity") }
            if (advancedOpen) {
                ThemeColorRole.entries.chunked(adaptation.editorColumns).forEach { roles ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        roles.forEach { role ->
                            var text by remember(active.id, role, active[role]) { mutableStateOf(active[role].toHex()) }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { candidate ->
                                    text = candidate.take(9)
                                    ThemeArgb.parse(text)?.let { update(editor.edit(target, role, it)); message = null }
                                        ?: run { message = "Invalid ${role.name}: use opaque #FFRRGGBB" }
                                },
                                label = { Text("${target.name} ${role.name} color hex") },
                                supportingText = { Text(ThemeArgb.parse(text)?.let { "${"%.1f".format(ThemeContrast.ratio(ThemeContrast.readableForeground(it), it))}:1" } ?: "Use opaque #FFRRGGBB") },
                                isError = ThemeArgb.parse(text) == null,
                                singleLine = true,
                                modifier = Modifier.weight(1f).heightIn(min = 64.dp).semantics {
                                    contentDescription = "${target.name} ${role.name} color hex"
                                },
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

            Text("My themes", style = MaterialTheme.typography.titleSmall)
            if (librarySnapshot.quarantined) {
                Text(
                    "Saved theme library is unavailable (${librarySnapshot.issue?.name}). Existing data is quarantined.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            message = if (repository.resetCorruptThemeLibrary()) "Unavailable library reset" else "Library reset failed"
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("theme-library-reset"),
                ) { Text("Reset unavailable library") }
            }
            OutlinedTextField(
                value = libraryName,
                onValueChange = { libraryName = it.take(ThemeLibraryCodec.MAX_NAME_CHARS) },
                label = { Text("Theme name") },
                supportingText = { Text("${library.themes.size}/${ThemeLibraryCodec.MAX_THEMES} saved on this device") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("theme-library-name"),
            )
            Button(
                onClick = {
                    val clean = ThemeLibraryCodec.cleanName(libraryName)
                    if (clean == null) message = "Enter a theme name"
                    else scope.launch {
                        val id = "theme-${clean.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30)}-${System.currentTimeMillis().toString(36)}"
                        message = if (repository.saveNamedTheme(id.take(48), clean, editor.draft)) "Theme saved" else "Theme library full or name already used"
                    }
                },
                enabled = !librarySnapshot.quarantined && library.themes.size < ThemeLibraryCodec.MAX_THEMES && ThemeLibraryCodec.cleanName(libraryName) != null,
                modifier = Modifier.heightIn(min = 48.dp).testTag("theme-library-save"),
            ) { Text("Save current theme") }
            library.themes.forEach { saved ->
                var editName by rememberSaveable(saved.id, saved.name) { mutableStateOf(saved.name) }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { update(editor.preview(saved.bundle)); message = "${saved.name} loaded as preview" },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("theme-library-load-${saved.id}"),
                    ) { Text("Load ${saved.name}") }
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it.take(ThemeLibraryCodec.MAX_NAME_CHARS) },
                        label = { Text("Rename ${saved.name}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("theme-library-rename-${saved.id}"),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            scope.launch { message = if (repository.renameNamedTheme(saved.id, editName)) "${saved.name} renamed" else "Choose a unique valid name" }
                        }, enabled = ThemeLibraryCodec.cleanName(editName) != null, modifier = Modifier.heightIn(min = 48.dp).testTag("theme-library-rename-action-${canonicalThemeName(saved.name).replace(Regex("[^a-z0-9]+"), "-")}")) { Text("Rename ${saved.name}") }
                        TextButton(onClick = {
                            pendingDeleteId = saved.id
                        }, modifier = Modifier.heightIn(min = 48.dp).testTag("theme-library-delete-${canonicalThemeName(saved.name).replace(Regex("[^a-z0-9]+"), "-")}")) { Text("Delete ${saved.name}") }
                    }
                }
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { update(editor.undo()) }, enabled = editor.canUndo) { Text("Undo") }
                    TextButton(onClick = { update(editor.reset()) }) { Text("Reset") }
                    TextButton(onClick = {
                        duplicateCounter += 1
                        val id = "custom-${target.name.lowercase(Locale.ROOT)}-${System.currentTimeMillis().toString(36)}-$duplicateCounter"
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

    pendingDeleteId?.let { id ->
        library.themes.firstOrNull { it.id == id }?.let { saved ->
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text("Delete ${saved.name}?") },
                text = { Text("This removes the saved theme from this phone. The applied theme does not change.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            message = if (repository.deleteNamedTheme(saved.id)) "${saved.name} deleted" else "${saved.name} was not deleted"
                            pendingDeleteId = null
                        }
                    }) { Text("Delete ${saved.name}") }
                },
                dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Keep ${saved.name}") } },
            )
        }
    }
}

@Composable
private fun AccessibleColorPicker(
    selected: ThemeArgb,
    target: ThemeTarget,
    role: ThemeColorRole,
    onSelect: (ThemeArgb) -> Unit,
) {
    val choices = remember {
        listOf(
            "Green" to 0xFF3DDC84,
            "Blue" to 0xFF75D7FF,
            "Violet" to 0xFFD9B8FF,
            "Rose" to 0xFFFF8CA8,
            "Amber" to 0xFFFFC857,
            "White" to 0xFFFFFFFF,
            "Slate" to 0xFF64748B,
            "Black" to 0xFF000000,
        ).map { (name, value) -> name to requireNotNull(ThemeArgb.of(value)) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { (name, color) ->
            val isSelected = selected == color
            Column(Modifier.width(72.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    color = Color(color.value.toInt()),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isSelected) 3.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(56.dp)
                        .testTag("theme-color-${role.name.lowercase(Locale.ROOT)}-${name.lowercase(Locale.ROOT)}")
                        .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(color) },
                    ).semantics {
                        contentDescription = "$name ${target.name} ${role.name} color, ${color.toHex()}, ${if (isSelected) "selected" else "not selected"}"
                    },
                ) {}
                Text(name, style = MaterialTheme.typography.labelSmall)
                if (isSelected) Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
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
            Text("Live ${target.name.lowercase(Locale.ROOT)} preview", color = foreground)
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
