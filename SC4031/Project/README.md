# Hybrid Edge-Cloud Fall Detection System (SC4031)

This project implements a multimodal emergency detection system using an **Android Smartphone** as the IoT device and a **Python Flask Server** as the Cloud. It utilizes a 2-stage cascade with a real-time streaming verification architecture.

## 🏗 Architecture

### Stage 1: Local Physical Trigger (Edge)
*   **Device:** Android Smartphone (Samsung S23+ supported).
*   **Sensor:** 6-Axis IMU (Accel + Gyro) sampled at **120Hz**.
*   **Logic:** 
    *   Continuously monitors for an impact (>40.0 m/s²).
    *   Upon impact, it captures a window of 120 samples (80 pre-impact, 40 post-impact).
    *   A local **TensorFlow Lite** CNN model verifies the "Fall" signature.

### Stage 2: Cloud Voice Verification (Cloud Streaming)
*   **Architecture:** Stateful Streaming with Sliding Window.
*   **Trigger:** Activated only if Stage 1 confirms a fall.
*   **Streaming:** The app records and sends audio in **1-second chunks** for up to 10 seconds.
*   **Cloud Logic:**
    *   **Rolling Buffer:** The server maintains a 2-second buffer for each device.
    *   **Sliding Window:** As chunks arrive, the server slides a 1s window (0.5s step) over the buffer to detect keywords ("HELP", "CANCEL").
    *   **Low Latency:** The system triggers the alarm immediately if a keyword is found with >85% confidence, without waiting for the full 10s to finish.

---

## 🚀 Advanced Features
*   **Real-Time Online Updating:** When a user clicks "FALSE ALARM", the app sends the entire captured audio sequence. The server performs **runtime fine-tuning** using `model.train_on_batch()` to adapt to the user's specific voice and environment.
*   **Multi-User Support:** Uses `deviceId` (Android ID) to track rolling buffers and logs for multiple simultaneous IoT devices.
*   **Link Verification:** Includes a "CONNECT" feature to verify Wi-Fi and IP connectivity before use.

---

## 🛠 Cloud API Reference

### 1. Connection Test: `GET /connect`
Verifies the IoT device can reach the cloud.
*   **Returns:** `{"status": "Connected"}`

### 2. Stream Inference: `POST /infer`
Sends a 1-second audio chunk (16kHz PCM).
*   **Payload:** `{"deviceId": "ID", "audio": [samples]}`
*   **Returns:** `{"keyword": "HELP/CANCEL/BACKGROUND", "confidence": 0.95}`

### 3. Online Retraining: `POST /update`
Sends corrective labels for runtime model updates.
*   **Payload:** `{"deviceId": "ID", "label": "HELP/BACKGROUND", "audio": [samples]}`

---

## 🏁 Getting Started

### 1. Server Setup
1.  Navigate to `/Cloud_Server`.
2.  Ensure `emergency_model.h5` is in the folder.
3.  Run `pip install -r requirements.txt`.
4.  Start server: `python app.py`. Note the **Local IP Address**.

### 2. Android Setup
1.  Open `/Android_Fall_Detector` in Android Studio.
2.  Install on phone (Developer Options -> USB Debugging must be ON).
3.  Enter your Laptop's IP in the app and tap **CONNECT**. The button will turn **Green** if successful.

### 3. Simulation & Retraining
1.  Drop the phone onto a soft surface (bed/sofa).
2.  When "LISTENING" appears, shout **"HELP"**.
3.  If it triggers incorrectly, tap **FALSE ALARM** to trigger the cloud retraining.
