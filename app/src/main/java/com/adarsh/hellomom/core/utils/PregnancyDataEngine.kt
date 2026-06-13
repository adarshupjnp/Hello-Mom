package com.adarsh.hellomom.core.utils

import com.adarsh.hellomom.domain.repository.PregnancyWeekData

object PregnancyDataEngine {

    fun getWeekData(week: Int): PregnancyWeekData {
        val boundedWeek = week.coerceIn(1, 40)
        return pregnancyWeeks[boundedWeek - 1]
    }

    private val pregnancyWeeks = listOf(
        PregnancyWeekData(1, "Poppy Seed", "0g", "0.1mm", "Fertilization and implantation occur.", "Life begins! Your baby is a tiny cluster of cells."),
        PregnancyWeekData(2, "Poppy Seed", "0g", "0.2mm", "The neural tube and placenta begin to form.", "Foundation is being built for the nervous system."),
        PregnancyWeekData(3, "Poppy Seed", "0g", "0.3mm", "Heart begins to beat and circulatory system forms.", "First heartbeat! Your baby's heart is now beating."),
        PregnancyWeekData(4, "Sesame Seed", "0g", "2mm", "Major organs like brain, spinal cord, and heart develop.", "Organogenesis is in full swing."),
        PregnancyWeekData(5, "Apple Seed", "0g", "4mm", "Arms and legs begin to bud.", "Your baby is starting to take shape."),
        PregnancyWeekData(6, "Sweet Pea", "1g", "6mm", "Facial features like eyes and nostrils begin to form.", "Baby has a face! Eyes and nose are starting to appear."),
        PregnancyWeekData(7, "Blueberry", "1g", "13mm", "Brain becomes more complex; limbs grow longer.", "Brain development is accelerating."),
        PregnancyWeekData(8, "Raspberry", "2g", "1.6cm", "Fingers and toes begin to form; joints develop.", "Baby is starting to move! Tiny limbs are forming joints."),
        PregnancyWeekData(9, "Grape", "3g", "2.3cm", "Essential organs are all present; tail disappears.", "Your baby is no longer an embryo, now a fetus!"),
        PregnancyWeekData(10, "Kumquat", "4g", "3.1cm", "Vital organs start functioning; baby can swallow.", "Organs are starting to work together."),
        PregnancyWeekData(11, "Fig", "8g", "4.1cm", "Baby starts to make small movements; genitals develop.", "Active movements begin, though you can't feel them yet."),
        PregnancyWeekData(12, "Lime", "14g", "5.4cm", "Reflexes develop; baby can open and close hands.", "End of the first trimester! Reflexes are forming."),
        PregnancyWeekData(13, "Lemon", "25g", "7.4cm", "Fingerprints form; baby can make facial expressions.", "Unique fingerprints are developing."),
        PregnancyWeekData(14, "Nectarine", "45g", "8.7cm", "Baby's neck gets longer; kidneys produce urine.", "Second trimester begins! Neck is lengthening."),
        PregnancyWeekData(15, "Apple", "70g", "10.1cm", "Baby can sense light; skeleton starts to harden.", "Baby can now sense light through closed eyelids."),
        PregnancyWeekData(16, "Avocado", "100g", "11.6cm", "Baby's scalp hair patterns start to develop.", "Hair follicles are starting to grow on the scalp."),
        PregnancyWeekData(17, "Pomegranate", "140g", "13cm", "Fat stores begin to develop under the skin.", "Baby is starting to put on some healthy fat."),
        PregnancyWeekData(18, "Sweet Potato", "190g", "14.2cm", "Baby can hear sounds; movement becomes noticeable.", "You might feel the first kicks now! Hearing is active."),
        PregnancyWeekData(19, "Mango", "240g", "15.3cm", "Vernix caseosa forms to protect baby's skin.", "A protective coating is forming on the baby's skin."),
        PregnancyWeekData(20, "Banana", "300g", "16.4cm", "Baby is halfway there! Taste buds are fully formed.", "Halfway mark! Baby can taste what you eat."),
        PregnancyWeekData(21, "Carrot", "360g", "26.7cm", "Digestive system is maturing; baby is more active.", "Regular sleep and wake cycles are beginning."),
        PregnancyWeekData(22, "Papaya", "430g", "27.8cm", "Lungs are developing; baby starts to look like a newborn.", "Baby's features are becoming more refined."),
        PregnancyWeekData(23, "Grapefruit", "500g", "28.9cm", "Inner ear is fully developed; baby has a sense of balance.", "Baby knows which way is up and down now."),
        PregnancyWeekData(24, "Corn", "600g", "30cm", "Lungs start producing surfactant; baby's skin is less transparent.", "Vital lung development is reaching a milestone."),
        PregnancyWeekData(25, "Rutabaga", "660g", "34.6cm", "Baby's hands are fully developed; can grasp the umbilical cord.", "Grip strength is developing; baby is exploring."),
        PregnancyWeekData(26, "Scallion", "760g", "35.6cm", "Eyes start to open; baby begins to breathe amniotic fluid.", "Baby is practicing breathing and opening eyes."),
        PregnancyWeekData(27, "Cauliflower", "875g", "36.6cm", "Brain waves show response to light and sound.", "End of the second trimester! Brain is very active."),
        PregnancyWeekData(28, "Eggplant", "1kg", "37.6cm", "Baby can blink; eyelashes are growing.", "Third trimester begins! Baby is getting big."),
        PregnancyWeekData(29, "Butternut Squash", "1.2kg", "38.6cm", "Muscles and lungs continue to mature.", "Baby is getting stronger and more coordinated."),
        PregnancyWeekData(30, "Cabbage", "1.3kg", "39.9cm", "Baby's brain is growing rapidly; can track light.", "Rapid brain growth and light tracking abilities."),
        PregnancyWeekData(31, "Coconut", "1.5kg", "41.1cm", "Baby can process information and track light.", "All five senses are now working together."),
        PregnancyWeekData(32, "Jicama", "1.7kg", "42.4cm", "Baby practices breathing and swallowing.", "Lungs are almost fully developed now."),
        PregnancyWeekData(33, "Pineapple", "1.9kg", "43.7cm", "Baby's skull remains soft for birth.", "Baby is preparing for the journey through the birth canal."),
        PregnancyWeekData(34, "Cantaloupe", "2.1kg", "45cm", "Baby's central nervous system and lungs are maturing.", "Nervous system is now fully mature."),
        PregnancyWeekData(35, "Honeydew", "2.4kg", "46.2cm", "Baby is putting on weight rapidly; space is getting tight.", "Baby is gaining about 30 grams a day now."),
        PregnancyWeekData(36, "Romaine Lettuce", "2.6kg", "47.4cm", "Baby is dropping lower into the pelvis.", "Engaging! Baby is getting into position for birth."),
        PregnancyWeekData(37, "Swiss Chard", "2.9kg", "48.6cm", "Baby is considered 'early term'. Organs are ready.", "Ready for the world! Almost full term."),
        PregnancyWeekData(38, "Leek", "3.1kg", "49.8cm", "Baby continues to develop surfactant for lungs.", "Final touches on lung and brain development."),
        PregnancyWeekData(39, "Watermelon", "3.3kg", "50.7cm", "Baby is full term! Brain is still growing.", "Full term! Brain development will continue for years."),
        PregnancyWeekData(40, "Pumpkin", "3.5kg", "51.2cm", "Baby is ready to meet the world! Birth is imminent.", "Birthday time! Your baby is ready to meet you.")
    )

    fun getMotherBodyChanges(week: Int): String {
        return when (week) {
            in 1..4 -> "You might experience implantation bleeding or early pregnancy symptoms like fatigue."
            in 5..8 -> "Morning sickness and frequent urination are common as hormones rise."
            in 9..12 -> "Your uterus is growing; you might notice a slight thickening of your waistline."
            in 13..16 -> "Nausea usually subsides; you might feel more energetic and start to show."
            in 17..20 -> "You'll likely feel the baby's first movements; your 'baby bump' is visible."
            in 21..24 -> "Increased appetite and possible leg cramps; baby's kicks are stronger."
            in 25..28 -> "You might experience backaches or swelling in ankles and feet."
            in 29..32 -> "Shortness of breath as the uterus pushes against the diaphragm."
            in 33..36 -> "Frequent bathroom trips and difficulty finding a comfortable sleeping position."
            else -> "Braxton Hicks contractions may increase as your body prepares for labor."
        }
    }
}
