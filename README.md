# 📱 Screen Recorder — Modern Android Screen Capture

A high-performance, lightweight, and modern screen recording Android application built using **Jetpack Compose**, **Kotlin Coroutines**, and Android's native **MediaProjection & MediaRecorder APIs**.

Designed with Material 3 principles and optimized for seamless video capture with floating overlay controls, on-screen live drawing annotations, and direct phone gallery integration.

---

## ✨ Features

- 🎥 **HD & Full HD Screen Recording**: Capture crisp videos at 60 FPS / 30 FPS with configurable bitrates up to 8 Mbps.
- 🎙️ **Audio Recording**: Record crisp audio via Microphone or choose pure muted screen capture.
- 🎨 **Floating Pen & Live Drawing**: Draw, highlight, and annotate directly over your screen while recording tutorials or gaming gameplay.
- ⏱️ **Floating Controls & Countdown**: Compact floating overlay widget with Pause, Resume, Stop, and customizable countdown timer (1s to 5s).
- 📁 **Direct Gallery & MediaStore Integration**: Automatically saves recordings to `Phone/Movies/ScreenRecorder/` and syncs with the system Android Gallery app.
- ⚡ **Zero-Lag Native Pipeline**: Utilizes hardware-accelerated H.264 video encoding via POSIX `ParcelFileDescriptor` for maximum stability and zero dropped frames.
- 🎨 **Material Design 3**: Modern, clean UI built 100% with Jetpack Compose.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with Kotlin StateFlow & Coroutines
- **Screen Capture**: Android `MediaProjectionManager`, `VirtualDisplay`, and `MediaRecorder`
- **Permissions**: Scoped Storage & MediaStore API (Android 7 to Android 15+)
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`)

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat or newer
- Android SDK 34+
- Java 17+

### Clone & Build
```bash
git clone https://github.com/YOUR_USERNAME/screen-recorder-android.git
cd screen-recorder-android
./gradlew assembleDebug
