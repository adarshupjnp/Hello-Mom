package com.adarsh.hellomom.data.local.entity

data class PregnancyTip(
    val week: Int,
    val babySize: String,
    val babyInfo: String,
    val healthTip: String
)

object PregnancyTipsProvider {
    val tips = listOf(
        PregnancyTip(1, "Seed", "Conception occurs.", "Start taking folic acid."),
        PregnancyTip(4, "Poppy seed", "Baby is an embryo.", "Stay hydrated."),
        PregnancyTip(8, "Raspberry", "Baby has fingers and toes.", "Healthy snacks."),
        PregnancyTip(12, "Lime", "Baby can move limbs.", "First trimester ending."),
        PregnancyTip(20, "Banana", "Baby can hear sounds.", "Enjoy the kicks!"),
        PregnancyTip(40, "Watermelon", "Ready for the world!", "Keep hospital bag ready.")
    )
}
