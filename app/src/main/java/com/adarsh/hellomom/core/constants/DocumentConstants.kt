package com.adarsh.hellomom.core.constants

/**
 * Static configuration for the Document Management module: allowed file types,
 * size limit, categories, and small helpers for type detection and mime mapping.
 */
object DocumentConstants {

    /** Selectable document categories. */
    val CATEGORIES = listOf(
        "Medical Report",
        "Prescription",
        "Ultrasound",
        "Insurance",
        "Appointment",
        "Personal",
        "Other"
    )

    /** Allowed lowercase file extensions (without the leading dot). */
    val ALLOWED_EXTENSIONS = setOf("pdf", "jpg", "jpeg", "png", "webp", "doc", "docx")

    /** Maximum upload size: 20 MB. */
    const val MAX_FILE_SIZE_BYTES: Long = 20L * 1024L * 1024L

    /** Mime types passed to the system file picker. */
    val PICKER_MIME_TYPES = arrayOf(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/webp",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )

    /** Coarse classification used to choose icons, previews and open behaviour. */
    enum class DocType { IMAGE, PDF, DOC, OTHER }

    fun typeOf(extension: String?): DocType = when (extension?.lowercase()) {
        "jpg", "jpeg", "png", "webp" -> DocType.IMAGE
        "pdf" -> DocType.PDF
        "doc", "docx" -> DocType.DOC
        else -> DocType.OTHER
    }

    fun mimeFor(extension: String?): String = when (extension?.lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "*/*"
    }

    /** Human-readable size string, e.g. "1.4 MB". */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unit = 0
        while (size >= 1024 && unit < units.lastIndex) {
            size /= 1024
            unit++
        }
        return if (unit == 0) "${bytes} B" else String.format("%.1f %s", size, units[unit])
    }

    /** Strip path separators / unsafe chars from a file name for use in a storage key. */
    fun sanitizeFileName(name: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\').trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.ifBlank { "file" }
    }
}
