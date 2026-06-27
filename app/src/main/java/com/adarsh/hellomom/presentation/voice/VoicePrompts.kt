package com.adarsh.hellomom.presentation.voice

import com.adarsh.hellomom.core.voice.VoiceIntentType
import com.adarsh.hellomom.core.voice.VoiceSlot

/**
 * All spoken/visible assistant copy in English, Hindi (Devanagari) and Hinglish (romanized).
 *
 * [lang] is the user's selected language ("English" / "Hindi" / "Hinglish"). Gujarati/Marathi are
 * blocked upstream and fall back to Hindi text here. **Hinglish MUST stay romanized** — it is spoken
 * by the Indian-English (en-IN) TTS voice, which can't pronounce Devanagari.
 */
internal object P {

    /** Pick copy for [lang]. Hinglish defaults to the English variant unless one is given. */
    private fun pick(lang: String, en: String, hi: String, hinglish: String = en): String = when (lang) {
        "Hinglish" -> hinglish
        "Hindi", "Gujarati", "Marathi" -> hi
        else -> en
    }

    fun listening(lang: String) = pick(lang, "Listening…", "सुन रही हूँ…", "Sun rahi hoon…")

    fun didntHear(lang: String) = pick(lang,
        "Sorry, I didn't catch that — please say it again.",
        "माफ़ कीजिए, सुनाई नहीं दिया — फिर से बोलिए।",
        "Sorry, sunai nahi diya — dobara boliye.")

    fun tryAgain(lang: String) = pick(lang,
        "Sorry, please try again.",
        "माफ़ कीजिए, फिर से कोशिश कीजिए।",
        "Sorry, dobara try kijiye.")

    fun noRecognizer(lang: String) = pick(lang,
        "Voice recognition isn't available on this device.",
        "इस डिवाइस पर वॉइस पहचान उपलब्ध नहीं है।",
        "Is device par voice recognition available nahi hai.")

    fun micPermission(lang: String) = pick(lang,
        "I need microphone access for voice — please allow it.",
        "वॉइस के लिए माइक की अनुमति चाहिए — कृपया अनुमति दें।",
        "Voice ke liye mic ki permission chahiye — please allow kijiye.")

    /** Spoken when Gujarati/Marathi is selected — bilingual so it's clear either way. */
    fun unsupportedLanguage() =
        "अभी वॉइस कमांड सिर्फ़ हिंदी और अंग्रेज़ी में हैं। Voice commands aren't supported in this language yet."

    fun notAuthorized(lang: String) = pick(lang,
        "Only the owner can make changes. You can view the information.",
        "यह बदलाव सिर्फ़ ओनर कर सकते हैं। आप जानकारी देख सकती हैं।",
        "Yeh change sirf owner kar sakte hain. Aap information dekh sakti hain.")

    fun didntUnderstand(lang: String) = pick(lang,
        "Sorry, I didn't understand. Did you mean appointments, reminders, medicines, or reports?",
        "माफ़ कीजिए, समझ नहीं आया। क्या आप अपॉइंटमेंट, रिमाइंडर, दवाइयाँ या रिपोर्ट चाहती हैं?",
        "Sorry, samajh nahi aaya. Kya aap appointment, reminder, medicine ya report chahti hain?")

    /** Low-confidence guess → offer the closest available feature and ask to confirm (yes/no). */
    fun didYouMean(feature: String, lang: String) = pick(lang,
        "I'm not sure I got that — did you want $feature? Please say yes or no.",
        "मुझे ठीक से समझ नहीं आया — क्या आप $feature देखना चाहती हैं? हाँ या नहीं बोलिए।",
        "Mujhe theek se samajh nahi aaya — kya aap $feature dekhna chahti hain? Haan ya nahi boliye.")

    /** Spoken when the user declines the suggestion — names what IS available, then steps back. */
    fun suggestionDeclined(lang: String) = pick(lang,
        "No problem. You can ask about appointments, reminders, medicines, reports, expenses or your baby's progress — just tap the mic.",
        "कोई बात नहीं। आप अपॉइंटमेंट, रिमाइंडर, दवाइयाँ, रिपोर्ट, खर्चे या बच्चे की ग्रोथ के बारे में पूछ सकती हैं — माइक पर टैप कीजिए।",
        "Koi baat nahi. Aap appointment, reminder, dawai, report, kharche ya baby ki progress ke baare mein pooch sakti hain — mic par tap kijiye.")

    fun cancelled(lang: String) = pick(lang,
        "Okay, cancelled.", "ठीक है, रहने देती हूँ।", "Theek hai, rehne deti hoon.")

    /** Polite close spoken when the user doesn't respond after the welcome greeting. */
    fun goodbye(lang: String) = pick(lang,
        "Thank you for your time. I'm always here to help — just tap the mic whenever you need me.",
        "आपका कीमती समय देने के लिए धन्यवाद। आपकी सहायता के लिए मैं हमेशा तैयार हूँ — जब भी ज़रूरत हो, माइक पर टैप कीजिए।",
        "Aapka keemti samay dene ke liye dhanyavaad. Aapki sahayata ke liye main hamesha taiyaar hoon — jab bhi zaroorat ho, mic par tap kijiye.")

    fun opening(name: String, lang: String) = pick(lang,
        "Opening $name.", "$name खोल रही हूँ।", "$name khol rahi hoon.")

    fun searching(name: String, query: String?, lang: String) =
        if (query.isNullOrBlank()) opening(name, lang)
        else pick(lang,
            "Searching $name for $query.",
            "$name में $query ढूँढ रही हूँ।",
            "$name mein $query dhoondh rahi hoon.")

    fun askSlot(slot: VoiceSlot, lang: String): String = when (slot) {
        VoiceSlot.DATE -> pick(lang, "For which date?", "किस तारीख़ के लिए?", "Kis date ke liye?")
        VoiceSlot.TIME -> pick(lang, "At what time?", "किस समय?", "Kis time?")
        VoiceSlot.MEDICINE_NAME -> pick(lang, "Which medicine?", "कौन सी दवा?", "Kaun si medicine?")
        VoiceSlot.TITLE -> pick(lang, "A reminder for what?", "किस चीज़ का रिमाइंडर?", "Kis cheez ka reminder?")
        VoiceSlot.DOCTOR_NAME -> pick(lang, "With which doctor?", "किस डॉक्टर के साथ?", "Kis doctor ke saath?")
        else -> pick(lang, "A little more detail, please?", "थोड़ी और जानकारी दीजिए?", "Thodi aur detail dijiye?")
    }

    fun medicineCreated(name: String?, time: String?, lang: String): String {
        val n = name ?: pick(lang, "the medicine", "दवा", "medicine")
        val t = time?.let { pick(lang, " at $it", " $it बजे", " $it") } ?: ""
        return pick(lang,
            "Done — $n$t. I've filled the medicine form, just tap Save.",
            "ठीक है — $n$t। दवा का फ़ॉर्म भर दिया है, सेव कर दीजिए।",
            "Theek hai — $n$t. Medicine ka form bhar diya hai, save kar dijiye.")
    }

    fun reminderCreated(title: String?, time: String?, lang: String): String {
        val tt = title ?: pick(lang, "this", "इसका", "iska")
        val t = time?.let { pick(lang, " at $it", " $it बजे", " $it") } ?: ""
        return pick(lang,
            "Done — a reminder for $tt$t. I've filled it in, just tap Save.",
            "ठीक है — $tt रिमाइंडर$t। फ़ॉर्म भर दिया है, सेव कर दीजिए।",
            "Theek hai — $tt ka reminder$t. Form bhar diya hai, save kar dijiye.")
    }

    fun appointmentCreated(date: String?, doctor: String?, lang: String): String {
        val d = date ?: pick(lang, "your date", "चुनी गई तारीख़", "aapki date")
        val doc = doctor?.let { pick(lang, " with $it", " $it के साथ", " $it ke saath") } ?: ""
        return pick(lang,
            "Appointment for $d$doc — I've opened the form, add the doctor and time, then Save.",
            "अपॉइंटमेंट $d के लिए$doc — फ़ॉर्म खोल दिया है, डॉक्टर और समय भरकर सेव करें।",
            "Appointment $d ke liye$doc — form khol diya hai, doctor aur time bharke save kijiye.")
    }

    fun genericAddOpened(name: String, lang: String) = pick(lang,
        "I've opened the form to add to $name — fill it in and Save.",
        "$name जोड़ने के लिए फ़ॉर्म खोल दिया है — जानकारी भरकर सेव करें।",
        "$name add karne ke liye form khol diya hai — detail bharke save kijiye.")

    fun babyWeight(weight: String, lang: String) = pick(lang,
        "This week, the baby weighs about $weight.",
        "इस हफ़्ते बच्चे का वजन लगभग $weight है।",
        "Is week baby ka weight lagbhag $weight hai.")

    fun babyLength(length: String, lang: String) = pick(lang,
        "This week, the baby's length is about $length.",
        "इस हफ़्ते बच्चे की लंबाई लगभग $length है।",
        "Is week baby ki lambai lagbhag $length hai.")

    fun babyFullProgress(week: Int, day: Int, size: String, weight: String, length: String, milestone: String, lang: String) = pick(lang,
        "You're in week $week, day $day. Your baby is about the size of a $size, weighing $weight and measuring $length. The main milestone this week is: $milestone.",
        "आप $week वें हफ़्ते के $day वें दिन पर हैं। आपका बच्चा एक $size के बराबर है, जिसका वजन $weight और लंबाई $length है। इस हफ्ते का मुख्य बदलाव है: $milestone।",
        "Aap week $week, day $day par hain. Baby abhi ek $size jitna hai, weight $weight aur length $length hai. Is week ka milestone hai: $milestone.")

    fun babySize(size: String, lang: String) = pick(lang,
        "Your baby is currently about the size of a $size.",
        "आपका बच्चा अभी एक $size के बराबर है।",
        "Aapka baby abhi ek $size jitna hai.")

    fun pregnancyWeek(week: Int, day: Int, lang: String) = pick(lang,
        "You are currently in week $week, day $day.",
        "अभी आप $week वें हफ़्ते के $day वें दिन पर हैं।",
        "Abhi aap week $week, day $day par hain.")

    fun kickLogged(count: Int, lang: String) = pick(lang,
        "Done! I've recorded a kick. That makes $count movements today.",
        "ठीक है! बच्चे की एक किक रिकॉर्ड कर ली है। आज कुल $count हलचल हुई हैं।",
        "Done! Ek kick record kar li hai. Aaj total $count kicks ho gayi hain.")

    fun waterLogged(glasses: Int, lang: String) = pick(lang,
        "Great! I've added a glass of water. Total today: $glasses glasses.",
        "बहुत अच्छे! एक गिलास पानी जोड़ दिया है। आज कुल: $glasses गिलास।",
        "Great! Ek glass pani add kar diya hai. Total aaj: $glasses glasses.")

    fun emergencyDialing(lang: String) = pick(lang,
        "Calling emergency services (102) now.",
        "अभी इमरजेंसी सेवा (102) को कॉल लगा रही हूँ।",
        "Emergency services (102) ko call laga rahi hoon.")

    fun deliveryDate(date: String, daysToGo: Int?, lang: String): String {
        val days = daysToGo?.let { pick(lang, " ($it days to go)", " ($it दिन बाकी हैं)", " ($it din baaki)") } ?: ""
        return pick(lang,
            "Your expected delivery date is $date$days.",
            "आपकी डिलीवरी की तारीख $date है$days।",
            "Aapki delivery date $date hai$days.")
    }

    fun nextAppointment(date: String, time: String, doctor: String, lang: String) = pick(lang,
        "Your next appointment is with $doctor on $date at $time.",
        "आपकी अगली अपॉइंटमेंट $doctor के साथ $date को $time बजे है।",
        "Aapki agali appointment $doctor ke saath $date ko $time baje hai.")

    fun noAppointments(lang: String) = pick(lang,
        "Currently, there are no upcoming appointments. Would you like to schedule one?",
        "फिलहाल अभी कोई अपॉइंटमेंट नहीं है। क्या आप अपॉइंटमेंट सेट करना चाहती हैं?",
        "Filhal abhi koi appointment nahi hai. Kya aap appointment set karna chahti hain?")

    fun todaySchedule(items: List<String>, lang: String): String {
        if (items.isEmpty()) return pick(lang,
            "Nothing left for today — you're all caught up!",
            "आज के लिए और कुछ नहीं है — सब पूरा हो गया!",
            "Aaj ke liye aur kuch nahi — sab done!")
        val list = items.joinToString(", ")
        return pick(lang,
            "Your remaining schedule for today is: $list.",
            "आज का बाकी शेड्यूल है: $list।",
            "Aaj ka baaki schedule: $list.")
    }

    fun welcome(name: String, lang: String): String {
        val n = if (name.isNotBlank()) " $name" else ""
        return when (lang) {
            "Hinglish" -> "Namaste$n, welcome back! Main aapki help ke liye taiyaar hoon. Aap mic par click karke baby progress, reminders, bills ya medicine ke baare mein pooch sakte hain."
            "Hindi" -> "नमस्ते$n, आपका स्वागत है। मैं आपकी मदद के लिए तैयार हूँ। आप माइक पर क्लिक करके बच्चे की ग्रोथ, रिमाइंडर, बिल या दवाइयों के बारे में पूछ सकते हैं।"
            else -> "Hello$n, welcome back. I'm happy to help. You can tap the mic to check your baby's progress, reminders, bills, or medicines."
        }
    }

    /** Short greeting spoken when the user taps the mic mid-session (the long welcome plays only on app open). */
    fun quickGreeting(name: String, lang: String): String {
        val n = if (name.isNotBlank()) " $name" else ""
        return pick(lang,
            "Hey$n, how can I help you?",
            "नमस्ते$n, मैं आपकी क्या मदद कर सकती हूँ?",
            "Hey$n, main aapki kya madad kar sakti hoon?")
    }

    /**
     * Short confirmation spoken when the user taps the mic to stop an in-progress reply or listen.
     * Always ends by reminding how to start again — tap the mic.
     */
    fun stopped(lang: String) = pick(lang,
        "Okay, stopping. Tap the mic to use it again.",
        "ठीक है, रुक रही हूँ। दोबारा उपयोग करने के लिए माइक पर टैप करें।",
        "Theek hai, ruk rahi hoon. Dobara use karne ke liye mic par tap kijiye.")

    fun anythingElse(lang: String) = pick(lang,
        "Anything else I can help you with?",
        "क्या मैं आपकी किसी और चीज़ में मदद कर सकती हूँ?",
        "Kya main aapki kisi aur cheez mein madad kar sakti hoon?")

    fun featureName(intent: VoiceIntentType, lang: String): String = when (intent) {
        // Hinglish reuses the English loanword (omitted hinglish arg → defaults to English label).
        VoiceIntentType.HOME -> pick(lang, "Home", "होम")
        VoiceIntentType.APPOINTMENT -> pick(lang, "Appointments", "अपॉइंटमेंट")
        VoiceIntentType.REPORTS -> pick(lang, "Reports", "रिपोर्ट्स")
        VoiceIntentType.MEDICINE -> pick(lang, "Medicines", "दवाइयाँ")
        VoiceIntentType.FOOD -> pick(lang, "Food & Diet", "डाइट")
        VoiceIntentType.SYMPTOM -> pick(lang, "Symptoms", "लक्षण")
        VoiceIntentType.CHAT -> pick(lang, "Chat", "चैट")
        VoiceIntentType.FAMILY -> pick(lang, "Family", "परिवार")
        VoiceIntentType.BILLING -> pick(lang, "Expenses", "खर्चे")
        VoiceIntentType.PROFILE -> pick(lang, "Profile", "प्रोफ़ाइल")
        VoiceIntentType.SETTINGS -> pick(lang, "Settings", "सेटिंग्स")
        VoiceIntentType.REMINDERS -> pick(lang, "Reminders", "रिमाइंडर")
        VoiceIntentType.NOTIFICATION_HISTORY -> pick(lang, "Notifications", "नोटिफिकेशन")
        VoiceIntentType.JOURNAL -> pick(lang, "Journal", "जर्नल")
        VoiceIntentType.CONTRACTION_TIMER -> pick(lang, "Contraction timer", "कॉन्ट्रैक्शन टाइमर")
        VoiceIntentType.BABY_PROGRESS -> pick(lang, "Baby progress", "बच्चे की ग्रोथ")
        VoiceIntentType.BABY_WEIGHT -> pick(lang, "Baby weight", "बच्चे का वजन")
        VoiceIntentType.BABY_SIZE -> pick(lang, "Baby size", "बच्चे का साइज")
        VoiceIntentType.BABY_LENGTH -> pick(lang, "Baby length", "बच्चे की लंबाई")
        VoiceIntentType.PREGNANCY_WEEK -> pick(lang, "Pregnancy week", "प्रेगनेंसी हफ्ता")
        VoiceIntentType.DELIVERY_DATE -> pick(lang, "Delivery date", "डिलीवरी डेट")
        VoiceIntentType.TODAY_SCHEDULE -> pick(lang, "Today's schedule", "आज का शेड्यूल")
        VoiceIntentType.HEALTH -> pick(lang, "Health", "हेल्थ")
        VoiceIntentType.QUICK_ACTIONS -> pick(lang, "Quick actions", "क्विक एक्शन")
        VoiceIntentType.HELP_SUPPORT -> pick(lang, "Help & support", "मदद")
        VoiceIntentType.KICK_COUNT -> pick(lang, "Kick counter", "किक काउंटर")
        VoiceIntentType.WATER_INTAKE -> pick(lang, "Water tracker", "पानी")
        VoiceIntentType.EMERGENCY -> pick(lang, "Emergency SOS", "इमरजेंसी")
        VoiceIntentType.MOTIVATION -> pick(lang, "Daily quote", "विचार")
        VoiceIntentType.UNKNOWN -> pick(lang, "Home", "होम")
    }
}
