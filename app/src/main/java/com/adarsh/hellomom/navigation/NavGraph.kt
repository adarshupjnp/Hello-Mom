package com.adarsh.hellomom.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.adarsh.hellomom.presentation.dashboard.DashboardScreen
import com.adarsh.hellomom.presentation.auth.LoginScreen
import com.adarsh.hellomom.presentation.auth.RegisterScreen
import com.adarsh.hellomom.presentation.auth.SplashScreen
import com.adarsh.hellomom.presentation.profile.ProfileCreationScreen
import com.adarsh.hellomom.presentation.medicine.MedicineScreen
import com.adarsh.hellomom.presentation.medicine.AddMedicineScreen
import com.adarsh.hellomom.presentation.food.FoodScreen
import com.adarsh.hellomom.presentation.settings.SettingsScreen
import com.adarsh.hellomom.presentation.appointment.AppointmentScreen
import com.adarsh.hellomom.presentation.billing.BillingScreen
import com.adarsh.hellomom.presentation.family.FamilyScreen
import com.adarsh.hellomom.presentation.family.InviteScreen
import com.adarsh.hellomom.presentation.chat.ChatScreen
import com.adarsh.hellomom.presentation.symptoms.SymptomScreen
import com.adarsh.hellomom.presentation.reminder.ReminderListScreen
import com.adarsh.hellomom.presentation.reminder.RemindLaterScreen
import com.adarsh.hellomom.presentation.reminder.AddReminderScreen
import com.adarsh.hellomom.presentation.reminder.NotificationHistoryScreen
import com.adarsh.hellomom.presentation.profile.ProfileScreen
import com.adarsh.hellomom.presentation.dashboard.ContractionTimerScreen
import com.adarsh.hellomom.presentation.dashboard.JournalScreen
import com.adarsh.hellomom.presentation.settings.AboutScreen
import com.adarsh.hellomom.presentation.settings.HelpSupportScreen
import com.adarsh.hellomom.presentation.settings.PrivacyPolicyScreen
import com.adarsh.hellomom.presentation.documents.DocumentsScreen
import com.adarsh.hellomom.presentation.documents.DocumentDetailsScreen
import com.adarsh.hellomom.presentation.baby.BabyProgressScreen

@Composable
fun NavGraph(navController: NavHostController) {

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {

        composable(Screen.Splash.route) {
            SplashScreen(navController)
        }

        composable(Screen.Login.route) {
            LoginScreen(navController)
        }

        composable(Screen.Register.route) {
            RegisterScreen(navController)
        }

        composable(Screen.ProfileCreation.route) {
            ProfileCreationScreen(navController)
        }

        composable(Screen.Home.route) {
            DashboardScreen(navController)
        }

        composable(Screen.Medicine.route) {
            MedicineScreen(navController)
        }

        composable(Screen.AddMedicine.route) {
            AddMedicineScreen(navController)
        }

        composable(Screen.Food.route) {
            FoodScreen(navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController)
        }

        composable(Screen.Appointment.route) {
            AppointmentScreen(navController)
        }

        // The "Reports" section is now the Document Management module
        // (upload / view / download / delete documents backed by Supabase Storage).
        // Family members can view & download; only the owner can upload/edit/delete.
        composable(Screen.Reports.route) {
            DocumentsScreen(navController)
        }

        composable(
            route = Screen.DocumentDetails.route,
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("fileType") { type = NavType.StringType },
                navArgument("url") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            DocumentDetailsScreen(
                navController = navController,
                name = backStackEntry.arguments?.getString("name").orEmpty(),
                fileType = backStackEntry.arguments?.getString("fileType").orEmpty(),
                url = backStackEntry.arguments?.getString("url").orEmpty()
            )
        }

        composable(Screen.Billing.route) {
            BillingScreen(navController)
        }

        composable(Screen.Family.route) {
            FamilyScreen(navController)
        }

        composable(Screen.Chat.route) {
            ChatScreen(navController)
        }

        composable(Screen.Symptom.route) {
            SymptomScreen(navController)
        }

        composable(Screen.Reminders.route) {
            ReminderListScreen(navController)
        }

        composable("add_reminder") {
            AddReminderScreen(navController)
        }

        composable(Screen.NotificationHistory.route) {
            NotificationHistoryScreen(navController)
        }

        composable(
            route = Screen.RemindLater.route,
            arguments = listOf(navArgument("reminder_id") { type = NavType.IntType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("reminder_id") ?: -1
            RemindLaterScreen(navController, id)
        }

        composable(Screen.FamilyDashboard.route) {
            ReminderListScreen(navController)
        }

        composable(Screen.Profile.route) {
            ProfileScreen(navController)
        }

        composable(Screen.BabyProgress.route) {
            BabyProgressScreen(navController)
        }

        composable(Screen.ContractionTimer.route) {
            ContractionTimerScreen(navController)
        }

        composable(Screen.Journal.route) {
            JournalScreen(navController)
        }

        composable(Screen.About.route) {
            AboutScreen(navController)
        }

        composable(Screen.HelpSupport.route) {
            HelpSupportScreen(navController)
        }

        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(navController)
        }

        // 🚨 FIXED INVITE ROUTE (IMPORTANT PART)
        composable(
            route = "invite/{code}",
            arguments = listOf(
                navArgument("code") {
                    type = NavType.StringType
                    nullable = false
                }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "https://hello-mom-6e500.web.app/invite/{code}"
                }
            )
        ) { backStackEntry ->

            val code = backStackEntry.arguments?.getString("code") ?: ""

            InviteScreen(navController, code)
        }
    }
}