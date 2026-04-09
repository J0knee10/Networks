# Path B: Android Smartphone Fall Detector (SC4031)

This project pivots the IoT device from an Arduino Nano 33 BLE Sense to an Android Smartphone, fulfilling the "Mobile App" and "Real-time Display/Voice" requirements (Slides 10 & 11).

## Architecture Changes
*   **Old:** Arduino -> Serial -> Gateway -> Cloud
*   **New:** Android App (IMU + Audio) -> HTTP -> Cloud (Flask)

## Implementation Steps

### 1. Project Setup (Android Studio)
*   **Language:** Kotlin
*   **Libraries:**
    *   `org.tensorflow:tensorflow-lite`: For local IMU inference (Stage 1).
    *   `com.squareup.okhttp3:okhttp`: For sending audio data to the Flask server (Stage 2).
    *   `android.speech.tts.TextToSpeech`: For synthetic voice output.

### 2. Stage 1: Local IMU Detection
*   **Sensor:** Use `Sensor.TYPE_ACCELEROMETER`.
*   **Logic:**
    *   Monitor for a >4.5G impact.
    *   If impact detected, run the TFLite `fall_model` locally.
    *   If a fall is confirmed, update the UI and trigger Stage 2.

### 3. Stage 2: Cloud Audio Verification
*   **Capture:** Use `AudioRecord` to capture 1 second of PCM data (16kHz).
*   **Cloud Call:** Send the PCM samples as a JSON array (`{"audio": [... ]}`) to your Flask server's `/infer` endpoint.
*   **Verdict:** If the server returns "HELP" or "EMERGENCY", use `TextToSpeech` to speak the alert and update the UI.

### 4. Advanced Task: Online Updating (Slide 16)
*   If the user clicks a "False Alarm" button on the app, the app will send that audio clip back to the server with a "BACKGROUND" label to the `/update` route.

## To-Do List
- [ ] Create Android Studio project.
- [ ] Implement `SensorEventListener` for IMU data.
- [ ] Port `fall_model.tflite` to `app/src/main/assets`.
- [ ] Implement `AudioRecord` for keyword capture.
- [ ] Connect to Flask server via HTTP.
- [ ] Implement `TextToSpeech` for voice alerts.
