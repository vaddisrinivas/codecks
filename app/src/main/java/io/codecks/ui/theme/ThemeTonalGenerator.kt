package io.codecks.ui.theme

object ThemeTonalGenerator {
    fun generate(seed: ThemeArgb, id: String, label: String, dark: Boolean): ThemeScheme {
        val black = requireNotNull(ThemeArgb.parse("#FF000000"))
        val white = requireNotNull(ThemeArgb.parse("#FFFFFFFF"))
        val background = if (dark) requireNotNull(ThemeArgb.parse("#FF080A09")) else requireNotNull(ThemeArgb.parse("#FFF8FAF8"))
        val surface = ThemeColorMath.composite(if (dark) white else black, background, if (dark) .07f else .04f)
        fun tint(target: ThemeArgb, amount: Float) = ThemeColorMath.composite(target, seed, amount)
        val border = ThemeColorMath.composite(if (dark) white else black, background, .55f)
        return ThemeScheme(
            id = id,
            label = label,
            preset = null,
            colors = mapOf(
                ThemeColorRole.Primary to seed,
                ThemeColorRole.Secondary to tint(if (dark) white else black, .28f),
                ThemeColorRole.Tertiary to ThemeArgb.fromRgb(
                    ((seed.value shr 8) and 0xFF) / 255f,
                    (seed.value and 0xFF) / 255f,
                    ((seed.value shr 16) and 0xFF) / 255f,
                ),
                ThemeColorRole.Surface to surface,
                ThemeColorRole.Background to background,
                ThemeColorRole.Border to border,
                ThemeColorRole.Success to requireNotNull(ThemeArgb.parse(if (dark) "#FF48E090" else "#FF087F3F")),
                ThemeColorRole.Warning to requireNotNull(ThemeArgb.parse(if (dark) "#FFFFC857" else "#FF7A5200")),
                ThemeColorRole.Danger to requireNotNull(ThemeArgb.parse(if (dark) "#FFFF7373" else "#FFB3261E")),
                ThemeColorRole.Button to ThemeColorMath.composite(seed, surface, .36f),
                ThemeColorRole.Grid to ThemeColorMath.composite(seed, background, .18f),
                ThemeColorRole.Glow to seed,
            ),
        )
    }
}
