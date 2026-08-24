package io.codecks.data.contextdeck

import io.codecks.data.ConnectionRepository
import io.codecks.domain.contextdeck.SpaceCard
import io.codecks.domain.contextdeck.WindowCard
import io.codecks.domain.contextdeck.WindowSpaceMap
import io.codecks.domain.reactive.ObservationStatus
import org.json.JSONObject

class ConnectionWindowSpaceRepository(
    private val connectionRepository: ConnectionRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun refresh(): Result<WindowSpaceMap> =
        connectionRepository.runBundledCommandRaw(WINDOW_SPACE_QUERY_COMMAND).mapCatching { payload ->
            parseWindowSpaceMap(payload, nowMillis())
        }

    suspend fun focus(windowId: String): Result<Unit> {
        val split = windowId.lastIndexOf(':')
        require(split in 2 until windowId.lastIndex) { "Window id is invalid." }
        val bundleId = windowId.substring(0, split)
        val index = windowId.substring(split + 1).toIntOrNull()
        require(bundleId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{1,127}")) && ".." !in bundleId) {
            "Window bundle id is invalid."
        }
        require(index != null && index in 0 until WindowSpaceMap.MAX_WINDOWS) { "Window index is invalid." }
        return connectionRepository.runBundledCommand(focusWindowCommand(bundleId, index)).map { }
    }
}

internal fun parseWindowSpaceMap(payload: String, observedAtMillis: Long): WindowSpaceMap {
    require(payload.toByteArray(Charsets.UTF_8).size in 2..64 * 1024) { "Window map payload size is invalid." }
    val root = JSONObject(payload)
    require(root.keys().asSequence().toSet() == setOf("windows", "spaces")) { "Window map fields are invalid." }
    val windowsJson = root.getJSONArray("windows")
    val spacesJson = root.getJSONArray("spaces")
    require(windowsJson.length() <= WindowSpaceMap.MAX_WINDOWS) { "Window map is too large." }
    require(spacesJson.length() <= WindowSpaceMap.MAX_SPACES) { "Space map is too large." }
    val windows = List(windowsJson.length()) { index ->
        val item = windowsJson.getJSONObject(index)
        require(
            item.keys().asSequence().toSet() ==
                setOf("id", "title", "appName", "bundleId", "spaceId", "displayId", "focused"),
        ) { "Window fields are invalid." }
        WindowCard(
            id = item.getString("id"),
            title = item.getString("title"),
            appName = item.getString("appName"),
            bundleId = item.getString("bundleId"),
            spaceId = item.optString("spaceId").takeIf(String::isNotBlank),
            displayId = item.optString("displayId").takeIf(String::isNotBlank),
            focused = item.getBoolean("focused"),
        )
    }
    val spaces = List(spacesJson.length()) { index ->
        val item = spacesJson.getJSONObject(index)
        require(item.keys().asSequence().toSet() == setOf("id", "label", "displayId", "focused")) {
            "Space fields are invalid."
        }
        SpaceCard(
            id = item.getString("id"),
            label = item.getString("label"),
            displayId = item.optString("displayId").takeIf(String::isNotBlank),
            focused = item.getBoolean("focused"),
        )
    }
    return WindowSpaceMap(windows, spaces, ObservationStatus.Fresh, observedAtMillis)
}

internal fun focusWindowCommand(bundleId: String, windowIndex: Int): String {
    require(bundleId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{1,127}")) && ".." !in bundleId)
    require(windowIndex in 0 until WindowSpaceMap.MAX_WINDOWS)
    return "/usr/bin/osascript -l JavaScript -e 'const se=Application(\"System Events\"); " +
        "const ps=se.applicationProcesses.whose({bundleIdentifier:\"$bundleId\"}); if(ps.length<1) throw Error(\"app_unavailable\"); " +
        "const ws=ps[0].windows(); if(ws.length<${windowIndex + 1}) throw Error(\"window_unavailable\"); " +
        "ps[0].frontmost=true; const a=ws[$windowIndex].actions.byName(\"AXRaise\"); if(a.length<1) throw Error(\"focus_unavailable\"); a[0].perform()'"
}

internal const val WINDOW_SPACE_QUERY_COMMAND: String =
    "/usr/bin/osascript -l JavaScript -e 'const se=Application(\"System Events\"); const ps=se.applicationProcesses.whose({backgroundOnly:false}); " +
        "const windows=[]; for(let i=0;i<ps.length&&windows.length<48;i++){try{const p=ps[i]; if(!p.visible())continue; " +
        "const b=String(p.bundleIdentifier()); const n=String(p.name()); const ws=p.windows(); for(let j=0;j<ws.length&&windows.length<48;j++){ " +
        "const t=String(ws[j].name()||\"Untitled\").replace(/[\\u0000-\\u001f\\u007f]/g,\" \").slice(0,96); " +
        "windows.push({id:b+\":\"+j,title:t,appName:n.slice(0,96),bundleId:b,spaceId:\"current\",displayId:\"\",focused:Boolean(p.frontmost())&&j===0});}}catch(e){}} " +
        "JSON.stringify({windows:windows,spaces:[{id:\"current\",label:\"Current Space\",displayId:\"\",focused:true}]})'"
