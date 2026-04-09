# Hybrid Edge-Cloud Fall Detection System (SC4031)

This project implements a multimodal emergency detection system using an **Android Smartphone** as the IoT device and a **Python Flask Server** as the Cloud. It utilizes a 2-stage cascade to balance power efficiency with high-accuracy verification.

## 🏗 Architecture
The system operates in two distinct stages:

### Stage 1: Local Physical Trigger (Edge)
*   **Device:** Android Smartphone.
*   **Sensor:** 6-Axis IMU (3-Axis Accelerometer + 3-Axis Gyroscope).
*   **Logic:**
    *   **Impact Detection:** Continuously monitors for a high-G impact (>40.0 m/s²).
    *   **Local Verification:** Upon impact, a local **TensorFlow Lite** CNN model analyzes a 1-second window (120 samples at `SENSOR_DELAY_FASTEST`) of motion data to confirm a "Fall" signature.
*   **Benefit:** Zero-latency response, privacy-preserving (mic is off), and works offline.

### Stage 2: Cloud Voice Verification (Cloud)
*   **Trigger:** Activated only if Stage 1 confirms a fall.
*   **Capture:** Records 1 second of 16kHz Mono PCM audio.
*   **Transmission:** Sends raw audio data via **HTTP POST (JSON)** to the Cloud.
*   **Cloud Logic:**
    *   **Feature Extraction:** Extracts 13 MFCCs (26 Mel bands, 16kHz) to match the training notebook.
    *   **Verification:** A Full Keras CNN Model identifies the keyword: `"HELP"`, `"CANCEL"`, or `"BACKGROUND"`.
*   **Response:**
    *   **HELP:** Triggers a synthetic voice alarm and red UI alert on the phone.
    *   **CANCEL:** Resets the system via voice command.
    *   **BACKGROUND:** Silently ignores the trigger (False Alarm).

---

## 🚀 Advanced Features
*   **Online Model Updating:** Fulfills advanced project criteria. When a user provides feedback (e.g., clicking "FALSE ALARM" after an emergency trigger), the Cloud Server performs **runtime fine-tuning**. It uses `model.train_on_batch()` to update the neural network weights immediately, allowing the system to learn the user's specific environment and voice over time.
*   **Multi-User Support:** The server uses the `deviceId` (Android ID) to track and log requests from multiple simultaneous devices.

---

## 📁 Folder Structure
*   `/Android_Fall_Detector`: Kotlin source for the Android app.
*   `/Cloud_Server`: Flask `app.py`, the `emergency_model.h5` model, and `retrain_data/` storage.
*   `/Training`: Jupyter notebooks (`.ipynb`) and CSV datasets for both IMU and Audio models.

---

## 🛠 Cloud API Reference

### 1. Inference: `POST /infer`
Sends audio for keyword verification.
*   **Payload:** `{"deviceId": "ID", "audio": [int_pcm_samples]}`
*   **Returns:** `{"keyword": "HELP/CANCEL/BACKGROUND", "confidence": 0.98}`

### 2. Online Update: `POST /update`
Sends a label correction to trigger runtime retraining.
*   **Payload:** `{"deviceId": "ID", "label": "HELP/BACKGROUND", "audio": [int_pcm_samples]}`
*   **Returns:** `{"status": "Success", "metrics": [loss, accuracy]}`

---

## 🏁 Getting Started

### 1. Server Setup
1.  Navigate to `/Cloud_Server`.
2.  Install dependencies: `pip install -r requirements.txt`.
3.  Start the server: `python app.py`. Note your laptop's Local IP address.

### 2. Android Setup
1.  Open `/Android_Fall_Detector` in Android Studio.
2.  Enter your Laptop's IP in the app's IP address field.
3.  Wear the phone and simulate a fall (drop onto a soft surface).

### 3. Online Retraining
1.  If the app incorrectly triggers an "EMERGENCY" alert, click **"FALSE ALARM"**.
2.  The app will send the audio to the cloud.
3.  Check the server console to see the model weights being updated in real-time.
