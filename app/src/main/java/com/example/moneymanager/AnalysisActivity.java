package com.example.moneymanager;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanager.databinding.ActivityAnalysisBinding;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AnalysisActivity extends AppCompatActivity {
    private static final Locale INDONESIAN = new Locale("id", "ID");
    private static final int[] INCOME_COLORS = {
            0xFFBCEBFF, 0xFF5BC0EB, 0xFF668BFF, 0xFF9BE4DA, 0xFFB5B0FF, 0xFFEAF7FF
    };
    private static final int[] EXPENSE_COLORS = {
            0xFFFFAA63, 0xFFE97038, 0xFFFFCF67, 0xFFB85931, 0xFFF08C82, 0xFFFFE2B5
    };

    private final Calendar month = Calendar.getInstance();
    private ActivityAnalysisBinding b;
    private List<Transaction> transactions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAnalysisBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        try (DBHelper db = new DBHelper(this)) {
            transactions = db.getAll();
        }

        month.set(Calendar.DAY_OF_MONTH, 1);
        if (savedInstanceState != null) {
            month.setTimeInMillis(savedInstanceState.getLong("month"));
        }
        boolean income = savedInstanceState != null && savedInstanceState.getBoolean("income");
        b.toggleType.check(income ? R.id.btnIncome : R.id.btnExpense);
        b.toggleType.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) render();
        });
        b.btnPrevious.setOnClickListener(v -> changeMonth(-1));
        b.btnNext.setOnClickListener(v -> changeMonth(1));
        b.btnBack.setOnClickListener(v -> finish());
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("month", month.getTimeInMillis());
        outState.putBoolean("income", showingIncome());
    }

    private void changeMonth(int delta) {
        month.add(Calendar.MONTH, delta);
        render();
    }

    private boolean showingIncome() {
        return b.toggleType.getCheckedButtonId() == R.id.btnIncome;
    }

    private void render() {
        boolean income = showingIncome();
        int[] colors = income ? INCOME_COLORS : EXPENSE_COLORS;
        String[] categories = Transaction.CATEGORIES;
        List<String> categoryNames = Arrays.asList(categories);
        BigInteger[] amounts = new BigInteger[categories.length];
        Arrays.fill(amounts, BigInteger.ZERO);

        String monthPrefix = new SimpleDateFormat("yyyy-MM-", Locale.US).format(month.getTime());
        b.tvMonth.setText(new SimpleDateFormat("MMMM yyyy", INDONESIAN).format(month.getTime()));
        BigInteger total = BigInteger.ZERO;
        for (Transaction transaction : transactions) {
            if (transaction.income != income || !transaction.date.startsWith(monthPrefix)) continue;
            int index = categoryNames.indexOf(transaction.category);
            if (index < 0) index = categoryNames.indexOf(Transaction.OTHER_CATEGORY);
            BigInteger amount = BigInteger.valueOf(transaction.amount);
            amounts[index] = amounts[index].add(amount);
            total = total.add(amount);
        }

        b.tvTotal.setText(Transaction.rupiah(total));
        b.tvTotal.setTextColor(ContextCompat.getColor(this, income ? R.color.income : R.color.expense));
        boolean empty = total.signum() == 0;
        b.chart.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.tvEmpty.setText(income ? "Belum ada pemasukan bulan ini" : "Belum ada pengeluaran bulan ini");
        b.legend.removeAllViews();
        if (empty) return;

        float[] sweeps = new float[categories.length];
        NumberFormat percent = NumberFormat.getPercentInstance(INDONESIAN);
        percent.setMaximumFractionDigits(1);
        for (int i = 0; i < categories.length; i++) {
            if (amounts[i].signum() == 0) continue;
            double fraction = amounts[i].doubleValue() / total.doubleValue();
            sweeps[i] = (float) (fraction * 360);
            addLegendRow(categories[i], amounts[i], percent.format(fraction), colors[i]);
        }
        b.chart.setSlices(sweeps, colors);
    }

    private void addLegendRow(String category, BigInteger amount, String percent, int color) {
        TextView row = new TextView(this);
        row.setText(category + "  ·  " + Transaction.rupiah(amount) + "  ·  " + percent);
        row.setTextColor(ContextCompat.getColor(this, R.color.text));
        row.setTextSize(15);

        float density = getResources().getDisplayMetrics().density;
        int spacing = Math.round(9 * density);
        int dotSize = Math.round(12 * density);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        dot.setBounds(0, 0, dotSize, dotSize);

        row.setCompoundDrawablesRelative(dot, null, null, null);
        row.setCompoundDrawablePadding(spacing);
        row.setPadding(0, spacing, 0, spacing);
        b.legend.addView(row);
    }
}
