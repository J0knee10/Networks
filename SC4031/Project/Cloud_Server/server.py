import os
import time
import threading
import numpy as np
import librosa
import tensorflow as tf
from flask import Flask, request, jsonify

app = Flask(__name__)

# Parameters (Matching your training exactly)
N_MFCC = 13
N_MELS = 26
SAMPLING_RATE = 16000
FRAME_SIZE = 512
HOP_LENGTH = 256
MIN_FREQ = 50
MAX_FREQ = SAMPLING_RATE / 2
CLASSES = ['BACKGROUND', 'CANCEL', 'HELP']
MODEL_PATH = "emergency_model.h5"

# Stateful buffers for sliding window (Key: deviceId, Value: numpy array)
device_buffers = {}
# Thread safety
model_lock = threading.Lock()

# Load Full Keras Model
if os.path.exists(MODEL_PATH):
    model = tf.keras.models.load_model(MODEL_PATH)
    # Explicitly compile to ensure it's ready for train_on_batch
    model.compile(optimizer='adam', loss='sparse_categorical_crossentropy', metrics=['accuracy'])
    print(f"Loaded and Compiled Cloud Model: {MODEL_PATH}")
else:
    model = None

def extract_features(samples):
    """Convert 1s (16000 samples) to MFCC features (1, 13, 61, 1)"""
    # Peak normalization
    max_val = np.max(np.abs(samples))
    if max_val > 0:
        samples = samples / max_val
        
    mfcc = librosa.feature.mfcc(
        y=samples, sr=SAMPLING_RATE, n_mfcc=N_MFCC, n_mels=N_MELS,
        n_fft=FRAME_SIZE, hop_length=HOP_LENGTH, fmin=MIN_FREQ, fmax=MAX_FREQ,
        window='hann', center=False, dct_type=2, norm='ortho', power=2
    )
    return mfcc[np.newaxis, ..., np.newaxis]

@app.route('/connect', methods=['GET'])
def test_connection():
    return jsonify({"status": "Connected", "message": "Cloud Server is Online"})

@app.route('/infer', methods=['POST'])
def infer():
    try:
        if model is None:
            return jsonify({"error": "Model not loaded"}), 500
            
        device_id = request.json.get('deviceId', 'Unknown')
        new_audio = np.array(request.json.get('audio', []), dtype=np.float32)
        
        if len(new_audio) == 0:
            return jsonify({"error": "No audio data"}), 400

        # 1. Update the rolling buffer for this device
        if device_id not in device_buffers:
            device_buffers[device_id] = new_audio
        else:
            # Append new data and keep only the last 2 seconds for sliding window context
            device_buffers[device_id] = np.append(device_buffers[device_id], new_audio)
            if len(device_buffers[device_id]) > SAMPLING_RATE * 2:
                device_buffers[device_id] = device_buffers[device_id][-SAMPLING_RATE * 2:]

        buffer = device_buffers[device_id]
        
        # 2. Perform Sliding Window Inference (Step = 0.5s)
        # This catches words split between 1s chunks
        best_result = "BACKGROUND"
        max_conf = 0.0
        
        # We slide a 1s window over the buffer
        step = SAMPLING_RATE // 2 # 0.5 second step
        for start in range(0, len(buffer) - SAMPLING_RATE + 1, step):
            window = buffer[start : start + SAMPLING_RATE]
            features = extract_features(window)
            
            with model_lock:
                preds = model.predict(features, verbose=0)
            
            idx = np.argmax(preds[0])
            label = CLASSES[idx]
            conf = float(preds[0][idx])
            
            # If we find a keyword with high confidence, prefer it
            if label != "BACKGROUND" and conf > 0.85:
                # Early return if we are sure
                return jsonify({"keyword": label, "confidence": conf})
            
            if conf > max_conf:
                max_conf = conf
                best_result = label

        return jsonify({"keyword": best_result, "confidence": max_conf})

    except Exception as e:
        print(f"Error: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/update', methods=['POST'])
def update():
    """Retrain model with feedback."""
    import traceback
    try:
        device_id = request.json.get('deviceId', 'Unknown')
        label = request.json.get('label', 'BACKGROUND')
        audio_list = request.json.get('audio', [])
        
        if not audio_list:
            return jsonify({"error": "Empty audio data"}), 400
            
        audio = np.array(audio_list, dtype=np.float32)
        
        # Slicing logic: If the feedback clip is long, use the loudest 1s
        if len(audio) > SAMPLING_RATE:
            max_energy = -1
            best_start = 0
            for start in range(0, len(audio) - SAMPLING_RATE, 512):
                energy = np.sum(audio[start : start + SAMPLING_RATE]**2)
                if energy > max_energy:
                    max_energy = energy
                    best_start = start
            audio = audio[best_start : best_start + SAMPLING_RATE]
        elif len(audio) < SAMPLING_RATE:
            # Pad with zeros if too short
            audio = np.pad(audio, (0, SAMPLING_RATE - len(audio)))

        features = extract_features(audio)
        label_idx = CLASSES.index(label)
        
        with model_lock:
            metrics = model.train_on_batch(features, np.array([label_idx]))
            model.save(MODEL_PATH)
            
        print(f"Model updated successfully for {device_id} with label {label}")
        return jsonify({"status": "Success", "metrics": str(metrics)})
    except Exception as e:
        print("!!! UPDATE ERROR !!!")
        traceback.print_exc() # Prints crash details to your console
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    import socket
    hostname = socket.gethostname()
    # Get all IPv4 addresses associated with the machine
    try:
        ip_addresses = socket.gethostbyname_ex(hostname)[2]
        print("\n" + "="*50)
        print("CLOUD SERVER STARTING")
        print("Available IP Addresses for your Android App:")
        for ip in ip_addresses:
            if not ip.startswith("127."): # Skip loopback
                print(f" -> http://{ip}:5000")
        print("="*50 + "\n")
    except Exception:
        print("Starting server...")
        
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
