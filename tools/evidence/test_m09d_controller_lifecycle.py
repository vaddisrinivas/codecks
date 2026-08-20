#!/usr/bin/env python3

from __future__ import annotations

import copy
import json
from pathlib import Path
import stat
import tempfile
import unittest
import warnings
import xml.etree.ElementTree as ET
import zipfile

from collect_m09d_controller_lifecycle import (
    ARTIFACT_MANIFEST, BASE_COMMIT, C1_PATHS, C2_PATHS, C3_PATHS, CLASS_NAME, EXEC_ENV,
    CHECKSUM_RECEIPT_SHA256, CHECKSUM_RECEIPT_URL, GRADLE_DISTRIBUTION_SHA256,
    GRADLE_PROPERTIES_SHA256,
    COMMAND, METHODS, ROOT, SOURCE_BINDINGS, canonical_junit, parse_junit,
    manifest_sha,
    require_source_bindings, require_wrapper_checksum_property, sanitize_log, tree_digest, validate_artifact_data,
    jdk_distribution_manifest, validate_command_header, validate_gradle_properties_bytes, validate_no_shrink, validate_pre_post,
    validate_source_snapshot_pair,
    validate_source_binding_values, validate_status_bytes, validate_topology, validate_working_bytes, zip_manifest,
)
from strict_json_schema import validate_json_schema


def junit_bytes(
    class_name: str = CLASS_NAME,
    methods: tuple[str, ...] = METHODS,
    tests: int = 10,
    failures: int = 0,
    errors: int = 0,
    skipped: int = 0,
) -> bytes:
    root = ET.Element("testsuite", {
        "name": class_name, "tests": str(tests), "failures": str(failures),
        "errors": str(errors), "skipped": str(skipped), "timestamp": "unstable", "time": "99",
    })
    for method in methods:
        ET.SubElement(root, "testcase", {"classname": class_name, "name": method, "time": "1"})
    return ET.tostring(root)


class M09DControllerLifecycleEvidenceTest(unittest.TestCase):
    def test_exact_xml_is_canonicalized_without_unstable_fields(self) -> None:
        canonical = canonical_junit(junit_bytes())
        self.assertEqual(sorted(METHODS), parse_junit(canonical))
        self.assertNotIn(b"timestamp", canonical)
        self.assertNotIn(b"time=", canonical)

    def test_xml_entity_and_doctype_are_rejected(self) -> None:
        for payload in (b"<!DOCTYPE x>" + junit_bytes(), b"<!ENTITY x 'y'>" + junit_bytes()):
            with self.assertRaises(ValueError):
                parse_junit(payload)

    def test_class_method_count_and_skip_mutations_are_rejected(self) -> None:
        mutations = (
            junit_bytes(class_name="swapped.Class"),
            junit_bytes(methods=METHODS[:-1] + ("swappedMethod",)),
            junit_bytes(tests=9),
            junit_bytes(skipped=1),
        )
        for payload in mutations:
            with self.assertRaises(ValueError):
                parse_junit(payload)

    def test_command_header_substitution_is_rejected(self) -> None:
        good = sanitize_log(b"> Task :app:validateReleaseSurface\n> Task :app:testOssReleaseUnitTest\nBUILD SUCCESSFUL in 1s\n")
        validate_command_header(good)
        with self.assertRaises(ValueError):
            validate_command_header(good.replace(b"--no-daemon", b"--daemon"))
        with self.assertRaises(ValueError):
            sanitize_log(b"> Task :app:validateReleaseSurface /Users/private\nBUILD SUCCESSFUL\n")

    def test_bridge_and_undo_source_mutations_are_rejected(self) -> None:
        values = {path: (ROOT / path).read_text(encoding="utf-8") for path, *_ in SOURCE_BINDINGS}
        validate_source_binding_values(values)
        for path, token in (
            ("app/src/main/java/io/codecks/AppCompositionRoot.kt", "placeOnDeck = homeViewModel::requestArtifactPlacement"),
            ("app/src/main/java/io/codecks/ui/home/HomeViewModel.kt", "fun undoLastDeckEdit"),
        ):
            changed = dict(values); changed[path] = changed[path].replace(token, "removed", 1)
            with self.assertRaises(ValueError):
                validate_source_binding_values(changed)
        valid_release = "buildTypes {\n    release {\n        isMinifyEnabled = false\n        isShrinkResources = false\n        }\n}\n"
        validate_no_shrink(valid_release)
        for changed in (valid_release.replace("isMinifyEnabled = false", "isMinifyEnabled = true"), valid_release.replace("isShrinkResources = false", "isShrinkResources = true")):
            with self.assertRaises(ValueError):
                validate_no_shrink(changed)

    def test_stale_swapped_and_path_topology_are_rejected(self) -> None:
        source, artifact = "1" * 40, "2" * 40
        validate_topology(source, BASE_COMMIT, C1_PATHS, artifact, source, C2_PATHS, artifact, C3_PATHS)
        mutations = (
            ("0" * 40, C1_PATHS, artifact, source, C2_PATHS, artifact, C3_PATHS),
            (BASE_COMMIT, C1_PATHS, artifact, artifact, C2_PATHS, artifact, C3_PATHS),
            (BASE_COMMIT, C1_PATHS, artifact, source, C3_PATHS, artifact, C3_PATHS),
            (BASE_COMMIT, C1_PATHS, artifact, source, C2_PATHS, source, C3_PATHS),
        )
        for source_parent, c1, artifact_commit, artifact_parent, c2, receipt_parent, c3 in mutations:
            with self.assertRaises(ValueError):
                validate_topology(source, source_parent, c1, artifact_commit, artifact_parent, c2, receipt_parent, c3)
        validate_pre_post({"sha": "same"}, {"sha": "same"}, "source")
        with self.assertRaisesRegex(ValueError, "changed during execution"):
            validate_pre_post({"sha": "before"}, {"sha": "after"}, "source")

    def test_artifact_command_class_method_source_and_claim_swaps_are_rejected(self) -> None:
        source = "1" * 40
        distribution_manifest = [
            {"path": "bin/gradle", "sha256": "1" * 64},
            {"path": "lib/gradle-launcher-9.4.1.jar", "sha256": "2" * 64},
        ]
        wrapper_manifest = [{"path": "gradle-9.4.1/LICENSE", "sha256": "6" * 64}]
        cache_manifest = [{"path": "module.jar", "sha256": "3" * 64}]
        jdk_manifest = [{"path": "bin/java", "sha256": "4" * 64}]
        sdk_manifests = [
            {"root": root, "files": 1, "bytes": 1, "sha256": manifest_sha([{"path": "file", "sha256": "8" * 64}]), "manifest": [{"path": "file", "sha256": "8" * 64}]}
            for root in ("$ANDROID_HOME/platforms/android-37.0", "$ANDROID_HOME/build-tools/36.0.0", "$ANDROID_HOME/platform-tools")
        ]
        gradle_home = {"root": "$GRADLE_USER_HOME", "files": 1, "bytes": 1, "sha256": manifest_sha([{"path": "file", "sha256": "9" * 64}]), "manifest": [{"path": "file", "sha256": "9" * 64}]}
        source_snapshot = {"head": source, "headTree": "2" * 40, "indexTree": "2" * 40, "status": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "c1": "c" * 64}
        data = {
            "schema": "codecks.m09d.controller-lifecycle-artifacts.v1", "sourceCommit": source,
            "command": list(COMMAND), "environment": EXEC_ENV,
            "className": CLASS_NAME, "methods": sorted(METHODS),
            "junit": {"path": "tasks/test-evidence/m09d-controller-lifecycle/junit.xml", "sha256": "a" * 64},
            "log": {"path": "tasks/test-evidence/m09d-controller-lifecycle/gradle.log", "sha256": "b" * 64},
            "compiledTrees": [
                {"root": "app/build/intermediates/built_in_kotlinc/ossRelease/compileOssReleaseKotlin/classes", "files": 1, "bytes": 1, "sha256": "c" * 64},
                {"root": "app/build/intermediates/built_in_kotlinc/ossReleaseUnitTest/compileOssReleaseUnitTestKotlin/classes", "files": 1, "bytes": 1, "sha256": "d" * 64},
            ],
            "snapshots": {"sourceBefore": source_snapshot, "sourceAfter": source_snapshot, "gradleHomeBefore": gradle_home, "gradleHomeAfter": gradle_home, "sdkBefore": sdk_manifests, "sdkAfter": sdk_manifests},
            "tools": {
                "wrapperProperties": "0" * 64,
                "officialChecksumContent": {"url": CHECKSUM_RECEIPT_URL, "bodySha256": CHECKSUM_RECEIPT_SHA256, "acquisitionAuth": "NOT_PROVEN"},
                "distributionZip": {"path": "$GRADLE_DISTRIBUTION_ZIP", "sha256": GRADLE_DISTRIBUTION_SHA256},
                "distribution": {"root": "$GRADLE_DISTRIBUTION_ROOT", "files": 2, "bytes": 2, "sha256": manifest_sha(distribution_manifest), "manifest": distribution_manifest},
                "zipExtract": {"equal": True, "sha256": manifest_sha(distribution_manifest)},
                "launcher": {"path": "$GRADLE_DISTRIBUTION_ROOT/lib/gradle-launcher-9.4.1.jar", "sha256": "2" * 64},
                "gradleBinary": {"path": "$GRADLE_DISTRIBUTION_ROOT/bin/gradle", "sha256": "1" * 64},
                "wrapperDirectory": {"root": "$GRADLE_USER_HOME/wrapper/dists/gradle-9.4.1-bin/arn2x92ynaizyzdaamcbpbhtj", "files": 1, "bytes": 1, "sha256": manifest_sha(wrapper_manifest), "manifest": wrapper_manifest},
                "dependencyCache": {"root": "$GRADLE_RO_DEP_CACHE/modules-2/files-2.1", "files": 1, "bytes": 1, "sha256": manifest_sha(cache_manifest), "manifest": cache_manifest},
                "gradleProperties": {"path": "$GRADLE_USER_HOME/gradle.properties", "sha256": GRADLE_PROPERTIES_SHA256},
                "sdkRequired": sdk_manifests,
                "localProperties": {"path": "local.properties", "state": "ABSENT"},
                "javaHome": "$JAVA_HOME", "javaExecutable": {"path": "$JAVA_HOME/bin/java", "sha256": "4" * 64},
                "jdkDistribution": {"root": "$JAVA_HOME", "files": 1, "bytes": 1, "sha256": manifest_sha(jdk_manifest), "manifest": jdk_manifest},
                "versions": {"vendor": "Oracle Corporation", "version": "20.0.2", "runtime": "20.0.2+9-78", "gradle": "9.4.1"},
                "jdkCommand": ["java", "-version"], "jdkVersion": "test-jdk",
                "gradleVersionCommand": ["$GRADLE_DISTRIBUTION_ROOT/bin/gradle", "--version", "--no-daemon"], "gradleVersion": "test-gradle",
                "initScripts": {"scopedUser": [], "defaultUser": [], "system": [], "distribution": [], "checked": ["$GRADLE_USER_HOME/init.gradle"]},
            },
            "claims": {"providerNetwork": "NOT_RUN", "networkDenial": "NOT_PROVEN", "longPressUi": "NOT_RUN", "device": "NOT_RUN", "physicalPhone": "NOT_RUN", "publicRelease": "NOT_RUN", "aiDeleteUndo": "NOT_AVAILABLE"},
        }
        validate_artifact_data(data, source, verify_current=False)
        for key, value in (("sourceCommit", "0" * 40), ("command", ["swapped"]), ("className", "swapped"), ("methods", ["swapped"]), ("environment", {})):
            changed = copy.deepcopy(data); changed[key] = value
            with self.assertRaises(ValueError):
                validate_artifact_data(changed, source, verify_current=False)
        for section, key in (("junit", "sha256"), ("log", "sha256"), ("compiledTrees", 0), ("tools", "wrapperProperties")):
            changed = copy.deepcopy(data)
            if section == "compiledTrees":
                changed[section][key]["sha256"] = "arbitrary"
            else:
                changed[section][key] = "arbitrary"
            with self.assertRaises(ValueError):
                validate_artifact_data(changed, source, verify_current=False)

        tool_mutations = (
            ("distributionZip", "sha256", "0" * 64),
            ("launcher", "sha256", "0" * 64),
            ("gradleBinary", "sha256", "0" * 64),
            ("gradleProperties", "sha256", "0" * 64),
            ("dependencyCache", "sha256", "0" * 64),
            ("distribution", "sha256", "0" * 64),
            ("jdkDistribution", "sha256", "0" * 64),
            ("javaExecutable", "sha256", "0" * 64),
        )
        for section, key, value in tool_mutations:
            changed = copy.deepcopy(data); changed["tools"][section][key] = value
            with self.assertRaises(ValueError):
                validate_artifact_data(changed, source, verify_current=False)
        for key, value in (("javaHome", "$OTHER_JAVA_HOME"), ("initScripts", {"scopedUser": ["init.gradle"], "defaultUser": [], "system": [], "distribution": [], "checked": []})):
            changed = copy.deepcopy(data); changed["tools"][key] = value
            with self.assertRaises(ValueError):
                validate_artifact_data(changed, source, verify_current=False)
        for key, value in (
            ("officialChecksumContent", {"url": CHECKSUM_RECEIPT_URL, "bodySha256": "0" * 64, "acquisitionAuth": "NOT_PROVEN"}),
            ("zipExtract", {"equal": False, "sha256": data["tools"]["distribution"]["sha256"]}),
            ("versions", {"vendor": "swapped", "version": "20.0.2", "runtime": "20.0.2+9-78", "gradle": "9.4.1"}),
            ("localProperties", {"path": "local.properties", "state": "PRESENT", "sha256": "0" * 64}),
        ):
            changed = copy.deepcopy(data); changed["tools"][key] = value
            with self.assertRaises(ValueError):
                validate_artifact_data(changed, source, verify_current=False)
        changed = copy.deepcopy(data); changed["tools"]["jdkVersion"] = "/Users/private/jdk"
        with self.assertRaisesRegex(ValueError, "private filesystem path"):
            validate_artifact_data(changed, source, verify_current=False)
        changed = copy.deepcopy(data); changed["snapshots"]["sourceAfter"] = {**source_snapshot, "c1": "0" * 64}
        with self.assertRaises(ValueError):
            validate_artifact_data(changed, source, verify_current=False)
        validate_source_snapshot_pair(source_snapshot, source_snapshot, source, "2" * 40, "c" * 64)
        forged_source = {**source_snapshot, "headTree": "0" * 40, "indexTree": "0" * 40}
        with self.assertRaises(ValueError):
            validate_source_snapshot_pair(forged_source, forged_source, source, "2" * 40, "c" * 64)
        forged_c1 = {**source_snapshot, "c1": "0" * 64}
        with self.assertRaises(ValueError):
            validate_source_snapshot_pair(forged_c1, forged_c1, source, "2" * 40, "c" * 64)
        changed = copy.deepcopy(data)
        forged_sdk = copy.deepcopy(sdk_manifests); forged_sdk[0]["sha256"] = "0" * 64
        changed["snapshots"]["sdkBefore"] = forged_sdk; changed["snapshots"]["sdkAfter"] = forged_sdk
        with self.assertRaises(ValueError):
            validate_artifact_data(changed, source, verify_current=False)
        changed = copy.deepcopy(data); changed["snapshots"]["gradleHomeBefore"]["sha256"] = "0" * 64
        with self.assertRaises(ValueError):
            validate_artifact_data(changed, source, verify_current=False)

    def test_wrapper_distribution_checksum_property_is_exact(self) -> None:
        valid = "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.4.1-bin.zip\n" + f"distributionSha256Sum={GRADLE_DISTRIBUTION_SHA256}\n"
        require_wrapper_checksum_property(valid)
        for changed in (valid.replace(GRADLE_DISTRIBUTION_SHA256, "0" * 64), valid.replace("9.4.1", "9.4.0"), valid + f"distributionSha256Sum={GRADLE_DISTRIBUTION_SHA256}\n"):
            with self.assertRaises(ValueError):
                require_wrapper_checksum_property(changed)
        validate_gradle_properties_bytes(b"org.gradle.daemon=false\norg.gradle.caching=false\norg.gradle.configuration-cache=false\n")
        with self.assertRaises(ValueError):
            validate_gradle_properties_bytes(b"org.gradle.daemon=true\n")

    def test_zip_path_traversal_and_committed_private_paths_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            cases = (
                (("gradle-9.4.1/../escape", b"bad", None),),
                (("gradle-9.4.1/same", b"one", None), ("gradle-9.4.1/same", b"two", None)),
                (("gradle-9.4.1/A", b"one", None), ("gradle-9.4.1/a", b"two", None)),
                (("gradle-9.4.1/e\u0301", b"bad", None),),
                (("gradle-9.4.1/file", b"one", None), ("gradle-9.4.1/file/child", b"two", None)),
                (("gradle-9.4.1/link", b"target", stat.S_IFLNK | 0o777),),
            )
            for index, entries in enumerate(cases):
                archive = Path(raw) / f"bad-{index}.zip"
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore", UserWarning)
                    with zipfile.ZipFile(archive, "w") as output:
                        for name, payload, mode in entries:
                            info = zipfile.ZipInfo(name)
                            if mode is not None:
                                info.external_attr = mode << 16
                            output.writestr(info, payload)
                with self.assertRaises(ValueError):
                    zip_manifest(archive)
            bomb = Path(raw) / "bomb.zip"
            with zipfile.ZipFile(bomb, "w", compression=zipfile.ZIP_DEFLATED) as output:
                output.writestr("gradle-9.4.1/bomb", b"x" * 4096)
            with self.assertRaises(ValueError):
                zip_manifest(bomb, max_bytes=1024)
            count = Path(raw) / "count.zip"
            with zipfile.ZipFile(count, "w") as output:
                output.writestr("gradle-9.4.1/a", b"a")
                output.writestr("gradle-9.4.1/b", b"b")
            with self.assertRaises(ValueError):
                zip_manifest(count, max_files=1)
        for path in (
            ROOT / "docs/evidence/M09D_CONTROLLER_LIFECYCLE.md",
            ROOT / "docs/evidence/M09D_CONTROLLER_LIFECYCLE_TODO.md",
            ROOT / "tools/evidence/schemas/codecks-m09d-controller-lifecycle-v1.schema.json",
        ):
            text = path.read_text(encoding="utf-8")
            self.assertNotIn("/Users/", text)
            self.assertNotIn("/tmp/", text)

    def test_status_gate_accepts_clean_and_rejects_dirty_independent_of_checkout(self) -> None:
        validate_status_bytes(b"")
        with self.assertRaisesRegex(ValueError, "exactly clean"):
            validate_status_bytes(b" M source.kt\x00")

    def test_working_file_must_equal_committed_blob(self) -> None:
        validate_working_bytes("source.kt", b"exact", b"exact")
        with self.assertRaisesRegex(ValueError, "committed blob"):
            validate_working_bytes("source.kt", b"dirty", b"exact")

    def test_compiled_tree_digest_rejects_empty_tree_and_binds_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            directory = Path(raw)
            with self.assertRaises(ValueError):
                tree_digest(directory)
            (directory / "A.class").write_bytes(b"one")
            first = tree_digest(directory)
            (directory / "A.class").write_bytes(b"two")
            self.assertNotEqual(first["sha256"], tree_digest(directory)["sha256"])
            with self.assertRaises(ValueError):
                tree_digest(directory, max_bytes=2)
            (directory / "B.class").write_bytes(b"x")
            with self.assertRaises(ValueError):
                tree_digest(directory, max_files=1)

    def test_jdk_manifest_rejects_declared_byte_and_file_count_overflow(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            (root / "java").write_bytes(b"abc")
            with self.assertRaises(ValueError):
                jdk_distribution_manifest(root, "$JDK", max_bytes=2)
            (root / "release").write_bytes(b"x")
            with self.assertRaises(ValueError):
                jdk_distribution_manifest(root, "$JDK", max_files=1)

    def test_source_hash_binding_is_live_and_no_shrink_is_present(self) -> None:
        bindings = require_source_bindings()
        self.assertEqual(3, len(bindings))
        self.assertTrue(all(len(item["sha256"]) == 64 for item in bindings))

    def test_closed_receipt_schema_rejects_source_and_artifact_hash_mutations(self) -> None:
        schema = json.loads((ROOT / "tools/evidence/schemas/codecks-m09d-controller-lifecycle-v1.schema.json").read_text())
        receipt = {
            "schema": "codecks.m09d.controller-lifecycle.v1", "status": "PASS",
            "scope": "CURRENT_SOURCE_CPU_CONTROLLER_LIFECYCLE_ONLY",
            "commitChain": {"base": BASE_COMMIT, "source": "1" * 40, "artifact": "2" * 40},
            "sourceBinding": {"files": [{"path": f"source-{i}", "sha256": "a" * 64} for i in range(3)], "c1Paths": sorted(C1_PATHS)},
            "artifacts": [{"path": path, "sha256": "b" * 64} for path in sorted(C2_PATHS)],
            "test": {"className": CLASS_NAME, "methods": sorted(METHODS), "tests": 10, "failures": 0, "errors": 0, "skipped": 0, "command": list(COMMAND), "environment": EXEC_ENV},
            "claims": {"providerNetwork": "NOT_RUN", "networkDenial": "NOT_PROVEN", "longPressUi": "NOT_RUN", "device": "NOT_RUN", "physicalPhone": "NOT_RUN", "publicRelease": "NOT_RUN", "aiDeleteUndo": "NOT_AVAILABLE"},
        }
        validate_json_schema(receipt, schema)
        for section in ("sourceBinding", "artifacts"):
            changed = copy.deepcopy(receipt)
            if section == "sourceBinding":
                changed[section]["files"][0]["sha256"] = "swapped"
            else:
                changed[section][0]["sha256"] = "swapped"
            with self.assertRaises(ValueError):
                validate_json_schema(changed, schema)


if __name__ == "__main__":
    unittest.main()
