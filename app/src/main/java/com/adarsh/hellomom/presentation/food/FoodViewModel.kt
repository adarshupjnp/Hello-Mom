package com.adarsh.hellomom.presentation.food

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.entity.WaterIntakeEntity
import com.adarsh.hellomom.domain.repository.AiRepository
import com.adarsh.hellomom.domain.repository.FoodRepository
import com.adarsh.hellomom.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class FoodViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val aiRepository: AiRepository,
    private val roleManager: RoleManager,
    private val syncRepository: SyncRepository
) : BaseViewModel<FoodIntent, FoodState, FoodEffect>() {

    private val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun createInitialState(): FoodState = FoodState()

    init {
        handleIntent(FoodIntent.LoadData)
    }

    override fun handleIntent(intent: FoodIntent) {
        when (intent) {
            FoodIntent.LoadData -> loadData()
            is FoodIntent.OnMealToggle -> toggleMeal(intent.meal)
            is FoodIntent.OnAddMeal -> addMeal(intent.type, intent.items, intent.time, intent.days)
            is FoodIntent.OnUpdateMeal -> updateMeal(intent.meal)
            is FoodIntent.OnDeleteMeal -> deleteMeal(intent.meal)
            FoodIntent.OnAddGlassWater -> updateWater(1)
            FoodIntent.OnRemoveGlassWater -> updateWater(-1)
            is FoodIntent.OnDateFilterChanged -> {
                setState { copy(selectedDate = intent.date) }
                loadData()
            }
        }
    }

    private fun loadData() {
        // Throttled background sync so a family member navigating here gets the owner's latest
        // meals & water — the Room flows below re-emit when the pull lands.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            val access = runCatching { roleManager.resolveAccess() }
                .getOrElse { e ->
                    SyncLogger.error("Food resolveAccess failed", e)
                    setState { copy(isLoading = false) }
                    return@launch
                }
            val user = access.user
            if (user != null) {
                // Family members view the owner's meals & water (activeUserId).
                val targetUserId = access.activeUserId
                // The week always comes from the owner's start date (family has none of its own).
                val week = PregnancyProgress.week(access.owner?.pregnancyStartDate ?: user.pregnancyStartDate)

                // Fetch AI Recommendation (null when offline / AI unavailable — UI handles null).
                val aiRec = runCatching { aiRepository.getNutritionRecommendation(week).getOrNull() }.getOrNull()

                val currentFilterDate = uiState.value.selectedDate?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                } ?: todayDate

                combine(
                    foodRepository.getMeals(targetUserId),
                    foodRepository.getWaterIntake(targetUserId, currentFilterDate)
                ) { meals, water ->
                    setState {
                        copy(
                            meals = meals,
                            waterIntake = water,
                            pregnancyWeek = week,
                            userName = user.fullName,
                            aiRecommendation = aiRec,
                            isOwner = access.isOwner,
                            isLoading = false
                        )
                    }
                    applyFilter()
                }.catch { e ->
                    SyncLogger.error("Food flow failed", e)
                    setState { copy(isLoading = false) }
                }.collect()
            } else {
                setState { copy(isLoading = false) }
            }
        }
    }

    private fun applyFilter() {
        val dateStr = uiState.value.selectedDate?.let {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
        } ?: todayDate

        val filtered = uiState.value.meals.filter { 
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.updatedAt)) == dateStr
        }
        
        setState { copy(filteredMeals = filtered) }
    }

    private fun toggleMeal(meal: com.adarsh.hellomom.data.local.entity.MealEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            foodRepository.updateMeal(meal.copy(isTaken = !meal.isTaken))
        }
    }

    private fun updateMeal(meal: com.adarsh.hellomom.data.local.entity.MealEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            // Preserve updatedAt: it is the meal's date bucket, so editing must not move it to today.
            foodRepository.updateMeal(meal.copy(syncStatus = com.adarsh.hellomom.data.local.SyncStatus.PENDING))
        }
    }

    private fun deleteMeal(meal: com.adarsh.hellomom.data.local.entity.MealEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            foodRepository.deleteMeal(meal)
        }
    }

    private fun addMeal(type: String, items: String, time: String, days: String) {
        viewModelScope.launch {
            val access = roleManager.resolveAccess()
            if (!access.isOwner) return@launch
            val meal = com.adarsh.hellomom.data.local.entity.MealEntity(
                mealId = UUID.randomUUID().toString(),
                userId = access.activeUserId,
                mealType = type,
                foodItems = items,
                timing = time,
                daysOfWeek = days,
                isTaken = false
            )
            foodRepository.insertMeal(meal)
        }
    }

    private fun updateWater(delta: Int) {
        viewModelScope.launch {
            val access = roleManager.resolveAccess()
            if (!access.isOwner) return@launch
            val targetUserId = access.activeUserId
            val dateStr = uiState.value.selectedDate?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
            } ?: todayDate

            val currentWater = uiState.value.waterIntake ?: WaterIntakeEntity(dateStr, targetUserId, 0)
            val newCount = (currentWater.glassesDrank + delta).coerceAtLeast(0)
            foodRepository.updateWaterIntake(currentWater.copy(glassesDrank = newCount))
        }
    }
}
