# Model Training Performance Results

This folder contains the datasets and training notebooks for the Hybrid Edge-Cloud Fall Detection System.

## 1. Local IMU Fall Detection Model
*   **Training Script:** `train_fall_model.ipynb`
*   **Model Results:** 
*   **Accuracy:**  83.33%
*   **Precision:** 81.25%
*   **Recall:**    86.67%
*   **F1-Score:**  0.8387
*   **Summary:** Trained to classify "Fall" vs "Normal" events using a 1-second 6-axis IMU window (120Hz).
*   **Deployment:** Converted to `fall_model.tflite` for edge deployment on the Android device.

## 2. Cloud Audio Verification Model
*   **Training Script:** `train_emergency_model.ipynb`
*   **Model Results:** 
*   **Accuracy:**  100.00%
*   **Precision:** 100.00%
*   **Recall:**    100.00%
*   **F1-Score:**  1.0000
*   **Summary:** Trained to classify "HELP", "CANCEL", and "BACKGROUND" noise using a 1-second 16kHz audio window processed as MFCCs.
*   **Deployment:** Saved as `emergency_model.h5` for deployment on the Flask Cloud Server.

---
*Note: Data was collected using Arduino Nano 33 BLE, due to different initial plans. These models are periodically updated via the online retraining endpoint (`/update`) using corrective user feedback to adapt to specific user environments.*
