# F8 Groups and leaderboard (Joe)

Friends compete on their daily diet score. A small server on your Mac
(FastAPI + SQLite) stores groups and scores; each phone or emulator uploads
its own daily scores and shows the group ranking. Everything runs locally:
no account, no internet.

## What the user sees (Groups tab 🏆)

- Set a nickname (first time only)
- Create a group → get a 6-character join code; friends type it to join
- Ranking for **Today** or **This week** (average since Monday, resets every Monday)
- 👑 crown for the leader, #2 / #3…, "–" for anyone who hasn't logged yet
- ⚡ streak (days in a row with a score), meal count
- Emoji reactions to friends (👏 🔥 💪 🥦); tap the same one again to remove it
- Refreshes every 10 seconds while the tab is open; scores upload automatically when you add or delete a meal
- Only score numbers are shared: never weight, photos or food details

## Running it

### 1. Start the server (keep this Terminal window open)

```
cd ~/AndroidStudioProjects/NutriQuest/backend
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

Check it works: open http://localhost:8000/health in your browser; it should say `{"ok":true,...}`.
API docs to explore: http://localhost:8000/docs

### 2. Run the app (second Terminal window)

```
cd ~/AndroidStudioProjects/NutriQuest
./gradlew installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.nutriquest.app/.MainActivity
```

Open **Groups** → enter a nickname → server address stays `http://10.0.2.2:8000` → **Save and connect**.
(`10.0.2.2` is how an emulator reaches your Mac.)

### 3. Demo with two "friends"

| Option | How |
|---|---|
| Two emulators | Android Studio → Device Manager → create a second phone (e.g. "Pixel 8"). Start both, then `./gradlew installDebug` installs on both. Each install gets its own user automatically. |
| Emulator + real phone | Phone on the same Wi-Fi as the Mac. Find the Mac's IP: `ipconfig getifaddr en0` (e.g. 192.168.1.23). In the phone's Groups settings, use `http://192.168.1.23:8000`. |

On phone A: create a group → read out the join code. On phone B: type the code → Join.
Log meals on both and watch the ranking update.

## Files

| File | New or changed | What it does |
|---|---|---|
| `backend/groups.py` | New | REST API: users, groups (create / join / leave by code), daily scores, leaderboard (day/week, rank, crown, streak), reactions. SQLite file `backend/nutriquest_groups.db` |
| `backend/test_groups.py` | New | 15 server tests: `python -m unittest test_groups -v` |
| `backend/main.py` | Changed (+3 lines) | Plugs the groups API into the existing server |
| `backend/requirements.txt` | Changed (+1 line) | `httpx`, needed only to run the tests |
| `app/src/main/java/com/nutriquest/app/GroupsApi.kt` | New | Talks to the server (built-in HttpURLConnection + org.json, no new libraries) |
| `app/src/main/java/com/nutriquest/app/GroupsViewModel.kt` | New | Nickname, server address, user ID, groups, leaderboard, score upload |
| `app/src/main/java/com/nutriquest/app/GroupsScreen.kt` | New | The Groups tab |
| `app/src/test/java/com/nutriquest/app/GroupsApiTest.kt` | New | 6 client tests using a fake server built into Java |
| `MealLogViewModel.kt` | Changed (+1 line) | `GROUPS` tab in `AppPage` |
| `MainActivity.kt` | Changed | Creates the groups ViewModel, shows the tab, uploads scores when meals change |
| `AndroidManifest.xml` | Changed | `INTERNET` permission; `usesCleartextTraffic` because the local server uses http:// |
| `.gitignore` | Changed | Keeps `backend/nutriquest_groups.db` out of Git |

## API summary

| Method | Path | Does |
|---|---|---|
| GET | `/health` | Is the server up? |
| PUT | `/users/{userId}` | Register or rename (`{"nickname"}`) |
| POST | `/groups` | Create (`{"userId","name"}`) → join code |
| POST | `/groups/join` | Join (`{"userId","code"}`) |
| POST | `/groups/{id}/leave` | Leave; the last person leaving deletes the group |
| GET | `/users/{userId}/groups` | My groups |
| PUT | `/scores` | Upload a day's score |
| DELETE | `/scores/{userId}/{date}` | Remove a day's score |
| GET | `/groups/{id}/leaderboard?userId&date&period=day\|week` | Ranking |
| PUT | `/groups/{id}/reactions` | React to a friend (toggle) |

## Tested

- Server: 15 new tests + teammate's 2 pass
- App: 30 unit tests pass (`./gradlew test`)
- End to end: the app was run against a live server with two seeded friends: it uploaded its
  score, joined by code, showed the ranking with crown and streaks, and sent a reaction
