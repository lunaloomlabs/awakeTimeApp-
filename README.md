# 🌙 AwakeTime

> **Sleep better, wake up refreshed.**  
> A personalized sleep cycle calculator & smart alarm app built for **Android (APK)** and **Web / iPhone (PWA)**.

Waking up in the middle of deep or REM sleep leaves you groggy. **AwakeTime** calculates the exact times when your sleep cycles transition from **REM → Light sleep**, so you wake up feeling alert and rested.

---

## 🚀 Try It Now

- **🌐 Web App (iPhone, Desktop & Android):** [Open AwakeTime Live](https://quiet-licorice-f4549d.netlify.app)
- **🤖 Android App (APK):** [Download Latest `AwakeTime.apk`](https://github.com/hakanyavuzsah/awakeTimeApp-/releases/latest)

> **New user tip:** Open **⚙️ Settings → Calibration Profile** and switch to **🌍 Average (90 min)** for standard adult sleep cycles, or enter your own cycle length if you have smartwatch/sleep tracker data.

---

## ✨ Features

- **🛏️ Bedtime → Wake-Up Calculator:** Enter when you're going to bed (plus how long it takes you to fall asleep) and get optimal wake-up windows across 3–7 full sleep cycles.
- **⏰ Wake-Up → Bedtime Calculator:** Enter when you need to wake up and find out the best times to go to sleep.
- **📊 Visual Sleep Cycle Map:** See how your Light, Deep, and REM stages distribute across the night.
- **👤 Calibration Profiles:**
  - **⭐ Main:** Custom personal calibration (95 min cycle).
  - **🌍 Average:** Standard sleep-research defaults (90 min cycle) — ideal for anyone without personal tracker data.
  - Each profile saves its own cycle length, sleep onset delay, and wake window tolerance independently.
- **🔔 Built-in & Custom Alarm Sounds:** Choose from 4 built-in tones (*Gentle, Birds, Chime, Classic*) or upload your own audio file (`MP3`, `WAV`, `OGG`, `M4A`).
- **📱 Native Android Background Alarms:** The Android APK uses `AlarmManager.setAlarmClock` + a foreground audio service so your alarm rings reliably on the Android **Alarm** channel even when your screen is locked or the app is closed — plus a 1-tap shortcut to mirror the alarm in your phone's Clock app.

---

## 📲 Installation

### Android (Recommended — Native APK)
1. Download `AwakeTime.apk` from the [Releases](https://github.com/hakanyavuzsah/awakeTimeApp-/releases/latest) page (or from the latest workflow run under **Actions → Build AwakeTime APK → Artifacts**).
2. Open the file on your Android phone and tap **Install** (allow *"Install unknown apps"* if prompted).
3. Grant notification permission when setting your first alarm so lock-screen controls work properly.

### iPhone (Safari Home Screen App)
1. Open the [live web link](https://quiet-licorice-f4549d.netlify.app) in **Safari**.
2. Tap the **Share** button → **Add to Home Screen**.
3. Open **AwakeTime** from your Home Screen and select **🌍 Average** in **⚙️ Settings**.

*(Note: iOS restricts background alarms for web apps, so on iPhone you can either keep the app open while charging or set the calculated time in the built-in Clock app.)*

---

## 🛠️ Tech Stack & Project Structure

- **Frontend:** Vanilla HTML, CSS, JavaScript + PWA (`manifest.json`, `sw.js`) inside [`www/`](www/)
- **Android Wrapper:** [Capacitor 6](https://capacitorjs.com/) + custom native Android Java plugin (`AwakeTimeAlarmPlugin`, `AlarmReceiver`, `AlarmService`) inside [`android/`](android/)
- **CI/CD:** Automated Android APK builds via GitHub Actions ([`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml))

### Local Development

```bash
# Install dependencies
npm install

# Sync web changes into the Android project
npm run sync

# Preview web version locally
python -m http.server 8080 --directory www
```

### Building the APK
Every push to `main` automatically builds a fresh `AwakeTime.apk` via GitHub Actions. To build locally with Android Studio / JDK 17:

```bash
npm run sync
cd android
./gradlew assembleDebug
```

The output APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`.
