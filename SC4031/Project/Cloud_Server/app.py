import os
import time
import threading
import numpy as np
import librosa
import tensorflow as tf
from flask import Flask, request, jsonify

app = Flask(__name__)

# Parameters (Matching your train_emergency_model.ipynb exactly)
N_MFCC = 13
N_MELS = 26
SAMPLING_RATE = 16000
FRAME_SIZE = 512
HOP_LENGTH = 256
MIN_FREQ = 50
MAX_FREQ = SAMPLING_RATE / 2
# Correct mapping from LabelEncoder in Notebook: {'BACKGROUND': 0, 'CANCEL': 1, 'HELP': 2}
CLASSES = ['BACKGROUND', 'CANCEL', 'HELP']
MODEL_PATH = "emergency_model.h5"

# Thread safety for model updates and inference
model_lock = threading.Lock()

# Load Full Keras Model
if os.path.exists(MODEL_PATH):
    model = tf.keras.models.load_model(MODEL_PATH)
    print(f"Loaded full Cloud Model: {MODEL_PATH}")
else:
    model = None
    print(f"Warning: {MODEL_PATH} not found. Server starting without model.")

def extract_features(raw_samples):
    """Convert raw PCM samples to MFCC features with shape (1, 13, 61, 1)"""
    samples = np.array(raw_samples, dtype=np.float32)
    # Peak normalization
    max_val = np.max(np.abs(samples))
    if max_val > 0:
        samples = samples / max_val
        
    mfcc = librosa.feature.mfcc(
        y=samples, sr=SAMPLING_RATE, n_mfcc=N_MFCC, n_mels=N_MELS,
        n_fft=FRAME_SIZE, hop_length=HOP_LENGTH, fmin=MIN_FREQ, fmax=MAX_FREQ,
        window='hann', center=False, dct_type=2, norm='ortho', power=2
    )
    # Shape for CNN: [batch, n_mfcc, n_frames, channel]
    return mfcc[np.newaxis, ..., np.newaxis]

@app.route('/infer', methods=['POST'])
def infer():
    try:
        if model is None:
            return jsonify({"error": "Model not loaded on server"}), 500
            
        device_id = request.json.get('deviceId', 'Unknown_Device')
        data = request.json.get('audio', [])
        
        if not data:
            return jsonify({"error": "No audio data"}), 400
            
        features = extract_features(data)
        
        # Ensure thread-safe inference
        with model_lock:
            predictions = model.predict(features)
        
        idx = np.argmax(predictions[0])
        result = CLASSES[idx]
        confidence = float(predictions[0][idx])
        
        print(f"[{device_id}] Inference Result: {result} ({confidence:.2f})")
        return jsonify({"keyword": result, "confidence": confidence})
    except Exception as e:
        print(f"Inference error: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/update', methods=['POST'])
def update():
    """Online model updating: Receives audio + label and retrains the model at runtime."""
    try:
        if model is None:
            return jsonify({"error": "Model not loaded"}), 500

        device_id = request.json.get('deviceId', 'Unknown_Device')
        label = request.json.get('label', 'BACKGROUND')
        audio = request.json.get('audio', [])
        
        if not audio:
            return jsonify({"error": "No audio data"}), 400
        if label not in CLASSES:
            return jsonify({"error": f"Invalid label. Must be one of {CLASSES}"}), 400

        print(f"[{device_id}] Online Update Requested: Labeling as {label}...")

        # 1. Prepare features and target
        features = extract_features(audio)
        label_idx = CLASSES.index(label)
        y_target = np.array([label_idx])

        # 2. Perform Online Retraining (Fine-tuning)
        with model_lock:
            # train_on_batch performs a single gradient update on the provided data
            metrics = model.train_on_batch(features, y_target)
            # Save the updated model weights
            model.save(MODEL_PATH)
            
        print(f"[{device_id}] Model retrained successfully. New metrics: {metrics}")

        # 3. Archive data for record keeping
        os.makedirs(f"retrain_data/{label}", exist_ok=True)
        filename = f"retrain_data/{label}/{device_id}_{int(time.time())}.txt"
        with open(filename, "w") as f:
            f.write(",".join(map(str, audio)))
            
        return jsonify({
            "status": "Success", 
            "message": "Model updated and sample archived",
            "metrics": str(metrics)
        })
    except Exception as e:
        print(f"Update error: {e}")
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    # Use 0.0.0.0 to allow connections from the Android device on the same network
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
