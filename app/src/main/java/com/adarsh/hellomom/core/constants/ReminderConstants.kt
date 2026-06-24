package com.adarsh.hellomom.core.constants

object ReminderConstants {
    val DAILY_AUTO_REMINDERS = listOf(
        PredefinedReminder("Morning Medicine", "Good morning! It's time to take your morning medicine."),
        PredefinedReminder("Coconut Water", "Time for some refreshing coconut water. Stay hydrated!"),
        PredefinedReminder("Lunch Meal", "It's time for your healthy lunch. Eat well!"),
        PredefinedReminder("Evening Meal", "Time for a light evening snack or meal."),
        PredefinedReminder("Dinner", "Dinner time! Have a nutritious meal before sleep."),
        PredefinedReminder("Night Medicine", "Don't forget to take your medicine before going to bed. Good night!"),
        PredefinedReminder("Sleep Reminder", "Good night! Time to rest well for you and your baby.")
    )

    /**
     * Fire time (hour of day, 24h, on the hour) for each entry in [DAILY_AUTO_REMINDERS], matched
     * by index: 8AM, 9AM, 12PM, 4PM, 7PM, 8PM, 10PM. Single source of truth for daily generation.
     * MUST stay the same length as [DAILY_AUTO_REMINDERS] (index-aligned).
     */
    val DAILY_REMINDER_HOURS = listOf(8, 9, 12, 16, 19, 20, 22)

    /** Daily auto-reminders (and any other reminder) are retained for this many days, then purged. */
    const val RETENTION_DAYS = 7

    val PREDEFINED_REMINDERS = listOf(
        PredefinedReminder("Awake Time", "Good morning! It's time to wake up and start your day."),
        PredefinedReminder("Drink Water", "Hello! Remember to stay hydrated. Time for a glass of water."),
        PredefinedReminder("Take Prenatal Vitamins", "Don't forget your prenatal vitamins for you and your baby."),
        PredefinedReminder("Morning Walk", "Time for a refreshing morning walk. It's good for your health."),
        PredefinedReminder("Morning Medicine", "Good morning! It's time to take your morning medicine."),
        PredefinedReminder("Drink Coconut Water", "Time for some coconut water. It's full of electrolytes."),
        PredefinedReminder("Healthy Meal Time", "It's time for a healthy and nutritious meal."),
        PredefinedReminder("Baby Kick Count Reminder", "Time to count your baby's kicks. Make sure your little one is active."),
        PredefinedReminder("Doctor Appointment", "Reminder: You have a doctor appointment today."),
        PredefinedReminder("Meditation / Relaxation Time", "Take some time to relax and meditate. Stay peaceful."),
        PredefinedReminder("Sleep Reminder", "Good night! It's time to get some rest for tomorrow.")
    )
}

data class PredefinedReminder(
    val title: String,
    val voiceMessage: String
)
