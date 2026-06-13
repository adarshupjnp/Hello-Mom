package com.adarsh.hellomom.data.remote.supabase

import com.adarsh.hellomom.core.constants.SupabaseConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated Supabase singleton.
 *
 * Owns the single [SupabaseClient] instance for the whole app and installs only the
 * plugins we need (Storage). Configuration is read from [SupabaseConfig] — nothing is
 * hardcoded here. Provided as a Hilt @Singleton so the same client is reused everywhere.
 */
@Singleton
class SupabaseManager @Inject constructor() {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.URL,
            supabaseKey = SupabaseConfig.ANON_KEY
        ) {
            install(Storage)
        }
    }
}
