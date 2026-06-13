package com.adarsh.hellomom.domain.repository

import android.net.Uri

interface OcrRepository {
    suspend fun extractTextFromImage(uri: Uri): Result<String>
}
