package com.adarsh.hellomom.domain.usecase

import javax.inject.Inject

class GenerateBabyMessageUseCase @Inject constructor() {

    fun execute(week: Int, weight: String): String {
        val fruit = getFruitMapping(week)
        val templates = listOf(
            "Namaste mumma! ❤️ Main abhi $week weeks ka ho gaya hoon. Main ek $fruit jitna bada hoon. Mera vajan lagbhag $weight hai. Main aapko feel kar sakta hoon mumma! 💕",
            "Hey mumma! Main $week weeks ka hoon aur mera weight $weight hai. Main $fruit ke size ka ho gaya hoon. Main dheere dheere grow ho raha hoon, aap apna dhyan rakho! ❤️",
            "Mumma, main abhi $week weeks ka hoon. Kya aapko pata hai main abhi ek $fruit jitna hoon? Mujhe aapki care chahiye mumma taaki main achhe se grow kar paun! 💕"
        )
        return templates.random()
    }

    private fun getFruitMapping(week: Int): String {
        return when (week) {
            4 -> "Poppy Seed"
            in 5..6 -> "Apple Seed"
            in 7..8 -> "Blueberry"
            in 9..10 -> "Grape"
            in 11..12 -> "Plum"
            in 13..14 -> "Lemon"
            in 15..16 -> "Avocado"
            in 17..18 -> "Orange"
            in 19..20 -> "Mango"
            in 21..22 -> "Pomegranate"
            in 23..24 -> "Coconut"
            in 25..26 -> "Papaya"
            in 27..28 -> "Pineapple"
            in 29..30 -> "Honeydew Melon"
            in 31..32 -> "Large Papaya"
            in 33..34 -> "Small Watermelon"
            in 35..40 -> "Watermelon"
            else -> "Fruit Stage Unavailable"
        }
    }
}
