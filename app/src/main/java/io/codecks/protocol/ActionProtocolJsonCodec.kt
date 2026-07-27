package io.codecks.protocol

import org.json.JSONArray
import org.json.JSONObject

object ActionProtocolJsonCodec {
    fun decodeActionDefinition(raw: String): ProtocolActionDefinition {
        val root = JSONObject(raw)
        return ProtocolActionDefinition(
            schemaVersion = root.requireString("schemaVersion"),
            id = root.requireActionId("id"),
            title = root.requireString("title"),
            platform = root.requireString("platform"),
            category = root.requireString("category"),
            description = root.requireString("description"),
            inputs = root.optJSONArray("inputs").toObjectList { item ->
                ProtocolActionInput(
                    name = item.requireString("name"),
                    type = item.requireString("type"),
                    required = item.optBoolean("required", false),
                    description = item.optString("description").ifBlank { null },
                )
            },
            effects = root.optJSONArray("effects").toStringList(),
            requiresConfirmation = root.optBoolean("requiresConfirmation", false),
            safety = root.getJSONObject("safety").let { safety ->
                ProtocolActionSafety(
                    level = safety.requireString("level"),
                    notes = safety.optString("notes").ifBlank { null },
                )
            },
            implementation = root.getJSONObject("implementation").let { impl ->
                ProtocolActionImplementation(
                    kind = impl.requireString("kind"),
                    command = impl.optString("command").ifBlank { null },
                )
            },
        ).validated()
    }

    fun encodeActionDefinition(value: ProtocolActionDefinition): String =
        JSONObject()
            .put("schemaVersion", value.schemaVersion)
            .put("id", value.id)
            .put("title", value.title)
            .put("platform", value.platform)
            .put("category", value.category)
            .put("description", value.description)
            .put("inputs", JSONArray().apply {
                value.inputs.forEach { input ->
                    put(
                        JSONObject()
                            .put("name", input.name)
                            .put("type", input.type)
                            .put("required", input.required)
                            .put("description", input.description),
                    )
                }
            })
            .put("effects", JSONArray(value.effects))
            .put("requiresConfirmation", value.requiresConfirmation)
            .put(
                "safety",
                JSONObject()
                    .put("level", value.safety.level)
                    .put("notes", value.safety.notes),
            )
            .put(
                "implementation",
                JSONObject()
                    .put("kind", value.implementation.kind)
                    .put("command", value.implementation.command),
            )
            .toString()

    fun decodeActionInvocation(raw: String): ProtocolActionInvocation {
        val root = JSONObject(raw)
        return ProtocolActionInvocation(
            schemaVersion = root.requireString("schemaVersion"),
            invocationId = root.requireToken("invocationId"),
            actionId = root.requireActionId("actionId"),
            requestedAt = root.requireString("requestedAt"),
            source = root.getJSONObject("source").let { source ->
                ProtocolInvocationSource(
                    deviceId = source.requireString("deviceId"),
                    surface = source.requireString("surface"),
                )
            },
            arguments = root.optJSONObject("arguments").toMapValue(),
            dryRun = root.optBoolean("dryRun", false),
        ).validated()
    }

    fun encodeActionInvocation(value: ProtocolActionInvocation): String =
        JSONObject()
            .put("schemaVersion", value.schemaVersion)
            .put("invocationId", value.invocationId)
            .put("actionId", value.actionId)
            .put("requestedAt", value.requestedAt)
            .put(
                "source",
                JSONObject()
                    .put("deviceId", value.source.deviceId)
                    .put("surface", value.source.surface),
            )
            .put("arguments", JSONObject(value.arguments))
            .put("dryRun", value.dryRun)
            .toString()

    fun decodeActionPlan(raw: String): ProtocolActionPlan {
        val root = JSONObject(raw)
        return ProtocolActionPlan(
            schemaVersion = root.requireString("schemaVersion"),
            planId = root.requireToken("planId"),
            createdAt = root.requireString("createdAt"),
            summary = root.requireString("summary"),
            steps = root.optJSONArray("steps").toObjectList { step ->
                ProtocolActionPlanStep(
                    stepId = step.requireString("stepId"),
                    actionId = step.requireActionId("actionId"),
                    arguments = step.optJSONObject("arguments").toMapValue(),
                    dependsOn = step.optJSONArray("dependsOn").toStringList(),
                )
            },
        ).validated()
    }

    fun encodeActionPlan(value: ProtocolActionPlan): String =
        JSONObject()
            .put("schemaVersion", value.schemaVersion)
            .put("planId", value.planId)
            .put("createdAt", value.createdAt)
            .put("summary", value.summary)
            .put("steps", JSONArray().apply {
                value.steps.forEach { step ->
                    put(
                        JSONObject()
                            .put("stepId", step.stepId)
                            .put("actionId", step.actionId)
                            .put("arguments", JSONObject(step.arguments))
                            .put("dependsOn", JSONArray(step.dependsOn)),
                    )
                }
            })
            .toString()

    fun decodeActionReceipt(raw: String): ProtocolActionReceipt {
        val root = JSONObject(raw)
        return ProtocolActionReceipt(
            schemaVersion = root.requireString("schemaVersion"),
            receiptId = root.requireToken("receiptId"),
            invocationId = root.requireToken("invocationId"),
            actionId = root.requireActionId("actionId"),
            status = ProtocolActionReceiptStatus.decode(root.requireString("status")),
            startedAt = root.requireString("startedAt"),
            finishedAt = root.requireString("finishedAt"),
            message = root.optString("message").ifBlank { null },
            outputs = root.optJSONObject("outputs").toMapValue(),
            error = root.optJSONObject("error")?.let { error ->
                ProtocolActionReceiptError(
                    code = error.requireString("code"),
                    message = error.requireString("message"),
                    retryable = error.opt("retryable")?.let { error.optBoolean("retryable") },
                )
            },
        ).validated()
    }

    fun encodeActionReceipt(value: ProtocolActionReceipt): String =
        JSONObject()
            .put("schemaVersion", value.schemaVersion)
            .put("receiptId", value.receiptId)
            .put("invocationId", value.invocationId)
            .put("actionId", value.actionId)
            .put("status", value.status.encode())
            .put("startedAt", value.startedAt)
            .put("finishedAt", value.finishedAt)
            .put("message", value.message)
            .put("outputs", JSONObject(value.outputs))
            .put(
                "error",
                value.error?.let { error ->
                    JSONObject()
                        .put("code", error.code)
                        .put("message", error.message)
                        .put("retryable", error.retryable)
                },
            )
            .toString()
}

private fun JSONObject.requireString(key: String): String =
    optString(key).takeIf(String::isNotBlank)
        ?: throw ProtocolValidationException("Missing or blank $key")

private fun JSONObject.requireToken(key: String): String =
    requireString(key).also { value ->
        require(TOKEN_REGEX.matches(value)) { "Invalid $key: $value" }
    }

private fun JSONObject.requireActionId(key: String): String =
    requireString(key).also { value ->
        require(ACTION_ID_REGEX.matches(value)) { "Invalid $key: $value" }
    }

private fun JSONArray?.toStringList(): List<String> =
    if (this == null) {
        emptyList()
    } else {
        (0 until length()).mapNotNull { index ->
            optString(index).takeIf(String::isNotBlank)
        }
    }

private fun <T> JSONArray?.toObjectList(mapper: (JSONObject) -> T): List<T> =
    if (this == null) {
        emptyList()
    } else {
        (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let(mapper)
        }
    }

private fun JSONObject?.toMapValue(): Map<String, Any?> =
    if (this == null) {
        emptyMap()
    } else {
        keys().asSequence().associateWith { key ->
            when (val value = opt(key)) {
                null,
                JSONObject.NULL -> null
                is JSONObject -> value.toMapValue()
                is JSONArray -> value.toListValue()
                else -> value
            }
        }
    }

private fun JSONArray.toListValue(): List<Any?> =
    (0 until length()).map { index ->
        when (val value = opt(index)) {
            null,
            JSONObject.NULL -> null
            is JSONObject -> value.toMapValue()
            is JSONArray -> value.toListValue()
            else -> value
        }
    }

private fun ProtocolActionDefinition.validated(): ProtocolActionDefinition {
    require(schemaVersion == SCHEMA_VERSION) { "Unsupported schemaVersion: $schemaVersion" }
    require(inputs.all { it.name.isNotBlank() && it.type.isNotBlank() }) { "Action inputs must be named and typed" }
    return this
}

private fun ProtocolActionInvocation.validated(): ProtocolActionInvocation {
    require(schemaVersion == SCHEMA_VERSION) { "Unsupported schemaVersion: $schemaVersion" }
    return this
}

private fun ProtocolActionPlan.validated(): ProtocolActionPlan {
    require(schemaVersion == SCHEMA_VERSION) { "Unsupported schemaVersion: $schemaVersion" }
    require(steps.isNotEmpty()) { "Action plan must contain at least one step" }
    return this
}

private fun ProtocolActionReceipt.validated(): ProtocolActionReceipt {
    require(schemaVersion == SCHEMA_VERSION) { "Unsupported schemaVersion: $schemaVersion" }
    return this
}

private const val SCHEMA_VERSION = "1.0"
private val TOKEN_REGEX = Regex("^[a-zA-Z0-9_-]{8,80}$")
private val ACTION_ID_REGEX = Regex("^[a-z][a-z0-9]*(\\.[a-z0-9]+)+$")
