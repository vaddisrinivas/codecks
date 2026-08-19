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
    def test_isolated_adb_namespace_and_default_audit_fail_closed(self):
        self.assertEqual(m16.adb_command(m16.ADB_SERVER_PORT,"devices"),["adb","-P","5039","devices"])
        self.assertEqual(m16.adb_command(m16.DEFAULT_ADB_SERVER_PORT,"devices"),["adb","-P","5037","devices"])
        with mock.patch.object(m16,"listener_pids",return_value=set()),mock.patch.object(m16.subprocess,"run") as run:
            self.assertEqual(m16.audit_default_adb()["status"],"absent"); run.assert_not_called()
        offline=mock.Mock(returncode=0,stdout="List of devices attached\nemulator-5554\toffline\n")
        with mock.patch.object(m16,"listener_pids",return_value={250}),mock.patch.object(m16,"process_command",return_value="adb server"), \
             mock.patch.object(m16.subprocess,"run",return_value=offline),self.assertRaises(m16.SafetyStop): m16.audit_default_adb()
        devices=mock.Mock(returncode=0,stdout="List of devices attached\nemulator-5554\tdevice\n")
        qemu=mock.Mock(returncode=0,stdout="1\n"); unknown=mock.Mock(returncode=0,stdout="Unknown_AVD\n")
        with mock.patch.object(m16,"listener_pids",return_value={250}),mock.patch.object(m16,"process_command",return_value="adb server"), \
             mock.patch.object(m16.subprocess,"run",side_effect=[devices,qemu,unknown]),self.assertRaises(m16.SafetyStop): m16.audit_default_adb()
        known=mock.Mock(returncode=0,stdout="Utopia_GL_1\n")
        processes=mock.Mock(returncode=0,stdout="30880 qemu-system -avd Utopia_GL_1 -port 5554\n")
        with mock.patch.object(m16,"listener_pids",return_value={250}),mock.patch.object(m16,"process_command",return_value="adb server") as command, \
             mock.patch.object(m16.subprocess,"run",side_effect=[devices,qemu,known,processes]) as run:
            audit=m16.audit_default_adb()
        self.assertEqual(audit["authorizedAvds"][0]["avd"],"Utopia_GL_1")
        sent=" ".join(str(call.args[0]) for call in run.call_args_list)
        for forbidden in ("install","uninstall","kill","force-stop","pm clear"): self.assertNotIn(forbidden,sent)

    def test_collision_partial_launch_and_unowned_cleanup_fail_closed(self):
        args=mock.Mock(run_dir="/tmp/m16-collision",mode="burnin2h",burnin_run_dir=None)
        with mock.patch.object(m16,"require_capacity"),mock.patch.object(m16,"audit_default_adb",return_value={}), \
             mock.patch.object(m16,"listener_pids",return_value={999}),mock.patch.object(m16,"acquire_owner") as acquire, \
             self.assertRaises(m16.SafetyStop): m16.launch(args)
        acquire.assert_not_called()
        state={"runToken":"1"*32,"emulatorPids":{avd:100+i for i,avd in enumerate(m16.AVDS)},
               "devices":{avd:f"emulator-{port}" for avd,port in zip(m16.AVDS,m16.EMULATOR_PORTS)},"isolatedAdb":{"owned":True}}
        with mock.patch.object(m16,"require_owner"),mock.patch.object(m16,"adb_server_binding",return_value={"owned":True}), \
             mock.patch.object(m16,"process_command",return_value="unrelated qemu"),mock.patch.object(m16,"adb_result") as send, \
             self.assertRaises(m16.SafetyStop): m16.stop_owned_emulators(Path("/tmp/run"),state)
        send.assert_not_called()

    def test_partial_launch_releases_owner_and_only_owned_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            args=mock.Mock(run_dir=str(Path(directory).resolve()),mode="burnin2h",burnin_run_dir=None); process=mock.Mock(pid=1111)
            blank=mock.Mock(returncode=0,stdout="List of devices attached\n")
            with mock.patch.object(m16,"require_capacity"),mock.patch.object(m16,"audit_default_adb",return_value={}), \
                 mock.patch.object(m16,"listener_pids",return_value=set()),mock.patch.object(m16,"adb_server_binding",return_value={"serverPid":9}), \
                 mock.patch.object(m16,"provision_binding",return_value={"avdHome":str(Path(directory)/"avd-home")}), \
                 mock.patch.object(m16.subprocess,"run",return_value=blank),mock.patch.object(m16,"managed_avd_config"), \
                 mock.patch.object(m16.Path,"is_file",return_value=True),mock.patch.object(m16.subprocess,"Popen",side_effect=[process,OSError("partial")]) as popen, \
                 mock.patch.object(m16.secrets,"token_hex",return_value="1"*32),mock.patch.object(m16,"process_command",return_value="m16Soak01Api35 qemu.codecks.m16_run_token="+"1"*32), \
                 mock.patch.object(m16,"acquire_owner"),mock.patch.object(m16,"release_owner") as release, \
                 mock.patch.object(m16,"stop_owned_adb_server") as stop_server, self.assertRaises(OSError): m16.launch(args)
            stop_server.assert_called_once(); release.assert_called_once()
            command=popen.call_args_list[0].args[0]; environment=popen.call_args_list[0].kwargs["env"]
            self.assertNotIn("-wipe-data",command)
            self.assertNotIn("-prop",command)
            self.assertIn("-logcat-output",command)
            self.assertTrue(command[command.index("-logcat-output")+1].endswith("emulator-m16Soak01Api35-"+"1"*32+".log"))
            self.assertEqual(command[command.index("-port")+1],"5580")
            self.assertEqual(Path(environment["ANDROID_AVD_HOME"]).resolve(),Path(directory).resolve()/"avd-home")

    def test_stale_server_binding_never_sends_kill(self):
        binding={"port":5039,"endpoint":"tcp:127.0.0.1:5039","serverPid":9,"cmdlineSha256":"a"*64}
        with mock.patch.object(m16,"adb_server_binding",return_value={**binding,"serverPid":10}), \
             mock.patch.object(m16.subprocess,"run") as run, self.assertRaises(m16.SafetyStop): m16.stop_owned_adb_server(binding)
        run.assert_not_called()
    def test_requires_exact_four_distinct_emulators(self):
        values = [f"{avd}=emulator-{port}" for avd,port in zip(m16.AVDS,m16.EMULATOR_PORTS)]
        self.assertEqual(len(m16.parse_devices(values)), 4)
        for mutation in (values[:3], values + [values[0]], values[:-1] + ["m16Soak04Api35=device-1"]):
            with self.assertRaises(m16.SafetyStop):
                m16.parse_devices(mutation)
        with self.assertRaisesRegex(m16.SafetyStop,"substitution"):
            m16.parse_devices([values[1].replace("m16Soak02","m16Soak01"),values[0].replace("m16Soak01","m16Soak02"),*values[2:]])

    def test_physical_device_and_wrong_api_fail_closed(self):
        with self.assertRaises(m16.SafetyStop):
            m16.adb("physical-1", "shell", "true")
        with mock.patch.object(m16, "adb", side_effect=["0"]):
            with self.assertRaisesRegex(m16.SafetyStop, "device_not_qemu"):
                m16.verify_device("m16Soak01Api35", "emulator-5580")

    def test_only_exact_m16_serials_can_receive_device_commands(self):
        with mock.patch.object(m16.subprocess,"run",return_value=mock.Mock(returncode=0,stdout="ok")) as run:
            self.assertEqual(m16.adb("emulator-5580","shell","true"),"ok")
        self.assertEqual(run.call_args.args[0],["adb","-P","5039","-s","emulator-5580","shell","true"])
        for serial in ("emulator-5554","emulator-5556","physical-1","emulator-5588"):
            with mock.patch.object(m16.subprocess,"run") as forbidden, self.assertRaisesRegex(m16.SafetyStop,"non_m16_serial"):
                m16.adb(serial,"shell","true")
            forbidden.assert_not_called()

    def test_isolated_inventory_allows_only_baseline_utopia_plus_exact_m16(self):
        devices={avd:f"emulator-{port}" for avd,port in zip(m16.AVDS,m16.EMULATOR_PORTS)}
        audit={"authorizedAvds":[{"avd":"Utopia_GL_1","serial":"emulator-5554"},{"avd":"Utopia_GL_2","serial":"emulator-5556"}]}
        lines=["List of devices attached",*(f"{serial}\tdevice" for serial in [*devices.values(),"emulator-5554","emulator-5556"])]
        good="\n".join(lines)+"\n"
        with mock.patch.object(m16.subprocess,"run",return_value=mock.Mock(returncode=0,stdout=good)):
            m16.global_adb_guard(devices,audit)
        for mutation in (good.replace("emulator-5586","emulator-5588"),good.replace("emulator-5554\tdevice","emulator-5554\toffline")):
            with mock.patch.object(m16.subprocess,"run",return_value=mock.Mock(returncode=0,stdout=mutation)),self.assertRaises(m16.SafetyStop):
                m16.global_adb_guard(devices,audit)

    def test_provision_creates_four_distinct_persistent_run_owned_avds(self):
        with tempfile.TemporaryDirectory() as directory:
            run_dir=Path(directory).resolve()/"run"; sdk=Path(directory).resolve()/"sdk"
            (sdk/"cmdline-tools/latest/bin").mkdir(parents=True); (sdk/"cmdline-tools/latest/bin/avdmanager").touch()
            (sdk/"system-images/android-35/default/arm64-v8a").mkdir(parents=True)
            calls=[]
            def create(command,**kwargs):
                calls.append((command,kwargs)); avd=command[command.index("--name")+1]
                home=Path(kwargs["env"]["ANDROID_AVD_HOME"]); root=home/f"{avd}.avd"; root.mkdir(parents=True)
                (home/f"{avd}.ini").write_text(f"path={root}\ntarget=android-35\n")
                (root/"config.ini").write_text("image.sysdir.1=system-images/android-35/default/arm64-v8a/\n")
                return mock.Mock(returncode=0,stdout="",stderr="")
            args=mock.Mock(run_dir=str(run_dir),mode="burnin2h",burnin_run_dir=None)
            with mock.patch.dict(m16.os.environ,{"ANDROID_HOME":str(sdk)}),mock.patch.object(m16,"require_capacity"), \
                 mock.patch.object(m16,"audit_default_adb",return_value={}),mock.patch.object(m16,"listener_pids",return_value=set()), \
                 mock.patch.object(m16.subprocess,"run",side_effect=create): m16.provision(args)
            binding=m16.provision_binding(run_dir)
            self.assertEqual([item["name"] for item in binding["avds"]],list(m16.AVDS))
            self.assertEqual(len({Path(item["configPath"]).parent for item in binding["avds"]}),4)
            self.assertTrue(all(call[1]["env"]["ANDROID_AVD_HOME"]==str(run_dir.resolve()/"avd-home") for call in calls))
            self.assertFalse(any("--force" in call[0] or "-wipe-data" in call[0] for call in calls))
            with mock.patch.object(m16,"require_capacity"),mock.patch.object(m16,"audit_default_adb",return_value={}), \
                 mock.patch.object(m16,"listener_pids",return_value=set()),mock.patch.object(m16.subprocess,"run") as rerun:
                m16.provision(args)
            rerun.assert_not_called()
            first=Path(binding["avds"][0]["configPath"]); first.write_text(first.read_text()+"tampered=yes\n")
            with self.assertRaisesRegex(m16.SafetyStop,"config_binding"): m16.provision_binding(run_dir)

    def test_provision_rejects_avd_home_symlink_redirect_before_avdmanager(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve(); run=root/"run"; redirect=root/"redirect"; run.mkdir(); redirect.mkdir()
            (run/"avd-home").symlink_to(redirect,target_is_directory=True)
            args=mock.Mock(run_dir=str(run),mode="burnin2h",burnin_run_dir=None)
            with mock.patch.object(m16,"require_capacity"),mock.patch.object(m16,"audit_default_adb",return_value={}), \
                 mock.patch.object(m16,"listener_pids",return_value=set()),mock.patch.object(m16.subprocess,"run") as execute, \
                 self.assertRaisesRegex(m16.SafetyStop,"symlink"): m16.provision(args)
            execute.assert_not_called()

    def test_identity_log_path_missing_substituted_wrong_token_outside_and_symlink_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            run=Path(directory).resolve()/"run"; run.mkdir(); token="1"*32; avd=m16.AVDS[0]
            expected=run/f"emulator-{avd}-{token}.log"
            with self.assertRaises(m16.SafetyStop): m16.identity_log_binding(run,avd,token)
            expected.write_text("safe")
            self.assertEqual(m16.identity_log_binding(run,avd,token)["identityLogPath"],str(expected))
            other=run/"other.log"; other.write_text("safe")
            for candidate,bound_token in ((other,token),(expected,"2"*32),(Path(directory).resolve()/"outside.log",token)):
                if not candidate.exists(): candidate.write_text("safe")
                with self.assertRaises(m16.SafetyStop): m16.identity_log_binding(run,avd,bound_token,candidate)
            expected.unlink(); expected.symlink_to(other)
            with self.assertRaisesRegex(m16.SafetyStop,"identity_log"): m16.identity_log_binding(run,avd,token,expected)

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

    def test_soak168_requires_fresh_exact_burnin_receipt_state_and_topology(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory).resolve()
            burnin = base / "burnin"
            soak = base / "soak"
            burnin.mkdir(); soak.mkdir()
            binding = {"sourceCommit": "a" * 40, "targetApkSha256": "b" * 64,
                       "testApkSha256": "c" * 64, "targetSignerSha256": "d" * 64,
                       "testSignerSha256": "e" * 64}
            def configs_for(root):
                home=root/"avd-home"; home.mkdir(); result=[]
                for avd,port in zip(m16.AVDS,m16.EMULATOR_PORTS):
                    path=home/f"{avd}.avd"/"config.ini"; path.parent.mkdir(); path.write_text(f"name={avd}\n")
                    result.append({"name":avd,"port":port,"configPath":str(path),
                                   "configSha256":m16.sha256_file(path),"runIdentity":"f"*32})
                return home,result
            burnin_home,burnin_configs=configs_for(burnin)
            soak_home,soak_configs=configs_for(soak)
            burnin_provision={"avdHomeLexical":str(burnin_home),"avdHomeCanonical":str(burnin_home.resolve()),"avds":burnin_configs}
            soak_provision={"avdHomeLexical":str(soak_home),"avdHomeCanonical":str(soak_home.resolve()),"avds":soak_configs}
            devices = [{"avd": avd, "serial": f"emulator-{port}", "api": 35,
                        "fingerprintSha256": f"{index + 4}" * 64, "uid": 10100,
                        "dataDir": "/data/user/0/app.codecks.internal"}
                       for index, (avd, port) in enumerate(zip(m16.AVDS, m16.EMULATOR_PORTS), 1)]
            profiles = [{**item, "eligibleSessions": 2} for item in m16.expected_profile_catalog()]
            isolated = {"port": 5039, "endpoint": "tcp:127.0.0.1:5039", "serverPid": 99, "cmdlineSha256": "9" * 64}
            emulator_pids = {avd: 100 + index for index, avd in enumerate(m16.AVDS, 1)}
            default_audit = {"status": "absent", "sanitizedNonM16EmulatorCount": 0, "authorizedAvds": [],
                             "serverPid": 0, "serverCmdlineSha256": "0" * 64}
            receipt = {"status": "PASS", "sourceCommit": binding["sourceCommit"], "binding": binding,
                       "devices": devices, "profiles": profiles, "wall": {"finishedWallMillis": 1_000},
                       "runtime": {"runIdentity": "f" * 32, "isolatedAdb": isolated,
                                   "emulatorPids": emulator_pids, "defaultAdbAudit": default_audit,
                                   "avdProvision": burnin_provision}}
            receipt_path = burnin / m16.BURNIN_RECEIPT_NAME
            receipt_path.write_text(json.dumps(receipt))
            state = {"schema": "codecks.m16.host-state.v1", "mode": "burnin2h", "durationHours": 2,
                     "status": "complete", "cleanupStatus": "complete", "completedWallMillis": 1_000,
                     "receiptSha256": m16.sha256_file(receipt_path), "binding": binding,
                     "deviceBindings": devices, "runToken": "f" * 32,
                     "devices": {item["avd"]: item["serial"] for item in devices},
                     "startedWallMillis": 1, "startedMonotonicNanos": 1, "profiles": 20,
                     "baselineHealth": {"swapUsedMiB": 0.0, "memoryFreePercent": 50,
                                        "availableGiB": 16.0, "load1": 1.0, "thermal": "nominal"},
                     "isolatedAdb": isolated, "emulatorPids": emulator_pids,
                     "defaultAdbAudit": default_audit, "avdHome": str(burnin_home),
                     "burninAdmission": m16.burnin_not_required()}
            state_path = burnin / m16.BURNIN_STATE_NAME
            state_path.write_text(json.dumps(state))
            validator_pass = mock.Mock(returncode=0, stdout="PASS M16 AUTONOMOUS_PROXY receipt\n")
            with mock.patch.object(m16.subprocess, "run", return_value=validator_pass):
                admission = m16.verify_burnin_admission(burnin, soak, binding, devices, soak_provision, 2_000)
                self.assertEqual((admission["status"], admission["maxBurninAgeHours"]), ("PASS", 24))
                self.assertEqual(admission["pathIdentitySha256"],m16.phase_path_identity(soak,burnin)["pathIdentitySha256"])
                outside=json.loads(json.dumps(soak_provision)); outside["avds"][0]["configPath"]=burnin_configs[0]["configPath"]
                with self.assertRaisesRegex(m16.SafetyStop,"config_binding"):
                    m16.verify_burnin_admission(burnin,soak,binding,devices,outside,2_000)
                wrong=soak_home/"wrong.avd"/"config.ini"; wrong.parent.mkdir(); wrong.write_bytes(Path(soak_configs[0]["configPath"]).read_bytes())
                suffix=json.loads(json.dumps(soak_provision)); suffix["avds"][0]["configPath"]=str(wrong)
                with self.assertRaisesRegex(m16.SafetyStop,"config_binding"):
                    m16.verify_burnin_admission(burnin,soak,binding,devices,suffix,2_000)
                bad_hash=json.loads(json.dumps(soak_provision)); bad_hash["avds"][0]["configSha256"]="0"*64
                with self.assertRaisesRegex(m16.SafetyStop,"config_binding"):
                    m16.verify_burnin_admission(burnin,soak,binding,devices,bad_hash,2_000)
                with self.assertRaisesRegex(m16.SafetyStop, "stale"):
                    m16.verify_burnin_admission(burnin, soak, binding, devices, soak_provision,
                                                1_000 + m16.BURNIN_MAX_AGE_MILLIS + 1)
                different = {**binding, "targetApkSha256": "0" * 64}
                with self.assertRaisesRegex(m16.SafetyStop, "mismatched"):
                    m16.verify_burnin_admission(burnin, soak, different, devices, soak_provision, 2_000)
                changed_devices = [dict(item) for item in devices]
                changed_devices[0]["fingerprintSha256"] = "0" * 64
                with self.assertRaisesRegex(m16.SafetyStop, "topology"):
                    m16.verify_burnin_admission(burnin, soak, binding, changed_devices, soak_provision, 2_000)
                receipt["status"] = "FAIL"
                receipt_path.write_text(json.dumps(receipt))
                state["receiptSha256"] = m16.sha256_file(receipt_path)
                state_path.write_text(json.dumps(state))
                with self.assertRaisesRegex(m16.SafetyStop, "stale_or_mismatched"):
                    m16.verify_burnin_admission(burnin, soak, binding, devices, soak_provision, 2_000)
                receipt["status"] = "PASS"
                receipt_path.write_text(json.dumps(receipt))
                state["receiptSha256"] = m16.sha256_file(receipt_path)
                state_path.write_text(json.dumps(state))
            with mock.patch.object(m16.subprocess, "run", return_value=mock.Mock(returncode=1, stdout="")), \
                 self.assertRaisesRegex(m16.SafetyStop, "external_validator"):
                m16.verify_burnin_admission(burnin, soak, binding, devices, soak_provision, 2_000)
            receipt_path.unlink()
            with self.assertRaisesRegex(m16.SafetyStop, "missing"):
                m16.verify_burnin_admission(burnin, soak, binding, devices, soak_provision, 2_000)

    def test_phase_directories_reject_same_related_symlink_and_reused_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory).resolve(); burnin=base/"burnin"; soak=base/"soak"; child=burnin/"child"
            burnin.mkdir(); soak.mkdir(); child.mkdir()
            with self.assertRaisesRegex(m16.SafetyStop,"relationship"):
                m16.resolve_phase_directories(str(burnin),str(burnin),"soak168h",True)
            with self.assertRaisesRegex(m16.SafetyStop,"relationship"):
                m16.resolve_phase_directories(str(child),str(burnin),"soak168h",True)
            alias=base/"burnin-alias"; alias.symlink_to(burnin,target_is_directory=True)
            with self.assertRaisesRegex(m16.SafetyStop,"symlink"):
                m16.resolve_phase_directories(str(soak),str(alias),"soak168h",True)
            (soak/"host-ledger.jsonl").write_text("historical")
            with self.assertRaisesRegex(m16.SafetyStop,"not_fresh"):
                m16.require_fresh_soak_evidence(soak)
            (soak/"host-ledger.jsonl").unlink()
            for name in ("phase.json","provision.json"): (soak/name).write_text("fixture")
            (soak/"avd-home").mkdir()
            m16.require_prelaunch_soak_directory(soak)
            (soak/"old-state.json").write_text("historical")
            with self.assertRaisesRegex(m16.SafetyStop,"not_new"):
                m16.require_prelaunch_soak_directory(soak)

    def test_phase_transition_uses_new_host_chain_without_historical_clock_rollback(self):
        health={"swapUsedMiB":0.0,"memoryFreePercent":50,"availableGiB":16.0,"load1":1.0,"thermal":"nominal"}
        with tempfile.TemporaryDirectory() as directory:
            burnin=Path(directory)/"burnin"; soak=Path(directory)/"soak"; burnin.mkdir(); soak.mkdir()
            with mock.patch.object(m16.time,"time",side_effect=[1.0,2.0,3.0,4.0]), \
                 mock.patch.object(m16.time,"monotonic_ns",side_effect=[1_000_000_000,2_000_000_000,3_000_000_000,4_000_000_000]):
                m16.append_host_event(burnin,{"type":"controller_start","mode":"burnin2h","profiles":20})
                m16.append_host_event(burnin,{"type":"monitor","workers":20,"complete":20,"qemuRssKiB":1,"freeGiB":99.0,"health":health})
                m16.append_host_event(soak,{"type":"controller_start","mode":"soak168h","profiles":20})
                m16.append_host_event(soak,{"type":"monitor","workers":20,"complete":0,"qemuRssKiB":1,"freeGiB":99.0,"health":health})
            burnin_lines=(burnin/"host-ledger.jsonl").read_text().splitlines()
            soak_lines=(soak/"host-ledger.jsonl").read_text().splitlines()
            self.assertEqual(json.loads(soak_lines[0])["previousHash"],"0"*64)
            self.assertNotEqual((burnin/"host-ledger.jsonl").resolve(),(soak/"host-ledger.jsonl").resolve())
            self.assertEqual(m16.verify_host_ledger((burnin/"host-ledger.jsonl").read_bytes(),1000,2000)["monitoredMillis"],1000)
            self.assertEqual(m16.verify_host_ledger((soak/"host-ledger.jsonl").read_bytes(),3000,4000)["monitoredMillis"],1000)


if __name__ == "__main__":
    unittest.main()
