package io.codecks.data.targets

import io.codecks.domain.targets.MacTarget

object SshTargetKeyAlias {
    fun forTarget(target: MacTarget): String =
        "codecks.ssh.${target.logicalId.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
}
