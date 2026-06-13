package com.adarsh.hellomom.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * A user document stored in Supabase Storage with metadata in Firestore at
 * `users/{uid}/documents/{documentId}`.
 *
 * All fields default so Firestore's automatic (de)serialization via `toObjects(...)`
 * works. [id] is annotated with [DocumentId] so it is populated from the Firestore
 * document id on read and excluded from the written payload.
 */
data class DocumentModel(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val fileUrl: String = "",
    val storagePath: String = "",
    val fileType: String = "",
    val fileSize: Long = 0,
    val uploadedAt: Timestamp? = null
)
