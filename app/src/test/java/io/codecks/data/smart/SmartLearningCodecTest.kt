package io.codecks.data.smart

import io.codecks.domain.smart.SmartAppKey
import io.codecks.domain.smart.SmartFeedback
import io.codecks.domain.smart.SmartFeedbackType
import io.codecks.domain.smart.SmartMacId
import io.codecks.domain.smart.SmartSurface
import io.codecks.domain.smart.smartTransitionKey
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLearningCodecTest {
    @Test
    fun encodeWritesSchemaVersionTwo() {
        val encoded = SmartLearningCodec.encode(listOf(feedback(actionId = "finder")))

        assertEquals(SmartLearningCodec.SCHEMA_VERSION, JSONObject(encoded).getInt("schemaVersion"))
    }

    @Test
    fun versionOneRecordMigratesWithSafeOptionalDefaults() {
        val raw = """
            {"schemaVersion":1,"events":[
              {"candidateId":"smart:home:any:finder","actionId":"finder","appKey":"","type":"Success","coarseHourBucket":0,"contextKeys":[],"atMillis":1}
            ]}
        """.trimIndent()

        val migrated = SmartLearningCodec.decode(raw).single()

        assertEquals("smart:deck:any:finder", migrated.candidateId)
        assertEquals(null, migrated.appKey)
        assertEquals(null, migrated.macId)
        assertEquals(null, migrated.success)
        assertEquals(0, migrated.coarseHourBucket)
        assertTrue(migrated.contextKeys.isEmpty())
    }

    @Test
    fun genuineVersionOneTypesMigrateWithIntentionalSemantics() {
        val migrated = SmartLearningCodec.decode(LEGACY_V1_ALL_TYPES)

        assertEquals(
            listOf(
                SmartFeedbackType.Pin,
                SmartFeedbackType.Why,
                SmartFeedbackType.Success,
                SmartFeedbackType.Failure,
                SmartFeedbackType.SuppressHere,
            ),
            migrated.map(SmartFeedback::type),
        )
        assertEquals(
            listOf("pin", "why", "success", "failure", "never"),
            migrated.mapNotNull(SmartFeedback::actionId),
        )
        assertTrue(migrated.all { it.surface == SmartSurface.Deck })
        assertTrue(migrated.all { it.macId == null })
        assertTrue(migrated.all { "notification:mail" !in it.contextKeys })
        assertTrue(migrated.all { event -> event.contextKeys.none { it.startsWith("mac:") } })

        val summary = SmartLearningCodec.summary(migrated, nowMillis = 10_000L)
        assertEquals(setOf("smart:deck:chrome:never"), summary.suppressedContextActionKeys)
        assertEquals(8, summary.actionScores["pin"])
        assertEquals(1, summary.actionScores["why"])
        assertEquals(6, summary.actionScores["success"])
        assertEquals(-8, summary.actionScores["failure"])
        assertFalse(summary.actionScores.containsKey("run"))
        assertFalse(summary.actionScores.containsKey("hide"))
    }

    @Test
    fun legacyRunAndHideAreDroppedInsteadOfBecomingLearningOrPersistentSuppression() {
        val migrated = SmartLearningCodec.decode(LEGACY_V1_ALL_TYPES)

        assertFalse(migrated.any { it.actionId == "run" })
        assertFalse(migrated.any { it.actionId == "hide" })
    }

    @Test
    fun legacyNeverForAppWithoutAppIsDroppedBecauseItPreviouslyHadNoEffect() {
        val raw = """
            {"schemaVersion":1,"events":[
              {"candidateId":"smart:home:any:never","actionId":"never","appKey":"","type":"NeverForApp","coarseHourBucket":12,"contextKeys":["surface:home"],"atMillis":1}
            ]}
        """.trimIndent()

        assertTrue(SmartLearningCodec.decode(raw).isEmpty())
    }

    @Test
    fun productionMigrationReencodesLegacyPayloadAsRoundTrippableVersionTwo() {
        val migratedRaw = SmartLearningCodec.migrateToCurrent(LEGACY_V1_ALL_TYPES)
        val migratedEvents = SmartLearningCodec.decode(migratedRaw)

        assertEquals(2, JSONObject(migratedRaw!!).getInt("schemaVersion"))
        assertEquals(SmartLearningCodec.decode(LEGACY_V1_ALL_TYPES), migratedEvents)
        assertFalse(migratedRaw.contains("mac_legacy_hash"))
        assertFalse(migratedRaw.contains("\"mac:"))
        assertFalse(migratedRaw.contains("notification:mail"))
        assertTrue(migratedEvents.all { event -> event.contextKeys.none { it.startsWith("mac:") } })
        assertEquals(null, SmartLearningCodec.migrateToCurrent(migratedRaw))
    }

    @Test
    fun unsupportedSchemaFailsClosed() {
        val raw = """
            {"schemaVersion":99,"events":[
              {"candidateId":"a","actionId":"a","surface":"Deck","type":"Success","atMillis":1}
            ]}
        """.trimIndent()

        assertTrue(SmartLearningCodec.decode(raw).isEmpty())
    }

    @Test
    fun nonNumericSchemaFailsClosed() {
        val raw = """
            {"schemaVersion":"2","events":[
              {"candidateId":"a","actionId":"a","surface":"Deck","type":"Success","atMillis":1}
            ]}
        """.trimIndent()

        assertTrue(SmartLearningCodec.decode(raw).isEmpty())
    }

    @Test
    fun codecDoesNotPersistRawContentFields() {
        val encoded = SmartLearningCodec.encode(
            listOf(
                feedback(
                    actionId = "open_docs",
                    contextKeys = setOf("macApp:chrome", "hour:12"),
                ),
            ),
        )

        assertTrue(encoded.contains("open_docs"))
        assertFalse(encoded.contains("clipboard"))
        assertFalse(encoded.contains("typed text"))
        assertFalse(encoded.contains("command"))
        assertFalse(encoded.contains("private-path"))
    }

    @Test
    fun codecDoesNotPersistConnectionIdentifiersOrNotificationSourceKeys() {
        val encoded = SmartLearningCodec.encode(
            listOf(
                feedback(
                    actionId = "finder",
                    contextKeys = setOf("macApp:chrome", "phone:phone"),
                ),
            ),
        )

        assertFalse(encoded.contains("user"))
        assertFalse(encoded.contains("host"))
        assertFalse(encoded.contains("port"))
        assertFalse(encoded.contains("hostKey"))
        assertFalse(encoded.contains("notificationSource"))
    }

    @Test
    fun decodeDropsLegacyConnectionFieldsAndNotificationSourceKeys() {
        val raw = """
            {"schemaVersion":1,"events":[
              {"candidateId":"smart:home:chrome:finder","actionId":"finder","appKey":"chrome","type":"Success","success":true,"coarseHourBucket":12,"atMillis":1,"user":"alice","hostname":"mac.local","port":22,"hostKey":"ssh-ed25519 AAAA","notificationSourceKeys":["mail"],"contextKeys":["surface:home","macApp:chrome","notification:mail"]}
            ]}
        """.trimIndent()

        val decoded = SmartLearningCodec.decode(raw)
        val encoded = SmartLearningCodec.encode(decoded)

        assertEquals(1, decoded.size)
        assertEquals("finder", decoded.first().actionId)
        assertFalse(encoded.contains("user"))
        assertFalse(encoded.contains("hostname"))
        assertFalse(encoded.contains("port\":22"))
        assertFalse(encoded.contains("hostKey"))
        assertFalse(encoded.contains("notificationSourceKeys"))
        assertFalse(encoded.contains("notification:mail"))
        assertFalse(encoded.contains("user:\""))
    }

    @Test
    fun opaqueTargetIdIsEncodedAndDecodedWithoutNormalization() {
        val opaqueTargetId = "mac_1f2e3d4c_opaque"

        val encoded = SmartLearningCodec.encode(
            listOf(
                feedback(
                    actionId = "finder",
                    appKey = "chrome",
                    surface = SmartSurface.Deck,
                    macId = opaqueTargetId,
                    contextKeys = setOf("macApp:chrome"),
                ),
            ),
        )

        val decoded = SmartLearningCodec.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals(opaqueTargetId, decoded.first().macId?.value)
    }

    @Test
    fun codecRoundTripsTypedSmartFields() {
        val encoded = SmartLearningCodec.encode(
            listOf(
                feedback(
                    actionId = "finder",
                    appKey = "chrome",
                    surface = SmartSurface.Trackpad,
                    macId = "mac_test",
                ),
            ),
        )

        val decoded = SmartLearningCodec.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals("chrome", decoded[0].appKey?.value)
        assertEquals(SmartSurface.Trackpad, decoded[0].surface)
        assertEquals("mac_test", decoded[0].macId?.value)
    }

    @Test
    fun corruptPayloadRecoversToEmpty() {
        assertEquals(emptyList<SmartFeedback>(), SmartLearningCodec.decode("{not-json"))
    }

    @Test
    fun corruptPayloadSkipsUnknownSurfaceRecords() {
        val encoded = """
            {"schemaVersion":2,"events":[
              {"candidateId":"a","actionId":"a","appKey":"chrome","surface":"Unknown","type":"Success","coarseHourBucket":12,"atMillis":1},
              {"candidateId":"b","actionId":"b","appKey":"chrome","surface":"Deck","type":"Success","coarseHourBucket":12,"atMillis":2}
            ]}
        """.trimIndent()

        val decoded = SmartLearningCodec.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals("b", decoded.first().candidateId)
    }

    @Test
    fun summaryAppliesRetentionAndClearEquivalent() {
        val now = SmartLearningCodec.RETENTION_MS + 10_000L
        val fresh = feedback(actionId = "fresh", atMillis = now - 1_000L, type = SmartFeedbackType.Success)
        val old = feedback(actionId = "old", atMillis = 0L, type = SmartFeedbackType.Success)

        val summary = SmartLearningCodec.summary(listOf(fresh, old), now)

        assertEquals(6, summary.actionScores["fresh"])
        assertFalse(summary.actionScores.containsKey("old"))
        assertEquals(SmartLearningCodec.summary(emptyList(), now), SmartLearningCodec.summary(SmartLearningCodec.decode(null), now))
    }

    @Test
    fun neverGlobalStoresActionId() {
        val summary = SmartLearningCodec.summary(
            listOf(feedback(actionId = "reload", appKey = "chrome", type = SmartFeedbackType.NeverGlobal)),
            nowMillis = 2_000L,
        )

        assertEquals(setOf("reload"), summary.globallySuppressedActionIds)
    }

    @Test
    fun suppressHereWithoutActiveAppUsesCanonicalAnyScope() {
        val summary = SmartLearningCodec.summary(
            listOf(
                feedback(
                    actionId = "reload",
                    appKey = null,
                    type = SmartFeedbackType.SuppressHere,
                ),
            ),
            nowMillis = 2_000L,
        )

        assertEquals(setOf("smart:deck:any:reload"), summary.suppressedContextActionKeys)
    }

    @Test
    fun successAndFailureCarryLearningScore() {
        val summary = SmartLearningCodec.summary(
            listOf(
                feedback(actionId = "reload", type = SmartFeedbackType.Success, atMillis = 1_000L),
                feedback(actionId = "broken", type = SmartFeedbackType.Failure, atMillis = 2_000L),
            ),
            nowMillis = 3_000L,
        )

        assertEquals(6, summary.actionScores["reload"])
        assertEquals(-8, summary.actionScores["broken"])
    }

    @Test
    fun successHistoryBuildsTransitionScores() {
        val summary = SmartLearningCodec.summary(
            listOf(
                feedback(actionId = "calendar", type = SmartFeedbackType.Success, atMillis = 1_000L),
                feedback(actionId = "notes", type = SmartFeedbackType.Success, atMillis = 2_000L),
            ),
            nowMillis = 3_000L,
        )

        val expectedKey = smartTransitionKey(
            surface = SmartSurface.Deck,
            appKey = SmartAppKey("chrome"),
            macId = SmartMacId("mac_123"),
            previousActionId = "calendar",
            nextActionId = "notes",
        )
        assertEquals(10, summary.transitionScores[expectedKey])
    }

    @Test
    fun neverGlobalDoesNotSuppressOtherActions() {
        val summary = SmartLearningCodec.summary(
            listOf(feedback(actionId = "reload", type = SmartFeedbackType.NeverGlobal)),
            nowMillis = 2_000L,
        )

        assertFalse("notes" in summary.globallySuppressedActionIds)
    }

    @Test
    fun transitionRequiresMatchingSurfaceAppAndMac() {
        val summary = SmartLearningCodec.summary(
            listOf(
                feedback(
                    actionId = "reload",
                    appKey = "chrome",
                    surface = SmartSurface.Deck,
                    macId = "mac_123",
                    atMillis = 1_000L,
                    type = SmartFeedbackType.Success,
                ),
                feedback(
                    actionId = "notes",
                    appKey = "chrome",
                    surface = SmartSurface.Trackpad,
                    macId = "mac_123",
                    atMillis = 2_000L,
                    type = SmartFeedbackType.Success,
                ),
                feedback(
                    actionId = "finder",
                    appKey = "safari",
                    surface = SmartSurface.Deck,
                    macId = "mac_123",
                    atMillis = 3_000L,
                    type = SmartFeedbackType.Success,
                ),
                feedback(
                    actionId = "terminal",
                    appKey = "chrome",
                    surface = SmartSurface.Deck,
                    macId = "mac_456",
                    atMillis = 4_000L,
                    type = SmartFeedbackType.Success,
                ),
            ),
            nowMillis = 5_000L,
        )

        assertTrue(summary.transitionScores.isEmpty())
    }

    @Test
    fun transitionRequiresGapWithinFiveMinutes() {
        val summary = SmartLearningCodec.summary(
            listOf(
                feedback(actionId = "reload", atMillis = 1_000L, type = SmartFeedbackType.Success),
                feedback(actionId = "notes", atMillis = 400_000L, type = SmartFeedbackType.Success),
            ),
            nowMillis = 401_000L,
        )

        assertTrue(summary.transitionScores.isEmpty())
    }

    private fun feedback(
        actionId: String,
        appKey: String? = "chrome",
        surface: SmartSurface = SmartSurface.Deck,
        macId: String? = "mac_123",
        contextKeys: Set<String> = setOf("macApp:chrome"),
        type: SmartFeedbackType = SmartFeedbackType.Success,
        atMillis: Long = 1_000L,
    ) = SmartFeedback(
        candidateId = "smart:$actionId",
        actionId = actionId,
        appKey = appKey?.let { SmartAppKey(it) },
        surface = surface,
        macId = macId?.let { SmartMacId(it) },
        type = type,
        success = null,
        coarseHourBucket = 12,
        contextKeys = contextKeys,
        atMillis = atMillis,
    )

    private companion object {
        // Exact v1 encoder shape: no surface or macId fields; old enum names.
        val LEGACY_V1_ALL_TYPES = """
            {"schemaVersion":1,"events":[
              {"candidateId":"smart:home:chrome:run","actionId":"run","appKey":"chrome","type":"Run","coarseHourBucket":12,"contextKeys":["hour:12","mac:mac_legacy_hash","macApp:chrome","notification:mail","phone:desktop","surface:home"],"atMillis":1},
              {"candidateId":"smart:home:chrome:pin","actionId":"pin","appKey":"chrome","type":"Pin","coarseHourBucket":12,"contextKeys":["hour:12","mac:mac_legacy_hash","macApp:chrome","notification:mail","phone:desktop","surface:home"],"atMillis":2},
              {"candidateId":"smart:home:chrome:hide","actionId":"hide","appKey":"chrome","type":"Hide","coarseHourBucket":12,"contextKeys":["hour:12","mac:mac_legacy_hash","macApp:chrome","notification:mail","phone:desktop","surface:home"],"atMillis":3},
              {"candidateId":"smart:home:chrome:why","actionId":"why","appKey":"chrome","type":"Why","coarseHourBucket":12,"contextKeys":["hour:12","mac:mac_legacy_hash","macApp:chrome","notification:mail","phone:desktop","surface:home"],"atMillis":4},
              {"candidateId":"smart:home:chrome:success","actionId":"success","appKey":"chrome","type":"Success","success":true,"coarseHourBucket":12,"contextKeys":["hour:12","mac:mac_legacy_hash","macApp:chrome","notification:mail","phone:desktop","surface:home"],"atMillis":5},
              {"candidateId":"smart:home:chrome:failure","actionId":"failure","appKey":"chrome","type":"Failure","success":false,"coarseHourBucket":12,"contextKeys":["hour:12","mac:mac_legacy_hash","macApp:chrome","notification:mail","phone:desktop","surface:home"],"atMillis":6},
              {"candidateId":"smart:home:chrome:never","actionId":"never","appKey":"chrome","type":"NeverForApp","coarseHourBucket":12,"contextKeys":["hour:12","mac:mac_legacy_hash","macApp:chrome","notification:mail","phone:desktop","surface:home"],"atMillis":7}
            ]}
        """.trimIndent()
    }
}
