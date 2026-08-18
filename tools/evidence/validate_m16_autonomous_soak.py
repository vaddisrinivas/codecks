#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys

SCHEMA = "codecks.autonomous-maturity.m16-soak.v1"
MILESTONES = tuple(f"M{i}" for i in range(10, 16))
FORBIDDEN_KEYS = re.compile(r"(?i)(secret|token|password|clipboard(text|content)|private.?key|username|userpath)")
FORBIDDEN_VALUES = re.compile(r"(?i)(/Users/|/home/|BEGIN [A-Z ]*PRIVATE KEY|bearer\s|password=|token=)")
TOP = {"schema","milestone","status","evidence","package","sourceCommit","binding","devices","runtime","profiles","dependencies","summary","wall","failureArtifacts","limitations"}


def schema_validate(value: object, schema: dict, location: str = "$") -> None:
    if "const" in schema and value != schema["const"]: raise ValueError(f"schema_const:{location}")
    if "enum" in schema and value not in schema["enum"]: raise ValueError(f"schema_enum:{location}")
    kind = schema.get("type")
    valid = {"object": isinstance(value,dict), "array": isinstance(value,list), "string": isinstance(value,str),
             "integer": isinstance(value,int) and not isinstance(value,bool)}.get(kind, True)
    if not valid: raise ValueError(f"schema_type:{location}")
    if isinstance(value,dict):
        required=set(schema.get("required",[])); properties=schema.get("properties",{})
        if not required.issubset(value): raise ValueError(f"schema_required:{location}")
        if schema.get("additionalProperties") is False and set(value)-set(properties): raise ValueError(f"schema_closed:{location}")
        for key,child in value.items():
            if key in properties: schema_validate(child,properties[key],f"{location}.{key}")
    elif isinstance(value,list):
        if len(value)<schema.get("minItems",0) or len(value)>schema.get("maxItems",sys.maxsize): raise ValueError(f"schema_items:{location}")
        for index,child in enumerate(value): schema_validate(child,schema.get("items",{}),f"{location}[{index}]")
    elif isinstance(value,str):
        if "pattern" in schema and not re.search(schema["pattern"],value): raise ValueError(f"schema_pattern:{location}")
        if len(value)>schema.get("maxLength",sys.maxsize): raise ValueError(f"schema_length:{location}")
    elif isinstance(value,int):
        if value<schema.get("minimum",value) or value>schema.get("maximum",value): raise ValueError(f"schema_range:{location}")


def load_host(repo: Path):
    path = repo / "scripts/m16_autonomous_soak.py"
    spec = importlib.util.spec_from_file_location("m16_host_validator", path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader
    spec.loader.exec_module(module)
    return module


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def privacy_scan(value: object, location: str = "$") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if FORBIDDEN_KEYS.search(key):
                raise ValueError(f"privacy_key:{location}.{key}")
            privacy_scan(child, f"{location}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value): privacy_scan(child, f"{location}[{index}]")
    elif isinstance(value, str) and FORBIDDEN_VALUES.search(value):
        raise ValueError(f"privacy_value:{location}")


def relative_file(base: Path, value: str) -> Path:
    path = (base / value).resolve()
    if not path.is_relative_to(base.resolve()) or not path.is_file():
        raise ValueError("artifact_path")
    return path


def cross_bind_recoveries(recomputed: list[dict], host_proof: dict) -> None:
    worker=sorted((item for profile in recomputed for item in profile["recoveries"]),key=lambda item:(item["profileId"],item["priorPid"],item["newPid"],item["freshAckId"]))
    host=sorted(({key:item[key] for key in ("profileId","priorPid","newPid","freshAckId")} for item in host_proof["recoveries"]),key=lambda item:(item["profileId"],item["priorPid"],item["newPid"],item["freshAckId"]))
    if worker!=host or len(host)!=host_proof["unexpectedWorkerDeaths"]: raise ValueError("recovery_cross_binding")


def verify_recovery_probes(base: Path, host_proof: dict, host_module) -> None:
    for recovery in host_proof["recoveries"]:
        probe=relative_file(base,recovery["repoProbePath"])
        if sha256(probe)!=recovery["repoProbeSha256"] or host_module.PRIVACY.search(probe.read_bytes()): raise ValueError("recovery_probe_binding")


def validate(receipt_path: Path, repo: Path) -> None:
    receipt_path, repo = receipt_path.resolve(), repo.resolve()
    receipt = json.loads(receipt_path.read_text())
    schema_path=Path(__file__).with_name("schemas")/"autonomous-maturity-m16-soak-v1.schema.json"
    schema_validate(receipt,json.loads(schema_path.read_text()))
    if set(receipt) != TOP: raise ValueError("closed_top_level")
    if (receipt["schema"],receipt["milestone"],receipt["evidence"],receipt["package"]) != (SCHEMA,"M16","AUTONOMOUS_PROXY","app.codecks.internal"):
        raise ValueError("identity")
    if receipt["status"] != "PASS": raise ValueError("status_not_pass")
    commit = receipt["sourceCommit"]
    if not re.fullmatch(r"[0-9a-f]{40}", commit): raise ValueError("source_commit")
    if subprocess.run(["git","cat-file","-e",f"{commit}^{{commit}}"],cwd=repo).returncode: raise ValueError("commit_missing")
    if subprocess.run(["git","merge-base","--is-ancestor",commit,"HEAD"],cwd=repo).returncode: raise ValueError("commit_not_ancestor")
    binding = receipt["binding"]
    if binding.get("sourceCommit") != commit: raise ValueError("binding_commit")
    for prefix in ("targetApk","testApk","xmlResult"):
        path = relative_file(repo, binding[f"{prefix}Path"])
        if sha256(path) != binding[f"{prefix}Sha256"]: raise ValueError(f"binding_{prefix}")
    host = load_host(repo)
    target_path=relative_file(repo,binding["targetApkPath"]); test_path=relative_file(repo,binding["testApkPath"])
    if host.apk_signer(target_path)!=binding["targetSignerSha256"] or host.apk_signer(test_path)!=binding["testSignerSha256"]: raise ValueError("signer")
    isolation=host.verify_isolation_xml(relative_file(repo,binding["xmlResultPath"]))
    if isolation!=binding["isolation"]: raise ValueError("isolation_binding")
    devices = receipt["devices"]
    if len(devices) != 4 or [item.get("avd") for item in devices] != [f"m16Soak0{i}Api35" for i in range(1,5)]: raise ValueError("devices")
    if len({item.get("serial") for item in devices}) != 4 or any(item.get("api") != 35 or item.get("dataDir") != "/data/user/0/app.codecks.internal" or item.get("targetApkSha256") != binding["targetApkSha256"] for item in devices):
        raise ValueError("device_binding")
    if binding["isolation"]["fingerprintSha256"] not in {item["fingerprintSha256"] for item in devices}: raise ValueError("isolation_device")
    device_keys={"avd","serial","api","fingerprintSha256","uid","dataDir","targetApkSha256","qemu","observedWallMillis","observedUptimeMillis"}; qemu_keys={"pid","rssKiB","cmdlineSha256","configSha256"}
    if any(set(item)!=device_keys or set(item["qemu"])!=qemu_keys or not all(re.fullmatch(r"[0-9a-f]{64}",item["qemu"][key]) for key in ("cmdlineSha256","configSha256")) for item in devices):
        raise ValueError("device_closed")
    runtime=receipt["runtime"]
    isolated=runtime["isolatedAdb"]
    if (set(isolated)!={"port","endpoint","serverPid","cmdlineSha256"} or isolated["port"]!=5039 or isolated["endpoint"]!="tcp:127.0.0.1:5039"
            or not isinstance(isolated["serverPid"],int) or isolated["serverPid"]<=0 or not re.fullmatch(r"[0-9a-f]{64}",isolated["cmdlineSha256"])
            or not re.fullmatch(r"[0-9a-f]{32}",runtime["runIdentity"])
            or set(runtime["emulatorPids"])!={f"m16Soak0{i}Api35" for i in range(1,5)}
            or set(runtime["emulatorPids"].values())!={item["qemu"]["pid"] for item in devices}
            or set(runtime["defaultAdbAudit"])!={"sanitizedNonM16EmulatorCount"}): raise ValueError("runtime_binding")
    dependencies = receipt["dependencies"]
    if [item.get("milestone") for item in dependencies] != list(MILESTONES): raise ValueError("dependency_order")
    for item in dependencies:
        if set(item) - {"milestone","path","sha256","status","externalNotRun"}: raise ValueError("dependency_closed")
        if sha256(relative_file(repo,item["path"])) != item["sha256"]: raise ValueError("dependency_digest")
        if item["milestone"] == "M12":
            if item.get("status") != "PASS_WITH_NOT_RUN" or item.get("externalNotRun") != 11: raise ValueError("m12_boundary")
        elif item.get("status") != "PASS": raise ValueError("dependency_status")
    profiles = receipt["profiles"]
    if len(profiles) != 20: raise ValueError("profile_count")
    recomputed = []
    for index, profile in enumerate(profiles):
        avd, slot = index // 5 + 1, index % 5 + 1
        profile_id = f"avd{avd:02d}-p{slot:02d}"
        if (profile.get("profileId"),profile.get("avd"),profile.get("process")) != (profile_id,f"m16Soak0{avd}Api35",f"app.codecks.internal:m16p{slot:02d}"):
            raise ValueError("profile_identity")
        ledger = relative_file(receipt_path.parent, profile["ledgerPath"])
        checkpoint = relative_file(receipt_path.parent, profile["checkpointPath"])
        nonce = hashlib.sha256(f"codecks-m16-nonce-v1:{profile_id}".encode()).hexdigest()[:32]
        actual = host.verify_worker_ledger(ledger.read_bytes(),checkpoint.read_bytes(),profile_id,profile["process"],nonce)
        for key,value in actual.items():
            if profile.get(key) != value: raise ValueError(f"profile_artifact:{profile_id}:{key}")
        recomputed.append(actual)
    target = profiles[0]["eligibleSessions"]
    if target not in {2,168} or any(item["eligibleSessions"] != target for item in profiles): raise ValueError("eligible_target")
    summary = receipt["summary"]
    host_ledger = relative_file(receipt_path.parent,"host-ledger.jsonl")
    wall = receipt["wall"]
    host_proof = host.verify_host_ledger(host_ledger.read_bytes(),wall["startedWallMillis"],wall["finishedWallMillis"])
    cross_bind_recoveries(recomputed,host_proof)
    verify_recovery_probes(receipt_path.parent,host_proof,host)
    crash_sessions=sum(x["crashOrAnrSessions"] for x in recomputed)
    if host_proof["crashOrAnrEvents"]!=crash_sessions or host_proof["failurePacketCount"]!=crash_sessions+host_proof["unexpectedWorkerDeaths"]: raise ValueError("failure_event_count")
    expected = {"admittedSessions":sum(x["attemptedSessions"] for x in recomputed),"eligibleSessions":sum(x["eligibleSessions"] for x in recomputed),
                "acknowledgedOperations":sum(x["acknowledgedOperations"] for x in recomputed),"crashOrAnrSessions":sum(x["crashOrAnrSessions"] for x in recomputed),
                "p0":sum(x["eligibleSessions"]!=target for x in recomputed)+int(sum(x["eligibleSessions"] for x in recomputed)!=target*20),
                "p1":crash_sessions+host_proof["unexpectedWorkerDeaths"],
                "classifiedFailures":sum(x["classifiedFailures"] for x in recomputed)+host_proof["unexpectedWorkerDeaths"]+crash_sessions}
    if summary != expected or summary["eligibleSessions"] != target*20 or summary["acknowledgedOperations"] < target*20*100 or summary["p0"] or summary["p1"]: raise ValueError("summary")
    crash_free = (summary["eligibleSessions"]-summary["crashOrAnrSessions"])/summary["eligibleSessions"]
    if crash_free < .995: raise ValueError("crash_rate")
    if wall["finishedWallMillis"]-wall["startedWallMillis"] < target*3_600_000 or host_proof["monitoredMillis"] < target*3_600_000: raise ValueError("wall_duration")
    if sha256(host_ledger) != wall["hostLedgerSha256"] or any(wall.get(key)!=value for key,value in host_proof.items()): raise ValueError("host_ledger")
    for artifact in receipt["failureArtifacts"]:
        path=relative_file(receipt_path.parent,artifact["path"])
        if path.stat().st_size>host.FAILURE_ARTIFACT_CAP or sha256(path)!=artifact["sha256"] or host.PRIVACY.search(path.read_bytes()): raise ValueError("failure_artifact")
    packet_dirs={str(Path(item["path"]).parent) for item in receipt["failureArtifacts"]}
    if packet_dirs!=set(host_proof["failurePackets"]): raise ValueError("failure_packet_bijection")
    limitations = receipt["limitations"]
    if not isinstance(limitations,list) or not 1<=len(limitations)<=8 or any(not isinstance(x,str) or not x or len(x)>240 for x in limitations): raise ValueError("limitations")
    privacy_scan(receipt)


def main() -> int:
    if len(sys.argv)!=2:
        print("usage: validate_m16_autonomous_soak.py RECEIPT",file=sys.stderr); return 2
    try:
        validate(Path(sys.argv[1]),Path(__file__).resolve().parents[2]); print("PASS M16 AUTONOMOUS_PROXY receipt"); return 0
    except (ValueError,OSError,json.JSONDecodeError) as error:
        print(f"FAIL:{error}",file=sys.stderr); return 1


if __name__=="__main__": raise SystemExit(main())
