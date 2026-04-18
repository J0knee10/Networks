/*
  IMU_Fall_Capture.ino (v2.0 - Circular Buffer)
  Stage 1: Training Data Collector for Fall Detection
  
  This script captures 1 second of data (120 samples at ~119Hz).
  It uses a circular buffer to ensure the 0.25s of data BEFORE the impact
  is included in the capture, showing the "flight/descent" phase.
*/

#include <Arduino_LSM9DS1.h>

// Thresholds
const float IMPACT_THRESHOLD = 4.5; // G's

// Buffering Configuration
const int PRE_SAMPLES = 40;   // ~330ms of data before impact
const int TOTAL_SAMPLES = 120; // ~1 second total capture window
const int POST_SAMPLES = TOTAL_SAMPLES - PRE_SAMPLES;

struct IMUSample {
  float ax, ay, az, gx, gy, gz;
};

IMUSample circularBuffer[PRE_SAMPLES];
int bufferIdx = 0;
bool bufferFull = false;

void setup() {
  Serial.begin(115200);
  while (!Serial);

  if (!IMU.begin()) {
    Serial.println("Failed to initialize IMU!");
    while (1);
  }
  
  Serial.println("aX,aY,aZ,gX,gY,gZ");
}

void loop() {
  float ax, ay, az, gx, gy, gz;

  if (IMU.accelerationAvailable() && IMU.gyroscopeAvailable()) {
    IMU.readAcceleration(ax, ay, az);
    IMU.readGyroscope(gx, gy, gz);
    float aSum = sqrt(ax*ax + ay*ay + az*az);

    // 1. Check for Trigger (Impact)
    if (aSum > IMPACT_THRESHOLD) {
      Serial.println("--- TRIGGERED ---");
      
      // A. Print Pre-Impact Samples (the Descent)
      int start = bufferFull ? bufferIdx : 0;
      int count = bufferFull ? PRE_SAMPLES : bufferIdx;
      
      for (int i = 0; i < count; i++) {
        int idx = (start + i) % PRE_SAMPLES;
        printSample(circularBuffer[idx]);
      }

      // B. Record and Print the Impact and Post-Impact Phase
      // Print the current trigger sample first
      Serial.print(ax, 3); Serial.print(",");
      Serial.print(ay, 3); Serial.print(",");
      Serial.print(az, 3); Serial.print(",");
      Serial.print(gx, 3); Serial.print(",");
      Serial.print(gy, 3); Serial.print(",");
      Serial.println(gz, 3);

      for (int i = 0; i < (POST_SAMPLES - 1); i++) {
        while (!IMU.accelerationAvailable() || !IMU.gyroscopeAvailable());
        IMU.readAcceleration(ax, ay, az);
        IMU.readGyroscope(gx, gy, gz);
        
        IMUSample s = {ax, ay, az, gx, gy, gz};
        printSample(s);
        delay(8); // Match the ~119Hz rate
      }
      
      Serial.println(); // Boundary for CSV parsing
      
      // Reset buffer to avoid re-triggering immediately
      bufferIdx = 0;
      bufferFull = false;
      Serial.println("Capture Finished. Ready for next fall.");
      delay(2000); // Wait for board to be picked up
    } else {
      // 2. Normal Mode: Keep filling the Circular Buffer
      circularBuffer[bufferIdx] = {ax, ay, az, gx, gy, gz};
      bufferIdx++;
      if (bufferIdx >= PRE_SAMPLES) {
        bufferIdx = 0;
        bufferFull = true;
      }
      delay(8); // Roughly 119Hz
    }
  }
}

void printSample(IMUSample s) {
  Serial.print(s.ax, 3); Serial.print(",");
  Serial.print(s.ay, 3); Serial.print(",");
  Serial.print(s.az, 3); Serial.print(",");
  Serial.print(s.gx, 3); Serial.print(",");
  Serial.print(s.gy, 3); Serial.print(",");
  Serial.println(s.gz, 3);
}
