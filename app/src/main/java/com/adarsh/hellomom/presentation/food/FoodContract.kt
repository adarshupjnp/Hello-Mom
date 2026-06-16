package com.adarsh.hellomom.presentation.food

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.data.local.entity.MealEntity
import com.adarsh.hellomom.data.local.entity.WaterIntakeEntity

sealed class FoodIntent : UiIntent {
    object LoadData : FoodIntent()
    data class OnMealToggle(val meal: MealEntity) : FoodIntent()
    data class OnAddMeal(val type: String, val items: String, val time: String, val days: String) : FoodIntent()
    data class OnUpdateMeal(val meal: MealEntity) : FoodIntent()
    data class OnDeleteMeal(val meal: MealEntity) : FoodIntent()
    object OnAddGlassWater : FoodIntent()
    object OnRemoveGlassWater : FoodIntent()
    data class OnDateFilterChanged(val date: Long?) : FoodIntent()
}

data class FoodState(
    val meals: List<MealEntity> = emptyList(),
    val filteredMeals: List<MealEntity> = emptyList(),
    val waterIntake: WaterIntakeEntity? = null,
    val selectedDate: Long? = null,
    val pregnancyWeek: Int = 0,
    val userName: String = "",
    val aiRecommendation: String? = null,
    val isLoading: Boolean = false,
    val isOwner: Boolean = false,
    val error: String? = null
) : UiState

sealed class FoodEffect : UiEffect {
    data class ShowError(val message: String) : FoodEffect()
}
