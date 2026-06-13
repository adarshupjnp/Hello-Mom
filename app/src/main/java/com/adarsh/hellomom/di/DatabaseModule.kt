package com.adarsh.hellomom.di

import android.content.Context
import androidx.room.Room
import com.adarsh.hellomom.data.local.AppDatabase
import com.adarsh.hellomom.data.local.dao.AppointmentDao
import com.adarsh.hellomom.data.local.dao.MealDao
import com.adarsh.hellomom.data.local.dao.MedicineDao
import com.adarsh.hellomom.data.local.dao.BillingDao
import com.adarsh.hellomom.data.local.dao.FamilyMemberDao
import com.adarsh.hellomom.data.local.dao.ReportDao
import com.adarsh.hellomom.data.local.dao.SymptomDao
import com.adarsh.hellomom.data.local.dao.UserDao
import com.adarsh.hellomom.data.local.dao.WaterIntakeDao
import com.adarsh.hellomom.data.local.dao.ReminderDao
import com.adarsh.hellomom.data.local.dao.ContractionDao
import com.adarsh.hellomom.data.local.dao.JournalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hello_mom_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideMedicineDao(db: AppDatabase): MedicineDao = db.medicineDao()

    @Provides
    fun provideMealDao(db: AppDatabase): MealDao = db.mealDao()

    @Provides
    fun provideWaterIntakeDao(db: AppDatabase): WaterIntakeDao = db.waterIntakeDao()

    @Provides
    fun provideAppointmentDao(db: AppDatabase): AppointmentDao = db.appointmentDao()

    @Provides
    fun provideReportDao(db: AppDatabase): ReportDao = db.reportDao()

    @Provides
    fun provideBillingDao(db: AppDatabase): BillingDao = db.billingDao()

    @Provides
    fun provideFamilyMemberDao(db: AppDatabase): FamilyMemberDao = db.familyMemberDao()

    @Provides
    fun provideSymptomDao(db: AppDatabase): SymptomDao = db.symptomDao()

    @Provides
    fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun provideContractionDao(db: AppDatabase): ContractionDao = db.contractionDao()

    @Provides
    fun provideJournalDao(db: AppDatabase): JournalDao = db.journalDao()
}
