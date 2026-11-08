package dev.ownkey.kbagent;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Single text field that keyboards type into during benchmark runs. */
public class TypeActivity extends Activity {
    static volatile TypeActivity instance;
    EditText field;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(24, 160, 24, 24);
        field = new EditText(this);
        field.setTextColor(Color.BLACK);
        field.setTextSize(18f);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setMinLines(4);
        boolean noLearn = getIntent() == null || getIntent().getBooleanExtra("nolearn", true);
        configureField(noLearn);
        root.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        instance = this;
        field.requestFocus();
    }

    void configureField(boolean noLearn) {
        field.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        int opts = EditorInfo.IME_ACTION_NONE;
        if (noLearn) opts |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        field.setImeOptions(opts);
    }

    void resetField(String text) {
        field.setText(text);
        field.setSelection(field.getText().length());
        field.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.restartInput(field);
        imm.showSoftInput(field, 0);
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
