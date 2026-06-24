# Hello Mom+ — Voice Assistant Guide

> Everything about the offline voice assistant in one place. Read this before changing it — most
> tweaks are a one-line edit in a known spot. **100% offline, free, no Gemini/cloud for commands.**
> Last updated: 2026-06-24.

---

## 1. What it does (at a glance)

A floating mic (bottom-right, animated when active) that, fully on-device:

- **Navigates** to any screen ("profile dikhao", "reports kholo", "baby progress").
- Opens **dashboard tabs** ("health dikhao", "quick").
- **Answers questions aloud** from real data ("baby weight batao", "kaun sa week", "delivery date kya hai", "aaj ka schedule").
- **Creates** records by **navigating + prefilling** (medicine, reminder) or **auto-opening the add dialog** (appointment, bill, meal, journal, symptom, …).
- **Multi-turn slot-filling**: asks for missing required fields before creating ("add medicine" → "Kaun si medicine?" → "Kis time?").
- **Greets on app open** with an active mic, then listens; says a polite goodbye after ~5s of silence.
- **"Did you mean X?"** confirmation on low-confidence input.
- Enforces the **owner-only RBAC write gate** (family = read-only).
- Languages: **English / Hindi / Hinglish** (default Hinglish). Gujarati/Marathi degrade gracefully.

---

## 2. Files

### Pipeline + models — `core/voice/`
| File | Responsibility |
|---|---|
| `VoiceModels.kt` | `VoiceIntentType`, `VoiceActionType`, `VoiceSlot`, `VoiceLanguage`, `VoiceCommandResult` (+ `CONFIDENCE_THRESHOLD`), `VoiceSlotRules` (required/filled slots) |
| `TextNormalizer.kt` | Lower-case, strip punctuation, Hinglish spelling variants; `detectLanguage()` |
| `IntentDetector.kt` | `DICTIONARY` (per-intent phrases, En/Hi/Hinglish) + Levenshtein fuzzy → `detect(): Detection(intent, confidence)` |
| `ActionDetector.kt` | OPEN / SEARCH / CREATE / UPDATE / DELETE (precedence: DELETE > UPDATE > CREATE > SEARCH > OPEN) |
| `EntityExtractor.kt` | date / time / doctor / medicine / frequency / query; slot-scoped `parseDate/parseTime/parseName`; **dynamic year** |
| `SpeechRecognizerManager.kt` | STT. `listenOnce(): Result<String>`; `languageTag()`; ~5s silence window; `VoiceInputException(reason)` |
| `VoicePrefillStore.kt` | Cross-screen hand-off: `putMedicine/consumeMedicine`, `putReminder/consumeReminder`, `requestAutoOpenAdd/consumeAutoOpenAdd` |
| `MicVisibilityController.kt` | `@Singleton` bridge for mic visibility (**default `false`**) — see §7 |

### MVI + UI — `presentation/voice/`
| File | Responsibility |
|---|---|
| `VoiceAssistantContract.kt` | `VoiceAssistantIntent`, `VoiceAssistantState`, `VoiceAssistantEffect`, `VoiceStatus` |
| `VoiceAssistantViewModel.kt` | **The orchestrator** — pipeline, RBAC, slot-filling, info queries, welcome, suggestion confirmation, tab nav |
| `VoicePrompts.kt` | `object P` — every spoken/visible string, 3-way via `pick(lang, en, hi, hinglish = en)` |
| `VoiceAssistantOverlay.kt` | Floating mic composable (animated), mic permission, effect→`navController`, welcome trigger, `selectDashboardTab` |
| `VoicePrefillAccess.kt` | Hilt `EntryPoint` + `rememberVoicePrefillStore()` so screens reach the store without a ViewModel |

### Speech OUTPUT — `core/utils/VoiceAssistant.kt`
Singleton TTS (shared by reminders/baby-voice too). `speak(text, onDone)` (real `UtteranceProgressListener` completion), young-female voice (pitch `1.2`, `selectFemaleVoice`), locale from the language pref (`Hinglish → en-IN`). Also has a legacy `startListening()` (unrelated to commands — keep dormant).

### Integration touch-points (edited, additive)
- `MainActivity.kt` — hosts `VoiceAssistantOverlay(navController)`; `applyLocale()` is **pinned to English** so date/time pickers & dates render in English regardless of voice language.
- `AndroidManifest.xml` — `RECORD_AUDIO` permission.
- `data/local/PreferenceManager.kt` — `selectedLanguage` default **"Hinglish"**.
- Auto-open hooks (`consumeAutoOpenAdd`): `AppointmentScreen`, `BillingScreen`, `FoodScreen`, `JournalScreen`, `SymptomScreen`, `ContractionTimerScreen`, `DocumentsScreen`.
- Prefill hooks: `AddMedicineScreen` (`consumeMedicine`), `AddReminderScreen` (`consumeReminder`).
- `DashboardScreen` / `BabyProgressScreen` — `SetMicVisibility` (hide mic while loading or AI chat open), AI FAB, and the welcome is triggered from the Overlay (not here).

---

## 3. Request flow (what happens per utterance)

```
Mic tap / welcome  →  SpeechRecognizerManager.listenOnce()   (STT, ~5s window)
        │ transcript
        ▼
VoiceAssistantViewModel.onTranscript(text)
        │ TextNormalizer.normalize()
        ├─ cancel phrase?        → cancelByUser()
        ├─ awaitingConfirmation? → handleConfirmation()   (yes → open guess, else decline)
        ├─ in slot dialogue?     → continueDialogue()      (fill the awaited slot)
        └─ else newCommand():
              ├─ detectStatusQuery()  → handleStatusIntent()  (SPEAK an answer: weight/size/week/due/schedule)
              ├─ IntentDetector.detect() + ActionDetector.detect() + EntityExtractor.extractAll()
              ├─ low confidence?  → suggestFeature()  ("did you mean X?")  or fallback()
              └─ route(intent, action):
                    ├─ HEALTH/QUICK_ACTIONS → Effect.NavigateToTab
                    ├─ CREATE  → RBAC owner check → slot-fill if needed → completeCreate() (prefill / auto-open)
                    └─ OPEN/SEARCH → Effect.Navigate(route)
```

All spoken output goes through `VoiceAssistant.speak()`. Navigation is emitted as an **Effect** consumed by the Overlay (which holds the `NavController`). The VM never navigates or writes to Room/Firestore directly.

---

## 4. How to extend (the common edits)

### 4.1 Add a new screen the user can open
1. `VoiceModels.kt` → add a value to `VoiceIntentType`.
2. `IntentDetector.kt` → add a `DICTIONARY` entry (phrases in En/Hi/Hinglish).
3. `VoiceAssistantViewModel.listRoute()` → map it to the **real route** from `navigation/Screen.kt`.
4. `VoicePrompts.featureName()` → add its spoken name (Hinglish defaults to the English label).
> `listRoute` and `featureName` are exhaustive `when`s — the compiler will force you to handle the new value.

### 4.2 Add a spoken info query (answer aloud)
1. `VoiceIntentType` → add the value; add it to `STATUS_INTENTS` in the VM.
2. `IntentDetector` dictionary + (optionally) `detectStatusQuery()` keywords so an asking phrase routes to it.
3. `VoiceAssistantViewModel.handleStatusIntent()` → add a branch that reads the data and returns a `P.*` string.
4. `VoicePrompts` → add the 3-way spoken string.
Data sources already wired into the VM: `PregnancyProgress`, `PregnancyDataEngine`, `RoleManager.resolveAccess()` (owner profile/start/due date), `medicine/food/reminder/scheduleRepository`.

### 4.3 Add a CREATE flow
- **If the feature has a real add-route** (like `add_medicine`, `add_reminder`): add to `CREATE_CAPABLE`, define required slots in `VoiceSlotRules.requiredSlots()`, handle in `completeCreate()` by putting a prefill in `VoicePrefillStore` + navigating; the destination screen consumes it.
- **If creation is a dialog inside a list screen**: add to `CREATE_CAPABLE`, in `completeCreate()` call `prefillStore.requestAutoOpenAdd(intent)` + navigate to the list route, and add a one-liner in that screen:
  ```kotlin
  val voicePrefill = rememberVoicePrefillStore()
  LaunchedEffect(Unit) { if (voicePrefill.consumeAutoOpenAdd(VoiceIntentType.X)) showAddDialog = true }
  ```

### 4.4 Change wording / add a language variant
All copy lives in `VoicePrompts.P`. Each function is `pick(lang, en, hi, hinglish)`. Edit the relevant string. `hinglish` defaults to `en` when omitted (used for feature names that are English loanwords).

### 4.5 Tunables (constants)
| What | Where |
|---|---|
| Min confidence to act | `VoiceCommandResult.CONFIDENCE_THRESHOLD` (0.45) |
| Min confidence to *suggest* "did you mean…" | `VoiceAssistantViewModel.SUGGEST_FLOOR` (0.2) |
| Slot-fill follow-up cap | `MAX_FOLLOW_UPS` (3) |
| Yes/cancel words | `YES_WORDS`, `CANCELS` (VM companion) |
| Fallback chip set | `TOP` (VM companion) |
| Recognizer silence window | `SpeechRecognizerManager` `EXTRA_SPEECH_INPUT_*` (~5000ms) |
| TTS pitch / rate | `VoiceAssistant.VOICE_PITCH` (1.2) / `VOICE_SPEECH_RATE` (1.0) |
| Daily-reminder hours / set | `core/constants/ReminderConstants` |

---

## 5. Languages

| Selected | Prompt text (`P.pick`) | TTS voice (`VoiceAssistant`) | STT (`SpeechRecognizerManager`) |
|---|---|---|---|
| English | English | `Locale.ENGLISH` | `en-IN` |
| Hindi | Devanagari | `Locale("hi")` | `hi-IN` |
| **Hinglish** (default) | Romanized | `Locale("en","IN")` | `en-IN` |
| Gujarati / Marathi | (blocked) | n/a | n/a |

- All three layers (transcript script, prompt script, voice) **must agree** per language — that's why Hinglish prompts are romanized (an English voice can't read Devanagari).
- `VoiceAssistantViewModel.lang()` returns `PreferenceManager.selectedLanguage`; `unsupportedLanguage()` blocks Gujarati/Marathi (speaks a bilingual "not supported yet").
- The language is chosen on the **Profile** screen (the Login selector was removed). Default lives in `PreferenceManager` **and** `VoiceAssistant` (keep them in sync if you change it).

---

## 6. Key behaviors (and where they live)

- **Welcome on app open** — Overlay sends `Welcome` once the mic is first visible (after the dashboard finishes loading). `VoiceAssistantViewModel.playWelcomeMessage()` (guarded by `welcomePlayed`): status `SPEAKING` (mic animates) → `voice.speak(msg){ beginListening() }` → if no reply, `onRecognitionError` (with `welcomeSession`) speaks `P.goodbye`.
- **Suggestion confirmation** — `suggestFeature()` sets `awaitingConfirmation`; next utterance hits `handleConfirmation()` (`isYes` → open the guess, else `P.suggestionDeclined`).
- **Slot-filling** — `startCreate()` → `askSlot()` → `continueDialogue()`/`mergeSlot()`; cap `MAX_FOLLOW_UPS`.
- **Tabs** — `tabIndexFor()` maps `HEALTH`/`QUICK_ACTIONS` to `AppTab` ordinals; `Effect.NavigateToTab` → Overlay `selectDashboardTab()` (stamps `NAV_SELECTED_TAB_KEY` on the Home back-stack entry).
- **Mic shows active** — Overlay `MicButton` animates when `state.status != IDLE && != FALLBACK`.

---

## 7. Gotchas (read before debugging)

1. **Two ViewModel instances.** The Overlay's `VoiceAssistantViewModel` is **Activity-scoped**; a screen's `hiltViewModel()` is **back-stack-scoped** — a *different* instance. Anything a screen needs to tell the Overlay's mic must go through a `@Singleton` bridge. That's why **mic visibility** uses `MicVisibilityController` and the **welcome** is triggered from the Overlay (not the Dashboard).
2. **`MicVisibilityController` defaults to `false`** so the mic (and the welcome) can't appear over the dashboard's loading shimmer. A screen must call `SetMicVisibility(true)` to show it (Dashboard/Baby do, once loaded).
3. **Only one `SpeechRecognizer` at a time** (Android). `SpeechRecognizerManager` owns command input; `VoiceAssistant.startListening()` (legacy) must stay dormant. The VM calls `voice.stop()` before listening so the mic never captures its own TTS.
4. **FAB placement** — the mic sits bottom-end **stacked above** each screen's own FAB; screen FABs (Add / "AI") are bottom-end too. Don't move it into the same slot.
5. **Pickers/dates are English-pinned** in `MainActivity.applyLocale()`, independent of the voice language. Don't re-introduce locale switching there to "fix" voice — voice locale is handled separately.
6. **`is`-prefixed booleans on Firestore entities** must use `@get:/@set:PropertyName` (see `PROJECT_GUIDE.md §4`) — relevant if a voice info query reads such a flag.

---

## 8. Known limitations / future work

- **Devanagari numerals & Hindi month names** aren't parsed by `EntityExtractor` (Latin-digit regex + English/romanized months). Pure-Devanagari spoken times/dates in a CREATE flow trigger a graceful slot re-ask. Hindi STT usually returns Latin digits, so this rarely bites. *To harden:* add Devanagari digit mapping + Hindi month names in `EntityExtractor`.
- **Suggestion decline** treats any non-"yes" as declined (doesn't re-parse "no, I meant medicine" as a new command). Change the `else` branch in `handleConfirmation()` to call `newCommand()` if you want that.
- **Welcome is once per app launch** (per Activity-scoped VM). Change `welcomePlayed` handling for per-login behavior.
- **Recognizer silence extras are hints** — Google's recognizer may not honor the exact 5s.
- **Female voice is best-effort** — depends on installed TTS voices for the locale.
- **Voice UPDATE/DELETE** intentionally navigate to the screen (on-screen confirm) rather than mutating by voice.

---

## 9. Build & test

```bash
# (Windows) JAVA_HOME must point at the Android Studio JBR
./gradlew.bat :app:compileDebugKotlin                                   # compile (runs Hilt/KSP)
./gradlew.bat :app:testDebugUnitTest --tests "com.adarsh.hellomom.voice.VoicePipelineTest"
```

`app/src/test/java/com/adarsh/hellomom/voice/VoicePipelineTest.kt` covers the **pure** pipeline
(intent detection En/Hi/Hinglish, action detection, time/date parsing incl. dynamic year, slot
rules). The VM flows (RBAC, slot-filling, welcome, confirmation) aren't unit-tested yet — verify on
a device. Per `PROJECT_GUIDE.md`, do **not** launch the emulator from automation.

---

*Companion docs: `PROJECT_GUIDE.md` (how the app is built) · `PROJECT_FULL_ARCHITECTURE_AND_UNDERSTANDING.md` (audit/architecture).*
