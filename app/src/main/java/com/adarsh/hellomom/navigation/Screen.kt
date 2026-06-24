package com.adarsh.hellomom.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object ProfileCreation : Screen("profile_creation")
    object Home : Screen("home")
    object Medicine : Screen("medicine")
    object AddMedicine : Screen("add_medicine")
    object Food : Screen("food")
    object Appointment : Screen("appointment")
    object Reports : Screen("reports")
    object Billing : Screen("billing")
    object Family : Screen("family")
    object Settings : Screen("settings")
    object Chat : Screen("chat")
    object Symptom : Screen("symptom")
    object Reminders : Screen("reminders")
    object RemindLater : Screen("remind_later/{reminder_id}")
    object NotificationHistory : Screen("notification_history")
    object FamilyDashboard : Screen("family_dashboard")
    object Profile : Screen("profile")
    object BabyProgress : Screen("baby_progress")
    object About : Screen("about")
    object ContractionTimer : Screen("contraction_timer")
    object Journal : Screen("journal")
    object HelpSupport : Screen("help_support")
    object PrivacyPolicy : Screen("privacy_policy")
    object DocumentDetails : Screen("document_details/{name}/{fileType}/{url}") {
        fun createRoute(name: String, fileType: String, url: String): String {
            return "document_details/" +
                "${android.net.Uri.encode(name)}/" +
                "${android.net.Uri.encode(fileType)}/" +
                android.net.Uri.encode(url)
        }
    }
    object Invite : Screen("invite/{code}")
}
