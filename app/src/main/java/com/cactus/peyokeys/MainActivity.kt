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
import com.cactus.CactusLM
import com.cactus.CactusModel
import com.cactus.CactusSTT
import com.cactus.VoiceModel
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var stt: CactusSTT
    private lateinit var lm: CactusLM
    private lateinit var recyclerView: RecyclerView
    private lateinit var tabLayout: TabLayout
    private lateinit var voiceModelAdapter: VoiceModelAdapter
    private lateinit var lmModelAdapter: LMModelAdapter
    private var whisperModels: List<VoiceModel> = emptyList()
    private var lmModels: List<CactusModel> = emptyList()

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

        tabLayout = findViewById(R.id.tab_layout)

        // Setup keyboard settings button
        val buttonEnableKeyboard: Button = findViewById(R.id.button_enable_keyboard)
        buttonEnableKeyboard.setOnClickListener {
            openKeyboardSettings()
        }

        stt = CactusSTT()
        lm = CactusLM()

        // Load voice models by default
        loadVoiceModels()

        // Setup tab selection listener
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showVoiceModels()
                    1 -> showLMModels()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Request microphone permission upfront if not already granted
        checkAndRequestMicrophonePermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_REQUEST_MICROPHONE_PERMISSION) {
            checkAndRequestMicrophonePermission()
        }
    }

    private fun checkAndRequestMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Microphone permission already granted")
                // Permission already granted, no need to show anything
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

    private fun showVoiceModels() {
        if (whisperModels.isEmpty()) {
            loadVoiceModels()
        } else {
            recyclerView.adapter = voiceModelAdapter
        }
    }

    private fun showLMModels() {
        if (lmModels.isEmpty()) {
            loadLMModels()
        } else {
            recyclerView.adapter = lmModelAdapter
        }
    }

    private fun loadVoiceModels() {
        lifecycleScope.launch {
            try {
                whisperModels = stt.getVoiceModels()
                val activeModelSlug = VoiceModelPreferences.getActiveModel(this@MainActivity)

                voiceModelAdapter = VoiceModelAdapter(
                    models = whisperModels,
                    selectedModelSlug = activeModelSlug,
                    onDownloadClick = { model, position ->
                        onDownloadVoiceModel(model)
                    },
                    onModelSelected = { model ->
                        onVoiceModelSelected(model)
                    }
                )
                recyclerView.adapter = voiceModelAdapter
                Log.d(TAG, "Loaded ${whisperModels.size} voice models, active: $activeModelSlug")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading voice models", e)
            }
        }
    }

    private fun loadLMModels() {
        lifecycleScope.launch {
            try {
                // Get available LM models from Cactus SDK
                lmModels = lm.getModels()

                val activeLMModelSlug = LMModelPreferences.getActiveModel(this@MainActivity)

                lmModelAdapter = LMModelAdapter(
                    models = lmModels,
                    selectedModelSlug = activeLMModelSlug,
                    onDownloadClick = { model, position ->
                        onDownloadLMModel(model)
                    },
                    onModelSelected = { model ->
                        onLMModelSelected(model)
                    }
                )
                recyclerView.adapter = lmModelAdapter
                Log.d(TAG, "Loaded ${lmModels.size} LM models, active: $activeLMModelSlug")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading LM models", e)
            }
        }
    }

    private fun onVoiceModelSelected(model: VoiceModel) {
        Log.d(TAG, "Voice model selected: ${model.slug}")
        VoiceModelPreferences.setActiveModel(this, model.slug)
    }

    private fun onDownloadVoiceModel(model: VoiceModel) {
        Log.d(TAG, "Download requested for voice model: ${model.slug}")
        lifecycleScope.launch {
            try {
                voiceModelAdapter.setDownloading(model.slug, true)
                stt.download(model = model.slug)
                voiceModelAdapter.updateModelDownloaded(model.slug)
                Log.d(TAG, "Download completed for voice model: ${model.slug}")
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading voice model: ${model.slug}", e)
                voiceModelAdapter.setDownloading(model.slug, false)
            }
        }
    }

    private fun onLMModelSelected(model: CactusModel) {
        Log.d(TAG, "LM model selected: ${model.slug}")
        LMModelPreferences.setActiveModel(this, model.slug)
    }

    private fun onDownloadLMModel(model: CactusModel) {
        Log.d(TAG, "Download requested for LM model: ${model.slug}")
        lifecycleScope.launch {
            try {
                lmModelAdapter.setDownloading(model.slug, true)
                lm.downloadModel(model.slug)
                lmModelAdapter.updateModelDownloaded(model.slug)
                Log.d(TAG, "Download completed for LM model: ${model.slug}")
                Toast.makeText(
                    this@MainActivity,
                    "Model ${model.name} downloaded successfully!",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading LM model: ${model.slug}", e)
                lmModelAdapter.setDownloading(model.slug, false)
                Toast.makeText(
                    this@MainActivity,
                    "Failed to download ${model.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
