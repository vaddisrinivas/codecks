# M09D controller lifecycle TODO

- Truth-narrowing source phase is direct from `f3b2a29a2c98ddace4ac9d4ad87122197c49d745` and changes exactly six evidence paths.
- The bounded CPU controller-recreation proxy is complete only when the exact C1 -> C2 exact-three -> C3 receipt chain passes the final receipt validator.
- Raw material in committed evidence: `NOT_RETAINED`; ignored local build-output retention: `NOT_PROVEN`; later raw-transformation revalidation: `NOT_POSSIBLE` by design.
- Abrupt-termination raw cleanup: `NOT_PROVEN`; safely remove the exact raw result and recollect before receipt validation.
- Long-press Compose UI: `NOT_RUN`.
- Provider and live network execution: `NOT_RUN`.
- Durable disk and process recreation: `NOT_RUN`; repository-backed controller recreation is only a proxy.
- Device, physical phone, public release, signing, publishing: `NOT_RUN`.
- AI delete undo: `NOT_AVAILABLE` by current product behavior.
