from flask import Flask, request, jsonify
import tensorflow as tf
import numpy as np
import librosa
import os

app = Flask(__name__)

# Parameters (Matching your TinyML training)
N_MFCC = 13
SAMPLING_RATE = 16000
FRAME_SIZE = 512
HOP_LENGTH = 256
CLASSES = ['HELP', 'EMERGENCY', 'CANCEL', 'BACKGROUND']

# Load Model
MODEL_PATH = "emergency_model.tflite"
if os.path.exists(MODEL_PATH):
    interpreter = tf.lite.Interpreter(model_path=MODEL_PATH)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
else:
    print(f"Warning: {MODEL_PATH} not found.")

def extract_features(raw_samples):
    # Convert to float and extract MFCC
    samples = np.array(raw_samples, dtype=np.float32)
    mfcc = librosa.feature.mfcc(y=samples, sr=SAMPLING_RATE, 
                                n_mfcc=N_MFCC, n_fft=FRAME_SIZE, 
                                hop_length=HOP_LENGTH)
    # Reshape for CNN: (1, 63, 13, 1)
    return mfcc.T[np.newaxis, ..., np.newaxis]

@app.route('/infer', methods=['POST'])
def infer():
    try:
        data = request.json.get('audio', [])
        if not data:
            return jsonify({"error": "No data"}), 400
            
        features = extract_features(data)
        
        # Run Inference
        interpreter.set_tensor(input_details[0]['index'], features)
        interpreter.invoke()
        output_data = interpreter.get_tensor(output_details[0]['index'])
        
        idx = np.argmax(output_data[0])
        result = CLASSES[idx]
        confidence = float(output_data[0][idx])
        
        print(f"Inference: {result} ({confidence:.2f})")
        return jsonify({"keyword": result, "confidence": confidence})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/update', methods=['POST'])
def update():
    # Advanced Task: Save data for online retraining
    # In a real project, this would trigger a background training job
    return jsonify({"status": "Data received for retraining"})

if __name__ == '__main__':
    # host='0.0.0.0' allows external connections (from Gateway)
    app.run(host='0.0.0.0', port=5000, debug=True)
