# 🤖 Cobot19-Tablet

**Cobot-Tablet** is an Android application designed to interface with the COBOT-19 robot.  
It enables emotion recognition, autonavagtion, and Bluetooth communication through a tablet-based interface.

---

## 📱 Features

- **Emotion Recognition**: Utilizes the tablet's front camera to capture images at intervals, analyzing facial expressions to determine user emotions.
- **Person Following**: Uses pose detection to track a person's position in real time, allowing the robot to follow them autonomously based on their location relative to the tablet's camera.
- **Bluetooth Communication**: Establishes a stable Bluetooth connection between the tablet and the robot for seamless data exchange.
- **Modular Architecture**: Organized codebase with clear separation of concerns, enhancing maintainability and scalability.

---

## 🛠️ Installation

### Prerequisites

- Android Studio installed on your development machine.
- An Android device running Android 5.0 (Lollipop) or higher.

### Steps

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/yaracham/cobot-tablet.git
   
2. **Open in Android Studio**:
  Launch **Android Studio**.
  Click on **File > Open...** and select the cloned folder.

3. **Build the Project**:
  Let Android Studio **sync and build** the project.
  Accept or resolve any **missing dependencies** if prompted.

4. **Run the App**:
  Connect the **Android tablet via USB**.
  Enable **USB debugging** from developer options.
  Press the green **Run ▶ button** to build and launch the app.

## 📚 Technologies Used

- **Kotlin** – Primary programming language for Android development.
- **Jetpack Compose** – Modern declarative UI toolkit for building native UIs.
- **Bluetooth API** – Used for managing Bluetooth connections to the COBOT-19 robot.
- **CameraX + MediaPipe** – Enables emotion and gesture recognition using the tablet's front camera.
