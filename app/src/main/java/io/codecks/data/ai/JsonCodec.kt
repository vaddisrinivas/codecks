package io.codecks.data.ai

internal sealed interface JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue

    data class Arr(val items: List<JsonValue>) : JsonValue

    data class Str(val value: String) : JsonValue

    data class Num(val value: Double) : JsonValue

    data class Bool(val value: Boolean) : JsonValue

    data object Null : JsonValue
}

internal fun parseJsonObject(text: String): JsonObject = JsonParser(text).parse().asObject()

internal fun parseJsonArray(text: String): List<JsonValue> =
    when (val value = JsonParser(text).parse()) {
        is JsonValue.Arr -> value.items
        else -> error("Expected JSON array")
    }

internal fun jsonObject(vararg pairs: Pair<String, Any?>): String = stringifyJson(JsonValue.Obj(pairs.toMap().mapValues { toJsonValue(it.value) }))

internal fun jsonArray(values: List<Any?>): JsonValue.Arr = JsonValue.Arr(values.map(::toJsonValue))

internal fun jsonArrayString(values: List<Any?>): String = stringifyJson(jsonArray(values))

internal fun jsonValueString(value: JsonValue): String = stringifyJson(value)

internal class JsonObject(private val fields: Map<String, JsonValue>) {
    fun string(name: String): String = optString(name) ?: error("Missing string field $name")

    fun optString(name: String): String? = (fields[name] as? JsonValue.Str)?.value

    fun int(name: String, default: Int): Int = ((fields[name] as? JsonValue.Num)?.value ?: default.toDouble()).toInt()

    fun long(name: String, default: Long): Long = ((fields[name] as? JsonValue.Num)?.value ?: default.toDouble()).toLong()

    fun bool(name: String, default: Boolean = false): Boolean = (fields[name] as? JsonValue.Bool)?.value ?: default

    fun has(name: String): Boolean = fields.containsKey(name)

    fun obj(name: String): JsonObject = fields[name]?.asObject() ?: error("Missing object field $name")

    fun optObj(name: String): JsonObject? = (fields[name] as? JsonValue.Obj)?.let { JsonObject(it.fields) }

    fun array(name: String): List<JsonValue> = (fields[name] as? JsonValue.Arr)?.items ?: emptyList()
}

internal fun JsonValue.asObject(): JsonObject =
    when (this) {
        is JsonValue.Obj -> JsonObject(fields)
        else -> error("Expected JSON object")
    }

private fun toJsonValue(value: Any?): JsonValue =
    when (value) {
        null -> JsonValue.Null
        is JsonValue -> value
        is String -> JsonValue.Str(value)
        is Boolean -> JsonValue.Bool(value)
        is Int -> JsonValue.Num(value.toDouble())
        is Long -> JsonValue.Num(value.toDouble())
        is Double -> JsonValue.Num(value)
        is Float -> JsonValue.Num(value.toDouble())
        is Map<*, *> -> JsonValue.Obj(value.entries.associate { (key, item) -> key.toString() to toJsonValue(item) })
        is List<*> -> JsonValue.Arr(value.map(::toJsonValue))
        else -> error("Unsupported JSON value type ${value::class.java.name}")
    }

private fun stringifyJson(value: JsonValue): String =
    when (value) {
        is JsonValue.Obj -> value.fields.entries.joinToString(prefix = "{", postfix = "}") { (key, item) ->
            "${escapeJson(key)}:${stringifyJson(item)}"
        }
        is JsonValue.Arr -> value.items.joinToString(prefix = "[", postfix = "]", transform = ::stringifyJson)
        is JsonValue.Str -> escapeJson(value.value)
        is JsonValue.Num -> {
            val longValue = value.value.toLong()
            if (longValue.toDouble() == value.value) longValue.toString() else value.value.toString()
        }
        is JsonValue.Bool -> value.value.toString()
        JsonValue.Null -> "null"
    }

private fun escapeJson(value: String): String =
    buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

private class JsonParser(private val source: String) {
    private var index = 0

    fun parse(): JsonValue {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        require(index == source.length) { "Unexpected trailing JSON content" }
        return value
    }

    private fun parseValue(): JsonValue =
        when (peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.Str(parseString())
            't' -> parseLiteral("true", JsonValue.Bool(true))
            'f' -> parseLiteral("false", JsonValue.Bool(false))
            'n' -> parseLiteral("null", JsonValue.Null)
            else -> parseNumber()
        }

    private fun parseObject(): JsonValue.Obj {
        expect('{')
        skipWhitespace()
        val fields = linkedMapOf<String, JsonValue>()
        if (peek() == '}') {
            index++
            return JsonValue.Obj(fields)
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            fields[key] = parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    index++
                    skipWhitespace()
                }
                '}' -> {
                    index++
                    return JsonValue.Obj(fields)
                }
                else -> error("Expected ',' or '}'")
            }
        }
    }

    private fun parseArray(): JsonValue.Arr {
        expect('[')
        skipWhitespace()
        val items = mutableListOf<JsonValue>()
        if (peek() == ']') {
            index++
            return JsonValue.Arr(items)
        }
        while (true) {
            items += parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    index++
                    skipWhitespace()
                }
                ']' -> {
                    index++
                    return JsonValue.Arr(items)
                }
                else -> error("Expected ',' or ']'")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (index < source.length) {
            val ch = source[index++]
            when (ch) {
                '"' -> return builder.toString()
                '\\' -> builder.append(
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> escaped
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            val hex = source.substring(index, index + 4)
                            index += 4
                            hex.toInt(16).toChar()
                        }
                        else -> error("Unsupported escape sequence \\$escaped")
                    },
                )
                else -> builder.append(ch)
            }
        }
        error("Unterminated string")
    }

    private fun parseLiteral(literal: String, value: JsonValue): JsonValue {
        require(source.startsWith(literal, index)) { "Expected $literal" }
        index += literal.length
        return value
    }

    private fun parseNumber(): JsonValue.Num {
        val start = index
        while (index < source.length && source[index] in "-+0123456789.eE") index++
        return JsonValue.Num(source.substring(start, index).toDouble())
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) index++
    }

    private fun expect(ch: Char) {
        require(peek() == ch) { "Expected '$ch'" }
        index++
    }

    private fun peek(): Char = source.getOrElse(index) { error("Unexpected end of JSON") }
}
