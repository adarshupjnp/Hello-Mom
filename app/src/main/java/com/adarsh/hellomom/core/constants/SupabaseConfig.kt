package com.adarsh.hellomom.core.constants

/**
 * Centralized Supabase configuration.
 *
 * All Supabase values live here so they are never hardcoded throughout the app.
 * The project uses a public Storage bucket ("documents"), so the anon key is safe
 * to ship in the client (it only grants the access the bucket policies allow).
 */
object SupabaseConfig {
    const val URL = "https://eddplixpeusejoykdokv.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVkZHBsaXhwZXVzZWpveWtkb2t2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODEwNTcxNDMsImV4cCI6MjA5NjYzMzE0M30.GeegwUNPDtNvfwC4FBhg0361QMPbbRFkZW20EsyMi1E"

    /** Public storage bucket that holds user documents. */
    const val BUCKET = "documents"
}
