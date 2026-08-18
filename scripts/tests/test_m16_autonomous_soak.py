import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

MODULE_PATH = Path(__file__).parents[1] / "m16_autonomous_soak.py"
SPEC = importlib.util.spec_from_file_location("m16_soak", MODULE_PATH)
m16 = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(m16)


class M16HostTests(unittest.TestCase):
    def test_requires_exact_four_distinct_emulators(self):
        values = [f"m16Soak0{i}Api35=emulator-{5552 + i * 2}" for i in range(1, 5)]
        self.assertEqual(len(m16.parse_devices(values)), 4)
        for mutation in (values[:3], values + [values[0]], values[:-1] + ["m16Soak04Api35=device-1"]):
            with self.assertRaises(m16.SafetyStop):
                m16.parse_devices(mutation)

    def test_physical_device_and_wrong_api_fail_closed(self):
        with self.assertRaises(m16.SafetyStop):
            m16.adb("physical-1", "shell", "true")
        with mock.patch.object(m16, "adb", side_effect=["0"]):
            with self.assertRaisesRegex(m16.SafetyStop, "device_not_qemu"):
                m16.verify_device("m16Soak01Api35", "emulator-5554")

    def test_singleton_is_exclusive_and_released_only_by_owner(self):
        with tempfile.TemporaryDirectory() as directory:
            lock = Path(directory) / "lock"
            run_dir = Path(directory).resolve()
            m16.acquire_owner(run_dir, lock)
            self.assertTrue(lock.exists())
            with self.assertRaises(m16.SafetyStop):
                m16.acquire_owner(run_dir, lock)
            m16.require_owner(run_dir, lock)
            m16.release_owner(run_dir, lock)
            self.assertFalse(lock.exists())

    def test_host_events_cannot_contain_ack(self):
        source = MODULE_PATH.read_text()
        self.assertNotIn('{"type": "ack"', source)
        self.assertNotIn("app.codecks/", source)
        self.assertNotIn("caffeinate", source)
        self.assertNotIn("launchd", source)

    def test_atomic_state_and_ledger_are_bounded(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            m16.atomic_state(root, {"status": "running"})
            self.assertEqual(json.loads((root / "state.json").read_text())["status"], "running")
            m16.append_host_event(root, {"type": "controller_start"})
            self.assertEqual(len((root / "host-ledger.jsonl").read_text().splitlines()), 1)
            with mock.patch.object(m16, "HOST_LEDGER_CAP", 1):
                with self.assertRaises(m16.SafetyStop):
                    m16.append_host_event(root, {"type": "overflow"})

    def test_capacity_reserve(self):
        with mock.patch.object(m16, "disk_free_gib", return_value=63.99):
            with self.assertRaisesRegex(m16.SafetyStop, "preprovision"):
                m16.require_capacity("pre", Path("."))
        with mock.patch.object(m16, "disk_free_gib", return_value=64.0):
            m16.require_capacity("pre", Path("."))

    def test_worker_ledger_hash_chain_and_tamper(self):
        profile="avd01-p01"; process="app.codecks.internal:m16p01"
        nonce=__import__("hashlib").sha256(f"codecks-m16-nonce-v1:{profile}".encode()).hexdigest()[:32]
        previous="0"*64; lines=[]; categories=sorted(m16.CATEGORIES)
        events=[{"type":"admitted","elapsedRealtimeMillis":1,"wallTimeMillis":1,"windowIndex":1,
                 "bootId":"b","eligibleTarget":2,"profileRoot":"m16/profiles/avd01-p01",
                 "seed":__import__("hashlib").sha256(f"codecks-m16-seed-v1:{profile}".encode()).hexdigest()[:16]}]
        for sequence in range(1,122):
            elapsed=1+(sequence-1)*30_000
            events.append({"type":"ack","elapsedRealtimeMillis":elapsed,"wallTimeMillis":elapsed,"windowIndex":1,
                "sequence":sequence,"ackId":__import__("hashlib").sha256(f"{nonce}:1:{sequence}".encode()).hexdigest(),
                "category":categories[(sequence-1)%len(categories)],"operationLatencyMillis":1,"totalPssKb":1,
                "batteryPercentProxy":90,"applicationExitReasons":{"crash":0,"nativeCrash":0,"anr":0,"self":0,"other":0},"crashOrAnr":False})
        events.extend([
            {"type":"window_complete","elapsedRealtimeMillis":3_600_001,"wallTimeMillis":3_600_001,"windowIndex":1,
             "sequence":121,"elapsedMillis":3_600_000,"activeMillis":3_600_000,"categories":categories,"eligible":True},
            {"type":"profile_complete","elapsedRealtimeMillis":3_600_002,"wallTimeMillis":3_600_002,
             "attemptedWindows":1,"eligibleWindows":1,"acknowledgedOperations":121},
        ])
        for event in events:
            event.update({"profileId":profile,"processName":process,"pid":123,"originNonce":nonce,"previousHash":previous})
            digest=m16.sha256_bytes(json.dumps(event,separators=(",", ":")).encode()); event["eventHash"]=digest; previous=digest
            lines.append(json.dumps(event,separators=(",", ":")))
        ledger=("\n".join(lines)+"\n").encode(); checkpoint=json.dumps({"ledgerHeadHash":previous}).encode()
        result=m16.verify_worker_ledger(ledger,checkpoint,profile,process,nonce)
        self.assertEqual((result["attemptedSessions"],result["eligibleSessions"]),(1,1))
        with self.assertRaises(m16.SafetyStop):
            m16.verify_worker_ledger(ledger.replace(b'"pid":123',b'"pid":124',1),checkpoint,profile,process,nonce)

    def test_failure_screenshot_never_captures_arbitrary_foreground(self):
        worker = {"profileId": "avd01-p01", "pid": 123, "processName": "app.codecks.internal:m16p01",
                  "reasonCode": "worker_failed", "attemptedWindows": 0}
        with tempfile.TemporaryDirectory() as directory, \
             mock.patch.object(m16, "adb_bytes", return_value=b"safe log" ) as binary, \
             mock.patch.object(m16, "adb", side_effect=["started", "mResumedActivity: other.Activity"]):
            with self.assertRaisesRegex(m16.SafetyStop, "safe_screenshot"):
                m16.capture_failure(Path(directory), "m16Soak01Api35", "emulator-5554", worker)
            self.assertFalse(any("screencap" in call.args for call in binary.call_args_list))

    def test_host_chain_recomputes_wall_monotonic_and_monitor_coverage(self):
        with tempfile.TemporaryDirectory() as directory, \
             mock.patch.object(m16.time,"time",side_effect=[1.0,16.0]), \
             mock.patch.object(m16.time,"monotonic_ns",side_effect=[1_000_000_000,16_000_000_000]):
            root=Path(directory)
            m16.append_host_event(root,{"type":"controller_start","mode":"burnin2h","profiles":20})
            m16.append_host_event(root,{"type":"monitor","workers":20,"complete":0,"qemuRssKiB":1,"freeGiB":99.0,
                "health":{"swapUsedMiB":0.0,"memoryFreePercent":50,"availableGiB":16.0,"load1":1.0,"thermal":"nominal"}})
            proof=m16.verify_host_ledger((root/"host-ledger.jsonl").read_bytes(),1000,16000)
            self.assertEqual((proof["monitorEvents"],proof["monitoredMillis"]),(1,15000))
            tampered=(root/"host-ledger.jsonl").read_bytes().replace(b'"hostWallMillis":16000',b'"hostWallMillis":26000')
            with self.assertRaises(m16.SafetyStop): m16.verify_host_ledger(tampered,1000,26000)

    def test_isolation_xml_requires_exact_live_methods_and_binding(self):
        cases="".join(f'<testcase classname="{m16.ISOLATION_CLASS}" name="{name}"/>' for name in sorted(m16.ISOLATION_METHODS))
        output='M16_BINDING package=app.codecks.internal flavor=playInternal project=:app api=35 fingerprintSha256='+'a'*64
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/"result.xml"
            path.write_text(f'<testsuite tests="4" failures="0" errors="0">{cases}<system-out>{output}</system-out></testsuite>')
            self.assertEqual(m16.verify_isolation_xml(path)["api"],35)
            path.write_text(path.read_text().replace('name="manifestCarriesFiveExactNamedProcesses"','name="proxy"'))
            with self.assertRaises(m16.SafetyStop): m16.verify_isolation_xml(path)


if __name__ == "__main__":
    unittest.main()
