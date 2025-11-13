package com.cactus.peyokeys

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
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
        val view = layoutInflater.inflate(R.layout.keyboard, null)
        setupKeyboard(view)
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
            // TODO: Switch to numbers/symbols layout
            Log.d(TAG, "Numbers key pressed")
        }

        // Comma key
        view.findViewById<Button>(R.id.key_comma).setOnClickListener {
            currentInputConnection?.commitText(",", 1)
            checkAndUpdateShiftState()
        }

        // Space key
        val spaceButton = view.findViewById<Button>(R.id.key_space)
        spaceButton.setOnClickListener {
            currentInputConnection?.commitText(" ", 1)
            checkAndUpdateShiftState()
        }

        // Long press on space for voice input
        spaceButton.setOnLongClickListener {
            handleVoiceInput()
            true
        }

        // Period key
        view.findViewById<Button>(R.id.key_period).setOnClickListener {
            currentInputConnection?.commitText(".", 1)
            checkAndUpdateShiftState()
        }

        // Return key
        view.findViewById<Button>(R.id.key_return).setOnClickListener {
            currentInputConnection?.commitText("\n", 1)
            isShifted = true
            updateLetterCase()
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
        val activeModel = VoiceModelPreferences.getActiveModel(this) ?: "whisper-base"
        Log.d(TAG, "Voice input triggered via long press on space, active model: $activeModel")

        serviceScope.launch {
            try {
                if (!stt.isReady()) {
                    stt.init(activeModel)
                }
                val result = stt.transcribe()
                result?.let {
                    currentInputConnection?.commitText(it.text, 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during voice input transcription", e)
            }
        }
    }
}
