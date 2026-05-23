# 🛡️ Warranty Vault

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-v1.9+-7F52FF?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpack-compose)](https://developer.android.com/jetpack/compose)
[![Database](https://img.shields.io/badge/Storage-Room%20SQLite-3DDC84?style=for-the-badge&logo=sqlite)](https://developer.android.com/training/data-storage/room)

**Warranty Vault** is a secure, elegant, and exceptionally designed physical item companion and dynamic warranty tracker. Built entirely from scratch using **Jetpack Compose**, **Kotlin**, and modern **Material 3 guidelines**, it allows users to scan or upload receipts, automatically extract receipt data using on-device/custom AI integrations, and safely persist warranties to local SQLite databases.

Styled in an **eye-safe, cozy graphite-slate dark theme** with elegant dynamic badge highlights and comfortable gray tones, Warranty Vault prevents squinting and ensures seamless user experience day and night.

---

## ✨ Features

*   📂 **Secure Local Storage**: Built on SQLite with **Room Database** to safeguard all warranty details, physical properties, purchase locations, and receipts entirely on-device offline.
*   🤖 **Intelligent OCR Parsing & Core Extraction**: Integrates Gemini AI scanning capability to easily extract items, prices, purchased venues, and warranty expiration timelines.
*   🛡️ **Smart Expiry Badges**: Adaptive state highlighting with muted, eyesafe warning states (soft forest greens for active items, cozy deep crimson-rose for expired status).
*   🎭 **Bespoke Profile Configurations**: Instant on-device image launcher to set customized avatars/display pictures, paired with local account profiles.
*   ⚙️ **Advanced Privacy & Key Infrastructure**: Secure locally-persisted custom credential management, enabling users to insert their personal credentials on-the-fly.

---

## 🛠️ Built With

*   **Language**: [Kotlin](https://kotlinlang.org/) for highly performant and modern asynchronous code.
*   **UI System**: [Jetpack Compose](https://developer.android.com/jetpack/compose) for a fully declarative and smooth material experience.
*   **Persistence**: [Room (SQLite)](https://developer.android.com/training/data-storage/room) for safe offline caching.
*   **Async Operations**: Coroutines, Flow, and `collectAsStateWithLifecycle` for optimal UI rendering.
*   **Image Loading**: [Coil](https://coil-kt.github.io/coil/) for performant asynchronous image decoding.

---

## 🚀 Running the Project Locally

To import, compile, and run this application on your local machine, follow these steps:

### Prerequisites
*   **Android Studio** (Koala / Ladybug or newer recommended)
*   **Android SDK 34+**
*   **Gradle 8.0+**

### Step-by-Step Setup
1.  **Clone or Import**: Close Android Studio if it is open, select **Open** or **Import**, and navigate to the directory containing this project.
2.  **SDK Synchronization**: Let Android Studio index the project and resolve standard Gradle dependencies.
3.  **Local Environment Settings**:
    *   Create a file named `.env` in the root directory.
    *   Populate it with a placeholder or your API key as shown:
        ```env
        GEMINI_API_KEY=your_actual_api_key_here
        ```
    *   *Note:* Even if you leave the default build configuration key blank, you can input your key dynamically inside the application's **Account Dialog Settings** page! Your credentials will reside safe and locally on your phone's memory.
4.  **Run**: Click the **Run** green arrow button with your targeted device/emulator connected.

---

## 🔒 Security & Code Confidentiality

### 1. How to Upload to GitHub Safely (Without Leaking Your API Key)
The project is strictly pre-configured to suppress credential leakage:
*   The `.env` file containing local API secrets is listed in the project's official `.gitignore` template.
*   **DO NOT** manually commit your `.env` file. Keep `.env.example` in your repository as a reference for setup.
*   When sharing or releasing your codebase on GitHub, anyone who forks it can simply provide their own key (either via their local `.env` or using the handy built-in setting dialog within the app).

### 2. Posting clean `.apk` releases
*   If you compile a release `.apk` using standard build systems, any API Key written inside a local `.env` file at compile time may be encoded inside the binary, which could be decompiled by external tools.
*   **Recommended Approach**: Securely build your `.apk` with a completely empty or placeholder value inside your local `.env`. Users downloading your app can input their Gemini API Key inside the **Account/Settings Profile dialog** in the top-right of the dashboard. This keeps the binary 100% clean and secures your custom credentials.

---


*Handcrafted with care. Pure Android native craftsmanship.*
