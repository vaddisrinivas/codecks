#!/usr/bin/env python3
import json
import sys
from pathlib import Path


REQUIRED = {
    "androidx-room",
    "kstatemachine",
    "compose-settings",
    "colorpicker-compose",
    "compose-icons-additional-modules",
}


def physical_lines(path: Path, start: int = 1, end: int | None = None) -> int:
    lines = path.read_text().splitlines()
    return len(lines[start - 1:end])


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("tasks/test-evidence/m02a/dependency-spike-results.json")
    receipt = json.loads(path.read_text())
    assert receipt["schema_version"] == 1
    assert receipt["milestone"] == "M02A"
    assert receipt["production_files_changed"] == 0
    assert receipt["protected_package_touched"] is False
    assert receipt["test_receipt"]["result"] == "PASS"
    candidates = {item["id"]: item for item in receipt["candidates"]}
    assert set(candidates) == REQUIRED
    assert all(item["decision"] in {"ADOPT", "REJECT"} for item in candidates.values())
    assert all(item["reason"] for item in candidates.values())
    assert candidates["androidx-room"]["decision"] == "REJECT"
    assert candidates["androidx-room"]["proof"]["zero_data_loss_runtime"] == "NOT_RUN"
    assert candidates["kstatemachine"]["loc"]["net_reduction_percent"] < 20
    assert candidates["compose-settings"]["loc"]["net_removed_after_tests_and_migrations"] < 300
    assert candidates["colorpicker-compose"]["proof"]["talkback"] == "NOT_RUN"
    assert candidates["compose-icons-additional-modules"]["loc"]["net_new_lines"] > 0
    root = path.resolve().parents[3]
    assert physical_lines(root / "app/src/main/java/io/codecks/data/RunHistoryRepository.kt", 1, 120) == 120
    assert physical_lines(root / "spikes/dependency-replacements/src/main/kotlin/io/codecks/spikes/RoomAutomationHistorySpike.kt") == 53
    assert physical_lines(root / "spikes/dependency-replacements/src/test/kotlin/io/codecks/spikes/RoomAutomationHistorySpikeTest.kt") == 28
    assert physical_lines(root / "app/src/main/java/io/codecks/data/automation/AutomationExecutionCoordinator.kt", 200, 222) == 23
    assert physical_lines(root / "spikes/dependency-replacements/src/main/kotlin/io/codecks/spikes/KStateMachineLifecycleSpike.kt") == 52
    assert physical_lines(root / "spikes/dependency-replacements/src/test/kotlin/io/codecks/spikes/KStateMachineLifecycleSpikeTest.kt") == 41
    assert physical_lines(root / "app/src/main/java/io/codecks/ui/settings/SettingsScreen.kt", 1408, 1433) == 26
    assert physical_lines(root / "spikes/dependency-replacements/src/main/kotlin/io/codecks/spikes/UiDependencySpike.kt", 26, 51) == 26
    assert physical_lines(root / "spikes/dependency-replacements/src/main/kotlin/io/codecks/spikes/UiDependencySpike.kt", 53, 65) + physical_lines(root / "spikes/dependency-replacements/src/main/kotlin/io/codecks/spikes/UiDependencySpike.kt", 78, 86) == 22
    assert physical_lines(root / "spikes/dependency-replacements/src/main/kotlin/io/codecks/spikes/UiDependencySpike.kt", 67, 76) == 10
    print(f"M02A receipt valid: {len(candidates)} candidates, all decisions explicit")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
