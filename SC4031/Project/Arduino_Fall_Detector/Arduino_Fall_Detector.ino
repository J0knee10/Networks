/*
  Arduino_Fall_Detector.ino
  SC4031 Project: Stage 1 (Local IMU Fall Detection) + Stage 2 (Audio Streaming)
*/

#include <Arduino_LSM9DS1.h>
#include <PDM.h>
#include <TensorFlowLite.h>
#include "fall_model.h" // Local IMU model (Converted from TFLite)

// System State Machine
enum State {IDLE, VERIFYING, STREAMING, ALARM};
State currentState = IDLE;

// Thresholds
const float IMPACT_THRESHOLD = 4.5; 
const float TILT_THRESHOLD_ANGLE = 60.0;
const unsigned long VERIFICATION_DELAY = 1500;
const int AUDIO_SAMPLES = 16000; // 1 second of audio

short audioBuffer[AUDIO_SAMPLES];
volatile int samplesRead = 0;
float standX, standY, standZ;
unsigned long stateStartTime = 0;

void setup() {
  Serial.begin(115200);
  while (!Serial);

  if (!IMU.begin()) { Serial.println("ERR: IMU Fail"); while (1); }
  
  // Initialize PDM for Stage 2
  PDM.onReceive(onPDMdata);
  if (!PDM.begin(1, 16000)) { Serial.println("ERR: PDM Fail"); while (1); }

  // Initial Calibration
  Serial.println("Calibrating... Stand upright.");
  delay(1000);
  if (IMU.accelerationAvailable()) {
    IMU.readAcceleration(standX, standY, standZ);
    float mag = sqrt(standX*standX + standY*standY + standZ*standZ);
    standX /= mag; standY /= mag; standZ /= mag;
  }
  
  pinMode(LED_BUILTIN, OUTPUT);
  Serial.println("SYSTEM_READY");
}

void loop() {
  switch (currentState) {
    case IDLE:
      checkIMU();
      break;
      
    case VERIFYING:
      if (millis() - stateStartTime > VERIFICATION_DELAY) verifyFall();
      break;
      
    case STREAMING:
      captureAndStream();
      break;
      
    case ALARM:
      digitalWrite(LED_BUILTIN, HIGH);
      Serial.println("EVENT:ALARM_ACTIVE");
      delay(5000);
      digitalWrite(LED_BUILTIN, LOW);
      currentState = IDLE;
      Serial.println("EVENT:RESET");
      break;
  }
}

void checkIMU() {
  float ax, ay, az;
  if (IMU.accelerationAvailable()) {
    IMU.readAcceleration(ax, ay, az);
    float aSum = sqrt(ax*ax + ay*ay + az*az);
    if (aSum > IMPACT_THRESHOLD) {
      currentState = VERIFYING;
      stateStartTime = millis();
      Serial.println("EVENT:IMPACT_DETECTED");
    }
  }
}

void verifyFall() {
  float ax, ay, az;
  if (IMU.accelerationAvailable()) {
    IMU.readAcceleration(ax, ay, az);
    float mag = sqrt(ax*ax + ay*ay + az*az);
    float dot = (ax/mag * standX) + (ay/mag * standY) + (az/mag * standZ);
    float angle = acos(dot) * 180.0 / PI;

    if (angle > TILT_THRESHOLD_ANGLE && (mag > 0.8 && mag < 1.2)) {
      Serial.println("EVENT:FALL_VERIFIED");
      currentState = STREAMING;
    } else {
      currentState = IDLE;
    }
  }
}

void captureAndStream() {
  Serial.println("CMD:START_STREAMING");
  samplesRead = 0;
  while (samplesRead < AUDIO_SAMPLES) { /* Wait for PDM buffer */ }

  Serial.println("--- AUDIO_DATA_START ---");
  for (int i = 0; i < AUDIO_SAMPLES; i++) {
    Serial.println(audioBuffer[i]);
  }
  Serial.println("--- AUDIO_DATA_END ---");

  // Wait for Cloud response via Gateway
  while (!Serial.available());
  String result = Serial.readStringUntil('\n');
  result.trim();

  if (result == "HELP" || result == "EMERGENCY") {
    currentState = ALARM;
  } else {
    Serial.println("EVENT:CANCELLED_BY_CLOUD");
    currentState = IDLE;
  }
}

void onPDMdata() {
  int bytesAvailable = PDM.available();
  int samplesToRead = bytesAvailable / 2;
  if (currentState == STREAMING && samplesRead + samplesToRead <= AUDIO_SAMPLES) {
    PDM.read(audioBuffer + samplesRead, bytesAvailable);
    samplesRead += samplesToRead;
  }
}
