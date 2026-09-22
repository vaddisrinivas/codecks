package io.codecks.ui.theme

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSystemSurfacePropagationTest {
    private var registered: ThemeActiveNotificationRefresher? = null

    @After
    fun tearDown() {
        registered?.let(ThemeActiveNotificationRegistry::unregister)
    }

    @Test
    fun `apply mirrors fresh colors before existing widget and active notification refresh`() {
        val expected = ThemeBundle(ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Cyber })
        var mirrored: ThemeBundle? = null
        var widgetObserved: ThemeBundle? = null
        var notificationObserved: ThemeBundle? = null
        var dispatchedIds = IntArray(0)
        val active = ThemeActiveNotificationRefresher { notificationObserved = mirrored }
        registered = active
        ThemeActiveNotificationRegistry.registerAndRefresh(active)
        val widgets = ThemeExistingWidgetRefresher(
            inventory = ThemeWidgetInventory { intArrayOf(7, 11) },
            sender = ThemeWidgetUpdateSender { ids ->
                dispatchedIds = ids
                widgetObserved = mirrored
            },
        )

        val receipt = ThemeSystemSurfacePropagator(
            writer = ThemeSystemSurfaceWriter { bundle -> mirrored = bundle; true },
            widgets = widgets,
            notificationRefresh = ThemeActiveNotificationRegistry::refreshIfActive,
        ).propagateApplied(expected)

        assertEquals(expected, mirrored)
        assertEquals(expected, widgetObserved)
        assertEquals(expected, notificationObserved)
        assertArrayEquals(intArrayOf(7, 11), dispatchedIds)
        assertEquals(ThemeSystemPropagationReceipt(true, true, true), receipt)
    }

    @Test
    fun `load reconciliation repairs mirror without widget or notification side effects`() {
        val expected = ThemeBundle(ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Aurora })
        var mirrored: ThemeBundle? = null
        var widgetCalls = 0
        var notificationCalls = 0
        val propagator = ThemeSystemSurfacePropagator(
            writer = ThemeSystemSurfaceWriter { bundle -> mirrored = bundle; true },
            widgets = ThemeExistingWidgetRefresher(
                ThemeWidgetInventory { intArrayOf(3) },
                ThemeWidgetUpdateSender { widgetCalls++ },
            ),
            notificationRefresh = { notificationCalls++; true },
        )

        assertTrue(propagator.reconcilePersisted(expected))
        assertEquals(expected, mirrored)
        assertEquals(0, widgetCalls)
        assertEquals(0, notificationCalls)
    }

    @Test
    fun `inactive notification and absent widget remain inert`() {
        var widgetCalls = 0
        val propagator = ThemeSystemSurfacePropagator(
            writer = ThemeSystemSurfaceWriter { true },
            widgets = ThemeExistingWidgetRefresher(
                ThemeWidgetInventory { IntArray(0) },
                ThemeWidgetUpdateSender { widgetCalls++ },
            ),
            notificationRefresh = ThemeActiveNotificationRegistry::refreshIfActive,
        )

        val receipt = propagator.propagateApplied(ThemeBundle(ThemePresetCatalog.default))

        assertTrue(receipt.mirrorUpdated)
        assertFalse(receipt.existingWidgetsUpdated)
        assertFalse(receipt.activeNotificationUpdated)
        assertEquals(0, widgetCalls)
    }

    @Test
    fun `unregistered notification cannot be refreshed after service destruction`() {
        var refreshes = 0
        val active = ThemeActiveNotificationRefresher { refreshes++ }
        assertTrue(ThemeActiveNotificationRegistry.registerAndRefresh(active))
        assertTrue(ThemeActiveNotificationRegistry.refreshIfActive())
        ThemeActiveNotificationRegistry.unregister(active)
        registered = null

        assertFalse(ThemeActiveNotificationRegistry.refreshIfActive())
        assertEquals(2, refreshes)
    }

    @Test
    fun `late destruction of replaced service cannot unregister fresh notification refresher`() {
        var oldRefreshes = 0
        var freshRefreshes = 0
        val old = ThemeActiveNotificationRefresher { oldRefreshes++ }
        val fresh = ThemeActiveNotificationRefresher { freshRefreshes++ }
        assertTrue(ThemeActiveNotificationRegistry.registerAndRefresh(old))
        assertTrue(ThemeActiveNotificationRegistry.registerAndRefresh(fresh))
        registered = fresh

        ThemeActiveNotificationRegistry.unregister(old)

        assertTrue(ThemeActiveNotificationRegistry.refreshIfActive())
        assertEquals(1, oldRefreshes)
        assertEquals(2, freshRefreshes)
    }

    @Test
    fun `apply between foreground creation and registration is reconciled synchronously`() {
        val stale = ThemeBundle(ThemePresetCatalog.default)
        val applied = ThemeBundle(ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Cyber })
        var mirrored = stale
        var foregroundNotification = mirrored
        val serviceRefresher = ThemeActiveNotificationRefresher { foregroundNotification = mirrored }
        registered = serviceRefresher
        val propagator = ThemeSystemSurfacePropagator(
            writer = ThemeSystemSurfaceWriter { bundle -> mirrored = bundle; true },
            widgets = ThemeExistingWidgetRefresher(
                ThemeWidgetInventory { IntArray(0) },
                ThemeWidgetUpdateSender { error("No widget exists") },
            ),
            notificationRefresh = ThemeActiveNotificationRegistry::refreshIfActive,
        )

        // Service has built its initial foreground notification, but has not registered yet.
        val applyReceipt = propagator.propagateApplied(applied)
        assertFalse(applyReceipt.activeNotificationUpdated)
        assertEquals(stale, foregroundNotification)

        assertTrue(ThemeActiveNotificationRegistry.registerAndRefresh(serviceRefresher))
        assertEquals(applied, foregroundNotification)
    }

    @Test
    fun `mirror failure blocks consumers from reading stale colors`() {
        var widgetCalls = 0
        var notificationCalls = 0
        val receipt = ThemeSystemSurfacePropagator(
            writer = ThemeSystemSurfaceWriter { false },
            widgets = ThemeExistingWidgetRefresher(
                ThemeWidgetInventory { intArrayOf(9) },
                ThemeWidgetUpdateSender { widgetCalls++ },
            ),
            notificationRefresh = { notificationCalls++; true },
        ).propagateApplied(ThemeBundle(ThemePresetCatalog.default))

        assertEquals(ThemeSystemPropagationReceipt(false, false, false), receipt)
        assertEquals(0, widgetCalls)
        assertEquals(0, notificationCalls)
    }
}
