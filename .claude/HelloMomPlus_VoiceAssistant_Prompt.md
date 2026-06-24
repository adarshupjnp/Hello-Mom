# Hello Mom+ — Offline Voice Assistant (Full Integration Prompt)

Act as a Senior Android Architect, Kotlin Engineer, MVI Architect, NLP Engineer, and Performance Engineer.

You are adding a feature to an **existing, mature** Android app called **Hello Mom+**. Read every constraint below before generating any code. Generate production-quality Kotlin that drops into the existing codebase without modification to existing files (except the one explicit reuse in §3).

---

## 0. HARD RULES (never violate)

- **DO NOT** rewrite, refactor, or restructure the existing architecture.
- **DO NOT** introduce new architecture patterns or libraries beyond what's already in the project.
- **DO NOT** break, edit, or delete any existing module.
- **DO NOT** use Gemini, OpenAI, Claude, any paid API, any cloud NLP, any backend/server processing, TensorFlow Lite, or ONNX. **Everything runs locally and offline. 100% free. Zero network calls.**
- **DO NOT** create a second speech/TTS system — `VoiceAssistant.kt` already exists (see §3).
- **DO NOT** let voice write directly to Room or Firestore. Voice **navigates and prefills only**; the existing screen's Save button stays the single write point.

---

## 1. EXISTING ARCHITECTURE (build strictly within this)

- **Pattern:** MVI + Clean-lite layering + Hilt DI + Room + Firestore (offline-first).
- **Package root:** `com.adarsh.hellomom`
- Each feature has a `Contract.kt` (Intent / State / Effect). ViewModels extend `BaseViewModel<Intent, State, Effect>` (in `core/`).
- Repositories are `@Singleton`, injected via Hilt (`di/RepositoryModule.kt`).
- UI: Jetpack Compose + Material 3, **single-Activity Navigation-Compose**, sealed `Screen` routes in `navigation/Screen.kt`.
- StateFlow + Coroutines throughout. minSdk 26.

Match the exact style of an existing feature's Contract / ViewModel / Screen triad. Study one before writing.

---

## 2. GOAL & ASSISTANT FEEL

A **completely free, fully offline** in-app **voice assistant** for Hello Mom+.

It is **NOT** a chatbot, medical advisor, or general conversational AI. It performs only:

1. Navigation
2. Feature discovery
3. Feature actions (open / search; create via prefill handoff)
4. Form prefill
5. Record-creation handoff (navigate + prefill → user taps Save)

**It must FEEL like an intelligent assistant, achieved entirely offline by:**
- **Confirming understanding aloud** — after recognizing a command, speak back what it understood before/while acting (e.g. "Opening your reports" / "Booking an appointment — for which date?").
- **Multi-turn slot-filling** (see §9) — when required info is missing, ask a follow-up, remember prior turns, and continue. This is the #1 "feel" feature and is fully free (just ViewModel state).
- **Graceful fallback** (see §12) — on a miss, never dead-end. Speak a short clarification and offer 2–4 quick-tap chips of likely intents. Patience reads as intelligence.

---

## 3. EXISTING VOICE COMPONENT — REUSE FOR OUTPUT, DON'T DUPLICATE

`core/utils/VoiceAssistant.kt` already handles **all speech OUTPUT**: native `TextToSpeech.speak()`, "baby voice" narration, reminder voice. It also contains a `listen()` / `SpeechRecognizer` used elsewhere.

**Integration boundary (obey strictly):**
- **All spoken output goes through the existing `VoiceAssistant.speak()`** — confirmations, follow-up questions, clarifications, "not supported" messages. Add **no** new TTS.
- **Input is new.** The new `SpeechRecognizerManager` exclusively owns *command* recognition.
- **Only one `SpeechRecognizer` may be active at a time** (Android limitation). The existing `VoiceAssistant.listen()` must stay dormant while command recognition runs. **Do not delete or rewrite `listen()`** — just guarantee the two never overlap (start/stop discipline).
- Reuse the existing locale resolution from the `selected_language` SharedPreference; don't invent a new one.

---

## 4. LANGUAGE SUPPORT

- The **command pipeline** understands: **English, Hindi, Hinglish**.
- The app's `selected_language` also offers **Gujarati and Marathi**, which the pipeline does **not** parse. **Degrade gracefully:** if `selected_language` is Gujarati or Marathi, speak a polite "voice commands aren't supported in this language yet" via `VoiceAssistant.speak()` and stop. Never crash; never attempt to parse Gu/Mr.

---

## 5. PIPELINE TO BUILD

```
SpeechRecognizerManager   (new; command input only)
        ↓
TextNormalizer
        ↓
IntentDetector            (dictionary + synonym + Levenshtein fuzzy)
        ↓
EntityExtractor
        ↓
ActionDetector
        ↓
VoiceCommandResult
        ↓
VoiceAssistantViewModel   (MVI; RBAC gate + slot-filling + spoken feedback)
        ↓
Navigation / existing feature ViewModel (prefill only)
        ↓
Existing Screen           (user taps existing Save)
```

---

## 6. ROLE / RBAC GATE (critical — non-negotiable)

The app uses `core/RoleManager.kt`. **Only the Owner can write**; Family Members are strictly read-only.

- Before any **CREATE / UPDATE / DELETE**, `VoiceAssistantViewModel` MUST check `RoleManager.resolveAccess().isOwner`.
- If **not** owner: return `VoiceCommandResult(action = NOT_AUTHORIZED, ...)`, speak a short explanation via `VoiceAssistant.speak()`, and **do not navigate to any create/edit screen**.
- Family members may use **OPEN** and **SEARCH** actions only.

---

## 7. SUPPORTED FEATURES — REAL ROUTES ONLY

Build intents only for features with a real destination in `navigation/Screen.kt`. Use the **actual route names** below — do NOT invent names like `AddAppointmentScreen`.

| Intent              | Real route(s) (`Screen.kt`)         | Allowed actions      |
|---------------------|--------------------------------------|----------------------|
| APPOINTMENT         | `appointment`, `add_appointment`     | OPEN, SEARCH, CREATE |
| REPORTS             | `reports`                            | OPEN, SEARCH         |
| MEDICINE_REMINDER   | `medicine`, `add_medicine`           | OPEN, SEARCH, CREATE |
| FOOD / DIET         | `food`                               | OPEN                 |
| SYMPTOM_TRACKER     | `symptom`                            | OPEN, CREATE         |
| DOCTOR_CHAT         | `chat`                               | OPEN                 |
| FAMILY              | `family`, `family_dashboard`         | OPEN                 |
| BILLING / PAYMENT   | `billing`                            | OPEN                 |
| PROFILE             | `profile`                            | OPEN                 |
| SETTINGS            | `settings`                           | OPEN                 |
| REMINDERS           | `reminders`, `notification_history`  | OPEN                 |
| JOURNAL             | `journal`                            | OPEN, CREATE         |
| CONTRACTION_TIMER   | `contraction_timer`                  | OPEN                 |
| BABY_PROGRESS       | `baby_progress`                      | OPEN                 |
| HELP_SUPPORT        | `help_support`                       | OPEN                 |
| DASHBOARD / HOME    | `home`                               | OPEN                 |

> **Verify every route against the actual `Screen.kt` before finalizing.** If a route above doesn't exist, drop that intent — do not invent a screen.
>
> **Do NOT** create intents for features without a screen (LAB_TESTS, INSURANCE, PHARMACY, HEALTH_TIPS, VITALS-as-a-screen, EMERGENCY_CONTACT-as-a-screen, FEEDBACK). If an unmapped intent is detected, return `FEATURE_NOT_AVAILABLE`.

---

## 8. CREATE BEHAVIOR — NAVIGATE + PREFILL ONLY

CREATE actions never write to Room/Firestore directly. They:
1. Pass the owner check (§6).
2. Navigate to the existing add/edit screen (`add_appointment`, `add_medicine`, etc.).
3. **Prefill** extracted entities into that screen's existing ViewModel state (`selectedDate`, `medicineName`, `time`, `frequency`, …).
4. The **user reviews and taps the existing Save button** — that existing path does the write + sync.

For reminders/appointments, hand off to the **existing repositories** (`ReminderRepository.ensureDailyReminders` / the existing appointment creation flow). Reminders use a legacy top-level `reminders/{id}` collection with a `synced: Boolean` flag — do not introduce a different reminder model.

---

## 9. MULTI-TURN SLOT-FILLING (the core "AI feel" feature — 100% offline)

Some intents need slots before handoff:
- APPOINTMENT (CREATE): requires `date`; optional `doctorName`, `time`.
- MEDICINE_REMINDER (CREATE): requires `medicineName` + `time`; optional `frequency`, `date`.

When a required slot is missing, the assistant must **converse to fill it**, holding state in `VoiceAssistantState`:

1. Detect intent + action + whatever entities were present.
2. If a required slot is missing → emit `AwaitingSlot(intent, action, missingSlot, filledSoFar)` into state, **speak the question** via `VoiceAssistant.speak()` (e.g. "Appointment kis date ke liye?"), and **re-arm `SpeechRecognizerManager`** for the next utterance.
3. On the next utterance, the ViewModel knows it's mid-dialogue: parse it **only for the awaited slot** (so "next Friday" is read as a date, not a new command), merge into `filledSoFar`.
4. Repeat until all required slots are filled, then navigate + prefill (§8).
5. Provide a spoken/visible **cancel** ("cancel" / "rehne do") that clears the dialogue state.
6. **Cap follow-ups at ~3** to avoid loops; after that, fall back (§12).

> This is entirely free and offline — it's just a small state machine in the ViewModel. It is the single biggest contributor to "feels like a real assistant." If one-shot-only is ever desired, this can be disabled via a flag, but build it by default.

Example dialogue:
- User: "appointment book karni hai" → speak: "Kis doctor ke saath?"
- User: "Dr. Sharma" → speak: "Kis date ke liye?"
- User: "next Friday" → navigate `add_appointment`, prefill doctorName=Dr. Sharma, date=<resolved>. Speak: "Dr. Sharma ke saath next Friday — details bhar di hain, save kar dijiye."

---

## 10. COMPONENTS TO CREATE

### 10.1 `SpeechRecognizerManager` (new; command input only)
- English / Hindi / Hinglish; locale from `selected_language`.
- Permission handling, robust error handling (no-match, timeout, busy), lifecycle-safe, coroutine-friendly (expose results via Flow or suspend).
- Clean start/stop so it never overlaps `VoiceAssistant.listen()`. Must be re-armable for slot-filling (§9).

### 10.2 `TextNormalizer`
lowercase, trim, punctuation removal, Hindi normalization, Hinglish normalization.
e.g. `"Doctor Se Appointment Book Karni Hai"` → `"doctor se appointment book karni hai"`.

### 10.3 `IntentDetector`
Dictionary-based + synonym-based + fuzzy matching (Levenshtein distance), returning a confidence score.
One dictionary per intent in §7 (English + Hindi + Hinglish). Examples:
- APPOINTMENT: `appointment`, `doctor appointment`, `doctor se milna`, `appointment book karo`, `डॉक्टर की अपॉइंटमेंट`
- REPORTS: `report`, `blood report`, `lab report`, `report dikhao`, `मेरी रिपोर्ट`
- MEDICINE_REMINDER: `medicine reminder`, `dawa reminder`, `दवा रिमाइंडर`

### 10.4 `EntityExtractor`
Extract: Date, Time, Doctor Name, Medicine Name, Frequency, Search Query.
- **Date:** today/tomorrow/next monday/`30 june`/`15 july`; Hindi `aaj`/`kal`/`parso`/`आज`/`कल`/`परसों`. **Derive year dynamically from the current date** — never hardcode 2026.
- **Time:** `8 am`/`8 pm`/`subah 8 baje`/`raat 10 baje`/`sham 6 baje`.
- **Frequency:** DAILY etc. (`roz`, `daily`).
- Provide a **slot-scoped parse mode** for §9 (parse one awaited slot only).

Examples:
- "Meri 30 June ki appointment add karo" → APPOINTMENT/CREATE, date=<year>-06-30
- "Kal 8 baje medicine reminder lagao" → date=TOMORROW, time=08:00
- "Roz subah 9 baje calcium reminder lagao" → medicine=Calcium, time=09:00, frequency=DAILY

### 10.5 `ActionDetector`
Actions: OPEN, CREATE, UPDATE, DELETE, SEARCH.
- "Profile kholo"→OPEN; "30 June appointment add karo"→CREATE; "Reminder delete karo"→DELETE; "Blood report dikhao"→SEARCH.
- CREATE/UPDATE/DELETE gated by §6.

### 10.6 `VoiceCommandResult` (model)
Fields: `intent`, `action`, `confidence`, `date`, `time`, `doctorName`, `medicineName`, `frequency`, `query`, `rawText`.

---

## 11. MVI INTEGRATION

Create, matching the existing pattern exactly:
- `VoiceAssistantContract` → `VoiceAssistantIntent`, `VoiceAssistantState` (incl. idle / listening / `AwaitingSlot` / fallback / navigating), `VoiceAssistantEffect` (navigation, prefill, speak).
- `VoiceAssistantViewModel` extending `BaseViewModel<Intent, State, Effect>`, using StateFlow.

The ViewModel orchestrates the pipeline, applies the RBAC gate (§6), runs slot-filling (§9), emits navigation/prefill Effects, and calls `VoiceAssistant.speak()` for all spoken feedback.

---

## 12. ERROR / FALLBACK / MISSING-FIELD HANDLING (this is where "feel" is won or lost)

- **Intent not found / low confidence / unmapped feature** → don't dead-end. Speak a short clarification ("I didn't catch that — did you mean appointments, reports, or medicines?") and show **2–4 quick-tap chips** of the most likely intents. Return `FEATURE_NOT_AVAILABLE` only after the user declines.
- **Not owner on a write action** → `action = NOT_AUTHORIZED` (§6) + spoken explanation.
- **Missing required slot** → enter slot-filling (§9), not an error.
- **Recognition errors** (no-match/timeout) → speak "Sorry, didn't hear that — try again?" and re-arm once.
- **Gujarati/Marathi selected** → graceful "not supported yet" (§4).

---

## 13. PERFORMANCE (targets, NOT blocking acceptance criteria)

Command processing comfortably under ~50ms (dictionary + Levenshtein is trivially this fast); fast navigation handoff; minimal memory/APK footprint; 100% offline.
**Honest caveat:** ~95% accuracy for Hinglish via native STT is aspirational — native STT handles code-switching weakly, and the router can't fix upstream recognition errors. Optimize for **graceful recovery** (§12), not perfect recognition. Do not treat 95% as a gate.

---

## 14. TESTING

Unit tests for: intent detection, entity extraction, date parsing (incl. dynamic year), time parsing, Hindi, Hinglish, the slot-scoped parse mode, the RBAC gate (owner vs family for CREATE/DELETE), graceful Gu/Mr degradation, FEATURE_NOT_AVAILABLE, and the multi-turn slot-filling state machine (incl. cancel + the 3-follow-up cap).
Integration test: full pipeline → navigation Effect, and a complete multi-turn appointment dialogue.

---

## 15. DEPENDENCY INJECTION (Hilt)

Singleton-providing modules:
- `VoiceModule` — provides `SpeechRecognizerManager`; injects the existing `VoiceAssistant`.
- `IntentModule` — provides `IntentDetector`, `TextNormalizer`.
- `EntityModule` — provides `EntityExtractor`, `ActionDetector`.

---

## 16. DELIVERABLES

1. Folder structure (under `com.adarsh.hellomom`, fitting existing layout)
2. Kotlin models (incl. `VoiceCommandResult`, slot-filling state types)
3. `SpeechRecognizerManager`
4. `TextNormalizer`
5. `IntentDetector` + intent dictionaries (En/Hi/Hinglish)
6. `EntityExtractor` (incl. slot-scoped parse)
7. `ActionDetector`
8. Hilt modules (`VoiceModule`, `IntentModule`, `EntityModule`)
9. `VoiceAssistantViewModel` + `VoiceAssistantContract`
10. Navigation integration (real `Screen.kt` routes)
11. Compose integration (mic entry point, listening UI, fallback chips, slot-filling UI)
12. `VoiceAssistant.speak()` wiring for confirmations/questions/clarifications
13. The RBAC gate
14. The multi-turn slot-filling state machine
15. Unit + integration tests
16. Performance notes

---

## REMINDER

Build a **completely free, fully offline**, Hindi + English + Hinglish voice **navigation, prefill, and slot-filling** assistant that integrates into the existing Hello Mom+ MVI architecture. Reuse `VoiceAssistant.kt` for all output; never run two recognizers at once; enforce the `RoleManager` owner-only write gate; navigate-and-prefill (never auto-save); use real `Screen.kt` routes; converse to fill missing slots; fall back gracefully with quick-tap chips; degrade gracefully for Gujarati/Marathi. No Gemini, OpenAI, cloud AI, TensorFlow Lite, or ONNX. Zero network calls.
