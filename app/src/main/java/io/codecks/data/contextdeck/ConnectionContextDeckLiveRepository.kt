package io.codecks.data.contextdeck

import io.codecks.data.ConnectionRepository
import io.codecks.domain.contextdeck.AnalogControl
import io.codecks.domain.contextdeck.AnalogControlKind
import io.codecks.domain.contextdeck.ContextDeckLiveRepository
import io.codecks.domain.contextdeck.ContextDeckLiveSnapshot
import io.codecks.domain.contextdeck.LiveSignal
import io.codecks.domain.contextdeck.LiveSignalId
import io.codecks.domain.contextdeck.LiveSignalValue
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.StateSource

class ConnectionContextDeckLiveRepository(
    private val connectionRepository: ConnectionRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ContextDeckLiveRepository {
    override suspend fun refresh(): Result<ContextDeckLiveSnapshot> =
        connectionRepository.runBundledCommand(CONTEXT_DECK_LIVE_STATE_COMMAND).mapCatching { output ->
            parseContextDeckLiveSnapshot(output, nowMillis())
        }

    override suspend fun setAnalog(kind: AnalogControlKind, valuePercent: Int): Result<Unit> {
        require(valuePercent in 0..100) { "Analog value must be 0..100." }
        val command = when (kind) {
            AnalogControlKind.Volume -> volumeCommand(valuePercent)
            AnalogControlKind.Brightness -> brightnessCommand(valuePercent)
            AnalogControlKind.Timeline -> timelineCommand(valuePercent)
        }
        return connectionRepository.runBundledCommand(command).map { Unit }
    }
}

internal fun parseContextDeckLiveSnapshot(output: String, observedAtMillis: Long): ContextDeckLiveSnapshot {
    require(observedAtMillis >= 0) { "Context Deck snapshot time must be non-negative." }
    val fields = output.trim().split('\t')
    require(fields.size == 6) { "Mac returned an invalid Context Deck snapshot." }
    val muted = fields[0].strictBit()
    val volume = fields[1].strictPercent()
    val music = fields[2].strictOptionalBit()
    val timeline = fields[3].strictOptionalPercent()
    val vpn = fields[4].strictOptionalBit()
    val brightness = fields[5].strictOptionalPercent()
    val source = StateSource.SshProbe

    fun signal(id: LiveSignalId, value: Boolean?): LiveSignal = LiveSignal(
        id = id,
        value = when (value) {
            true -> LiveSignalValue.Active
            false -> LiveSignalValue.Inactive
            null -> LiveSignalValue.Unknown
        },
        status = if (value == null) ObservationStatus.Unavailable else ObservationStatus.Fresh,
        observedAtMillis = observedAtMillis.takeIf { value != null },
        source = source.takeIf { value != null },
    )

    return ContextDeckLiveSnapshot(
        signals = listOf(
            signal(LiveSignalId.Mute, muted),
            signal(LiveSignalId.Camera, null),
            signal(LiveSignalId.Music, music),
            signal(LiveSignalId.Recording, null),
            signal(LiveSignalId.Vpn, vpn),
        ),
        analogControls = listOf(
            AnalogControl(AnalogControlKind.Volume, volume, ObservationStatus.Fresh),
            AnalogControl(
                AnalogControlKind.Brightness,
                brightness,
                if (brightness == null) ObservationStatus.Unavailable else ObservationStatus.Fresh,
                if (brightness == null) "Brightness helper unavailable" else null,
            ),
            AnalogControl(
                AnalogControlKind.Timeline,
                timeline,
                if (timeline == null) ObservationStatus.Unavailable else ObservationStatus.Fresh,
                if (timeline == null) "No active Music timeline" else null,
            ),
        ),
        observedAtMillis = observedAtMillis,
    )
}

internal fun volumeCommand(percent: Int): String {
    require(percent in 0..100)
    return "/usr/bin/osascript -e 'set volume output volume $percent'"
}

internal fun brightnessCommand(percent: Int): String {
    require(percent in 0..100)
    val decimal = "%.2f".format(java.util.Locale.ROOT, percent / 100.0)
    return "if command -v betterdisplaycli >/dev/null 2>&1; then betterdisplaycli set -brightness=${percent}%; " +
        "elif command -v brightness >/dev/null 2>&1; then brightness $decimal; else exit 69; fi"
}

internal fun timelineCommand(percent: Int): String {
    require(percent in 0..100)
    return "/usr/bin/osascript -l JavaScript -e 'const m=Application(\"Music\"); " +
        "if(!m.running()) throw Error(\"music_not_running\"); const d=m.currentTrack().duration(); " +
        "if(!(d>0)) throw Error(\"timeline_unavailable\"); m.playerPosition=d*${percent}/100'"
}

private fun String.strictBit(): Boolean = when (this) {
    "0" -> false
    "1" -> true
    else -> error("Invalid required state bit.")
}

private fun String.strictOptionalBit(): Boolean? = when (this) {
    "-1" -> null
    else -> strictBit()
}

private fun String.strictPercent(): Int = toIntOrNull()?.takeIf { it in 0..100 }
    ?: error("Invalid required percentage.")

private fun String.strictOptionalPercent(): Int? = when (this) {
    "-1" -> null
    else -> strictPercent()
}

internal val CONTEXT_DECK_LIVE_STATE_COMMAND: String =
    "v=$(/usr/bin/osascript -l JavaScript -e 'const a=Application.currentApplication(); a.includeStandardAdditions=true; " +
        "const v=a.getVolumeSettings(); let p=-1; let t=-1; try { const m=Application(\"Music\"); if(m.running()){ " +
        "p=String(m.playerState())===\"playing\"?1:0; const d=m.currentTrack().duration(); if(d>0)t=Math.round(m.playerPosition()/d*100); }} catch(e){}; " +
        "[v.outputMuted?1:0,v.outputVolume,p,t].join(\"\\t\")'); " +
        "if /usr/sbin/scutil --nc list 2>/dev/null | /usr/bin/grep -q '(Connected)'; then n=1; else n=0; fi; " +
        "if command -v brightness >/dev/null 2>&1; then b=$(brightness -l 2>/dev/null | /usr/bin/awk '/display 0: brightness/{printf \"%d\",${'$'}NF*100;exit}'); else b=-1; fi; " +
        "test -n \"${'$'}b\" || b=-1; /usr/bin/printf '%s\\t%s\\t%s\\n' \"${'$'}v\" \"${'$'}n\" \"${'$'}b\""
