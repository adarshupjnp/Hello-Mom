# Hello Mom+ — Full Architecture & Technical Understanding

> Consolidated architecture, product, AI, security, scalability and UX analysis for future reference.
> Companion to `PROJECT_GUIDE.md` (which documents *how to build*; this documents *what exists and how good it is*).
> Derived from source — `PROJECT_GUIDE.md`, `RoleManager.kt`, `SyncRepositoryImpl.kt`, `AiRepositoryImpl.kt`,
> `VoiceAssistant.kt`, `firestore.rules`, `build.gradle.kts`, navigation and presentation packages.
> Last updated: 2026-06-24.

---

## 0. Executive summary

A pregnancy-companion Android app with **one Owner** (the pregnant mom, hardcoded to names containing
`adarsh`/`riya`) and **read-only Family Members**. The engineering is genuinely strong for a solo build —
clean MVI + Clean-lite layering, offline-first sync, documented conventions. The real risks are about
**reach and safety**, not craft: it is a single-family app today, the Firestore rules are globally
readable, the Gemini key ships in the APK, and the AI auto-triages symptoms without guardrails.

| Dimension | Score | Notes |
|---|---|---|
| Overall Architecture | **8 / 10** | Clean MVI + Clean-lite + offline-first; well-documented |
| Product Readiness (private family app) | **7.5 / 10** | Shippable to the intended family after light hardening |
| Product Readiness (public healthcare product) | **~4 / 10** | Blocked by rules, key exposure, no compliance, single-tenant |
| Security (public) | **4 / 10** | Global-read rules, embedded key, no encryption/minify |
| Security (private family) | **~7 / 10** | Most findings are "tolerable" for trusted users |
| Scalability | **5 / 10** | Pattern scales; hardcoded owner + rules cap it at one family today |

**Headline recommendation: harden, don't rebuild.** ~2–4 weeks of hardening (see §16).

---

## 1. What the app is

- A pregnancy-care app: the **Owner** records appointments, medicines, meals/water, symptoms, journal,
  bills, contractions, reports, kicks and reminders. **Family Members** link to the owner and see
  everything live, but can never write.
- Package root: `app/src/main/java/com/adarsh/hellomom/`
- Pattern: **MVI** (`BaseViewModel<Intent, State, Effect>`) + **Clean-lite layering** + Hilt DI + Room + Firestore.
- Owner identity is **hardcoded** by name convention (`RoleManager.isOwnerName` → contains `adarsh`/`riya`).
  → **Actual real-world capacity: one owner + a handful of family viewers.** This single fact reframes
  every scalability/cost/security conclusion.

---

## 2. Architecture

### 2.1 Pattern
**Hybrid: MVI (presentation) + Clean-style layering + offline-first repository + Hilt DI.**

- MVI: each feature has a `Contract.kt` defining `Intent` / `State` / `Effect`; ViewModels extend
  `BaseViewModel<Intent, State, Effect>` (in `core/`).
- Clean-*lite*: `domain/repository` interfaces are consumed by ViewModels; `data/repository` holds impls.
  There is **no use-case/interactor layer** — ViewModels call repositories directly (reasonable at this size).
- All repositories are `@Singleton` (`di/RepositoryModule.kt`).

### 2.2 Layer map

| Layer | Path | Notes |
|---|---|---|
| UI (Compose + ViewModels) | `presentation/<feature>/` | Screen / ViewModel / Contract per feature |
| Domain interfaces | `domain/repository/` | ViewModels depend on these, never on impls |
| Repository impls | `data/repository/` | Offline-first: write Room → best-effort Firestore push |
| Local DB | `data/local/` (`dao/`, `entity/`, `AppDatabase`) | The only thing list screens read from |
| Remote | `data/remote/` | Gemini via **Retrofit REST** (not the generativeai SDK — avoids a Ktor crash) |
| Background sync | `data/worker/SyncWorker.kt` | Scheduled in `HelloMomApp.kt` (startup + 15-min periodic) |
| Notifications | `notification/` | `ReminderManager` (AlarmManager), `AppointmentReminderScheduler`, receivers |
| DI | `di/` | Hilt modules; all repos `@Singleton` |

### 2.3 Feature packages (`presentation/`)
`appointment`, `auth`, `baby`, `billing`, `chat`, `components`, `dashboard`, `documents`, `family`,
`food`, `medicine`, `permission`, `profile`, `reminder`, `schedule`, `settings`, `symptoms`, `update`.

---

## 3. Tech stack (from `build.gradle.kts`)

- **minSdk 26** (Android 8.0), **targetSdk / compileSdk 35**, `versionName 1.0`, `versionCode 1`.
- **UI:** Jetpack Compose (BOM), Material 3, Navigation-Compose, Material Icons Extended, Lottie.
- **DI:** Hilt (+ hilt-work, hilt-navigation-compose), KSP.
- **Local:** Room (KSP).
- **Cloud:** Firebase (Auth, Firestore, Storage, Messaging, Analytics) + **Supabase Storage** (+ Ktor/OkHttp)
  for document files + Retrofit/OkHttp for the in-app update system.
- **AI:** Gemini `gemini-1.5-flash` via Retrofit REST (`data/remote/ai/`). Key from `local.properties` →
  `BuildConfig.GEMINI_API_KEY`.
- **OCR/Camera:** ML Kit text recognition (on-device) + CameraX (prescription/report capture).
- **Auth:** Credentials API + Google ID + Play Services Auth.
- **Background:** WorkManager.
- ⚠️ **`isMinifyEnabled = false`** in release → no R8 shrinking/obfuscation (larger APK, readable code/key).

---

## 4. Feature inventory

**Present:** email + Google login, pregnancy week tracker, due-date (derived), fetal growth + baby-size
visuals, weekly guidance content, diet recommendations (Gemini), meal + water + weight + sleep + mood
tracking, medication/supplement reminders, doctor appointments (+PDF export, 24h/1h alerts), symptom log
(+AI risk), kick counter, contraction timer, report upload + OCR, AI assistant (Gemini), voice (TTS/STT +
baby voice), family access (RBAC), billing/expenses, emergency contact field, push + local-alarm
notifications, in-app update.

**Partial:** multi-language (voice locale only, **no UI string localization** — one `strings.xml`),
daily tips (content-driven), accessibility (Compose defaults + some `contentDescription`s, untuned),
Hinglish (works only because Gemini tolerates code-switched text).

**Absent:** vaccination tracker, hospital locator, exercise/yoga module, human chat support,
postpartum care module.

Count: **28 nav routes, 18 feature modules, ~25 user-facing features.**

---

## 5. Navigation (`navigation/Screen.kt`)

Single-Activity + Compose Navigation, sealed `Screen` routes (~28): `splash`, `login`, `register`,
`profile_creation`, `home`, `medicine`, `add_medicine`, `food`, `appointment`, `reports`, `billing`,
`family`, `settings`, `chat`, `symptom`, `reminders`, `remind_later/{id}`, `notification_history`,
`family_dashboard`, `profile`, `baby_progress`, `about`, `contraction_timer`, `journal`, `help_support`,
`privacy_policy`, `document_details/{name}/{fileType}/{url}`, `invite/{code}`.

- Bottom-nav tabs via `AppBottomNavBar`.
- Deep link: `https://hello-mom-6e500.web.app/invite` (family join).
- Auth stack cleared with `popUpTo(0)` so back exits the app when signed in.

**User journey:** Splash → Login/Register (email or Google; Google now also prompts for a mandatory
WhatsApp number) → Owner does ProfileCreation (`pregnancyStartDate`) → Home dashboard. Family links to the
owner (self-healing in `RoleManager`) → read-only dashboard. Daily loop: dashboard week/baby card →
reminders fire → log meals/water/symptoms/kicks → AI chat → upload reports.

---

## 6. Data model & storage map

Room (local, UI source of truth) ↔ Firestore (cloud, shared between devices). See `PROJECT_GUIDE.md §4`
for the full per-entity table. Key points:

- Per-user subcollections under `users/{uid}/...` (appointments, medicines, meals, water_intake, symptoms,
  journal, bills, contractions, reports, family_members, daily_schedule_status).
- **Reminders** live in a **top-level** `reminders/{id}` collection filtered by `userId`, and use a legacy
  `synced: Boolean` flag instead of the `SyncStatus` enum everyone else uses.
- Firestore-only (snapshot listeners): `users/{uid}/health_metrics/current`, `users/{uid}/kicks/{date}`,
  `pregnancy_progress/{week}` (read-only week content).
- **Document files → Supabase Storage** (`documents` bucket, anon RLS); metadata in Firestore, file also
  stays local.
- Pregnancy week is **always derived at display time** from the owner's `pregnancyStartDate`
  (`core/utils/PregnancyProgress.kt`), never stored.
- Firestore gotcha: `is`-prefixed booleans must be pinned with `@get:/@set:PropertyName` (see guide §4).

---

## 7. Sync architecture (`data/repository/SyncRepositoryImpl.kt`)

Singleton, serialized by an internal `Mutex`. (Detailed write/pull/reconcile rules in `PROJECT_GUIDE.md §5`.)

- **Write path (owner only):** Room first (instant UI) → best-effort Firestore → `pushPendingData()`
  re-pushes anything still pending.
- **Pull path:** `syncAll()` resolves role → `pullOwnerData(ownerId)` upserts every collection as SYNCED and
  **reconciles deletions** via `delete...NotIn(userId, keepIds)` (keeps unpushed PENDING rows). Pulled
  reminders/appointments get local alarms scheduled (family gets the same notifications).
- **Triggers:** app start (one-time worker) · every 15 min (periodic worker, network-gated) · every screen
  load (`syncIfStale`, 60s throttle / 5s for family) · pull-to-refresh · Profile "Sync Data".
- **Real-time:** dashboard health/kicks/week listeners + reminder-screen listener + a **family dashboard
  real-time mirror** (`observeOwnerRealtime`) that re-pulls on any owner change (family only).
- **Daily reminder lifecycle** (reworked 2026-06): the owner's daily auto-reminders are generated +
  >7-day purge runs from the owner branch of `syncAll`, the midnight `DayChangeReceiver`, and on screen
  open — all idempotent, mutex-guarded in `ReminderRepository.ensureDailyReminders`. Family is viewer-only.

**Architecture note:** `SyncRepositoryImpl` is a ~570-line "god class" (every collection's push/pull/
reconcile inline). Works well; a per-entity sync strategy would age better at scale.

---

## 8. AI assistant (`data/repository/AiRepositoryImpl.kt`)

- Provider: **Gemini `gemini-1.5-flash`** via Retrofit REST (`data/remote/ai/GeminiApiService.kt`),
  key in `BuildConfig`.
- Functions:
  - `getChatResponse` — **single-turn** (no conversation memory), **no system prompt / disclaimer**;
    error handling is fragile (string-matches "error"/"Unexpected Response").
  - `analyzeSymptomRisk` — LLM returns Low/Medium/High/**Emergency** as JSON → parsed by **regex**.
  - `parsePrescription` — OCR text → medicine name/dosage/timing → regex-parsed.
  - `getNutritionRecommendation` — week-based diet advice.
- ⚠️ Risks: regex JSON parsing breaks if Gemini wraps output in ```json fences; **autonomous medical
  triage** with no disclaimer/audit; online-only.

**Recommended AI approach: Hybrid** — Gemini primary + on-device FAQ/intent fallback (offline) + a
**deterministic red-flag override** (bleeding, severe pain, reduced fetal movement → "contact your
doctor/emergency" regardless of the LLM). Add a system prompt, disclaimer, and structured JSON parsing
(Gson/Moshi, not regex). Stop treating the LLM as an autonomous triage authority.

### AI option matrix

| Approach | Accuracy | Cost | Maint | Scale | Offline | Hi | En | Hinglish | Medical | Speed |
|---|---|---|---|---|---|---|---|---|---|---|
| Rule-based FAQ | Low | Free | High | High | ✅ | manual | manual | ❌ | Narrow | Instant |
| Keyword match | Low | Free | High | High | ✅ | manual | manual | ❌ | Narrow | Instant |
| Intent + fuzzy | Med | Free | High | High | ✅ | ~ | ✅ | ~ | Narrow | Instant |
| TFLite local | Med | APK+ | High | High | ✅ | Weak | OK | Weak | Risky | Fast |
| ONNX local | Med | APK+ | High | High | ✅ | Weak | OK | Weak | Risky | Fast |
| Gemini API | High | Free→$ | Low | High | ❌ | ✅ | ✅ | ✅ | Good* | 1–3s |
| **Hybrid (recommended)** | High | Low | Med | High | ✅* | ✅ | ✅ | ✅ | Best | Instant→3s |

\* with guardrails (system prompt + disclaimer + red-flag override). Offline ✅* = on-device fallback.

---

## 9. Voice assistant (`core/utils/VoiceAssistant.kt`)

- Native Android `TextToSpeech` (speak) + `SpeechRecognizer` (listen), locale from a `selected_language`
  SharedPreference (English / Hindi / Gujarati / Marathi). Emojis stripped before TTS.
- Plus a "baby voice" narration VM and reminder voice via foreground service / FCM push.
- No bundled ML model; free.

**Recommended voice approach: Hybrid** — keep native STT/TTS (free, multilingual) for capture/playback;
add an **intent router + fuzzy matcher** for offline commands; route open questions (and Hinglish
interpretation) to Gemini when online (native STT handles code-switching poorly).

### Voice option matrix

| Approach | Accuracy | Cost | Maint | Scale | Offline | Hi | En | Hinglish | Perf |
|---|---|---|---|---|---|---|---|---|---|
| Keyword | Low | Free | High | High | ✅ | manual | manual | ❌ | Instant |
| Intent + fuzzy | Med | Free | Med | High | ✅ | ~ | ✅ | ~ | Instant |
| TFLite | Med | APK+ | High | High | ✅ | Weak | OK | Weak | Fast |
| ONNX | Med | APK+ | High | High | ✅ | Weak | OK | Weak | Fast |
| Gemini | High | Free→$ | Low | High | ❌ | ✅ | ✅ | ✅ | 1–3s |
| **Hybrid: native STT/TTS + router + Gemini (recommended)** | High | Low | Med | High | ~ | ✅ | ✅ | ✅ | Instant→3s |

---

## 10. Notifications & reminders (`notification/`)

- All local notifications: `ReminderManager` (exact AlarmManager alarms) → `ReminderReceiver` →
  `ReminderService` foreground notification + optional voice.
- Reminders scheduled on create/update; family devices schedule the same alarms on pull (idempotent by id).
  Max 5 simultaneous; 1-hour auto-snooze then expiry.
- Appointments get **two alarms** (24h + 1h before) on owner and family devices.
- `DayChangeReceiver` (midnight + on launch): expires stale unacted reminders, sweeps old notifications,
  and (owner-only) generates the new day's reminders + purges >7-day-old ones.
- FCM (`MyFirebaseMessagingService`) is for reminder voice pushes only, not data sync.
- Permission gate in `MainActivity` (`presentation/permission/PermissionGate.kt`): system notification
  prompt, then a custom "Allow Alarms & Reminders" dialog → `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.

---

## 11. Roles & access control (`core/RoleManager.kt`)

- Single source of truth for RBAC. Owner = full read/write of own data; everyone else = read-only viewer
  of the linked owner's data.
- Owner detection is **hardcoded** (`isOwnerUser` → name/email contains `adarsh`/`riya`).
- `resolveAccess()` → `AccessInfo(isOwner, activeUserId, user, owner)`. Every mutation must check `isOwner`.
- Family links (`UserEntity.linkedPrimaryUserId`) are **self-healing**: stale links are re-discovered by
  scanning users and persisted locally + remotely; the member is registered under
  `users/{ownerId}/family_members/{memberId}`.

---

## 12. Security analysis

`firestore.rules` is **intentionally permissive for a trusted family** (the file itself says it is "NOT
suitable as-is for a public, multi-tenant product").

| # | Finding | Severity (public) | Severity (private) |
|---|---|---|---|
| 1 | `read: if signedIn()` — any account reads any user's full medical tree | 🔴 Critical | 🟡 Tolerable |
| 2 | Gemini API key embedded in APK + no minify → extractable, quota theft | 🔴 Critical | 🟠 Medium |
| 3 | `family_members`/`families`/`invites` writable by any signed-in user | 🔴 High | 🟡 Tolerable |
| 4 | LLM auto-triages symptoms ("Emergency") with no disclaimer/audit | 🔴 High (safety) | 🟠 Medium |
| 5 | Room DB unencrypted (no SQLCipher) — plaintext medical data on device | 🟠 Medium | 🟠 Medium |
| 6 | `isMinifyEnabled = false` (no R8/obfuscation) | 🟠 Medium | 🟢 Low |
| 7 | Firebase Analytics on health data | 🟠 Medium | 🟡 Low |
| 8 | Supabase `documents` bucket anon RLS — verify per-user scoping | 🟠 Medium | 🟡 Low |

**Compliance:** no HIPAA / GDPR / India-DPDP controls (no encryption at rest beyond OS, global read rules,
analytics on health data, autonomous LLM triage). Acceptable as a private family app; not compliant as a
public health product.

### Security requirements by data type
- **Personal data (name, phone for WhatsApp):** minimize; scope `family_members` to the owner's tree; consent for analytics.
- **Pregnancy data:** owner-write/role-read rules; encrypt at rest; never log to analytics.
- **Medical records (symptoms, meds):** strict per-user rules; audit AI access; disclaimer on AI-derived statements.
- **Reports/prescriptions (files):** per-user storage paths + signed URLs; encrypt; verify no public bucket.
- **Authentication:** keep Firebase Auth; add App Check to bind requests to the app; re-auth for sensitive views.
- **Cloud sync:** TLS (default); App Check + per-user rules; proxy the Gemini key via a Cloud Function.

---

## 13. Scalability analysis

Architecture *pattern* scales; four hard blockers don't: hardcoded owner, global-read rules,
client-embedded API key, no minify. Also: full-collection pulls + delete-not-in reconcile on every sync
(O(all docs)), ~12 real-time listeners per family device, symptom mirror-all on every push, no pagination.

| Load / day | Verdict | What it needs |
|---|---|---|
| 100 | ✅ Effortless · ~$0 | De-hardcode owner first |
| 1,000 | ✅ Fine · free–low | Tighten rules · key proxy · Firestore indexes |
| 10,000 | 🟠 Works with changes | Per-user rules · pagination · drop full reconcile · cost monitoring |
| 100,000 | 🔴 Real rework | Multi-tenant custom claims · sharding · AI cost controls · abuse protection |

---

## 14. Performance

- Compose + Room `Flow`s → UI is instant (<100ms).
- Bottlenecks: **2–3 MB bundled baby PNGs** (`drawable/sz_*.png`) inflate APK + cause decode spikes;
  full pulls + reconcile + three overlapping sync triggers (throttled); OCR + CameraX transient memory.
- No baseline profiling exists.

### Estimates (current build, mid-range device)
| Metric | Estimate |
|---|---|
| Local screen (Room) | <100 ms |
| AI response (Gemini) | 1–3 s |
| STT | 1–2 s · TTS instant |
| Memory (typical) | 120–200 MB |
| Memory (OCR/camera spike) | ~280–330 MB |
| APK size (now) | ~35–55 MB |
| APK size (after R8 + WebP + drop Supabase) | ~20–28 MB |
| Battery | Low–Moderate (alarms + 15-min worker + listeners) |
| AI accuracy (general pregnancy Q&A) | ~85–92% (triage NOT reliable — gate it) |
| Maintenance | Moderate |

---

## 15. Database recommendation

| Option | Performance | Offline | Scale | Security | Maintenance | Cost |
|---|---|---|---|---|---|---|
| Room only | ★★★★★ | ★★★★★ | ★★ | ★★★ | ★★★★ | Free |
| Raw SQLite | ★★★★★ | ★★★★★ | ★★ | ★★ | ★★ | Free |
| Realm | ★★★★ | ★★★★★ | ★★★ | ★★★ | ★★★ | $ at scale |
| Firestore only | ★★★ | ★★★ | ★★★★★ | ★★★ | ★★★★ | Free→$$ |
| Supabase | ★★★ | ★★ | ★★★★ | ★★★★ | ★★★ | Free→$ |
| **Hybrid Room + Firestore (recommended)** | ★★★★★ | ★★★★★ | ★★★★ | ★★★→★★★★ | ★★★ | Free→$ |

**Keep Hybrid Room (local SoT) + Firestore (sync)** — your current model. Add **SQLCipher** for at-rest
encryption and tighten rules. Move document files to Firebase Storage and retire Supabase/Ktor.

---

## 16. Recommendations — treatment plan (priority order, ~2–4 weeks)

1. **Scope Firestore rules + add App Check** — per-user read rules; bind requests to the app. Closes the
   only genuine 🔴 (every account reading every medical tree).
2. **Proxy the Gemini key + enable R8** — move the key behind a Cloud Function so it never ships in the
   APK; turn on minify/obfuscation/shrinking (also cuts size).
3. **Guardrail the AI** — system prompt + disclaimer, JSON via Gson (not regex), deterministic red-flag
   override for emergency symptoms.
4. **Encrypt data at rest (SQLCipher)** — medical data is plaintext in Room today.
5. **De-hardcode the owner** — replace the name check in `RoleManager` with a `role`/`ownerId` field on the
   user document (the unlock for ever serving a second family).
6. **Drop Supabase · WebP the images** — use Firebase Storage for documents (one backend, fewer crashes);
   convert multi-MB baby PNGs to WebP.
7. **Offline FAQ + intent router** — small on-device answer set + command router so the assistant works
   with no network.

**Keep:** the MVI + Clean-lite + offline-first architecture. Don't rewrite — the wins are in hardening.

---

## 17. Final scorecard

| Item | Verdict |
|---|---|
| Recommended Architecture | MVI + Clean-lite + offline-first (keep current) |
| Recommended Database | Hybrid Room (+SQLCipher) + Firestore |
| Recommended AI Assistant | Hybrid — Gemini + offline FAQ + rules-based safety override |
| Recommended Voice Assistant | Hybrid — native STT/TTS + intent router + Gemini interpretation |
| Estimated Accuracy | ~85–92% general Q&A; autonomous medical triage not reliable — gate it |
| Estimated Response Time | Local <100 ms · AI 1–3 s |
| Estimated Development Effort | Already substantial/mature; hardening ≈ 2–4 weeks |
| Estimated Maintenance Effort | Moderate |
| Security Score | 4/10 (public) · ~7/10 (private family) |
| Scalability Score | 5/10 |
| Overall Architecture Score | 8/10 |
| Overall Product Readiness | 7.5/10 (private/family) · ~4/10 (public healthcare) |

---

## 18. Known gaps / future work (from `PROJECT_GUIDE.md §10` + this analysis)

- Owner detection hardcoded to `adarsh`/`riya` — replace with a role field for multi-family support.
- `SymptomLogEntity` has no sync flag (mirror-all every push); adding `syncStatus` needs a Room migration.
- Reminders use `synced: Boolean` instead of the `SyncStatus` enum — unify in the next reminder rework.
- Water intake has no deletion reconciliation (per-day upsert docs, no delete UI).
- Timezone: epoch-millis with device-local interpretation can shift the displayed week ±1 day around midnight.
- `firestore.rules` permissive for linked reads — must be reworked before any public release.
- No UI string localization (one `strings.xml`); multi-language is voice-locale + LLM tolerance only.
- No automated tests of substance; no baseline performance profiling.

---

*This document is an advisory engineering assessment derived from source as of 2026-06-24. Scores are
engineering judgement, not certification. Security and readiness numbers are stated for a public release;
for the actual private-family deployment, readiness reads ~7.5 and most security findings drop one level.*
