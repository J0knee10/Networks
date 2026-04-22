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
import android.util.Log
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
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var tflite: Interpreter? = null
    private lateinit var tts: TextToSpeech

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // UI Elements
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var accelValues: TextView
    private lateinit var gyroValues: TextView
    private lateinit var micIndicator: LinearLayout
    private lateinit var editServerIp: EditText
    private lateinit var feedbackLayout: LinearLayout
    private lateinit var btnCancel: Button
    private lateinit var btnConnect: Button
    private lateinit var btnFeedbackHelp: Button
    private lateinit var btnFeedbackCancel: Button
    private lateinit var btnFeedbackNone: Button
    private var originalBtnColor: android.content.res.ColorStateList? = null
    private var originalTextColor: Int = 0

    // State Machine
    enum class State { IDLE, VERIFYING, LISTENING, ALARM }
    private var currentState = State.IDLE
    private var isConnected = false
    private var lastAudioData: MutableList<Short> = mutableListOf()
    private var lastCloudVerdict = ""

    // Constants
    private val IMPACT_THRESHOLD = 40.0f
    private val SAMPLING_RATE = 16000
    private val MAX_LISTENING_CHUNKS = 10 
    private val SAMPLES_PER_GESTURE = 120
    private val NUM_CHANNELS = 6
    private val TARGET_SAMPLE_INTERVAL_MS = 1000L / 120L 

    private var currentAccel = FloatArray(3)
    private var currentGyro = FloatArray(3)
    private val sensorBuffer = mutableListOf<FloatArray>() 
    
    private var lastSampleTime: Long = 0
    private var postImpactSamplesLeft = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        titleText = findViewById(R.id.title_text)
        accelValues = findViewById(R.id.accel_values)
        gyroValues = findViewById(R.id.gyro_values)
        micIndicator = findViewById(R.id.mic_indicator)
        editServerIp = findViewById(R.id.edit_server_ip)
        feedbackLayout = findViewById(R.id.feedback_layout)
        btnCancel = findViewById(R.id.btn_cancel)
        btnFeedbackHelp = findViewById(R.id.btn_feedback_help)
        btnFeedbackCancel = findViewById(R.id.btn_feedback_cancel)
        btnFeedbackNone = findViewById(R.id.btn_feedback_none)
        btnConnect = findViewById(R.id.btn_connect)

        originalBtnColor = btnConnect.backgroundTintList
        originalTextColor = btnConnect.currentTextColor

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        tts = TextToSpeech(this) { if (it != TextToSpeech.ERROR) tts.language = Locale.UK }

        try { tflite = Interpreter(loadModelFile("fall_model.tflite")) } catch (e: Exception) { statusText.text = "ERR: NO MODEL" }

        btnCancel.setOnClickListener { resetToIdle() }
        btnFeedbackHelp.setOnClickListener { sendFeedbackToCloud("HELP") }
        btnFeedbackCancel.setOnClickListener { sendFeedbackToCloud("CANCEL") }
        btnFeedbackNone.setOnClickListener { sendFeedbackToCloud("BACKGROUND") }
        btnConnect.setOnClickListener { if (isConnected) disconnectFromServer() else testServerConnection() }

        titleText.setOnLongClickListener {
            if (isConnected) {
                Toast.makeText(this, "DEMO: Triggering Voice Capture", Toast.LENGTH_SHORT).show()
                startVoiceCapture()
                true
            } else {
                Toast.makeText(this, "Connect to server first!", Toast.LENGTH_SHORT).show()
                false
            }
        }
        
        requestPermissions()
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fd = assets.openFd(modelName)
        return FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            currentAccel[0] = event.values[0]; currentAccel[1] = event.values[1]; currentAccel[2] = event.values[2]
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            currentGyro[0] = event.values[0]; currentGyro[1] = event.values[1]; currentGyro[2] = event.values[2]
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSampleTime >= TARGET_SAMPLE_INTERVAL_MS) {
            lastSampleTime = currentTime
            sensorBuffer.add(floatArrayOf(currentAccel[0], currentAccel[1], currentAccel[2], currentGyro[0], currentGyro[1], currentGyro[2]))
            if (sensorBuffer.size > SAMPLES_PER_GESTURE) sensorBuffer.removeAt(0)

            val mag = sqrt(currentAccel[0]*currentAccel[0] + currentAccel[1]*currentAccel[1] + currentAccel[2]*currentAccel[2])
            accelValues.text = String.format("X:%5.2f | Y:%5.2f | Z:%5.2f", currentAccel[0], currentAccel[1], currentAccel[2])
            gyroValues.text = String.format("X:%5.2f | Y:%5.2f | Z:%5.2f", currentGyro[0], currentGyro[1], currentGyro[2])

            if (currentState == State.IDLE && mag > IMPACT_THRESHOLD) {
                currentState = State.VERIFYING
                postImpactSamplesLeft = 40
                statusText.text = "IMPACT!..."
                statusText.setTextColor(resources.getColor(android.R.color.holo_orange_dark))
            } 
            else if (currentState == State.VERIFYING && postImpactSamplesLeft > 0) {
                if (--postImpactSamplesLeft == 0) runInference()
            }
        }
    }

    private fun runInference() {
        if (sensorBuffer.size < SAMPLES_PER_GESTURE) { resetToIdle(); return }
        if (!isConnected) {
            runOnUiThread { Toast.makeText(this, "Please connect to server first!", Toast.LENGTH_LONG).show() }
            resetToIdle()
            return
        }
        statusText.text = "ANALYZING..."
        statusText.setTextColor(resources.getColor(android.R.color.holo_orange_light))
        val inputBuffer = Array(1) { Array(SAMPLES_PER_GESTURE) { Array(NUM_CHANNELS) { FloatArray(1) } } }
        for (i in 0 until SAMPLES_PER_GESTURE) {
            val s = sensorBuffer[i]
            inputBuffer[0][i][0][0] = ((s[0] / 9.81f) + 3.0f) / 6.0f 
            inputBuffer[0][i][1][0] = ((s[1] / 9.81f) + 3.0f) / 6.0f 
            inputBuffer[0][i][2][0] = ((s[2] / 9.81f) + 3.0f) / 6.0f 
            inputBuffer[0][i][3][0] = (Math.toDegrees(s[3].toDouble()).toFloat() + 400.0f) / 800.0f 
            inputBuffer[0][i][4][0] = (Math.toDegrees(s[4].toDouble()).toFloat() + 400.0f) / 800.0f 
            inputBuffer[0][i][5][0] = (Math.toDegrees(s[5].toDouble()).toFloat() + 400.0f) / 800.0f 
        }
        val output = Array(1) { FloatArray(2) }
        try { 
            tflite?.run(inputBuffer, output)
            if (output[0][0] > 0.5f) {
                startVoiceCapture() 
            } else {
                runOnUiThread { 
                    Toast.makeText(this, "Impact detected, but not a fall.", Toast.LENGTH_SHORT).show()
                    statusText.text = "FALSE ALARM"
                    statusText.setTextColor(resources.getColor(android.R.color.darker_gray))
                }
                statusText.postDelayed({ if (currentState == State.IDLE) resetToIdle() }, 2000)
                resetToIdle()
            }
        } catch (e: Exception) { resetToIdle() }
    }

    private fun startVoiceCapture() {
        currentState = State.LISTENING
        statusText.text = "LISTENING..."
        statusText.setTextColor(resources.getColor(android.R.color.holo_orange_light))
        micIndicator.visibility = View.VISIBLE
        lastAudioData.clear()
        Thread {
            Thread.sleep(500)
            for (i in 0 until MAX_LISTENING_CHUNKS) {
                if (currentState != State.LISTENING) break
                val chunk = record1Second()
                if (chunk.isNotEmpty()) {
                    lastAudioData.addAll(chunk.toList())
                    val verdict = sendChunkToCloud(chunk)
                    if (verdict == "ERROR") {
                        runOnUiThread { 
                            Toast.makeText(this@MainActivity, "Server connection lost!", Toast.LENGTH_SHORT).show()
                            resetToIdle() 
                        }
                        return@Thread
                    }
                    if (verdict == "HELP" || verdict == "CANCEL") {
                        runOnUiThread { handleCloudVerdict(verdict) }; return@Thread
                    }
                }
            }
            runOnUiThread { resetToIdle() }
        }.start()
    }

    private fun record1Second(): ShortArray {
        val buffer = ShortArray(SAMPLING_RATE)
        val bufSize = AudioRecord.getMinBufferSize(SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return ShortArray(0)
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
        if (recorder.state != AudioRecord.STATE_INITIALIZED) return ShortArray(0)
        recorder.startRecording()
        var read = 0
        while (read < SAMPLING_RATE) { val r = recorder.read(buffer, read, SAMPLING_RATE - read); if (r < 0) break; read += r }
        recorder.stop(); recorder.release(); return buffer
    }

    private fun getBaseUrl(): String {
        var input = editServerIp.text.toString().trim().replace("http://", "").replace("https://", "").removeSuffix("/")
        return if (!input.contains(":")) "http://$input:5000" else "http://$input"
    }

    private fun sendChunkToCloud(audio: ShortArray): String {
        val url = getBaseUrl() + "/infer"
        val json = Gson().toJson(mapOf("deviceId" to android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID), "audio" to audio.toList()))
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        return try {
            val response = client.newCall(Request.Builder().url(url).post(body).build()).execute()
            if (!response.isSuccessful) return "ERROR"
            (Gson().fromJson(response.body?.string(), Map::class.java))["keyword"] as? String ?: "BACKGROUND"
        } catch (e: Exception) { "ERROR" }
    }

    private fun handleCloudVerdict(keyword: String) {
        micIndicator.visibility = View.INVISIBLE
        lastCloudVerdict = keyword
        
        val red = resources.getColor(android.R.color.holo_red_light)
        val orange = resources.getColor(android.R.color.holo_orange_light)
        val grey = resources.getColor(android.R.color.darker_gray)
        val green = resources.getColor(android.R.color.holo_green_dark)
        
        feedbackLayout.visibility = View.VISIBLE
        btnFeedbackHelp.setBackgroundColor(red)
        btnFeedbackCancel.setBackgroundColor(orange)
        btnFeedbackNone.setBackgroundColor(grey)
        
        when (keyword) {
            "HELP", "EMERGENCY" -> {
                currentState = State.ALARM
                statusText.text = "EMERGENCY!"
                statusText.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                btnFeedbackHelp.setBackgroundColor(green)
                tts.speak("Emergency detected.", TextToSpeech.QUEUE_FLUSH, null, null)
            }
            "CANCEL" -> {
                currentState = State.ALARM
                statusText.text = "CANCELLED?"
                statusText.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
                btnFeedbackCancel.setBackgroundColor(green)
                tts.speak("Alarm cancelled. Was this correct?", TextToSpeech.QUEUE_FLUSH, null, null)
            }
            else -> resetToIdle()
        }
    }

    private fun sendFeedbackToCloud(label: String) {
        if (lastAudioData.isEmpty()) return
        val url = getBaseUrl() + "/update"
        Thread {
            try {
                val json = Gson().toJson(mapOf("deviceId" to android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID), "label" to label, "audio" to lastAudioData))
                client.newCall(Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaTypeOrNull())).build()).execute()
                runOnUiThread { Toast.makeText(this, "Feedback sent: $label", Toast.LENGTH_SHORT).show(); resetToIdle() }
            } catch (e: Exception) { runOnUiThread { Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show() } }
        }.start()
    }

    private fun resetToIdle() {
        currentState = State.IDLE
        statusText.text = "IDLE"
        statusText.setTextColor(resources.getColor(android.R.color.holo_green_dark))
        micIndicator.visibility = View.GONE
        feedbackLayout.visibility = View.GONE
        lastCloudVerdict = ""
    }

    private fun updateConnectButtonUI() {
        if (isConnected) {
            btnConnect.text = "DISCONNECT"
            btnConnect.setBackgroundColor(resources.getColor(android.R.color.white))
            btnConnect.setTextColor(resources.getColor(android.R.color.holo_green_dark))
        } else {
            btnConnect.text = "CONNECT"
            btnConnect.backgroundTintList = originalBtnColor
            btnConnect.setTextColor(originalTextColor)
        }
    }

    private fun handleConnectionFailure() {
        btnConnect.backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(android.R.color.holo_red_light))
        btnConnect.text = "FAILED"
        btnConnect.postDelayed({ isConnected = false; updateConnectButtonUI() }, 1000)
    }

    private fun testServerConnection() {
        val url = getBaseUrl() + "/connect"
        statusText.text = "CONNECTING..."
        btnConnect.isEnabled = false
        Thread {
            try {
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                val status = (Gson().fromJson(response.body?.string(), Map::class.java))["status"]
                runOnUiThread {
                    btnConnect.isEnabled = true
                    if (status == "Connected") { 
                        Toast.makeText(this, "Link Established!", Toast.LENGTH_SHORT).show()
                        isConnected = true; updateConnectButtonUI(); statusText.text = "IDLE" 
                    }
                    else { handleConnectionFailure(); statusText.text = "IDLE" }
                }
            } catch (e: Exception) { runOnUiThread { btnConnect.isEnabled = true; handleConnectionFailure(); statusText.text = "IDLE" } }
        }.start()
    }

    private fun disconnectFromServer() { isConnected = false; updateConnectButtonUI() }
    private fun requestPermissions() { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET), 1) }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
