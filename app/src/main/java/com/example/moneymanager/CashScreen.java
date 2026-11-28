package com.example.moneymanager;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.math.BigInteger;
import java.text.NumberFormat;

final class CashScreen {
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int FIELD_OUTLINE = 0xFF778A7D;
    static final int ERROR = 0xFFB3261E;

    final AppCompatActivity context;
    final LinearLayout root;
    final LinearLayout body;
    final int green;
    final int ink;
    final int muted;
    final int surface;
    final int outline;
    final int mint;

    CashScreen(AppCompatActivity context, String title, Runnable back) {
        this.context = context;
        green = context.getColor(R.color.cash_green);
        ink = context.getColor(R.color.cash_text);
        muted = context.getColor(R.color.cash_secondary);
        surface = context.getColor(R.color.cash_surface);
        outline = context.getColor(R.color.cash_outline);
        mint = context.getColor(R.color.cash_mint);
        root = column();
        root.setBackgroundColor(context.getColor(R.color.cash_background));
        root.setFocusableInTouchMode(true);

        LinearLayout header = horizontal();
        header.setMinimumHeight(dp(64));
        header.setPadding(dp(8), 0, dp(16), 0);
        header.setBackground(shape(surface, 8, outline));
        ImageButton arrow = new ImageButton(context);
        arrow.setImageResource(R.drawable.ic_back);
        arrow.setImageTintList(ColorStateList.valueOf(green));
        arrow.setBackground(clickable(surface, 24, 0));
        arrow.setContentDescription("Kembali");
        arrow.setOnClickListener(view -> back.run());
        header.addView(arrow, size(48, 48));
        TextView heading = text(title, 20, ink, true);
        ViewCompat.setAccessibilityHeading(heading, true);
        heading.setPadding(dp(8), 0, 0, 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, WRAP, 1f));
        root.addView(header, size(MATCH, WRAP));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        body = column();
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(body, new ScrollView.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));
    }

    int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    LinearLayout.LayoutParams size(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
    }

    LinearLayout column() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    LinearLayout horizontal() {
        LinearLayout view = new LinearLayout(context);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    TextView text(String value, float size, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (medium) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    void space(LinearLayout target, int height) {
        target.addView(new View(context), size(MATCH, height));
    }

    GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    RippleDrawable clickable(int fill, int radius, int stroke) {
        return new RippleDrawable(ColorStateList.valueOf(0x24164938), shape(fill, radius, stroke), null);
    }

    MaterialButton button(String label, boolean filled, Runnable action) {
        MaterialButton view = new MaterialButton(context);
        view.setText(label);
        view.setAllCaps(false);
        view.setCornerRadius(dp(12));
        view.setMinHeight(dp(48));
        view.setInsetTop(0);
        view.setInsetBottom(0);
        view.setBackgroundTintList(ColorStateList.valueOf(filled ? green : 0x00000000));
        view.setTextColor(filled ? 0xFFFFFFFF : green);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    void footer(View action) {
        LinearLayout.LayoutParams params = size(MATCH, WRAP);
        params.setMargins(dp(16), dp(8), dp(16), dp(22));
        root.addView(action, params);
    }

    void navigation(int selected, BottomNavigationView.OnItemSelectedListener listener) {
        BottomNavigationView navigation = new BottomNavigationView(context);
        navigation.setBackgroundResource(R.drawable.cash_navigation_background);
        ColorStateList tint = context.getColorStateList(R.color.cash_nav_content);
        navigation.setItemIconTintList(tint);
        navigation.setItemTextColor(tint);
        navigation.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_LABELED);
        navigation.inflateMenu(R.menu.cash_navigation);
        navigation.setSelectedItemId(selected);
        navigation.setOnItemSelectedListener(listener);
        root.addView(navigation, size(MATCH, WRAP));
    }

    LinearLayout row(String title, String detail, int icon, Runnable action) {
        LinearLayout row = horizontal();
        row.setMinimumHeight(dp(60));
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        row.setBackground(clickable(surface, 8, outline));
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());
        row.addView(icon(icon), size(20, 20));
        LinearLayout labels = column();
        labels.setPadding(dp(12), 0, dp(8), 0);
        labels.addView(text(title, 14, ink, true));
        labels.addView(text(detail, 12, muted, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.addView(icon(R.drawable.ic_cash_chevron), size(20, 20));
        return row;
    }

    ImageView icon(int res) {
        ImageView image = new ImageView(context);
        image.setImageResource(res);
        image.setColorFilter(green);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    Field field(String label, String hint, String help, int inputType, int size) {
        LinearLayout group = column();
        TextView caption = text(label, 12, muted, true);
        group.addView(caption);
        EditText input = new EditText(context);
        input.setId(View.generateViewId());
        caption.setLabelFor(input.getId());
        input.setTextSize(size);
        input.setTextColor(ink);
        input.setHintTextColor(muted);
        input.setContentDescription(label);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setHint(hint);
        input.setPadding(dp(12), dp(6), dp(12), dp(6));
        input.setMinimumHeight(dp(size > 20 ? 56 : 48));
        group.addView(input, size(MATCH, WRAP));
        TextView helper = text(help, 12, muted, true);
        helper.setPadding(0, dp(3), 0, 0);
        helper.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        group.addView(helper);
        Field field = new Field(group, caption, input, helper, help);
        field.error(null);
        return field;
    }

    Field currency(String label, String help) {
        Field field = field(label, "Rp", help, InputType.TYPE_CLASS_NUMBER, 24);
        EditText input = field.input;
        input.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        input.setKeyListener(DigitsKeyListener.getInstance("0123456789.Rp"));
        input.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable value) {
                String digits = digits(value.toString());
                String formatted = digits.isEmpty() ? "" : "Rp" + NumberFormat.getIntegerInstance(MainActivity.INDONESIAN)
                        .format(new BigInteger(digits));
                if (formatted.contentEquals(value)) return;
                int cursor = Math.min(value.length(), Math.max(0, input.getSelectionStart()));
                int after = digits(value.subSequence(cursor, value.length()).toString()).length();
                value.replace(0, value.length(), formatted);
                int position = value.length();
                while (after > 0 && position > 0) {
                    if (Character.isDigit(value.charAt(--position))) after--;
                }
                input.setSelection(position);
            }
        });
        return field;
    }

    static String digits(String value) {
        return value.replaceAll("[^0-9]", "");
    }

    final class Field {
        final LinearLayout group;
        final TextView label;
        final EditText input;
        final TextView hint;
        final String help;

        Field(LinearLayout group, TextView label, EditText input, TextView hint, String help) {
            this.group = group;
            this.label = label;
            this.input = input;
            this.hint = hint;
            this.help = help;
        }

        void error(String message) {
            input.setBackground(shape(surface, 8, message == null ? FIELD_OUTLINE : ERROR));
            label.setTextColor(message == null ? muted : ERROR);
            hint.setTextColor(message == null ? muted : ERROR);
            hint.setText(message == null ? help : message);
            hint.setVisibility(message == null && help.isEmpty() ? View.GONE : View.VISIBLE);
        }

        String value() {
            return input.getText().toString().trim();
        }

        void amount(long value) {
            input.setText(value == 0 ? "" : Long.toString(value));
            input.setSelection(input.length());
        }
    }
}
