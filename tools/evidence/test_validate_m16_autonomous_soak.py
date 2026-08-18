import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

MODULE_PATH=Path(__file__).with_name("validate_m16_autonomous_soak.py")
SPEC=importlib.util.spec_from_file_location("validate_m16",MODULE_PATH); validator=importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(validator)
SOURCE_REPO=Path(__file__).resolve().parents[2]
os.environ.setdefault("ANDROID_HOME",str(Path.home()/"Library/Android/sdk"))

def digest(data: bytes)->str: return hashlib.sha256(data).hexdigest()

class ValidatorIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.repo=Path(self.temp.name); self.run=self.repo/"run"; self.run.mkdir()
        (self.repo/"scripts").mkdir(); shutil.copy2(SOURCE_REPO/"scripts/m16_autonomous_soak.py",self.repo/"scripts/m16_autonomous_soak.py")
        targets=list((SOURCE_REPO/"app/build/outputs/apk/playInternal/release").glob("*.apk"))
        tests=list((SOURCE_REPO/"app/build/outputs/apk/androidTest/playInternal/release").glob("*.apk"))
        if len(targets)!=1 or len(tests)!=1: self.fail("exact current target/test APKs must be assembled")
        app=self.repo/"artifacts"; app.mkdir(); target=app/"target.apk"; test=app/"test.apk"
        shutil.copy2(targets[0],target); shutil.copy2(tests[0],test)
        fingerprint="b"*64
        methods=("exactTwentyImmutableIdentitiesAreDisjoint","profileContextsCannotReadEachOthersStores","manifestCarriesFiveExactNamedProcesses","fiveLiveServicesOwnFivePidsLocksAndRealRepositoryStores")
        cases="".join(f'<testcase classname="io.codecks.internalquality.m16.M16ProfileIsolationInstrumentedTest" name="{name}"/>' for name in methods)
        xml=app/"result.xml"; xml.write_text(f'<testsuite tests="4" failures="0" errors="0">{cases}<system-out>M16_BINDING package=app.codecks.internal flavor=playInternal project=:app api=35 fingerprintSha256={fingerprint}</system-out></testsuite>')
        deps=[]
        for milestone in validator.MILESTONES:
            path=self.repo/"tasks/test-evidence"/f"{milestone.lower()}.json"; path.parent.mkdir(parents=True,exist_ok=True); path.write_text(milestone)
            item={"milestone":milestone,"path":str(path.relative_to(self.repo)),"sha256":digest(path.read_bytes()),"status":"PASS"}
            if milestone=="M12": item.update(status="PASS_WITH_NOT_RUN",externalNotRun=11)
            deps.append(item)
        (self.repo/"tasks/test-evidence/m16-dependency-manifest.json").write_text(json.dumps({"dependencies":deps}))
        subprocess.run(["git","init","-q"],cwd=self.repo,check=True); subprocess.run(["git","add","."],cwd=self.repo,check=True)
        subprocess.run(["git","-c","user.name=M16 Test","-c","user.email=m16@example.invalid","commit","-qm","fixture"],cwd=self.repo,check=True)
        commit=subprocess.check_output(["git","rev-parse","HEAD"],cwd=self.repo,text=True).strip()
        self.host=validator.load_host(self.repo)
        isolation=self.host.verify_isolation_xml(xml)
        binding={"sourceCommit":commit,"targetApkPath":"artifacts/target.apk","targetApkSha256":digest(target.read_bytes()),
                 "testApkPath":"artifacts/test.apk","testApkSha256":digest(test.read_bytes()),"xmlResultPath":"artifacts/result.xml",
                 "xmlResultSha256":digest(xml.read_bytes()),"targetSignerSha256":self.host.apk_signer(target),
                 "testSignerSha256":self.host.apk_signer(test),"isolation":isolation}
        profiles=[]
        for avd in range(1,5):
            for slot in range(1,6): profiles.append(self.make_profile(avd,slot))
        host_ledger=self.make_host_ledger()
        proof=self.host.verify_host_ledger(host_ledger.read_bytes(),1000,7_201_000)
        devices=[{"avd":f"m16Soak0{i}Api35","serial":f"emulator-{5552+i*2}","api":35,"fingerprintSha256":fingerprint,"uid":10100,
          "dataDir":"/data/user/0/app.codecks.internal","targetApkSha256":binding["targetApkSha256"],"observedWallMillis":1,"observedUptimeMillis":1,
          "qemu":{"pid":100+i,"rssKiB":1024,"cmdlineSha256":"c"*64,"configSha256":"f"*64}} for i in range(1,5)]
        self.receipt={"schema":validator.SCHEMA,"milestone":"M16","status":"PASS","evidence":"AUTONOMOUS_PROXY","package":"app.codecks.internal","sourceCommit":commit,
          "binding":binding,"devices":devices,"runtime":{"isolatedAdb":{"port":5039,"endpoint":"tcp:127.0.0.1:5039","serverPid":99,"cmdlineSha256":"9"*64},"runIdentity":"1"*32,
          "emulatorPids":{f"m16Soak0{i}Api35":100+i for i in range(1,5)},"defaultAdbAudit":{"sanitizedNonM16EmulatorCount":2}},
          "profiles":profiles,"dependencies":deps,"failureArtifacts":[],
          "summary":{"admittedSessions":40,"eligibleSessions":40,"acknowledgedOperations":4840,"crashOrAnrSessions":0,"p0":0,"p1":0,"classifiedFailures":0},
          "wall":{"startedWallMillis":1000,"finishedWallMillis":7_201_000,"hostLedgerSha256":digest(host_ledger.read_bytes()),**proof},"limitations":["AUTONOMOUS_PROXY only"]}

    def tearDown(self): self.temp.cleanup()

    def event(self, lines, previous, profile, process, nonce, body):
        pid=body.pop("_pid",123)
        body.update(profileId=profile,processName=process,pid=pid,originNonce=nonce,previousHash=previous)
        value=digest(json.dumps(body,separators=(",", ":")).encode()); body["eventHash"]=value; lines.append(json.dumps(body,separators=(",", ":"))); return value

    def make_profile(self,avd,slot,recovery=False,same_pid=False):
        profile=f"avd{avd:02d}-p{slot:02d}"; process=f"app.codecks.internal:m16p{slot:02d}"; nonce=digest(f"codecks-m16-nonce-v1:{profile}".encode())[:32]
        seed=digest(f"codecks-m16-seed-v1:{profile}".encode())[:16]; previous="0"*64; lines=[]; total=0
        categories=sorted(self.host.CATEGORIES)
        current_pid=123
        for window in (1,2):
            start=(window-1)*3_700_000+1
            previous=self.event(lines,previous,profile,process,nonce,{"type":"admitted","elapsedRealtimeMillis":start,"wallTimeMillis":start,"windowIndex":window,"bootId":"boot","eligibleTarget":2,"profileRoot":f"m16/profiles/{profile}","seed":seed})
            for sequence in range(1,122):
                elapsed=start+(sequence-1)*30_000; total+=1
                if recovery and window==1 and sequence==61:
                    new_pid=123 if same_pid else 124
                    previous=self.event(lines,previous,profile,process,nonce,{"type":"resumed","elapsedRealtimeMillis":elapsed-1,"wallTimeMillis":elapsed-1,
                        "windowIndex":window,"sequence":60,"priorPid":123,"newPid":new_pid,"_pid":new_pid})
                    current_pid=new_pid
                previous=self.event(lines,previous,profile,process,nonce,{"type":"ack","elapsedRealtimeMillis":elapsed,"wallTimeMillis":elapsed,"windowIndex":window,"sequence":sequence,"_pid":current_pid,
                  "ackId":digest(f"{nonce}:{window}:{sequence}".encode()),"category":categories[(sequence-1)%len(categories)],"operationLatencyMillis":1,"totalPssKb":1,"batteryPercentProxy":90,
                  "applicationExitReasons":{"crash":0,"nativeCrash":0,"anr":0,"self":0,"other":0},"crashOrAnr":False})
            previous=self.event(lines,previous,profile,process,nonce,{"type":"window_complete","elapsedRealtimeMillis":start+3_600_000,"wallTimeMillis":start+3_600_000,"windowIndex":window,
              "sequence":121,"elapsedMillis":3_600_000,"activeMillis":3_600_000,"categories":categories,"eligible":True})
        previous=self.event(lines,previous,profile,process,nonce,{"type":"profile_complete","elapsedRealtimeMillis":7_300_002,"wallTimeMillis":7_300_002,"attemptedWindows":2,"eligibleWindows":2,"acknowledgedOperations":total})
        directory=self.run/"profiles"/profile; directory.mkdir(parents=True,exist_ok=True); ledger=directory/"ledger.jsonl"; ledger.write_text("\n".join(lines)+"\n"); checkpoint=directory/"checkpoint.json"; checkpoint.write_text(json.dumps({"ledgerHeadHash":previous}))
        result=self.host.verify_worker_ledger(ledger.read_bytes(),checkpoint.read_bytes(),profile,process,nonce)
        return {**result,"avd":f"m16Soak0{avd}Api35","process":process,"ledgerPath":str(ledger.relative_to(self.run)),"checkpointPath":str(checkpoint.relative_to(self.run))}

    def make_host_ledger(self,recovery=False,stale_ack=False,wrong_profile=False,wrong_probe=False,missing_event=False,probe_hash=None):
        path=self.run/"host-ledger.jsonl"; previous="0"*64; lines=[]
        def add(wall,mono,body):
            nonlocal previous
            event={"schema":"codecks.m16.host-event.v1","previousHash":previous,"hostWallMillis":wall,"hostMonotonicNanos":mono,**body}
            previous=digest(json.dumps(event,sort_keys=True,separators=(",", ":")).encode()); event["eventHash"]=previous; lines.append(json.dumps(event,sort_keys=True,separators=(",", ":")))
        add(1000,1_000_000_000,{"type":"controller_start","mode":"burnin2h","profiles":20}); add(1001,1_001_000_000,{"type":"twenty_workers_admitted","workers":20})
        if recovery:
            profile="avd01-p02" if wrong_profile else "avd01-p01"; old_ack=digest(f"{digest('codecks-m16-nonce-v1:avd01-p01'.encode())[:32]}:1:60".encode())
            packet="failures/1000-avd01-p01"
            if not missing_event: add(1100,1_100_000_000,{"type":"unexpected_worker_missing","profileId":"avd01-p01","failurePacket":packet,"oldPid":123,"lastAckId":old_ack})
            fresh=old_ack if stale_ack else digest(f"{digest('codecks-m16-nonce-v1:avd01-p01'.encode())[:32]}:1:61".encode())
            add(1200,1_200_000_000,{"type":"worker_restarted","profileId":profile,"oldPid":123,"newPid":124,"freshAckId":fresh,
                "repoProbePath":f"restart-probes/{profile}-124.json","repoProbeSha256":"0"*64 if wrong_probe else (probe_hash or "a"*64)})
        health={"swapUsedMiB":0.0,"memoryFreePercent":50,"availableGiB":16.0,"load1":1.0,"thermal":"nominal"}
        for index in range(1,481): add(1000+index*15000,1_000_000_000+index*15_000_000_000,{"type":"monitor","workers":20,"complete":0,"qemuRssKiB":1,"freeGiB":99.0,"health":health})
        path.write_text("\n".join(lines)+"\n"); return path

    def validate(self,value=None):
        path=self.run/"receipt.json"; path.write_text(json.dumps(value or self.receipt)); validator.validate(path,self.repo)

    def test_repo_real_positive(self): self.validate()

    def test_end_to_end_mutations_fail(self):
        mutations=[]
        for edit in (
            lambda x:x["binding"].update(targetSignerSha256="0"*64), lambda x:x["binding"]["isolation"].update(api=34),
            lambda x:x["profiles"][0].update(eligibleSessions=1), lambda x:x["wall"].update(monitoredMillis=1),
            lambda x:x["summary"].update(classifiedFailures=1), lambda x:x.update(extra=1)):
            value=copy.deepcopy(self.receipt); edit(value); mutations.append(value)
        for value in mutations:
            with self.assertRaises((ValueError,self.host.SafetyStop)): self.validate(value)

    def test_repaired_recovery_cross_binding_and_mutations(self):
        worker=self.make_profile(1,1,recovery=True)
        probe=self.run/"restart-probes/avd01-p01-124.json"; probe.parent.mkdir(); probe.write_text('{"profileId":"avd01-p01"}')
        ledger=self.make_host_ledger(recovery=True,probe_hash=digest(probe.read_bytes()))
        proof=self.host.verify_host_ledger(ledger.read_bytes(),1000,7_201_000)
        validator.cross_bind_recoveries([worker],proof); validator.verify_recovery_probes(self.run,proof,self.host)
        with self.assertRaises(self.host.SafetyStop): self.make_profile(1,1,recovery=True,same_pid=True)
        for options in ({"stale_ack":True},{"wrong_profile":True},{"missing_event":True}):
            with self.assertRaises(self.host.SafetyStop):
                self.host.verify_host_ledger(self.make_host_ledger(recovery=True,**options).read_bytes(),1000,7_201_000)
        bad=copy.deepcopy(proof); bad["recoveries"][0]["repoProbeSha256"]="0"*64
        with self.assertRaises(ValueError): validator.verify_recovery_probes(self.run,bad,self.host)

if __name__=="__main__": unittest.main()
