# M09D controller lifecycle TODO

- Repeatability source phase is direct from `eeb664106fe9a6d6fc7fac74dc977d10583aeb65` and changes exactly six evidence paths.
- The bounded CPU controller-recreation proxy is complete only when the exact C1 -> C2 exact-three -> C3 receipt chain passes the final receipt validator.
- Raw material in committed evidence: `NOT_RETAINED`; ignored local build-output retention: `NOT_PROVEN`; later raw-transformation revalidation: `NOT_POSSIBLE` by design.
- Invocation-owned writable Gradle-home material: `NOT_RETAINED`; post-run live revalidation: `NOT_APPLICABLE`; final validation binds committed C2 state.
- Abrupt-termination raw cleanup: `NOT_PROVEN`; safely remove the exact raw result and recollect before receipt validation.
- Long-press Compose UI: `NOT_RUN`.
- Provider and live network execution: `NOT_RUN`.
- Durable disk and process recreation: `NOT_RUN`; repository-backed controller recreation is only a proxy.
- Device, physical phone, public release, signing, publishing: `NOT_RUN`.
- AI delete undo: `NOT_AVAILABLE` by current product behavior.
