package com.adarsh.hellomom.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.adarsh.hellomom.data.local.dao.*
import com.adarsh.hellomom.data.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        MedicineEntity::class,
        MealEntity::class,
        WaterIntakeEntity::class,
        AppointmentEntity::class,
        ReportEntity::class,
        BillingEntity::class,
        FamilyMemberEntity::class,
        SymptomLogEntity::class,
        ReminderEntity::class,
        ContractionEntity::class,
        JournalEntity::class,
        DailyScheduleStatusEntity::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun medicineDao(): MedicineDao
    abstract fun mealDao(): MealDao
    abstract fun waterIntakeDao(): WaterIntakeDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun reportDao(): ReportDao
    abstract fun billingDao(): BillingDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun symptomDao(): SymptomDao
    abstract fun reminderDao(): ReminderDao
    abstract fun contractionDao(): ContractionDao
    abstract fun journalDao(): JournalDao
    abstract fun dailyScheduleStatusDao(): DailyScheduleStatusDao
}
