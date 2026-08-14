package io.legado.app.help.storage

import cn.hutool.crypto.digest.DigestUtil
import cn.hutool.crypto.symmetric.AES
import io.legado.app.help.config.LocalConfig

class BackupAES : AES(
    DigestUtil.sha256(LocalConfig.password ?: "").copyOfRange(0, 16)
)