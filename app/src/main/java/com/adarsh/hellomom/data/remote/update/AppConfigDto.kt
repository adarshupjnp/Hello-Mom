package com.adarsh.hellomom.data.remote.update

import com.google.gson.annotations.SerializedName

/**
 * Raw row returned by Supabase for:
 *   GET /rest/v1/app_config?id=eq.1&select=*
 *
 * The PostgREST endpoint always returns a JSON array, so callers receive a
 * [List] of these and pick the first row.
 */
data class AppConfigDto(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("latest_version_code")
    val latestVersionCode: Int = 0,

    @SerializedName("latest_version_name")
    val latestVersionName: String = "",

    @SerializedName("apk_url")
    val apkUrl: String = "",

    @SerializedName("force_update")
    val forceUpdate: Boolean = false,

    @SerializedName("updated_at")
    val updatedAt: String? = null
)
