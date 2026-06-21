package com.adarsh.hellomom.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Centralised two-way sync between Firebase Firestore and the local Room cache.
 *
 * The dashboard (and every other history screen) reads exclusively from Room, keyed by the
 * owner's userId. For family members that local cache starts empty, so we must PULL the owner's
 * data from Firestore into Room before those flows can emit anything. For the owner we PUSH any
 * locally-pending changes so family members can see the latest data.
 */
interface SyncRepository {

    /**
     * Resolve the currently signed-in user, push any pending local changes, and pull the latest
     * data for the relevant owner into Room. Safe to call repeatedly and from the background;
     * returns success (no-op) when there is no signed-in user or no network.
     */
    suspend fun syncAll(): Result<Unit>

    /**
     * Throttled [syncAll] meant to be fired on every screen load: it no-ops when a sync finished
     * recently (within [maxAgeMillis]) or is already running, so navigating between screens stays
     * cheap while family members still see fresh owner data without a manual sync.
     */
    suspend fun syncIfStale(maxAgeMillis: Long = DEFAULT_SYNC_STALENESS_MS): Result<Unit>

    /**
     * Download the owner's user profile, appointments, medicines, symptoms, reminders and family
     * members from Firestore into Room. [ownerUserId] is the owner's userId (not a family UUID).
     */
    suspend fun pullOwnerData(ownerUserId: String): Result<Unit>

    /**
     * Real-time mirror for read-only family members. Attaches Firestore snapshot listeners on the
     * owner's shared collections; whenever the owner adds / edits / deletes / marks anything, the
     * listener re-pulls the owner's data into Room (reusing [pullOwnerData], so reconciliation and
     * alarm scheduling stay in one place). Because every screen reads Room, the UI repaints instantly
     * with no manual refresh. The returned [Flow] keeps the listeners alive until it is cancelled
     * (collect it from the dashboard's viewModelScope); they are removed automatically on cancel.
     *
     * Owners are NOT meant to use this — they already see their own writes via the local Room cache,
     * and re-pulling could clobber a not-yet-pushed local edit.
     */
    fun observeOwnerRealtime(ownerUserId: String): Flow<Unit>

    companion object {
        /** Screens consider the cache fresh for this long before triggering another sync. */
        const val DEFAULT_SYNC_STALENESS_MS: Long = 60_000

        /**
         * Shorter freshness window applied to read-only family members: they are not the writers,
         * so on every screen navigation we re-pull the owner's data almost immediately, ensuring
         * they always see the latest. (Owners keep the longer [DEFAULT_SYNC_STALENESS_MS].)
         */
        const val FAMILY_SYNC_STALENESS_MS: Long = 5_000
    }
}
