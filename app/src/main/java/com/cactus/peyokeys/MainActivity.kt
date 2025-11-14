package com.cactus.peyokeys

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cactus.CactusContextInitializer
import com.cactus.CactusSTT
import com.cactus.VoiceModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var stt: CactusSTT
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VoiceModelAdapter
    private var whisperModels: List<VoiceModel> = emptyList()

    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_REQUEST_MICROPHONE_PERMISSION = "com.cactus.peyokeys.REQUEST_MICROPHONE_PERMISSION"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Microphone permission granted")
            Toast.makeText(this, "Microphone permission granted! You can now use voice input.", Toast.LENGTH_LONG).show()
        } else {
            Log.d(TAG, "Microphone permission denied")
            Toast.makeText(this, "Microphone permission is required for voice input", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CactusContextInitializer.initialize(this)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recycler_view_models)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Setup keyboard settings button
        val buttonEnableKeyboard: Button = findViewById(R.id.button_enable_keyboard)
        buttonEnableKeyboard.setOnClickListener {
            openKeyboardSettings()
        }

        stt = CactusSTT()
        loadVoiceModels()

        // Check if this activity was launched for permission request
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_REQUEST_MICROPHONE_PERMISSION) {
            requestMicrophonePermission()
        }
    }

    private fun requestMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Microphone permission already granted")
                Toast.makeText(this, "Microphone permission already granted", Toast.LENGTH_SHORT).show()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Show an explanation to the user
                Toast.makeText(
                    this,
                    "Microphone permission is needed for voice input on the keyboard",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                // Request the permission
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun openKeyboardSettings() {
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d(TAG, "Opened keyboard settings")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening keyboard settings", e)
        }
    }

    private fun loadVoiceModels() {
        lifecycleScope.launch {
            try {
                whisperModels = stt.getVoiceModels()
                val activeModelSlug = VoiceModelPreferences.getActiveModel(this@MainActivity)

                adapter = VoiceModelAdapter(
                    models = whisperModels,
                    selectedModelSlug = activeModelSlug,
                    onDownloadClick = { model, position ->
                        onDownloadModel(model)
                    },
                    onModelSelected = { model ->
                        onModelSelected(model)
                    }
                )
                recyclerView.adapter = adapter
                Log.d(TAG, "Loaded ${whisperModels.size} voice models, active: $activeModelSlug")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading voice models", e)
            }
        }
    }

    private fun onModelSelected(model: VoiceModel) {
        Log.d(TAG, "Model selected: ${model.slug}")
        VoiceModelPreferences.setActiveModel(this, model.slug)
    }

    private fun onDownloadModel(model: VoiceModel) {
        Log.d(TAG, "Download requested for model: ${model.slug}")
        lifecycleScope.launch {
            try {
                adapter.setDownloading(model.slug, true)
                stt.download(model = model.slug)
                adapter.updateModelDownloaded(model.slug)
                Log.d(TAG, "Download completed for model: ${model.slug}")
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading model: ${model.slug}", e)
                adapter.setDownloading(model.slug, false)
            }
        }
    }
}
