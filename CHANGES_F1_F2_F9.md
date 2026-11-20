# F1 + F2 + F9 changes (Joe)

## What this adds

| Feature | What the user sees |
|---|---|
| F1 Sign-up and profile | First-launch setup in 4 steps: name (also the Groups nickname) → age, sex, height, weight, activity → diet goal → chef and tone. "Skip for now" keeps the defaults. Profile gains **About you** and **Your account** cards ("Run setup again"). |
| F2 Goal setting | Suggested daily targets from body stats + diet goal, with the working shown. **Use these targets** fills Profile → Daily targets. |
| F9 Notification | **Daily reminder** card: on/off, time (12:30 / 18:00 / 20:00 / 21:30), **Send test now**. The notification shows today's score so far plus the goal reminder from `GoalFeedback.kt` in the roast language; with no meals it nudges you to log one. Tapping opens the app. |

## How targets are suggested (F2)

- BMR (Mifflin-St Jeor) = 10 × kg + 6.25 × cm − 5 × age + 5 (male) / −161 (female) / −78 (not stated)
- Maintenance = BMR × activity (1.2 / 1.375 / 1.55 / 1.725 / 1.9)

| Diet goal | Calories | Protein | Fat |
|---|---|---|---|
| Weight management | maintenance × 0.85 (never below BMR or 1200 kcal) | 1.6 g/kg | 30% |
| Build muscle | maintenance × 1.10 | 1.8 g/kg | 25% |
| Balanced, Regular meals, My own goal, Fewer sweet snacks | maintenance | 1.2 g/kg | 30% |

Carbs fill the remaining calories. The app labels these as estimates, not medical advice.

## Files

| File | New or changed | What it does |
|---|---|---|
| `BodyStats.kt` | New | Body stats, BMR/maintenance, presets, storage |
| `Account.kt` | New | `LocalAccount` (one user ID for the account and Groups), `SetupResult`, `AccountViewModel` |
| `Onboarding.kt` | New | The 4-step setup screens |
| `ProfileExtras.kt` | New | About you, Daily reminder and Your account cards |
| `Reminders.kt` | New | Reminder settings, message text, WorkManager scheduling, worker, notification |
| `res/drawable/ic_stat_nutriquest.xml` | New | Notification icon |
| `BodyStatsTest.kt` (9), `AccountTest.kt` (5), `ReminderTest.kt` (9) | New | Tests |
| `MainActivity.kt` | Changed | Shows setup on first launch; passes the account to Profile |
| `ProfileScreen.kt` | Changed | Adds the 3 new cards; its section/choice/number helpers are now `internal` so the new cards reuse them |
| `GroupsViewModel.kt` | Changed | Uses `LocalAccount.userId` (same stored ID, so groups you joined are kept) |
| `AndroidManifest.xml` | Changed | `POST_NOTIFICATIONS` permission |
| `app/build.gradle.kts`, `libs.versions.toml` | Changed | WorkManager 2.12.0 (+ `work-testing` for tests) |

## Tested

- `./gradlew assembleDebug` builds; `./gradlew test` → 61 tests pass
- Setup flow clicked through on the computer: the profile saved with the Build muscle preset (2880 kcal, 126 g protein) and Gym buddy, and Home greets "Hey, Joe!"
