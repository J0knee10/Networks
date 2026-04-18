"""
serial_data_collector.py
Nano 33 BLE Sense Data Collection Tool

USAGE STEPS:
1. Identify your Arduino COM port (e.g., COM3, COM4) in the Arduino IDE.
2. Upload the corresponding .ino sketch (IMU_Fall_Capture or Audio_Keyword_Capture).
3. Close the Arduino Serial Monitor (only one program can use the port at a time).
4. Run this script from your terminal:

   FOR IMU FALL DATA:
   python serial_data_collector.py --port COM3 --file fall_data.csv --mode imu

   FOR AUDIO KEYWORD DATA:
   python serial_data_collector.py --port COM3 --file help_voice.csv --mode audio

5. In 'audio' mode, the script will wait for you to press ENTER before sending 'r' 
   to the Arduino to start a 1-second recording.
"""

import serial
import argparse
import os

def collect_data(port, baud, filename, mode):
    print(f"Opening port {port} at {baud} baud...")
    ser = serial.Serial(port, baud, timeout=1)
    
    # Clear buffer
    ser.reset_input_buffer()
    
    print(f"Mode: {mode}")
    print(f"Output: {filename}")
    print("Press Ctrl+C to stop recording.")

    with open(filename, "a") as f:
        try:
            while True:
                if mode == "audio":
                    print("\nWaiting for board to be ready... (Press Enter to trigger or Ctrl+C to stop)")
                    # Instead of reset_input_buffer, let's just print what's coming in
                    # until the user presses Enter.
                    
                    print("Board says: ", end="", flush=True)
                    # This is a bit tricky with input() blocking. 
                    # Let's just do a simple reset and send 'r' when they press enter.
                    ser.reset_input_buffer()
                    input()
                    
                    print("Sending 'r' to trigger recording...")
                    ser.write(b'r')
                    ser.flush()
                    
                    samples = []
                    is_collecting = False
                    
                    # Wait for data. We'll give it some time to start.
                    while True:
                        raw_line = ser.readline()
                        line = raw_line.decode('utf-8', errors='replace').strip()
                        
                        if not raw_line:
                            if is_collecting:
                                # We were collecting and it stopped. 
                                # Maybe the transmission is done or interrupted.
                                print("\nTimeout while collecting data. Current count:", len(samples))
                                if len(samples) >= 16000:
                                    print("Count looks good, saving anyway.")
                                    break
                                continue # Keep waiting
                            continue
                            
                        # Print everything so user knows what's happening
                        if not is_collecting:
                            if line:
                                print(f"Board: {line}")
                        
                        if "--- DATA START ---" in line:
                            print("Recording detected. Collecting...")
                            is_collecting = True
                            samples = []
                            continue
                            
                        if "--- DATA END ---" in line:
                            if samples:
                                f.write(",".join(samples) + "\n")
                                print(f"\nFinished sample ({len(samples)} points saved to CSV).")
                            else:
                                print("\nWarning: Received DATA END but no samples were collected.")
                            break
                            
                        if is_collecting:
                            if line: # Capture everything including "0"
                                samples.append(line)
                            # Provide some progress feedback every 500 samples
                            if len(samples) % 1000 == 0 and len(samples) > 0:
                                print(f"Collected {len(samples)} samples...", end="\r", flush=True)
                else:
                    # IMU Mode: Just append every line to the CSV
                    line = ser.readline().decode('utf-8').strip()
                    if line:
                        print(line)
                        f.write(line + "\n")
                        
        except KeyboardInterrupt:
            print("\nStopping collection...")
        finally:
            ser.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Serial Data Collector for Nano 33 BLE Sense")
    parser.add_argument("--port", type=str, required=True, help="COM port (e.g., COM3)")
    parser.add_argument("--baud", type=int, default=115200, help="Baud rate (default: 115200)")
    parser.add_argument("--file", type=str, required=True, help="Filename to save (e.g., fall_data.csv)")
    parser.add_argument("--mode", type=str, choices=["imu", "audio"], default="imu", help="Data type: imu or audio")
    
    args = parser.parse_args()
    collect_data(args.port, args.baud, args.file, args.mode)
