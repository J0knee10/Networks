import serial
import requests
import json
import time

# --- CONFIGURATION ---
SERIAL_PORT = 'COM3'  # Update this to your Arduino port
BAUD_RATE = 115200
CLOUD_URL = "http://localhost:5000/infer" # Update if server is on a different machine

def start_bridge():
    try:
        ser = serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=2)
        print(f"Connected to Arduino on {SERIAL_PORT}")
    except Exception as e:
        print(f"Error connecting to Serial: {e}")
        return

    while True:
        if ser.in_waiting > 0:
            line = ser.readline().decode('utf-8').strip()
            
            if line == "CMD:START_STREAMING":
                print("Arduino is ready to stream audio...")
                
            elif line == "--- AUDIO_DATA_START ---":
                print("Receiving audio samples...")
                audio_samples = []
                while True:
                    val = ser.readline().decode('utf-8').strip()
                    if val == "--- AUDIO_DATA_END ---":
                        break
                    if val:
                        try:
                            audio_samples.append(float(val))
                        except ValueError:
                            continue
                
                print(f"Captured {len(audio_samples)} samples. Sending to Cloud...")
                
                # Send to Flask Server
                try:
                    response = requests.post(CLOUD_URL, json={"audio": audio_samples})
                    result = response.json()
                    keyword = result.get('keyword', 'BACKGROUND')
                    conf = result.get('confidence', 0)
                    
                    print(f"Cloud Result: {keyword} (Conf: {conf:.2f})")
                    
                    # Send result back to Arduino
                    ser.write(f"{keyword}\n".encode())
                except Exception as e:
                    print(f"Cloud Error: {e}")
                    ser.write("BACKGROUND\n".encode())
            
            else:
                if line: print(f"Arduino: {line}")

if __name__ == "__main__":
    start_bridge()
