package io.codecks.ui.theme

data class ThemeEditorState(
    val applied: ThemeBundle,
    val draft: ThemeBundle = applied,
    val undo: List<ThemeBundle> = emptyList(),
) {
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canApply: Boolean get() = draft != applied && ThemeContrast.isBundleReadable(draft)

    fun preview(bundle: ThemeBundle): ThemeEditorState = copy(draft = bundle, undo = (undo + draft).takeLast(MAX_UNDO))
    fun edit(target: ThemeTarget, role: ThemeColorRole, color: ThemeArgb): ThemeEditorState {
        val updated = draft.resolve(target).withColor(role, color)
        return preview(draft.withScheme(target, updated))
    }
    fun setOpacity(target: ThemeTarget, role: ThemeOpacityRole, opacity: Float): ThemeEditorState {
        val current = draft.resolve(target)
        val updated = current.copy(opacity = current.opacity.with(role, opacity.coerceIn(.2f, 1f)), preset = null)
        return preview(draft.withScheme(target, updated))
    }
    fun duplicate(target: ThemeTarget, newId: String, newLabel: String): ThemeEditorState {
        val duplicate = draft.resolve(target).copy(id = newId, label = newLabel, preset = null)
        return preview(draft.withScheme(target, duplicate))
    }
    fun undo(): ThemeEditorState = undo.lastOrNull()?.let { copy(draft = it, undo = undo.dropLast(1)) } ?: this
    fun reset(): ThemeEditorState = preview(applied)
    fun applied(): ThemeEditorState = if (canApply) copy(applied = draft, undo = emptyList()) else this
    fun save(): String = ThemeSchemeCodec.encode(draft)

    companion object {
        private const val MAX_UNDO = 20
        fun restore(applied: ThemeBundle, savedDraft: String?): ThemeEditorState {
            val restored = (savedDraft?.let(ThemeSchemeCodec::decode) as? ThemeImportResult.Success)?.bundle
            return ThemeEditorState(applied, restored ?: applied)
        }
    }
}
