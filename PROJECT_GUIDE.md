# Hello Mom+ — Project Guide

> Single reference for how this app is built and how its data flows. Read this before adding a
> feature so you don't have to re-discover the architecture. Last updated: 2026-06-12.

---

## 1. What the app is

A pregnancy companion app with **one Owner** (the pregnant mom) and **read-only Family Members**.
The owner records appointments, medicines, meals/water, symptoms, journal, bills, contractions,
reports and reminders. Family members link to the owner and see all of it, live, but can never
write.

- Package root: `app/src/main/java/com/adarsh/hellomom/`
- Pattern: **MVI** (`BaseViewModel<Intent, State, Effect>` in `core/`) + Hilt DI + Room + Firestore.
- Verify changes with `.\gradlew.bat assembleDebug` — **never** launch the emulator.

## 2. Layer map

| Layer | Path | Notes |
|---|---|---|
| UI (Compose screens + ViewModels) | `presentation/<feature>/` | One folder per feature, each with Screen / ViewModel / Contract (Intent, State, Effect) |
| Domain interfaces | `domain/repository/` | ViewModels depend on these, never on impls |
| Repository impls | `data/repository/` | Offline-first: write Room → best-effort Firestore push |
| Room | `data/local/` (`dao/`, `entity/`, `AppDatabase`) | The ONLY thing list screens read from |
| Firestore / Supabase / Gemini | `data/remote/` | Gemini uses **Retrofit REST**, not the generativeai SDK (SDK reintroduces a Ktor crash) |
| Background sync | `data/worker/SyncWorker.kt` | Scheduled in `HelloMomApp.kt` |
| Notifications | `notification/` | `ReminderManager` (AlarmManager), `AppointmentReminderScheduler`, `ReminderReceiver` |
| DI modules | `di/` | All repositories are `@Singleton` (`RepositoryModule`) |

## 3. Roles & family sharing (RBAC)

**Single source of truth: `core/RoleManager.kt`.**

- Owner accounts are identified by hardcoded name convention: full name contains `adarsh` or
  `riya` (`RoleManager.isOwnerName`). Change it there only.
- Every ViewModel starts with `roleManager.resolveAccess()` → `AccessInfo`:
  - `isOwner` — write access gate. **Every mutation must check this** (`if (!access.isOwner) return`).
  - `activeUserId` — whose data to read: own id for owners, the **owner's id** for family.
  - `user` — the logged-in user; `owner` — the resolved owner profile (self for owners).
- Family links live in `UserEntity.linkedPrimaryUserId` and are **self-healing**: if the stored
  link is stale, RoleManager re-discovers the owner by name in Firestore and persists the fix
  locally + remotely. It also registers the member under
  `users/{ownerId}/family_members/{memberId}` so the owner sees their contact card.

## 4. Data storage map

Room (local, source of truth for the UI) ↔ Firestore (cloud, shared between devices):

| Room table | Entity | Firestore path | Sync flag | Delete style |
|---|---|---|---|---|
| users | UserEntity | `users/{userId}` | `syncStatus` | n/a |
| appointments | AppointmentEntity | `users/{uid}/appointments/{id}` | `syncStatus` | hard remote, reconciled |
| medicines | MedicineEntity | `users/{uid}/medicines/{id}` | `syncStatus` | soft local (`isDeleted`), hard remote |
| meals | MealEntity | `users/{uid}/meals/{id}` | `syncStatus` | soft local, hard remote |
| water_intake | WaterIntakeEntity (key = `yyyy-MM-dd`) | `users/{uid}/water_intake/{date}` | `syncStatus` | upsert-only, no delete UI |
| symptom_logs | SymptomLogEntity | `users/{uid}/symptoms/{id}` | **none** (mirror-all) | no delete UI |
| journal_entries | JournalEntity | `users/{uid}/journal/{id}` | `syncStatus` | hard, reconciled |
| billing | BillingEntity | `users/{uid}/bills/{id}` | `syncStatus` | hard, reconciled |
| contractions | ContractionEntity | `users/{uid}/contractions/{id}` | `syncStatus` | hard, reconciled |
| reports | ReportEntity (metadata; file stays local) | `users/{uid}/reports/{id}` | `syncStatus` | hard, reconciled |
| family_members | FamilyMemberEntity | `users/{ownerId}/family_members/{memberId}` | `syncStatus` | reconciled |
| reminders | ReminderEntity (Int autoincrement id) | **top-level** `reminders/{id}` filtered by `userId` | `synced: Boolean` (legacy, different from others) | hard, reconciled + alarm cancel |
| daily_schedule_status | DailyScheduleStatusEntity (key = `{date}_{type}_{refId}`) | `users/{uid}/daily_schedule_status/{id}` | `syncStatus` | reconciled (rows persist per day; daily reset is implicit — a new date has no rows) |

The owner's daily wake-up / sleep times for Today's Schedule live on `UserEntity.wakeUpTime` /
`sleepTime` (synced as part of the profile doc). The "Today's Schedule" section (Your Health tab)
is a self-contained feature in `presentation/schedule/` (`TodayScheduleSection` +
`TodayScheduleViewModel`) — it derives a time-ordered timeline from medicines + today's meals +
those routine times and overlays the daily marks above; it does NOT touch `DashboardViewModel`.

Outside Room:
- `users/{uid}/health_metrics/current` and `users/{uid}/kicks/{date}` — Firestore-only, read via
  **snapshot listeners** in `DashboardRepositoryImpl` (real-time).
- `pregnancy_progress/{week}` — read-only week content (listener).
- Documents (Reports section UI) — **Supabase Storage**, bucket `documents`, needs anon RLS
  policies (`DocumentRepositoryImpl`).
- `families/{familyId}` — invite-join bookkeeping only.

**Rule for new entities:** all fields need default values (Firestore `toObjects()` requires a
no-arg constructor) + add `syncStatus`/`updatedAt`, and wire them into ALL THREE places in
`SyncRepositoryImpl`: push (pending), pull (upsert as SYNCED), reconcile (delete-not-in).

**Firestore + Kotlin `is`-prefixed booleans — MUST annotate.** A Kotlin `val isDone: Boolean`
serializes through its `isDone()` getter, so Firestore stores the field as `done` (the "is" is
stripped) but on read matches the backing field `isDone` → a `true` value silently comes back
`false`. This was the Upcoming green-tick sync bug (owner read it from Room and saw the tick;
family read it from Firestore and never did, even on manual refresh). Every shared boolean flag
that must round-trip as `true` (`isDone`, `isCompleted`, `isTaken`, `isDeleted`) is pinned with
`@get:PropertyName("…") @set:PropertyName("…")` and declared `var`. Do the same for any new
is-prefixed boolean on a Firestore-synced entity.

## 5. Sync — how data moves (read this before touching sync)

Core file: `data/repository/SyncRepositoryImpl.kt` (singleton, serialised by an internal `Mutex`).

### Write path (owner only)
1. Repository writes Room immediately (UI updates instantly) with `syncStatus = PENDING`.
2. Same call does a best-effort Firestore write (Firestore offline queue handles no-network).
3. `pushPendingData()` (part of `syncAll`) re-pushes anything still PENDING and flips it to SYNCED.
   Symptoms have no flag — they are mirrored in full on every push.

### Read/pull path
`syncAll()` resolves the role, then `pullOwnerData(ownerId)`:
- Pulls every collection listed above, upserts into Room as SYNCED.
- **Reconciles deletions**: local rows missing from the remote snapshot are deleted
  (`delete...NotIn(userId, keepIds)` DAO queries), EXCEPT rows still PENDING (unpushed local
  edits) and EXCEPT symptoms on the owner's own device (no sync flag → a fresh local log must
  survive). Reconciled appointments/reminders also get their local alarms cancelled.
- Pulled reminders are scheduled as local alarms (family gets the same notifications as the owner).
- Pulled appointments get 1-day-before / 1-hour-before alarms scheduled (see §7).

### Sync triggers (all of these exist — don't add more without reason)
| Trigger | Where | Kind |
|---|---|---|
| App start | `HelloMomApp.kt` one-time WorkManager job | full `syncAll` |
| Every 15 min | `HelloMomApp.kt` periodic WorkManager job (network-gated) | full `syncAll` |
| **Every screen load** | each ViewModel's load function | `syncIfStale()` (throttled) |
| Dashboard pull-to-refresh | `DashboardIntent.Refresh` | forced `syncAll` |
| Profile "Sync Data" button | `ProfileIntent.SyncData` | forced `syncAll` |

`syncIfStale(maxAge = 60s default, see SyncRepository.DEFAULT_SYNC_STALENESS_MS)` is the
navigation auto-sync: it no-ops if a sync finished < 60 s ago or one is already running. Because
every list screen collects a **Room Flow**, the screen repaints by itself the moment the pull
lands — family members never need a manual sync.

### Real-time vs throttled
- Real-time (Firestore listeners): dashboard health metrics, kicks, week content, and the
  reminder screen's `reminders` listener (scoped to the owner's userId, detached in `onCleared`).
- **Family dashboard real-time mirror**: `SyncRepository.observeOwnerRealtime(ownerId)` attaches
  snapshot listeners on all of the owner's shared collections and, on any change, re-pulls via
  `pullOwnerData` (reusing the one reconcile + alarm path). `DashboardViewModel` collects it for
  **family members only** (owners already see their own writes from Room; re-pulling could clobber
  an unpushed local edit). Signals are conflated and serialised by `realtimeMutex`. Because the
  Dashboard ViewModel stays in the back stack, the mirror keeps every Room-backed screen live with
  no manual refresh. Listeners detach in `awaitClose` when the scope is cancelled.
- Everything else: ≤ 60 s stale on navigation, ≤ 15 min in the background.

### Offline / connectivity
- The two dashboard Firestore-listener flows (`getMotherHealthData`, `getDailyKickCount`) end with
  `.onStart { emit(default) }.catch { emit(default) }` so the dashboard `combine()` never stalls
  on an offline first-load (the old infinite-shimmer bug for a fresh family login with no cache).
- `presentation/components/Connectivity.kt`: `rememberIsOnline()` (live `NetworkCallback`-backed
  state), `OfflineBanner` (slim top banner when offline), `OfflineScreen` (full retry screen shown
  instead of shimmer when offline with nothing cached). The Dashboard uses all three.

## 6. Pregnancy week calculation

**Single source of truth: `core/utils/PregnancyProgress.kt`.** Never re-implement the math.

- `week(startDate)` → 1..42, `dayOfWeek(startDate)` → 1..7, `trimester(week)` → 1..3,
  `totalDays(startDate)`.
- Null-safe and future-date-safe (returns week 1 / day 1 — never crashes, never negative).
- The week is **always computed from the OWNER's `pregnancyStartDate`**
  (`access.owner?.pregnancyStartDate ?: user.pregnancyStartDate`). Family members have no start
  date of their own; using `user.pregnancyStartDate` directly for them is a bug (shows Week 1).
- The week is never stored — always derived at display time, so it can't go stale.
- `PregnancyDataEngine.getWeekData(week)` internally clamps to 1..40 for content lookup
  (weeks 41–42 show week-40 content). That is intentional.

## 7. Notifications

All local notifications go through `notification/ReminderManager` (exact AlarmManager alarms →
`ReminderReceiver` → `ReminderService` foreground notification + optional voice).

- **Reminders**: scheduled on create/update; family devices schedule the same alarms when
  reminders are pulled in sync (idempotent, keyed by reminder id). Max 5 simultaneous
  notifications; 1-hour auto-snooze then expiry.
- **Appointments** (`notification/AppointmentReminderScheduler`): every appointment with a future
  time gets **two alarms — 24 h before and 1 h before** — on the owner's device (on insert/update)
  AND on family devices (on sync pull). Deleting/rescheduling an appointment cancels/moves both.
  Alarm request codes are `appointmentId.hashCode()` masked into the `0x20000000` / `0x40000000`
  ranges so they can never collide with reminder row ids.
- FCM (`service/MyFirebaseMessagingService`) is used for reminder voice pushes only — it is NOT a
  data-sync channel.
- **Permission gate** (`presentation/permission/PermissionGate.kt`, hosted in `MainActivity`):
  flow: (1) on launch, show the Android **system** notification popup — no custom dialog. If the
  user **doesn't allow**, immediately re-show the same system popup (gated by
  `shouldShowRequestPermissionRationale` + a `requestingNotif` guard + a `notifRequestTick` so it
  can't loop once permanently denied), and re-ask again on each `ON_RESUME`. (2) Once notifications
  are **allowed**, show the custom "Allow Alarms & Reminders" dialog → its Allow button opens
  `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (exact alarms have no runtime popup). Alarm state is
  re-checked on `ON_RESUME` so the dialog clears after the user returns from Settings. The custom
  dialog is used ONLY for alarms — never for notifications. Camera/mic are best-effort (optional).

## 8. Crash-safety conventions (follow these in new code)

- ViewModels: wrap `roleManager.resolveAccess()` in `runCatching`; add `.catch { }` to every
  collected Flow; a load failure must end with `isLoading = false`, never an unhandled throw.
- Firestore deserialization (`toObject(s)`) is always inside `runCatching` — malformed docs must
  not crash the app.
- Firestore snapshot listeners must be stored (`ListenerRegistration`) and removed in
  `onCleared()` / `awaitClose { }`. The old reminder-screen leak came from skipping this.
- Entities: every field has a default value (Firestore needs a no-arg constructor).
- Background sync failures are silent by design (`Result.failure` + `SyncLogger`); the UI keeps
  showing the Room cache.
- Use `SyncLogger` (`core/utils/SyncLogger.kt`) for sync/data logging — it tags reads, writes,
  and resolution steps consistently.

## 9. Checklist — adding a new synced feature

1. Entity in `data/local/entity/` — all-default fields + `syncStatus` + `updatedAt`.
2. DAO with: Flow read by `userId`, upsert, `getPendingSync...()`, `delete...NotIn(userId, keepIds)`.
3. Register in `AppDatabase` (bump version / migration).
4. Repository impl: Room first, then best-effort Firestore push under `users/{uid}/<collection>`.
5. `SyncRepositoryImpl`: add push-pending block, pull block (upsert SYNCED), reconcile line.
6. ViewModel: `resolveAccess()` → read by `activeUserId`, gate writes on `isOwner`,
   `syncIfStale()` on load, `PregnancyProgress` for any week display, `.catch` on flows.
7. Build: `.\gradlew.bat assembleDebug`.

## 10. Known gaps / future work

- **Owner detection is hardcoded** to names "adarsh"/"riya" (`RoleManager.isOwnerName`). Replace
  with a role field on the user document when multi-family support is needed.
- **SymptomLogEntity has no sync flag** — pushes mirror all rows every sync (fine at this scale);
  deletion reconciliation is family-side only. Adding `syncStatus` requires a Room migration.
- **Reminders use `synced: Boolean`** instead of the `SyncStatus` enum (legacy). Unify during the
  next reminder rework.
- **Water intake has no deletion reconciliation** (per-day upsert docs, no delete UI).
- **Timezone**: all dates are epoch millis with device-local interpretation; a timezone change can
  shift the displayed week by ±1 day around midnight.
- Firestore rules (`firestore.rules`) are permissive for linked reads; review before production.
