# Diabetes Assistant / دستیار دیابت

Personal Android app (Persian + English) for diabetes self-management.

## v0.1 features
- Medicines (pills + insulin) with daily times
- Alarm-style reminders: full-screen over the lock screen, sound + vibration
- "I took it" / "Skip" confirmation; repeats every N minutes until confirmed, then marks as *missed*
- Today screen with progress, home-screen widget (next dose + progress)
- Blood-sugar log (mg/dL or mmol/L), tags (fasting, before/after meal, bedtime), 7-day chart, target range alerts
- Jalali dates in Persian mode, local-only storage, JSON backup/restore

The app **does not calculate or suggest insulin doses**; it only reminds and records what the doctor prescribed.
It is not a medical device.

## Roadmap
- v0.2: read meter value with the camera (OCR + confirm step), PDF report for the doctor
- v0.3: food carbs — offline Persian food database, barcode scan (Open Food Facts), photo estimate (AI, user's own API key)

## Build
Every push to `main` builds a signed APK with GitHub Actions → see **Releases** / **Actions → artifacts**.

Signing secrets (repo → Settings → Secrets and variables → Actions):
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Without them the APK is signed with a temporary debug key (updates then require uninstalling first — back up data before).

Local build: Android Studio (Ladybug+) → open folder → Run. Requires JDK 17, Gradle 8.9 (`gradle assembleDebug`).
