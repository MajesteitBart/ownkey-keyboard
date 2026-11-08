package dev.ownkey.kbagent;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Benchmark agent. Start with:
 *   adb shell am instrument -w -e port 7788 dev.ownkey.kbagent/.Agent
 * then `adb forward tcp:7788 tcp:7788` and send one JSON command per line.
 */
public class Agent extends Instrumentation {
    private int port = 7788;
    private boolean noLearn = true;
    private UiAutomation ua;
    private volatile boolean running = true;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        if (arguments != null) {
            String p = arguments.getString("port");
            if (p != null) port = Integer.parseInt(p);
            String nl = arguments.getString("nolearn");
            if (nl != null) noLearn = Boolean.parseBoolean(nl);
        }
        start();
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            ua = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = ua.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            ua.setServiceInfo(info);
            launch();
            serve();
            Bundle result = new Bundle();
            result.putString("status", "stopped");
            finish(0, result);
        } catch (Throwable t) {
            Bundle result = new Bundle();
            result.putString("error", String.valueOf(t));
            finish(1, result);
        }
    }

    private void launch() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(getTargetContext().getPackageName(), TypeActivity.class.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("nolearn", noLearn);
        startActivitySync(intent);
        waitForIdleSync();
    }

    private void serve() throws Exception {
        try (ServerSocket server = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"))) {
            while (running) {
                try (Socket s = server.accept()) {
                    s.setTcpNoDelay(true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
                    String line;
                    while (running && (line = in.readLine()) != null) {
                        JSONObject resp;
                        try {
                            resp = handle(new JSONObject(line));
                        } catch (Throwable t) {
                            resp = new JSONObject();
                            resp.put("ok", false);
                            resp.put("error", String.valueOf(t));
                        }
                        out.println(resp.toString());
                    }
                } catch (Throwable t) {
                    // Client went away; keep serving.
                }
            }
        }
    }

    private JSONObject handle(JSONObject cmd) throws Exception {
        String op = cmd.getString("op");
        JSONObject r = new JSONObject();
        r.put("ok", true);
        switch (op) {
            case "ping":
                r.put("t", SystemClock.uptimeMillis());
                break;
            case "tap": {
                float x = (float) cmd.getDouble("x");
                float y = (float) cmd.getDouble("y");
                long hold = cmd.optLong("hold", 35);
                tap(x, y, hold);
                break;
            }
            case "taps": {
                // Sequence of taps with a fixed gap, for fast typing without per-tap round trips.
                JSONArray pts = cmd.getJSONArray("pts");
                long hold = cmd.optLong("hold", 35);
                long gap = cmd.optLong("gap", 60);
                for (int i = 0; i < pts.length(); i++) {
                    JSONArray p = pts.getJSONArray(i);
                    tap((float) p.getDouble(0), (float) p.getDouble(1), hold);
                    SystemClock.sleep(gap);
                }
                break;
            }
            case "text":
                readText(r);
                break;
            case "reset": {
                final String text = cmd.optString("text", "");
                final TypeActivity a = activity();
                runOnMainSync(() -> a.resetField(text));
                SystemClock.sleep(cmd.optLong("settle", 250));
                readText(r);
                break;
            }
            case "ime":
                if (cmd.optBoolean("fresh", true)) ua.clearCache();
                r.put("nodes", imeNodes(cmd.optBoolean("all", false)));
                break;
            case "windows": {
                JSONArray arr = new JSONArray();
                for (AccessibilityWindowInfo w : ua.getWindows()) {
                    JSONObject o = new JSONObject();
                    Rect b = new Rect();
                    w.getBoundsInScreen(b);
                    o.put("type", w.getType());
                    o.put("title", String.valueOf(w.getTitle()));
                    o.put("bounds", b.flattenToString());
                    arr.put(o);
                }
                r.put("windows", arr);
                break;
            }
            case "shot": {
                Bitmap bmp = ua.takeScreenshot();
                if (cmd.has("w")) {
                    bmp = Bitmap.createBitmap(bmp, cmd.getInt("x"), cmd.getInt("y"), cmd.getInt("w"), cmd.getInt("h"));
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
                r.put("png", Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP));
                break;
            }
            case "relaunch":
                launch();
                break;
            case "quit":
                running = false;
                break;
            default:
                r.put("ok", false);
                r.put("error", "unknown op " + op);
        }
        return r;
    }

    private TypeActivity activity() {
        TypeActivity a = TypeActivity.instance;
        if (a == null) {
            launch();
            a = TypeActivity.instance;
        }
        return a;
    }

    private void readText(JSONObject r) throws Exception {
        final TypeActivity a = activity();
        final String[] text = new String[1];
        final int[] sel = new int[2];
        runOnMainSync(() -> {
            EditText f = a.field;
            text[0] = f.getText().toString();
            sel[0] = f.getSelectionStart();
            sel[1] = f.getSelectionEnd();
        });
        r.put("text", text[0]);
        r.put("selStart", sel[0]);
        r.put("selEnd", sel[1]);
    }

    private void tap(float x, float y, long holdMs) {
        long down = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ua.injectInputEvent(ev, true);
        ev.recycle();
        SystemClock.sleep(holdMs);
        long up = SystemClock.uptimeMillis();
        ev = MotionEvent.obtain(down, up, MotionEvent.ACTION_UP, x, y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ua.injectInputEvent(ev, true);
        ev.recycle();
    }

    private JSONArray imeNodes(boolean all) throws Exception {
        JSONArray out = new JSONArray();
        List<AccessibilityWindowInfo> windows = ua.getWindows();
        for (AccessibilityWindowInfo w : windows) {
            if (w.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root != null) walk(root, out, 0, all);
        }
        return out;
    }

    private void walk(AccessibilityNodeInfo n, JSONArray out, int depth, boolean all) throws Exception {
        CharSequence text = n.getText();
        CharSequence desc = n.getContentDescription();
        if (all || text != null || desc != null) {
            JSONObject o = new JSONObject();
            Rect b = new Rect();
            n.getBoundsInScreen(b);
            o.put("d", depth);
            o.put("text", text == null ? JSONObject.NULL : text.toString());
            o.put("desc", desc == null ? JSONObject.NULL : desc.toString());
            o.put("cls", String.valueOf(n.getClassName()));
            o.put("id", n.getViewIdResourceName() == null ? JSONObject.NULL : n.getViewIdResourceName());
            o.put("b", new JSONArray(new int[]{b.left, b.top, b.right, b.bottom}));
            o.put("click", n.isClickable());
            out.put(o);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) walk(c, out, depth + 1, all);
        }
    }
}
