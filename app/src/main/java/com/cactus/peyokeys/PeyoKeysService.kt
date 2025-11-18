package com.cactus.peyokeys

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.cactus.CactusContextInitializer
import com.cactus.CactusLM
import com.cactus.CactusSTT
import com.cactus.CactusInitParams
import com.cactus.ChatMessage
import com.cactus.SpeechRecognitionParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PeyoKeysService : InputMethodService() {

    private enum class State {
        IDLE,
        RECORDING_FOR_TRANSCRIBE,
        RECORDING_FOR_DRAFT,
        TRANSCRIBING,
        DRAFTING
    }

    private var currentState = State.IDLE
    private var isShifted = true  // Start with capital letters
    private val letterButtons = mutableMapOf<Char, Button>()
    private lateinit var stt: CactusSTT
    private lateinit var lm: CactusLM
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var shiftButton: ImageButton? = null
    private var isNumbersLayout = false

    // Toolbar button state management
    private var toolbarMicButton: Button? = null
    private var toolbarDraftButton: Button? = null
    private var toolbarStatus: TextView? = null
    private var toolbarMicProgress: ProgressBar? = null
    private var toolbarDraftProgress: ProgressBar? = null

    companion object {
        private const val TAG = "PeyoKeysService"
    }

    override fun onCreate() {
        super.onCreate()
        CactusContextInitializer.initialize(this)
        stt = CactusSTT()
        lm = CactusLM()
        Log.d(TAG, "onCreate() called")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            lm.unload()
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading LM model", e)
        }
        Log.d(TAG, "onDestroy() called")
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView() called")
        val layoutId = if (isNumbersLayout) R.layout.keyboard_numbers else R.layout.keyboard
        val view = layoutInflater.inflate(layoutId, null)
        if (isNumbersLayout) {
            setupNumbersKeyboard(view)
        } else {
            setupKeyboard(view)
        }
        return view
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        checkAndUpdateShiftState()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    private fun setupToolbar(view: View) {
        // Toolbar buttons and status
        toolbarMicButton = view.findViewById<Button>(R.id.toolbar_microphone)
            ?: view.findViewById<Button>(R.id.toolbar_microphone_num)
        toolbarDraftButton = view.findViewById<Button>(R.id.toolbar_draft)
            ?: view.findViewById<Button>(R.id.toolbar_draft_num)
        toolbarStatus = view.findViewById<TextView>(R.id.toolbar_status)
            ?: view.findViewById<TextView>(R.id.toolbar_status_num)
        toolbarMicProgress = view.findViewById<ProgressBar>(R.id.toolbar_microphone_progress)
            ?: view.findViewById<ProgressBar>(R.id.toolbar_microphone_progress_num)
        toolbarDraftProgress = view.findViewById<ProgressBar>(R.id.toolbar_draft_progress)
            ?: view.findViewById<ProgressBar>(R.id.toolbar_draft_progress_num)

        // Hold-to-speak for Transcribe button
        toolbarMicButton?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (currentState == State.IDLE) {
                        startVoiceRecording()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (currentState == State.RECORDING_FOR_TRANSCRIBE) {
                        stopVoiceRecordingAndTranscribe()
                    }
                    true
                }
                else -> false
            }
        }

        // Hold-to-speak for AI Draft button
        toolbarDraftButton?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (currentState == State.IDLE) {
                        startDraftRecording()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (currentState == State.RECORDING_FOR_DRAFT) {
                        stopDraftRecordingAndGenerate()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupKeyboard(view: View) {
        setupToolbar(view)

        // Letter keys - Row 1
        setupLetterKey(view, R.id.key_q, "Q")
        setupLetterKey(view, R.id.key_w, "W")
        setupLetterKey(view, R.id.key_e, "E")
        setupLetterKey(view, R.id.key_r, "R")
        setupLetterKey(view, R.id.key_t, "T")
        setupLetterKey(view, R.id.key_y, "Y")
        setupLetterKey(view, R.id.key_u, "U")
        setupLetterKey(view, R.id.key_i, "I")
        setupLetterKey(view, R.id.key_o, "O")
        setupLetterKey(view, R.id.key_p, "P")

        // Letter keys - Row 2
        setupLetterKey(view, R.id.key_a, "A")
        setupLetterKey(view, R.id.key_s, "S")
        setupLetterKey(view, R.id.key_d, "D")
        setupLetterKey(view, R.id.key_f, "F")
        setupLetterKey(view, R.id.key_g, "G")
        setupLetterKey(view, R.id.key_h, "H")
        setupLetterKey(view, R.id.key_j, "J")
        setupLetterKey(view, R.id.key_k, "K")
        setupLetterKey(view, R.id.key_l, "L")

        // Letter keys - Row 3
        setupLetterKey(view, R.id.key_z, "Z")
        setupLetterKey(view, R.id.key_x, "X")
        setupLetterKey(view, R.id.key_c, "C")
        setupLetterKey(view, R.id.key_v, "V")
        setupLetterKey(view, R.id.key_b, "B")
        setupLetterKey(view, R.id.key_n, "N")
        setupLetterKey(view, R.id.key_m, "M")

        // Shift key
        shiftButton = view.findViewById<ImageButton>(R.id.key_shift)
        shiftButton?.setOnClickListener {
            isShifted = !isShifted
            updateLetterCase()
            updateShiftIcon()
        }

        // Backspace key
        view.findViewById<ImageButton>(R.id.key_backspace).setOnClickListener {
            currentInputConnection?.deleteSurroundingText(1, 0)
            checkAndUpdateShiftState()
        }

        // Numbers/symbols key
        view.findViewById<Button>(R.id.key_numbers).setOnClickListener {
            isNumbersLayout = true
            setInputView(onCreateInputView())
        }

        // Comma key
        view.findViewById<Button>(R.id.key_comma).setOnClickListener {
            currentInputConnection?.commitText(",", 1)
            checkAndUpdateShiftState()
        }

        // Space key
        view.findViewById<Button>(R.id.key_space).setOnClickListener {
            currentInputConnection?.commitText(" ", 1)
            checkAndUpdateShiftState()
        }

        // Period key
        view.findViewById<Button>(R.id.key_period).setOnClickListener {
            currentInputConnection?.commitText(".", 1)
            checkAndUpdateShiftState()
        }

        // Return key
        view.findViewById<ImageButton>(R.id.key_return).setOnClickListener {
            handleReturnKey()
        }

        // Initialize letter case and shift icon
        updateLetterCase()
        updateShiftIcon()
    }

    private fun setupLetterKey(view: View, buttonId: Int, letter: String) {
        val button = view.findViewById<Button>(buttonId)
        letterButtons[letter[0]] = button

        button.setOnClickListener {
            val textToInsert = if (isShifted) letter.uppercase() else letter.lowercase()
            currentInputConnection?.commitText(textToInsert, 1)
            if (isShifted) {
                isShifted = false
                updateLetterCase()
                updateShiftIcon()
            }
        }
    }

    private fun updateLetterCase() {
        letterButtons.forEach { (char, button) ->
            button.text = if (isShifted) char.uppercase() else char.lowercase()
        }
    }

    private fun updateShiftIcon() {
        shiftButton?.setImageResource(
            if (isShifted) R.drawable.ic_shift_locked
            else R.drawable.ic_shift
        )
    }

    private fun checkAndUpdateShiftState() {
        val ic = currentInputConnection ?: return

        val textBefore = ic.getTextBeforeCursor(3, 0)?.toString() ?: ""

        val shouldCapitalize = textBefore.isEmpty() ||
                textBefore.endsWith(". ") ||
                textBefore.endsWith("? ") ||
                textBefore.endsWith("! ") ||
                textBefore.endsWith("\n")

        if (shouldCapitalize != isShifted) {
            isShifted = shouldCapitalize
            updateLetterCase()
            updateShiftIcon()
        }
    }

    private fun startVoiceRecording() {
        // Check for microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Microphone permission not granted")
            Toast.makeText(this, "Grant mic permission in app", Toast.LENGTH_SHORT).show()
            requestMicrophonePermission()
            return
        }

        val activeModel = VoiceModelPreferences.getActiveModel(this)
        Log.d(TAG, "Starting voice recording, active model: $activeModel")

        // Set recording state immediately on main thread (synchronously)
        currentState = State.RECORDING_FOR_TRANSCRIBE
        toolbarMicButton?.isEnabled = true
        toolbarMicButton?.alpha = 0.7f
        toolbarMicButton?.text = "Listening..."
        toolbarStatus?.text = "Recording..."
        toolbarMicProgress?.visibility = View.GONE

        serviceScope.launch(Dispatchers.IO) {
            try {
                if (!stt.isReady()) {
                    stt.init(activeModel)
                }

                // Start recording with long duration, result will come here (blocking on IO thread)
                // When stop() is called, this will return with the transcription result
                val result = stt.transcribe(
                    params = SpeechRecognitionParams(maxDuration = Long.MAX_VALUE)
                )

                // transcribe() has completed, update UI and insert text on main thread
                launch(Dispatchers.Main) {
                    if (result != null && result.text?.isNotBlank() == true) {
                        currentInputConnection?.commitText(result.text, 1)
                        Log.d(TAG, "Transcribed text: ${result.text}")
                    } else {
                        Log.d(TAG, "No speech detected or empty result")
                        Toast.makeText(this@PeyoKeysService, "No speech detected", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during voice recording", e)
                launch(Dispatchers.Main) {
                    Toast.makeText(this@PeyoKeysService, "Transcription failed", Toast.LENGTH_SHORT).show()
                }
            } finally {
                // Reset recording and transcribing state on main thread
                launch(Dispatchers.Main) {
                    currentState = State.IDLE
                    toolbarMicButton?.isEnabled = true
                    toolbarMicButton?.alpha = 1.0f
                    toolbarMicButton?.text = "Transcribe"
                    toolbarMicProgress?.visibility = View.GONE
                    toolbarStatus?.text = "Hold the buttons to start"
                }
            }
        }
    }

    private fun stopVoiceRecordingAndTranscribe() {
        // Update status IMMEDIATELY on main thread (synchronously)
        currentState = State.TRANSCRIBING
        toolbarMicButton?.isEnabled = false
        toolbarMicButton?.alpha = 0.5f
        toolbarMicButton?.text = ""
        toolbarMicProgress?.visibility = View.VISIBLE
        toolbarStatus?.text = "Transcribing..."

        // Stop the recording in the background
        serviceScope.launch(Dispatchers.IO) {
            try {
                stt.stop()
                Log.d(TAG, "Voice recording stopped, transcription will begin")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping voice recording", e)
                launch(Dispatchers.Main) {
                    currentState = State.IDLE
                    toolbarMicButton?.isEnabled = true
                    toolbarMicButton?.alpha = 1.0f
                    toolbarMicButton?.text = "Transcribe"
                    toolbarMicProgress?.visibility = View.GONE
                    toolbarStatus?.text = "Hold the buttons to start"
                }
            }
        }
    }

    private fun requestMicrophonePermission() {
        try {
            // Launch MainActivity to request permission
            val intent = Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_REQUEST_MICROPHONE_PERMISSION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting microphone permission", e)
        }
    }

    private fun startDraftRecording() {
        // Check for microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Microphone permission not granted for draft")
            Toast.makeText(this, "Grant mic permission in app", Toast.LENGTH_SHORT).show()
            requestMicrophonePermission()
            return
        }

        val activeVoiceModel = VoiceModelPreferences.getActiveModel(this) ?: "whisper-base"
        val activeLMModel = LMModelPreferences.getActiveModel(this)
        Log.d(TAG, "Starting draft recording, active model: $activeVoiceModel")

        // Set recording state immediately on main thread (synchronously)
        currentState = State.RECORDING_FOR_DRAFT
        toolbarDraftButton?.isEnabled = true
        toolbarDraftButton?.alpha = 0.7f
        toolbarDraftButton?.text = "Listening..."
        toolbarStatus?.text = "Recording..."
        toolbarDraftProgress?.visibility = View.GONE

        serviceScope.launch(Dispatchers.IO) {
            try {
                // Initialize STT if needed
                if (!stt.isReady()) {
                    stt.init(activeVoiceModel)
                }

                // Start recording with long duration, result will come here (blocking on IO thread)
                // When stop() is called, this will return with the transcription result
                val transcriptionResult = stt.transcribe(
                    params = SpeechRecognitionParams(maxDuration = Long.MAX_VALUE)
                )

                // transcribe() has completed, check result
                if (transcriptionResult == null || transcriptionResult.text.isNullOrBlank()) {
                    Log.d(TAG, "No speech detected for draft instruction")
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@PeyoKeysService, "No speech detected", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val instruction = transcriptionResult.text
                Log.d(TAG, "Draft instruction transcribed: $instruction")

                // Show processing state on main thread
                launch(Dispatchers.Main) {
                    currentState = State.DRAFTING
                    toolbarDraftButton?.isEnabled = false
                    toolbarDraftButton?.alpha = 0.5f
                    toolbarDraftButton?.text = ""
                    toolbarDraftProgress?.visibility = View.VISIBLE
                    toolbarStatus?.text = "AI is drafting..."
                }

                // Initialize LM model
                try {
                    lm.initializeModel(
                        CactusInitParams(
                            model = activeLMModel,
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing LM model: $activeLMModel", e)
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@PeyoKeysService, "Model not downloaded", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Create prompt for LM
                val systemPrompt = "You are a helpful writing assistant. Generate the requested content concisely. Only provide the content itself, without any preamble or explanation."

                // Generate completion
                val result = lm.generateCompletion(
                    messages = listOf(
                        ChatMessage(content = systemPrompt, role = "system"),
                        ChatMessage(content = instruction!!, role = "user")
                    )
                )

                // Insert result on main thread
                launch(Dispatchers.Main) {
                    if (result != null && result.success && result.response?.isNotBlank() == true) {
                        currentInputConnection?.commitText(result.response, 1)
                        Log.d(TAG, "Draft content inserted: ${result.response?.take(50)}... (${result.tokensPerSecond} tokens/s)")
                    } else {
                        Log.d(TAG, "LM returned empty or failed response")
                        Toast.makeText(this@PeyoKeysService, "Generation failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during draft recording/generation", e)
                launch(Dispatchers.Main) {
                    Toast.makeText(this@PeyoKeysService, "Draft failed", Toast.LENGTH_SHORT).show()
                }
            } finally {
                // Reset drafting state on main thread
                launch(Dispatchers.Main) {
                    currentState = State.IDLE
                    toolbarDraftButton?.isEnabled = true
                    toolbarDraftButton?.alpha = 1.0f
                    toolbarDraftButton?.text = "AI Draft"
                    toolbarDraftProgress?.visibility = View.GONE
                    toolbarStatus?.text = "Hold the buttons to start"
                }
            }
        }
    }

    private fun stopDraftRecordingAndGenerate() {
        // Update status IMMEDIATELY on main thread (synchronously)
        currentState = State.TRANSCRIBING
        toolbarDraftButton?.isEnabled = false
        toolbarDraftButton?.alpha = 0.5f
        toolbarDraftButton?.text = ""
        toolbarDraftProgress?.visibility = View.VISIBLE
        toolbarStatus?.text = "Transcribing..."

        // Stop the recording in the background
        serviceScope.launch(Dispatchers.IO) {
            try {
                stt.stop()
                Log.d(TAG, "Draft recording stopped, transcription will begin")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping draft recording", e)
                launch(Dispatchers.Main) {
                    currentState = State.IDLE
                    toolbarDraftButton?.isEnabled = true
                    toolbarDraftButton?.alpha = 1.0f
                    toolbarDraftButton?.text = "AI Draft"
                    toolbarDraftProgress?.visibility = View.GONE
                    toolbarStatus?.text = "Hold the buttons to start"
                }
            }
        }
    }

    private fun setupNumbersKeyboard(view: View) {
        setupToolbar(view)

        // Number keys
        setupSimpleKey(view, R.id.key_1, "1")
        setupSimpleKey(view, R.id.key_2, "2")
        setupSimpleKey(view, R.id.key_3, "3")
        setupSimpleKey(view, R.id.key_4, "4")
        setupSimpleKey(view, R.id.key_5, "5")
        setupSimpleKey(view, R.id.key_6, "6")
        setupSimpleKey(view, R.id.key_7, "7")
        setupSimpleKey(view, R.id.key_8, "8")
        setupSimpleKey(view, R.id.key_9, "9")
        setupSimpleKey(view, R.id.key_0, "0")

        // Symbol keys - Row 2
        setupSimpleKey(view, R.id.key_at, "@")
        setupSimpleKey(view, R.id.key_hash, "#")
        setupSimpleKey(view, R.id.key_dollar, "$")
        setupSimpleKey(view, R.id.key_underscore, "_")
        setupSimpleKey(view, R.id.key_ampersand, "&")
        setupSimpleKey(view, R.id.key_minus, "-")
        setupSimpleKey(view, R.id.key_plus, "+")
        setupSimpleKey(view, R.id.key_lparen, "(")
        setupSimpleKey(view, R.id.key_rparen, ")")
        setupSimpleKey(view, R.id.key_slash, "/")

        // Symbol keys - Row 3
        setupSimpleKey(view, R.id.key_asterisk, "*")
        setupSimpleKey(view, R.id.key_quote, "\"")
        setupSimpleKey(view, R.id.key_apostrophe, "'")
        setupSimpleKey(view, R.id.key_colon, ":")
        setupSimpleKey(view, R.id.key_semicolon, ";")
        setupSimpleKey(view, R.id.key_exclamation, "!")
        setupSimpleKey(view, R.id.key_question, "?")

        // Backspace
        view.findViewById<ImageButton>(R.id.key_backspace_num).setOnClickListener {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        // ABC key to switch back to letters
        view.findViewById<Button>(R.id.key_abc).setOnClickListener {
            isNumbersLayout = false
            setInputView(onCreateInputView())
        }

        // Comma
        view.findViewById<Button>(R.id.key_comma_num).setOnClickListener {
            currentInputConnection?.commitText(",", 1)
        }

        // Space key
        view.findViewById<Button>(R.id.key_space_num).setOnClickListener {
            currentInputConnection?.commitText(" ", 1)
        }

        // Period
        view.findViewById<Button>(R.id.key_period_num).setOnClickListener {
            currentInputConnection?.commitText(".", 1)
        }

        // Return
        view.findViewById<ImageButton>(R.id.key_return_num).setOnClickListener {
            handleReturnKey()
        }
    }

    private fun setupSimpleKey(view: View, buttonId: Int, text: String) {
        view.findViewById<Button>(buttonId).setOnClickListener {
            currentInputConnection?.commitText(text, 1)
        }
    }

    private fun handleReturnKey() {
        val ic = currentInputConnection ?: return
        val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
        val action = imeOptions and EditorInfo.IME_MASK_ACTION

        when (action) {
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_DONE -> {
                // Perform the action for search, go, send, or done
                ic.performEditorAction(action)
                Log.d(TAG, "Performed editor action: $action")
            }
            else -> {
                // Default: insert newline
                ic.commitText("\n", 1)
                isShifted = true
                updateLetterCase()
            }
        }
    }
}
