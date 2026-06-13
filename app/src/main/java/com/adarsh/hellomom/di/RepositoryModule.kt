package com.adarsh.hellomom.di

import com.adarsh.hellomom.data.repository.AppointmentRepositoryImpl
import com.adarsh.hellomom.data.repository.AuthRepositoryImpl
import com.adarsh.hellomom.data.repository.FoodRepositoryImpl
import com.adarsh.hellomom.data.repository.MedicineRepositoryImpl
import com.adarsh.hellomom.data.repository.BillingRepositoryImpl
import com.adarsh.hellomom.data.repository.DashboardRepositoryImpl
import com.adarsh.hellomom.data.repository.FamilyRepositoryImpl
import com.adarsh.hellomom.data.repository.InviteRepositoryImpl
import com.adarsh.hellomom.data.repository.ReportRepositoryImpl
import com.adarsh.hellomom.data.repository.UserRepositoryImpl
import com.adarsh.hellomom.data.repository.ReminderRepositoryImpl
import com.adarsh.hellomom.data.repository.SyncRepositoryImpl
import com.adarsh.hellomom.data.repository.ContractionRepositoryImpl
import com.adarsh.hellomom.data.repository.JournalRepositoryImpl
import com.adarsh.hellomom.data.repository.DocumentRepositoryImpl
import com.adarsh.hellomom.domain.repository.AppointmentRepository
import com.adarsh.hellomom.domain.repository.AuthRepository
import com.adarsh.hellomom.domain.repository.BillingRepository
import com.adarsh.hellomom.domain.repository.DashboardRepository
import com.adarsh.hellomom.domain.repository.FamilyRepository
import com.adarsh.hellomom.domain.repository.FoodRepository
import com.adarsh.hellomom.domain.repository.InviteRepository
import com.adarsh.hellomom.domain.repository.MedicineRepository
import com.adarsh.hellomom.domain.repository.ReportRepository
import com.adarsh.hellomom.domain.repository.UserRepository
import com.adarsh.hellomom.domain.repository.ReminderRepository
import com.adarsh.hellomom.domain.repository.SyncRepository
import com.adarsh.hellomom.domain.repository.ContractionRepository
import com.adarsh.hellomom.domain.repository.JournalRepository
import com.adarsh.hellomom.domain.repository.DocumentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindMedicineRepository(
        medicineRepositoryImpl: MedicineRepositoryImpl
    ): MedicineRepository

    @Binds
    @Singleton
    abstract fun bindFoodRepository(
        foodRepositoryImpl: FoodRepositoryImpl
    ): FoodRepository

    @Binds
    @Singleton
    abstract fun bindAppointmentRepository(
        appointmentRepositoryImpl: AppointmentRepositoryImpl
    ): AppointmentRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(
        reportRepositoryImpl: ReportRepositoryImpl
    ): ReportRepository

    @Binds
    @Singleton
    abstract fun bindBillingRepository(
        billingRepositoryImpl: BillingRepositoryImpl
    ): BillingRepository

    @Binds
    @Singleton
    abstract fun bindFamilyRepository(
        familyRepositoryImpl: FamilyRepositoryImpl
    ): FamilyRepository

    @Binds
    @Singleton
    abstract fun bindInviteRepository(
        inviteRepositoryImpl: InviteRepositoryImpl
    ): InviteRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(
        dashboardRepositoryImpl: DashboardRepositoryImpl
    ): DashboardRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(
        reminderRepositoryImpl: ReminderRepositoryImpl
    ): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(
        syncRepositoryImpl: SyncRepositoryImpl
    ): SyncRepository

    @Binds
    @Singleton
    abstract fun bindContractionRepository(
        contractionRepositoryImpl: ContractionRepositoryImpl
    ): ContractionRepository

    @Binds
    @Singleton
    abstract fun bindJournalRepository(
        journalRepositoryImpl: JournalRepositoryImpl
    ): JournalRepository

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        documentRepositoryImpl: DocumentRepositoryImpl
    ): DocumentRepository
}
