package com.cactus.peyokeys

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.cactus.CactusContextInitializer
import com.cactus.CactusSTT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PeyoKeysService : InputMethodService() {

    private var isShifted = true  // Start with capital letters
    private val letterButtons = mutableMapOf<Char, Button>()
    private lateinit var stt: CactusSTT
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var spaceButton: Button? = null
    private var isTranscribing = false
    private var isNumbersLayout = false

    companion object {
        private const val TAG = "PeyoKeysService"
    }

    override fun onCreate() {
        super.onCreate()
        CactusContextInitializer.initialize(this)
        stt = CactusSTT()
        Log.d(TAG, "onCreate() called")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
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
        Log.d(TAG, "onStartInput() called - inputType: ${attribute?.inputType}")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView() called")
        checkAndUpdateShiftState()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        Log.d(TAG, "onEvaluateFullscreenMode() called")
        return false
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        Log.d(TAG, "onEvaluateInputViewShown() called")
        return true
    }

    private fun setupKeyboard(view: View) {
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
        view.findViewById<Button>(R.id.key_shift).setOnClickListener {
            isShifted = !isShifted
            updateLetterCase()
        }

        // Backspace key
        view.findViewById<Button>(R.id.key_backspace).setOnClickListener {
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
        spaceButton = view.findViewById<Button>(R.id.key_space)
        spaceButton?.setOnClickListener {
            if (!isTranscribing) {
                currentInputConnection?.commitText(" ", 1)
                checkAndUpdateShiftState()
            }
        }

        // Long press on space for voice input
        spaceButton?.setOnLongClickListener {
            if (!isTranscribing) {
                handleVoiceInput()
            }
            true
        }

        // Period key
        view.findViewById<Button>(R.id.key_period).setOnClickListener {
            currentInputConnection?.commitText(".", 1)
            checkAndUpdateShiftState()
        }

        // Return key
        view.findViewById<Button>(R.id.key_return).setOnClickListener {
            handleReturnKey()
        }

        // Initialize letter case
        updateLetterCase()
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
            }
        }
    }

    private fun updateLetterCase() {
        letterButtons.forEach { (char, button) ->
            button.text = if (isShifted) char.uppercase() else char.lowercase()
        }
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
        }
    }

    private fun handleVoiceInput() {
        // Check for microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Microphone permission not granted")
            showSpacebarMessage("Grant mic permission in app")
            requestMicrophonePermission()
            return
        }

        val activeModel = VoiceModelPreferences.getActiveModel(this) ?: "whisper-base"
        Log.d(TAG, "Voice input triggered via long press on space, active model: $activeModel")

        serviceScope.launch {
            try {
                // Set transcribing state
                isTranscribing = true
                spaceButton?.text = "Transcribing..."
                spaceButton?.alpha = 0.7f

                if (!stt.isReady()) {
                    stt.init(activeModel)
                }
                val result = stt.transcribe()

                if (result != null && result.text?.isNotBlank() == true) {
                    currentInputConnection?.commitText(result.text, 1)
                    Log.d(TAG, "Transcribed text: ${result.text}")
                } else {
                    Log.d(TAG, "No speech detected or empty result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during voice input transcription", e)
                showSpacebarMessage("Transcription failed")
            } finally {
                // Reset transcribing state
                isTranscribing = false
                spaceButton?.text = "hold to talk"
                spaceButton?.alpha = 1.0f
            }
        }
    }

    private fun showSpacebarMessage(message: String) {
        spaceButton?.text = message
        spaceButton?.alpha = 0.7f
        spaceButton?.postDelayed({
            if (!isTranscribing) {
                spaceButton?.text = "hold to talk"
                spaceButton?.alpha = 1.0f
            }
        }, 3000)
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

    private fun setupNumbersKeyboard(view: View) {
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
        view.findViewById<Button>(R.id.key_backspace_num).setOnClickListener {
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
        spaceButton = view.findViewById<Button>(R.id.key_space_num)
        spaceButton?.setOnClickListener {
            if (!isTranscribing) {
                currentInputConnection?.commitText(" ", 1)
            }
        }

        // Long press on space for voice input
        spaceButton?.setOnLongClickListener {
            if (!isTranscribing) {
                handleVoiceInput()
            }
            true
        }

        // Period
        view.findViewById<Button>(R.id.key_period_num).setOnClickListener {
            currentInputConnection?.commitText(".", 1)
        }

        // Return
        view.findViewById<Button>(R.id.key_return_num).setOnClickListener {
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
