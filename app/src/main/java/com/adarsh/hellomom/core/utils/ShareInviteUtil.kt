package com.adarsh.hellomom.core.utils

import android.content.Context
import android.content.Intent

object ShareInviteUtil {
    fun shareInviteToWhatsApp(context: Context, inviteText: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, inviteText)
            setPackage("com.whatsapp")
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general sharing if WhatsApp is not installed
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, inviteText)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Invitation"))
        }
    }
}
