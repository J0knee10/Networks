package com.example.iot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var tflite: Interpreter? = null
    private lateinit var tts: TextToSpeech

    // UI Elements
    private lateinit var statusText: TextView
    private lateinit var accelValues: TextView
    private lateinit var micIndicator: TextView
    private lateinit var editServerIp: EditText
    private lateinit var feedbackLayout: LinearLayout
    private lateinit var btnCancel: Button
    private lateinit var btnCorrect: Button
    private lateinit var btnFalseAlarm: Button

    // State Machine
    enum class State { IDLE, VERIFYING, LISTENING, ALARM }
    private var currentState = State.IDLE
    private var lastAudioData: ShortArray? = null

    // Constants
    private val IMPACT_THRESHOLD = 40.0f // ~4G in m/s^2
    private val SAMPLING_RATE = 16000
    private val AUDIO_DURATION_SEC = 1
    private val SAMPLES_PER_GESTURE = 120
    private val NUM_CHANNELS = 6

    // Sensor Buffers
    private var currentAccel = FloatArray(3)
    private var currentGyro = FloatArray(3)
    private val sensorBuffer = LinkedList<FloatArray>() // Store 6-axis samples

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind UI
        statusText = findViewById(R.id.status_text)
        accelValues = findViewById(R.id.accel_values)
        micIndicator = findViewById(R.id.mic_indicator)
        editServerIp = findViewById(R.id.edit_server_ip)
        feedbackLayout = findViewById(R.id.feedback_layout)
        btnCancel = findViewById(R.id.btn_cancel)
        btnCorrect = findViewById(R.id.btn_correct)
        btnFalseAlarm = findViewById(R.id.btn_false_alarm)

        // Initialize Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Initialize TTS
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) tts.language = Locale.UK
        }

        // Load TFLite Model
        try {
            tflite = Interpreter(loadModelFile("fall_model.tflite"))
        } catch (e: Exception) {
            statusText.text = "ERR: NO MODEL"
        }

        btnCancel.setOnClickListener { resetToIdle() }
        btnCorrect.setOnClickListener { sendFeedbackToCloud("HELP") }
        btnFalseAlarm.setOnClickListener { sendFeedbackToCloud("BACKGROUND") }
        
        requestPermissions()
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            currentAccel[0] = event.values[0]
            currentAccel[1] = event.values[1]
            currentAccel[2] = event.values[2]
            
            val mag = sqrt(currentAccel[0].pow(2) + currentAccel[1].pow(2) + currentAccel[2].pow(2))
            accelValues.text = String.format("X: %.2f | Y: %.2f | Z: %.2f", currentAccel[0], currentAccel[1], currentAccel[2])

            // Capture 6-axis snapshot
            val snapshot = floatArrayOf(
                currentAccel[0], currentAccel[1], currentAccel[2],
                currentGyro[0], currentGyro[1], currentGyro[2]
            )
            sensorBuffer.add(snapshot)
            if (sensorBuffer.size > SAMPLES_PER_GESTURE) sensorBuffer.removeFirst()

            if (currentState == State.IDLE && mag > IMPACT_THRESHOLD) {
                verifyFallLocally()
            }
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            currentGyro[0] = event.values[0]
            currentGyro[1] = event.values[1]
            currentGyro[2] = event.values[2]
        }
    }

    private fun Float.pow(n: Int) = Math.pow(this.toDouble(), n.toDouble()).toFloat()

    private fun verifyFallLocally() {
        if (sensorBuffer.size < SAMPLES_PER_GESTURE) return

        currentState = State.VERIFYING
        statusText.text = "VERIFYING..."
        statusText.setTextColor(resources.getColor(android.R.color.holo_orange_dark))

        // Prepare TFLite Input: [1, 120, 6, 1]
        val inputBuffer = Array(1) { Array(SAMPLES_PER_GESTURE) { Array(NUM_CHANNELS) { FloatArray(1) } } }
        
        for (i in 0 until SAMPLES_PER_GESTURE) {
            val sample = sensorBuffer[i]
            // Normalization per Notebook Option B
            inputBuffer[0][i][0][0] = ((sample[0] / 9.81f) + 3.0f) / 6.0f // Ax
            inputBuffer[0][i][1][0] = ((sample[1] / 9.81f) + 3.0f) / 6.0f // Ay
            inputBuffer[0][i][2][0] = ((sample[2] / 9.81f) + 3.0f) / 6.0f // Az
            inputBuffer[0][i][3][0] = (Math.toDegrees(sample[3].toDouble()).toFloat() + 400.0f) / 800.0f // Gx
            inputBuffer[0][i][4][0] = (Math.toDegrees(sample[4].toDouble()).toFloat() + 400.0f) / 800.0f // Gy
            inputBuffer[0][i][5][0] = (Math.toDegrees(sample[5].toDouble()).toFloat() + 400.0f) / 800.0f // Gz
        }

        // TFLite input flattened for the cnn reshape (-1, 120, 6, 1)
        // Since Interpreter.run takes generic Object, we can pass the multidimensional array directly
        val output = Array(1) { FloatArray(2) } // [Fall, Normal]
        tflite?.run(inputBuffer, output)

        // Check if Fall (index 0) probability > 0.5
        if (output[0][0] > 0.5f) { 
            startVoiceCapture()
        } else {
            resetToIdle()
        }
    }

    private fun startVoiceCapture() {
        currentState = State.LISTENING
        statusText.text = "LISTENING..."
        statusText.setTextColor(resources.getColor(android.R.color.holo_orange_light)) // Yellow-ish
        micIndicator.visibility = View.VISIBLE

        Thread {
            lastAudioData = recordAudio()
            lastAudioData?.let { sendToCloud(it) }
        }.start()
    }

    private fun sendFeedbackToCloud(label: String) {
        val audio = lastAudioData ?: return
        val serverIp = editServerIp.text.toString()
        val url = "http://$serverIp:5000/update"
        val client = OkHttpClient()
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)

        val json = Gson().toJson(mapOf(
            "deviceId" to deviceId,
            "label" to label,
            "audio" to audio.toList()
        ))
        
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(body).build()

        Thread {
            try {
                client.newCall(request).execute()
                runOnUiThread {
                    Toast.makeText(this, "Feedback sent: $label", Toast.LENGTH_SHORT).show()
                    resetToIdle()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Failed to send feedback", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun recordAudio(): ShortArray {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val buffer = ShortArray(SAMPLING_RATE * AUDIO_DURATION_SEC)
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return ShortArray(0)
        }
        
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        recorder.startRecording()
        recorder.read(buffer, 0, buffer.size)
        recorder.stop()
        recorder.release()
        
        return buffer
    }

    private fun sendToCloud(audio: ShortArray) {
        val serverIp = editServerIp.text.toString()
        val url = "http://$serverIp:5000/infer"
        val client = OkHttpClient()

        // Get a unique ID for this device (Slide 15: Multiple Users)
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)

        val json = Gson().toJson(mapOf(
            "deviceId" to deviceId,
            "audio" to audio.toList()
        ))
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(body).build()

        try {
            val response = client.newCall(request).execute()
            val responseData = response.body?.string()
            val result = Gson().fromJson(responseData, Map::class.java)
            val keyword = result["keyword"] as? String ?: "BACKGROUND"

            runOnUiThread {
                handleCloudVerdict(keyword)
            }
        } catch (e: Exception) {
            runOnUiThread {
                statusText.text = "CLOUD ERROR"
                resetToIdle()
            }
        }
    }

    private fun handleCloudVerdict(keyword: String) {
        micIndicator.visibility = View.INVISIBLE
        
        when (keyword) {
            "HELP", "EMERGENCY" -> {
                currentState = State.ALARM
                statusText.text = "EMERGENCY!"
                statusText.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                feedbackLayout.visibility = View.VISIBLE
                
                // Slide 10: Synthetic Voice
                tts.speak("Emergency detected. Initiating help request.", TextToSpeech.QUEUE_FLUSH, null, null)
            }
            "CANCEL" -> {
                tts.speak("Alarm cancelled. Returning to monitoring mode.", TextToSpeech.QUEUE_FLUSH, null, null)
                Toast.makeText(this, "Voice Cancel Detected", Toast.LENGTH_SHORT).show()
                resetToIdle()
            }
            else -> {
                resetToIdle()
            }
        }
    }

    private fun resetToIdle() {
        currentState = State.IDLE
        statusText.text = "IDLE"
        statusText.setTextColor(resources.getColor(android.R.color.holo_green_dark))
        micIndicator.visibility = View.INVISIBLE
        feedbackLayout.visibility = View.GONE
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET), 1)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
