package com.adarsh.hellomom.core.utils

import android.util.Log

/**
 * Centralised, filterable logging for the data layer so you can SEE exactly what gets written
 * to the local Room database vs. what gets written to / read from Firebase.
 *
 * Filter logcat by the single tag to watch the whole sync story:
 *
 *     adb logcat -s HelloMomSync
 *
 * Conventions:
 *  - [LOCAL  ⇩]  data being written into the on-device Room database
 *  - [FIREBASE ⇧] data being pushed up to Cloud Firestore
 *  - [FIREBASE ⇩] data being pulled down from Cloud Firestore
 *  - [RESOLVE]    role / owner resolution and pregnancy-week calculations
 */
object SyncLogger {

    const val TAG = "HelloMomSync"

    /** A write into the on-device Room database. */
    fun local(action: String, entity: String, details: String) {
        Log.d(TAG, "[LOCAL  ⇩] $action  $entity → $details")
    }

    /** A write pushed up to Cloud Firestore. */
    fun firebaseWrite(action: String, path: String, details: String) {
        Log.d(TAG, "[FIREBASE ⇧] $action  $path → $details")
    }

    /** A read pulled down from Cloud Firestore. */
    fun firebaseRead(action: String, path: String, details: String) {
        Log.d(TAG, "[FIREBASE ⇩] $action  $path → $details")
    }

    /** Role / owner resolution and pregnancy-week calculation. */
    fun resolve(message: String) {
        Log.d(TAG, "[RESOLVE] $message")
    }

    /** General informational milestone in the sync flow. */
    fun info(message: String) {
        Log.i(TAG, "[INFO] $message")
    }

    /** A non-fatal problem (offline, swallowed exception, missing data). */
    fun warn(message: String, error: Throwable? = null) {
        if (error != null) Log.w(TAG, "[WARN] $message", error) else Log.w(TAG, "[WARN] $message")
    }

    fun error(message: String, error: Throwable? = null) {
        if (error != null) Log.e(TAG, "[ERROR] $message", error) else Log.e(TAG, "[ERROR] $message")
    }
}
