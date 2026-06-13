package com.adarsh.hellomom.domain.model

/**
 * Clean domain representation of the latest published build, mapped from the
 * Supabase `app_config` row. Decoupled from the network DTO on purpose.
 */
data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
    val forceUpdate: Boolean
)
