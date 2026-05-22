# VI Teacher Toolkit 🏫

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**VI Teacher Toolkit** is a comprehensive, feature-rich Android application specifically designed and optimized for **Visually Impaired (VI) Teachers**. It aims to solve daily administrative challenges in a classroom environment by providing robust tools that are fully compatible with screen readers (like Google TalkBack) and feature custom Text-to-Speech (TTS) spoken announcements.

Whether managing a class register, scheduling lessons, taking notes, or securely saving login credentials, the VI Teacher Toolkit provides an accessible, private, and offline-first solution.

---

## 🌟 Key Features

### 1. 📋 Accessible Class & Attendance Register
* **Student Registry:** Create and manage your class list with student details.
* **Daily Attendance:** Take student attendance quickly. The interface uses customized accessibility elements to ensure screen-readers announce state changes clearly (e.g., "Present", "Absent").
* **Attendance History:** Review history in detail. Compare attendance over time and view reports for specific dates.

### 2. 📅 Interactive Timetable Scheduler
* **Custom School Hours:** Define your school hours and standard period durations.
* **Daily & Weekly Schedules:** Schedule classes for different slots across the week.
* **Conflict Prevention:** Designed to prevent overlapping entries.

### 3. 🔊 Speech-Assisted Reminders (TTS)
* **Hands-Free Alerts:** Integrated with Android's Text-to-Speech (TTS) engine.
* **Smart Reminders:** Automatically schedules system alarms to speak reminders aloud when a period is about to start or end.
* **Reliability:** Built with a background receiver that automatically restores reminders after the device boots up.

### 4. 📝 Secure Notepad (My Notes)
* **Lesson Planning & Tasks:** Create and organize notes, lesson plans, or observations.
* **Rich Note Editor:** Easily edit, update, or delete entries.
* **Offline Storage:** Everything is saved locally on your device for fast access and ultimate privacy.

### 5. 🔒 Secure Password Locker
* **Credentials Manager:** Securely store login details for school administration portals, grading systems, and email accounts.
* **Local Encryption:** Uses Android security practices to keep your data encrypted.
* **PIN Lock Screen:** Protected by a secure 4-digit PIN authentication portal.

---

## 🛠️ Architecture & Tech Stack

The application is built following modern Android development practices, ensuring high performance, stability, and compatibility.

* **Language:** 100% [Kotlin](https://kotlinlang.org/)
* **Database:** [Room SQLite Database](https://developer.android.com/training/data-storage/room) for fully local, robust, and offline-first data persistence.
* **UI & Layouts:** ViewBinding with responsive XML layouts optimized with custom accessibility tags, content descriptions, and spoken announcements.
* **Background Processing:** Android Alarms and Services for scheduling timely speech reminders.
* **Minimum SDK:** Android 26 (Android 8.0)

---

## ♿ Accessibility First (A11y)

Unlike general-purpose teacher tools, **VI Teacher Toolkit** is built from the ground up to support visually impaired educators:
* **Custom Accessible Views:** Features custom components like `AccessibleSpinner` to prevent screen reader focus loss.
* **Explicit Announcements:** Uses `announceForAccessibility()` on critical actions (like exiting the app or saving records) to provide immediate, clear auditory feedback.
* **Contrast and Sizing:** High-contrast color palette and scalable typography compliant with Android Accessibility guidelines.

---

## 🚀 Getting Started

### Prerequisites
* [Android Studio Hedgehog](https://developer.android.com/studio) or newer.
* Android SDK 26+ (Device or Emulator).

### Setup and Installation
1. **Clone the Repository:**
   ```bash
   git clone https://github.com/hamsajaisal/VITeacherToolkit.git
   ```
2. **Open in Android Studio:**
   * Select `File -> Open` and choose the cloned folder.
   * Allow Gradle to sync and download dependencies.
3. **Run the App:**
   * Connect an Android device (make sure TalkBack is enabled for the best testing experience!) or launch an emulator.
   * Click **Run** (`Shift + F10`) in Android Studio.

---

## 🤝 Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make to improve accessibility, add features, or fix bugs are **greatly appreciated**!

1. Fork the Project.
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the Branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details. Anyone is free to use, modify, and distribute this software for personal or commercial purposes.
