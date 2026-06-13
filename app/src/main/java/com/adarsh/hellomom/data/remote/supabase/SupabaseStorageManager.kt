package com.adarsh.hellomom.data.remote.supabase

import com.adarsh.hellomom.core.constants.SupabaseConfig
import io.github.jan.supabase.storage.storage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the Supabase Storage API for the public "documents" bucket.
 *
 * Keeps all storage details (bucket name, upload / url / delete calls) in one place so the
 * repository stays free of SDK specifics.
 */
@Singleton
class SupabaseStorageManager @Inject constructor(
    private val supabaseManager: SupabaseManager
) {
    private val bucket
        get() = supabaseManager.client.storage.from(SupabaseConfig.BUCKET)

    /** Upload [bytes] to [path] in the bucket. [upsert] overwrites any existing object. */
    suspend fun upload(path: String, bytes: ByteArray, upsert: Boolean = true) {
        bucket.upload(path, bytes) { this.upsert = upsert }
    }

    /** Public URL for an object in the public bucket. */
    fun publicUrl(path: String): String = bucket.publicUrl(path)

    /** Remove an object from the bucket. */
    suspend fun delete(path: String) {
        bucket.delete(path)
    }
}
