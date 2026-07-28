package io.codecks.core.reactive

import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.DeterministicReactiveEngine
import io.codecks.domain.reactive.ReactiveCatalogControlSpec
import io.codecks.domain.reactive.ReactiveEngine
import io.codecks.domain.reactive.ReactiveIcon
import io.codecks.domain.reactive.ReactiveActionReceipt
import io.codecks.domain.reactive.providers.ActiveAppReactiveControlProvider
import io.codecks.domain.reactive.providers.AppleShortcutCatalog
import io.codecks.domain.reactive.providers.AppleShortcutsReactiveControlProvider
import io.codecks.domain.reactive.providers.AccessibilityDiscoveryReactiveControlProvider
import io.codecks.domain.reactive.providers.MediaReactiveControlProvider
import io.codecks.domain.reactive.providers.MonitorBrightnessReactiveControlProvider
import io.codecks.domain.reactive.providers.ReactiveAppActionMapping
import io.codecks.domain.reactive.providers.SpotlightSftpReactiveControlProvider
import io.codecks.domain.reactive.providers.UndoReceiptReactiveControlProvider
import io.codecks.domain.reactive.providers.WindowReactiveControlProvider

fun defaultReactiveTrackpadEngine(
    actionRevisions: Map<String, ActionRevision> = emptyMap(),
    receipts: () -> List<ReactiveActionReceipt> = { emptyList() },
    shortcuts: () -> AppleShortcutCatalog? = { null },
): ReactiveEngine =
    DeterministicReactiveEngine(
        providers = listOf(
            UndoReceiptReactiveControlProvider(receipts),
            AppleShortcutsReactiveControlProvider(shortcuts),
            ActiveAppReactiveControlProvider(
                mappings = listOf(
                    ReactiveAppActionMapping(
                        appTokens = setOf("chrome", "safari", "firefox", "brave", "arc", "edge", "browser"),
                        actionTokens = setOf("reload", "browser", "tab", "dev"),
                        reason = "Browser controls",
                        score = 95,
                    ),
                    ReactiveAppActionMapping(
                        appTokens = setOf("finder", "desktop", "file"),
                        actionTokens = setOf("finder", "desktop", "downloads", "documents"),
                        reason = "Finder shortcuts",
                        score = 90,
                    ),
                    ReactiveAppActionMapping(
                        appTokens = setOf("terminal", "iterm", "warp", "shell", "kitty"),
                        actionTokens = setOf("terminal", "github"),
                        reason = "Terminal workspace",
                        score = 82,
                    ),
                    ReactiveAppActionMapping(
                        appTokens = setOf("cursor", "code", "editor", "xcode", "studio"),
                        actionTokens = setOf("github", "terminal", "dev"),
                        reason = "Coding context",
                        score = 78,
                    ),
                ),
                controls = listOf(
                    ReactiveCatalogControlSpec(
                        actionId = "browser_back",
                        title = "Back",
                        icon = ReactiveIcon.ArrowLeft,
                        requiredCapabilities = setOf(CodecksCapability.MacCommand),
                    ),
                    ReactiveCatalogControlSpec(
                        actionId = "browser_forward",
                        title = "Forward",
                        icon = ReactiveIcon.ArrowRight,
                        requiredCapabilities = setOf(CodecksCapability.MacCommand),
                    ),
                    ReactiveCatalogControlSpec(
                        actionId = "reload",
                        title = "Reload",
                        icon = ReactiveIcon.Reload,
                        requiredCapabilities = setOf(CodecksCapability.MacCommand),
                    ),
                    ReactiveCatalogControlSpec(
                        actionId = "new_tab",
                        title = "New Tab",
                        icon = ReactiveIcon.Add,
                        requiredCapabilities = setOf(CodecksCapability.MacCommand),
                    ),
                    ReactiveCatalogControlSpec(
                        actionId = "finder",
                        title = "Finder",
                        icon = ReactiveIcon.Finder,
                        requiredCapabilities = setOf(CodecksCapability.MacCommand),
                    ),
                    ReactiveCatalogControlSpec(
                        actionId = "terminal",
                        title = "Terminal",
                        icon = ReactiveIcon.Terminal,
                        requiredCapabilities = setOf(CodecksCapability.MacCommand),
                    ),
                    ReactiveCatalogControlSpec(
                        actionId = "github",
                        title = "GitHub",
                        icon = ReactiveIcon.Browser,
                        requiredCapabilities = setOf(CodecksCapability.MacCommand),
                    ),
                    ReactiveCatalogControlSpec(
                        actionId = "dev_tools",
                        title = "Dev Tools",
                        icon = ReactiveIcon.Generic,
                        requiredCapabilities = setOf(CodecksCapability.MacCommand),
                    ),
                ).map { spec ->
                    spec.copy(resolvedActionRevision = actionRevisions[spec.actionId])
                },
            ),
            MediaReactiveControlProvider(),
            WindowReactiveControlProvider(),
            SpotlightSftpReactiveControlProvider(),
            MonitorBrightnessReactiveControlProvider(),
            AccessibilityDiscoveryReactiveControlProvider(),
        ),
    )
