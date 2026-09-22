#!/usr/bin/env python3
"""Dependency-free executor for the closed JSON Schema vocabulary used by evidence receipts."""

from __future__ import annotations

import json
import re
from typing import Any

SUPPORTED = {
    "$schema", "$id", "$defs", "$ref", "type", "const", "enum", "allOf",
    "required", "properties", "additionalProperties", "minProperties", "maxProperties",
    "minItems", "maxItems", "uniqueItems", "prefixItems", "items",
    "minLength", "maxLength", "pattern",
}


def _resolve(root: dict, reference: str) -> Any:
    if not reference.startswith("#/"):
        raise ValueError(f"external JSON Schema reference forbidden: {reference}")
    value: Any = root
    for token in reference[2:].split("/"):
        token = token.replace("~1", "/").replace("~0", "~")
        if not isinstance(value, dict) or token not in value:
            raise ValueError(f"unresolved JSON Schema reference: {reference}")
        value = value[token]
    return value


def validate_schema_document(schema: dict) -> None:
    if not isinstance(schema, dict):
        raise ValueError("JSON Schema root must be an object")

    def walk(node: Any) -> None:
        if isinstance(node, bool):
            return
        if not isinstance(node, dict):
            raise ValueError("JSON Schema node must be an object or boolean")
        unknown = set(node) - SUPPORTED
        if unknown:
            raise ValueError(f"unsupported JSON Schema keywords: {sorted(unknown)}")
        if "$ref" in node:
            _resolve(schema, node["$ref"])
        for key in ("properties", "$defs"):
            if key in node:
                if not isinstance(node[key], dict):
                    raise ValueError(f"JSON Schema {key} must be an object")
                for child in node[key].values():
                    walk(child)
        for key in ("allOf", "prefixItems"):
            if key in node:
                if not isinstance(node[key], list):
                    raise ValueError(f"JSON Schema {key} must be an array")
                for child in node[key]:
                    walk(child)
        if isinstance(node.get("additionalProperties"), dict):
            walk(node["additionalProperties"])
        if "items" in node:
            walk(node["items"])

    walk(schema)


def validate_json_schema(instance: Any, schema: dict) -> None:
    validate_schema_document(schema)

    def fail(path: str, reason: str) -> None:
        raise ValueError(f"JSON Schema violation at {path}: {reason}")

    def validate(value: Any, node: Any, path: str) -> None:
        if node is False:
            fail(path, "value forbidden")
        if node is True:
            return
        if "$ref" in node:
            validate(value, _resolve(schema, node["$ref"]), path)
        for child in node.get("allOf", []):
            validate(value, child, path)
        if "const" in node and value != node["const"]:
            fail(path, "const mismatch")
        if "enum" in node and value not in node["enum"]:
            fail(path, "enum mismatch")

        expected_type = node.get("type")
        matches = {
            "object": isinstance(value, dict),
            "array": isinstance(value, list),
            "string": isinstance(value, str),
            "integer": isinstance(value, int) and not isinstance(value, bool),
            "number": isinstance(value, (int, float)) and not isinstance(value, bool),
            "boolean": isinstance(value, bool),
            "null": value is None,
        }
        if expected_type is not None:
            accepted = expected_type if isinstance(expected_type, list) else [expected_type]
            if not any(matches.get(item, False) for item in accepted):
                fail(path, f"expected type {accepted}")

        if isinstance(value, dict):
            required = node.get("required", [])
            missing = set(required) - set(value)
            if missing:
                fail(path, f"missing keys {sorted(missing)}")
            if len(value) < node.get("minProperties", 0) or len(value) > node.get("maxProperties", len(value)):
                fail(path, "property count out of bounds")
            properties = node.get("properties", {})
            for key, child_value in value.items():
                if key in properties:
                    validate(child_value, properties[key], f"{path}.{key}")
                elif node.get("additionalProperties") is False:
                    fail(path, f"unknown key {key}")
                elif isinstance(node.get("additionalProperties"), dict):
                    validate(child_value, node["additionalProperties"], f"{path}.{key}")

        if isinstance(value, list):
            if len(value) < node.get("minItems", 0) or len(value) > node.get("maxItems", len(value)):
                fail(path, "item count out of bounds")
            if node.get("uniqueItems") and len({json.dumps(item, sort_keys=True) for item in value}) != len(value):
                fail(path, "items are not unique")
            prefix = node.get("prefixItems", [])
            for index, child in enumerate(prefix[:len(value)]):
                validate(value[index], child, f"{path}[{index}]")
            if len(value) > len(prefix) and "items" in node:
                for index in range(len(prefix), len(value)):
                    validate(value[index], node["items"], f"{path}[{index}]")
            elif not prefix and isinstance(node.get("items"), dict):
                for index, child_value in enumerate(value):
                    validate(child_value, node["items"], f"{path}[{index}]")

        if isinstance(value, str):
            if len(value) < node.get("minLength", 0) or len(value) > node.get("maxLength", len(value)):
                fail(path, "string length out of bounds")
            if "pattern" in node and re.search(node["pattern"], value) is None:
                fail(path, "pattern mismatch")

    validate(instance, schema, "$")
