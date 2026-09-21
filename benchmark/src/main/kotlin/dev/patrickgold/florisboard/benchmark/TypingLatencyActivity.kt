package dev.patrickgold.florisboard.benchmark

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import java.util.concurrent.LinkedBlockingQueue

/** Synthetic host. Stores timings only; never copies editor content to evidence. */
class TypingLatencyActivity : Activity() {
    lateinit var input: EditText
    val samples = LinkedBlockingQueue<Double>()
    val openings = LinkedBlockingQueue<Double>()
    @Volatile var inputAt = 0L
    private var openAt = 0L
    private var changed = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        input = EditText(this).apply {
            contentDescription = "Ownkey synthetic typing benchmark"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            setSingleLine(false)
        }
        setContentView(input)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (inputAt != 0L) changed = true }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        input.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (changed && inputAt != 0L) {
                    samples.offer((System.nanoTime() - inputAt) / 1_000_000.0)
                    changed = false; inputAt = 0L
                }
                if (openAt != 0L && android.os.Build.VERSION.SDK_INT >= 30 && input.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true) {
                    openings.offer((System.nanoTime() - openAt) / 1_000_000.0)
                    openAt = 0L
                }
                return true
            }
        })
    }
    fun showKeyboard() {
        input.requestFocus()
        input.post {
            openAt = System.nanoTime()
            getSystemService(InputMethodManager::class.java).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    @androidx.annotation.RequiresApi(30)
    fun isKeyboardVisible(): Boolean =
        input.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) ?: true
    fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
    }
}
