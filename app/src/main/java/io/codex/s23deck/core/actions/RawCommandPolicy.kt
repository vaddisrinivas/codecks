package io.codex.s23deck.core.actions

object RawCommandPolicy {
    private val blockedPatterns = listOf(
        BlockedPattern(Regex("""(?i)(^|[;&|]\s*)sudo(\s|$)"""), "sudo is not allowed from Codecks"),
        BlockedPattern(Regex("""(?i)\brm\s+(-[^\s]*r[^\s]*f|-rf|-fr)\b"""), "recursive force delete is blocked"),
        BlockedPattern(Regex("""(?i)\brm\b(?=[^;&|]*\s-r\b)(?=[^;&|]*\s-f\b)"""), "recursive force delete is blocked"),
        BlockedPattern(Regex("""(?i)\bdiskutil\s+(erase\w*|partition\w*|apfs\s+delete|unmountDisk)\b"""), "destructive disk commands are blocked"),
        BlockedPattern(Regex("""(?i)\bmkfs(\.|\s|$)"""), "filesystem creation commands are blocked"),
        BlockedPattern(Regex("""(?i)\bdd\s+.*\bof=/dev/"""), "raw device writes are blocked"),
        BlockedPattern(Regex("""(?i)\bchmod\s+-R\s+777\b"""), "broad permission changes are blocked"),
        BlockedPattern(Regex("""(?i)\bchmod\b(?=[^;&|]*\s-R\b)(?=[^;&|]*\s777\b)"""), "broad permission changes are blocked"),
        BlockedPattern(Regex("""(?i)\bchown\s+-R\s+[^;&|]+/"""), "broad ownership changes are blocked"),
        BlockedPattern(Regex("""(?i)\b(curl|wget)\b[^;&|]*\|\s*(sh|bash|zsh)\b"""), "download-and-run shell pipelines are blocked"),
        BlockedPattern(Regex("""(?i)\b(eval|sh|bash|zsh)\b[^;&|]*\$\([^)]*\b(curl|wget)\b"""), "download-and-run shell substitution is blocked"),
        BlockedPattern(Regex("""(?i)(^|[;&|]\s*)(sh|bash|zsh)\b[^;&|]*\s-c\b"""), "nested shell evaluation is blocked"),
        BlockedPattern(Regex("""(?i)(^|[;&|]\s*)(python3?|perl|ruby|node)\b[^;&|]*\s(-c|-e|--eval)\b"""), "inline interpreter evaluation is blocked"),
        BlockedPattern(Regex("""(?i)\bbase64\b[^;&|]*\|\s*(sh|bash|zsh)\b"""), "encoded shell payloads are blocked"),
        BlockedPattern(Regex("""(?i)\b(nc|ncat)\b[^;&|]*\s-e\s"""), "reverse shell execution is blocked"),
        BlockedPattern(Regex("""(?i)(^|[;&|]\s*)(scp|sftp|ftp)\b"""), "remote file transfer is blocked"),
        BlockedPattern(Regex("""(?i)(^|[;&|]\s*)rsync\b"""), "remote file transfer is blocked"),
        BlockedPattern(Regex("""(?i)(^|[;&|]\s*)(cat|less|more|cp|pbcopy|open)\b[^;&|]*(\.ssh/id_(rsa|ed25519)|\.aws/credentials|\.config/agent-secrets|\.codex/auth\.json|\.claude/settings\.json|\.gemini/\.env)"""), "secret file access is blocked"),
        BlockedPattern(Regex("""(?i)\bsecurity\s+(dump-keychain|find-generic-password|find-internet-password|unlock-keychain|export)\b"""), "keychain extraction is blocked"),
        BlockedPattern(Regex("""(?i)(^|[;&|]\s*)osascript\b.*\bkeystroke\b.*\b(password|passcode|token|secret)\b"""), "secret keystroke UI scripting is blocked"),
        BlockedPattern(Regex("""(?i)(^|[;&|]\s*)osascript\b.*\b(password|passcode|keychain)\b"""), "password/keychain UI scripting is blocked"),
        BlockedPattern(Regex("""(?i)(^|[;&|]\s*)osascript\b.*\bwith\s+administrator\s+privileges\b"""), "administrator UI scripting is blocked"),
        BlockedPattern(Regex("""(?i)(^|[;&|]\s*)launchctl\s+(bootstrap|bootout|load|unload|enable|disable|kickstart)\b"""), "launch service mutation is blocked"),
    )

    private val safeTemplatePatterns = listOf(
        Regex("""(?is)^\s*pbpaste\s*$"""),
        Regex("""(?is)^\s*pbpaste\s+2>/dev/null\s*\|\s*head\s+-c\s+[0-9]{1,5}\s*$"""),
        Regex("""(?is)^\s*printf(\s+%s)?\s+.+\|\s*pbcopy\s*$"""),
        Regex("""(?is)^\s*printf\s+[^|;&]+$"""),
        Regex("""(?is)^\s*open\s+-a\s+("[^"]+"|'[^']+'|[A-Za-z0-9_.:/~%+-]+)(\s+("[^"]+"|'[^']+'|https?://[^\s;&|]+|[A-Za-z0-9_.:/~%+-]+))?\s*$"""),
        Regex("""(?is)^\s*open(\s+-[a-zA-Z]+\s+("[^"]+"|'[^']+'|[^\s;&|]+))*\s+("[^"]+"|'[^']+'|https?://[^\s;&|]+|[A-Za-z0-9_.:/~%+-]+)\s*$"""),
        Regex("""(?is)^\s*osascript\s+(-e\s+("[^"]*"|'[^']*')\s*)+$"""),
        Regex("""(?is)^\s*shortcuts\s+run\s+("[^"]+"|'[^']+'|[A-Za-z0-9_.: /~%+-]+)\s*$"""),
        Regex("""(?is)^\s*networksetup\s+-getairportnetwork\s+en0\s+2>/dev/null\s*\|\s*sed\s+'s/\^Current Wi-Fi Network: //'\s*$"""),
        Regex("""(?is)^\s*sysctl\s+-n\s+kern\.boottime\s*\|\s*sed\s+'s/\.\*sec = \\\([0-9]\*\\\)\.\*/\\1/'\s*$"""),
        Regex("""(?is)^\s*pmset\s+-g\s+batt\s*\|\s*grep\s+-Eo\s+'[^']+'\s*\|\s*head\s+-1\s*\|\s*tr\s+-d\s+'%'\s*$"""),
        Regex("""(?is)^\s*if\s+\[\s+-e\s+("[^"]+"|'[^']+')\s+\];\s+then\s+stat\s+-f\s+%m\s+("[^"]+"|'[^']+');\s+fi\s*$"""),
        Regex("""(?is)^\s*sleep\s+[0-9]+(\.[0-9]+)?\s*$"""),
        Regex("""(?is)^\s*caffeinate\s+-u\s+-t\s+[0-9]{1,5}\s*$"""),
        Regex("""(?is)^\s*python3\s+-m\s+py_compile\s+("[^"]+"|'[^']+'|[^\s;&|]+)\s*$"""),
    )

    fun firstViolation(command: String): String? =
        blockedPatterns.firstOrNull { it.regex.containsMatchIn(command) }?.reason

    fun firstAllowlistViolation(command: String): String? {
        firstViolation(command)?.let { return it }
        val fragments = command
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
        if (fragments.isEmpty()) return "empty command"
        val unsupported = fragments.firstOrNull { fragment ->
            safeTemplatePatterns.none { pattern -> pattern.matches(fragment) }
        }
        return unsupported?.let { "unsupported command template: ${it.take(120)}" }
    }

    fun requireAllowed(command: String) {
        val reason = firstViolation(command) ?: return
        throw SecurityException("Command blocked: $reason")
    }

    fun requireSafeTemplate(command: String) {
        val reason = firstAllowlistViolation(command) ?: return
        throw SecurityException("Command blocked: $reason")
    }
}

private data class BlockedPattern(
    val regex: Regex,
    val reason: String,
)
