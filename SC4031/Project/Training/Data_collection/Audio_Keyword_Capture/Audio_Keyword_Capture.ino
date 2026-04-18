/*
  Audio_Keyword_Capture.ino
  Record 1 second of audio for Keywords: "HELP", "CANCEL"
  
  Updated for robustness: 
  - Fixes hanging bug in collection loop.
  - Ensures PDM buffer is cleared.
  - Follows best practices from Lab exercises.
*/

#include <PDM.h>

const int SAMPLE_RATE = 16000;
const int NUM_SAMPLES = 16000; // 1 second
short audioBuffer[NUM_SAMPLES];
volatile int samplesRead = 0;
volatile bool isRecording = false;

void setup() {
  Serial.begin(115200);
  while (!Serial);

  // Set callback before begin
  PDM.onReceive(onPDMdata);
  
  // Set gain (0 to 20, default is 20)
  PDM.setGain(20);

  if (!PDM.begin(1, SAMPLE_RATE)) {
    Serial.println("Failed to start PDM!");
    while (1);
  }
  
  Serial.println("Board ready. Send 'r' to start 1-second recording...");
}

void loop() {
  // Check for start command
  if (!isRecording) {
    if (Serial.available() > 0) {
      char c = Serial.read();
      if (c == 'r') {
        samplesRead = 0;
        isRecording = true;
        Serial.println("--- RECORDING ---");
      }
    }
  }

  // Check if recording is finished
  if (isRecording && samplesRead >= NUM_SAMPLES) {
    isRecording = false;
    
    Serial.println("--- DATA START ---");
    for (int i = 0; i < NUM_SAMPLES; i++) {
      Serial.println(audioBuffer[i]);
    }
    Serial.println("--- DATA END ---");
    Serial.println("Send 'r' to start 1-second recording...");
  }
}

void onPDMdata() {
  // Query the number of bytes available
  int bytesAvailable = PDM.available();

  if (isRecording && samplesRead < NUM_SAMPLES) {
    // Read into a temporary buffer
    short tempBuffer[bytesAvailable / 2];
    int samplesAvailable = PDM.read(tempBuffer, bytesAvailable) / 2;

    // Copy to our main buffer until full
    for (int i = 0; i < samplesAvailable; i++) {
      if (samplesRead < NUM_SAMPLES) {
        audioBuffer[samplesRead] = tempBuffer[i];
        samplesRead++;
      }
    }
  } else {
    // Clear the PDM internal buffer if we aren't recording
    // This is critical to prevent old data from being read later
    short garbage[bytesAvailable / 2];
    PDM.read(garbage, bytesAvailable);
  }
}
