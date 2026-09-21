package dev.patrickgold.florisboard.benchmark

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Supply the measured centre of a letter key via keyX/keyY; shell text injection is not typing. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class TypingLatencyBenchmark {
    @Test fun touchToVisibleTextAndKeyboardOpen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Supply keyX/keyY for the fixed keyboard layout", args.containsKey("keyX") && args.containsKey("keyY"))
        val x = args.getString("keyX")!!.toFloat()
        val y = args.getString("keyY")!!.toFloat()
        val target = args.getString("targetPackage") ?: TARGET_PACKAGE
        require(target.matches(Regex("[a-zA-Z0-9_.]+")))
        val device = UiDevice.getInstance(instrumentation)
        val oldIme = device.executeShellCommand("settings get secure default_input_method").trim()
        val oldHardwareIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        var launched: TypingLatencyActivity? = null
        try {
            device.executeShellCommand("ime enable $target/dev.patrickgold.florisboard.FlorisImeService")
            device.executeShellCommand("ime set $target/dev.patrickgold.florisboard.FlorisImeService")
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
            val context = instrumentation.context
            val activity = instrumentation.startActivitySync(Intent(context, TypingLatencyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TypingLatencyActivity
            launched = activity
            val typing = JSONArray()
            val opening = JSONArray()
            val rounds = (args.getString("rounds")?.toInt() ?: 20).coerceIn(1, 100)
            val taps = (args.getString("taps")?.toInt() ?: 100).coerceIn(1, 500)
            val opens = (args.getString("opens")?.toInt() ?: 10).coerceIn(1, 100)
            repeat(rounds) {
                val openBatch = JSONArray()
                repeat(opens + 1) { index ->
                    instrumentation.runOnMainSync { activity.hideKeyboard(); activity.input.setText("") }
                    val hiddenDeadline = SystemClock.elapsedRealtime() + 10_000
                    var visible = true
                    while (visible && SystemClock.elapsedRealtime() < hiddenDeadline) {
                        instrumentation.runOnMainSync { visible = activity.isKeyboardVisible() }
                        if (visible) SystemClock.sleep(50)
                    }
                    check(!visible) { "Keyboard did not become hidden" }
                    activity.openings.clear()
                    instrumentation.runOnMainSync { activity.showKeyboard() }
                    val open = activity.openings.poll(10, TimeUnit.SECONDS)
                    assertNotNull("Keyboard did not become visible", open)
                    if (index > 0) openBatch.put(open)
                    SystemClock.sleep(300)
                }
                opening.put(openBatch)
                val batch = JSONArray()
                repeat(taps + 10) { tap ->
                    activity.samples.clear()
                    activity.inputAt = System.nanoTime()
                    val down = SystemClock.uptimeMillis()
                    for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                        event.source = InputDevice.SOURCE_TOUCHSCREEN
                        check(instrumentation.uiAutomation.injectInputEvent(event, true))
                        event.recycle()
                        if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(20)
                    }
                    val latency = activity.samples.poll(3, TimeUnit.SECONDS)
                    assertNotNull("Key tap produced no visible editor update; check coordinates", latency)
                    if (tap >= 10) batch.put(latency)
                    SystemClock.sleep(40)
                }
                typing.put(batch)
            }
            val result = JSONObject().put("schema", 2).put("rounds_ms", typing).put("keyboard_open_rounds_ms", opening)
                .put("api", android.os.Build.VERSION.SDK_INT).put("abi", android.os.Build.SUPPORTED_ABIS[0])
                .put("metric", "touch injection to next host pre-draw after editor update")
            File(context.getExternalFilesDir(null), "typing-latency.json").writeText(result.toString())
        } finally {
            instrumentation.runOnMainSync { launched?.finish() }
            if (oldIme.matches(Regex("[A-Za-z0-9_./]+")) && oldIme.contains('/')) device.executeShellCommand("ime set $oldIme")
            if (oldHardwareIme == "null") device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
            else if (oldHardwareIme in setOf("0", "1")) device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $oldHardwareIme")
        }
    }
}
