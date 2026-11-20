# F6 + F7 changes (Joe)

## What this adds

| Feature | What the user sees |
|---|---|
| F6 Meal storage | Meals and their photos stay after closing the app. Diary has ‹ › to browse past days and a "Delete meal" button. |
| F7 Scores | Every meal gets a "Fit" score (0–100). Home and Diary show the daily score with its breakdown and the biggest gap. |

## Files

| File | New or changed | What it does |
|---|---|---|
| `app/src/main/java/com/nutriquest/app/MealStorage.kt` | New | Room database (`meals` table), `MealRepository` (load, save, delete), moves photos to `files/meal_photos/` |
| `app/src/main/java/com/nutriquest/app/DailyScore.kt` | New | The proposal's score formula, as plain Kotlin |
| `app/src/main/java/com/nutriquest/app/ScoreCards.kt` | New | `DailyScoreCard`, `MealScoreChip`, `DiaryDayPicker` |
| `app/src/test/java/com/nutriquest/app/DailyScoreTest.kt` | New | 14 tests for the score formula |
| `app/src/test/java/com/nutriquest/app/MealStorageTest.kt` | New | 8 tests for saving and loading meals (Robolectric, no emulator needed) |
| `app/src/main/java/com/nutriquest/app/MealLogViewModel.kt` | Changed | Loads saved meals at start-up, scores and saves each new meal, `deleteMeal`, `diaryDate`, `scoreFor(date)`; `onCleared` no longer deletes saved photos |
| `app/src/main/java/com/nutriquest/app/MainActivity.kt` | Changed | Score card on Home; Diary shows one day at a time with day picker, score card and delete button; Fit chip on meal cards; Home shows the last 5 meals |
| `app/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` | Changed | Adds Room 2.8.5 + KSP 2.3.14; Robolectric 4.18 for tests only |

Every change in the teammate's files is marked with a `// F6` or `// F7` comment, or wrapped in `---- F6 meal storage + F7 scores (Joe) ----`.

## Score formula (F7)

Daily Score = 0.45·S_cal + 0.35·S_macro + 0.20·S̄_meal

- S(x, t): d = |x − t| / t → 100 if d ≤ 10%, 250 × (0.50 − d) between 10% and 50%, 0 if d ≥ 50%
- S_cal = S(kcal, kcal target); S_macro = average of S(protein), S(carbs), S(fat)
- Meal Fit = how closely the meal's protein / carb / fat share of energy matches the targets' share: 100 × (1 − gap / 0.6), minus 20 for a sweet snack, kept between 0 and 100
- Today's score says "so far" and changes as meals are added; past days say "final"

## Tested

- `./gradlew assembleDebug`: builds
- `./gradlew test`: 24 tests pass (14 score, 8 storage, 2 existing)
