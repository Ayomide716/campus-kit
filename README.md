# Campus Survival Kit

An offline Android toolkit for students: Kotlin, framework Views only, no
third-party runtime libraries, no internet permission.

| Tool | What it does |
|---|---|
| 🗓 **Timetable** | Weekly lectures with a reminder before each one (5–60 min, or off). Optional auto-vibrate/silent during lectures. |
| 🎓 **GPA / CGPA** | Semesters and courses on the Nigerian 5-point scale (A=5 … F=0). Enter a score (graded with your editable bands) or a letter. Shows CGPA, class of degree and the GPA you need next semester to hit a target. |
| ✅ **Attendance** | Tap ✓/✗ per class. Shows your % and how many more classes you can miss and still meet the requirement (default 75%). Can import courses from the timetable. |
| ⏱ **Study timer** | Pomodoro focus/break timer with a live countdown notification, study streak and weekly totals. |
| 📝 **Exams** | Countdown to every exam, with reminders the day before and 1 hour before. |
| 💸 **Budget** | Monthly allowance and expenses in ₦, by category, with "≈ ₦X per day left". |
| 🧠 **Flashcards** | Decks and cards with Leitner spaced repetition. Paste many cards at once as `question | answer`. |

Default grade bands: A 70+, B 60–69, C 50–59, D 45–49, E 40–44, F < 40
(change them under Settings → Grading). Degree classes: First 4.50+, 2:1
3.50–4.49, 2:2 2.40–3.49, Third 1.50–2.39, Pass 1.00–1.49.

## Install on your phone

GitHub Actions builds a signed debug APK on every push. On your phone open
**Releases → Campus Kit latest debug build → CampusKit-debug.apk**, open it,
and allow installing from your browser if asked. Updates install over the
previous version and keep your data (all builds share `app/debug.keystore`).

## Permissions

- **Notifications**: lecture, exam and timer alerts (asked on first launch).
- **Alarms & reminders** (exact alarms): makes reminders on time. If you deny
  it, reminders still come but Android may delay them up to ~10 minutes.
- **Run at startup**: restores reminders after a reboot.
- **Do Not Disturb access** (optional): only for full silent mode in lectures;
  vibrate mode works without it.

The app has no internet permission, keeps data in a private local database
and excludes it from cloud backups.

## Project

```
core/     pure Kotlin logic (grading, attendance, money, schedule, Leitner…) + unit tests
data/     SQLite database and preferences
system/   alarms, notifications, auto-silent, study timer, reboot receiver
ui/       small programmatic UI kit and base screen
screens/  one Activity per tool
```

Build locally with `./gradlew assembleDebug` (JDK 17, Android SDK 35) or open
in Android Studio.
