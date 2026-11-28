package com.example.moneymanager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanager.databinding.ItemCashCategoryBinding;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.example.moneymanager.CashScreen.MATCH;
import static com.example.moneymanager.CashScreen.WRAP;

public class AnalysisActivity extends AppCompatActivity {
    private final Calendar reportMonth = Calendar.getInstance();
    private final NumberFormat percent = NumberFormat.getPercentInstance(MainActivity.INDONESIAN);
    private final Map<String, BigInteger> totals = new HashMap<>();
    private final BigInteger[] weeks = new BigInteger[4];
    private BigInteger expense = BigInteger.ZERO;
    private List<Transaction> transactions;
    private SharedPreferences preferences;
    private String monthKey;
    private String page = "insight";
    private String editingCategory;
    private CashScreen.Field activeLimit;
    private String limitDraft;
    private boolean reportIncome;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        reportMonth.set(Calendar.DAY_OF_MONTH, 1);
        percent.setMaximumFractionDigits(1);
        preferences = getSharedPreferences("cash_lens", MODE_PRIVATE);
        if (saved != null) {
            page = saved.getString("page", "insight");
            editingCategory = saved.getString("category");
            limitDraft = saved.getString("draft");
            reportMonth.setTimeInMillis(saved.getLong("reportMonth"));
            reportIncome = saved.getBoolean("reportIncome");
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Calendar now = Calendar.getInstance();
        monthKey = new SimpleDateFormat("yyyy-MM", Locale.US).format(now.getTime());
        try (DBHelper db = new DBHelper(this)) {
            transactions = db.getAll();
        }
        summarize();
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("page", page);
        state.putString("category", editingCategory);
        if (activeLimit != null) state.putString("draft", activeLimit.value());
        state.putLong("reportMonth", reportMonth.getTimeInMillis());
        state.putBoolean("reportIncome", reportIncome);
    }

    private void summarize() {
        totals.clear();
        expense = BigInteger.ZERO;
        Arrays.fill(weeks, BigInteger.ZERO);
        for (Transaction transaction : transactions) {
            if (transaction.income || transaction.amount <= 0 || !inMonth(transaction, monthKey)) continue;
            BigInteger amount = BigInteger.valueOf(transaction.amount);
            String category = Transaction.displayCategory(transaction.category);
            totals.merge(category, amount, BigInteger::add);
            expense = expense.add(amount);
            int day = Integer.parseInt(transaction.date.substring(8));
            int week = Math.max(0, Math.min(3, (day - 1) / 7));
            weeks[week] = weeks[week].add(amount);
        }
    }

    private boolean inMonth(Transaction transaction, String month) {
        return transaction.date.length() == 10 && transaction.date.startsWith(month + "-")
                && transaction.date.substring(8).matches("[0-9]{2}");
    }

    private void show(String destination) {
        limitDraft = null;
        page = destination;
        render();
    }

    private void back() {
        switch (page) {
            case "budgetEdit":
            case "categories": show("budget"); break;
            case "categoryEdit": show("categories"); break;
            case "monthly": show(expense.signum() == 0 ? "insight" : "breakdown"); break;
            case "breakdown":
            case "budget": show("insight"); break;
            default: finish();
        }
    }

    private void render() {
        if (activeLimit != null && (page.equals("budgetEdit") || page.equals("categoryEdit")) && limitDraft == null) {
            limitDraft = activeLimit.value();
        }
        activeLimit = null;
        CashScreen screen;
        switch (page) {
            case "budget": screen = budget(); break;
            case "budgetEdit": screen = limitEditor(null); break;
            case "categories": screen = categories(); break;
            case "categoryEdit": screen = limitEditor(editingCategory); break;
            case "breakdown": screen = breakdown(); break;
            case "monthly": screen = monthly(); break;
            default: screen = insight();
        }
        setContentView(screen.root);
        screen.root.requestFocus();
    }

    private CashScreen insight() {
        CashScreen screen = new CashScreen(this, "Insight", this::back);
        if (expense.signum() == 0) {
            screen.space(screen.body, 52);
            TextView info = screen.text("ⓘ", 24, screen.green, true);
            info.setGravity(Gravity.CENTER);
            info.setBackground(screen.shape(screen.mint, 16, 0));
            screen.body.addView(info, screen.size(58, 58));
            screen.space(screen.body, 22);
            screen.body.addView(screen.text("Data belum cukup", 26, screen.ink, true));
            screen.space(screen.body, 46);
            screen.body.addView(screen.text("Catat transaksi untuk melihat tren dan perbandingan. "
                    + "Satu kategori tetap bisa dibaca melalui Riwayat.", 16, screen.muted, false));
            LinearLayout actions = screen.column();
            actions.addView(screen.button("Tambah transaksi", true,
                    () -> startActivity(new Intent(this, FormActivity.class))), screen.size(MATCH, WRAP));
            actions.addView(screen.button("Anggaran", false, () -> show("budget")), screen.size(MATCH, WRAP));
            actions.addView(screen.button("Analisis bulanan", false, () -> show("monthly")), screen.size(MATCH, WRAP));
            screen.footer(actions);
            return screen;
        }

        addBudgetCard(screen);
        screen.space(screen.body, 28);
        screen.body.addView(screen.text("Tren pengeluaran", 20, screen.ink, true));
        screen.space(screen.body, 16);
        addTrend(screen);
        screen.space(screen.body, 18);
        Map.Entry<String, BigInteger> largest = ordered(totals).get(0);
        screen.body.addView(screen.text("Kategori terbesar: " + largest.getKey() + " ("
                + percent.format(largest.getValue().doubleValue() / expense.doubleValue()) + ").",
                14, screen.ink, false));
        screen.space(screen.body, 30);
        addRow(screen, "Rincian pengeluaran", "Nominal per kategori", R.drawable.ic_cash_insight,
                () -> show("breakdown"));
        addRow(screen, "Anggaran", "Penggunaan dan sisa batas", R.drawable.ic_cash_receipt,
                () -> show("budget"));
        navigation(screen);
        return screen;
    }

    private CashScreen budget() {
        CashScreen screen = new CashScreen(this, "Anggaran", this::back);
        screen.space(screen.body, 6);
        addBudgetCard(screen);
        screen.space(screen.body, 32);
        addRow(screen, "Ubah anggaran", "Batas pengeluaran bulanan", R.drawable.ic_cash_edit,
                () -> show("budgetEdit"));
        addRow(screen, "Anggaran kategori", "Opsional untuk tiap kategori", R.drawable.ic_cash_categories,
                () -> show("categories"));
        addRow(screen, "Lihat Insight", "Tren dan pengeluaran terbesar", R.drawable.ic_cash_insight,
                () -> show("insight"));
        navigation(screen);
        return screen;
    }

    private void navigation(CashScreen screen) {
        screen.navigation(R.id.navInsight, item -> {
            int id = item.getItemId();
            if (id == R.id.navInsight) return true;
            if (id == R.id.navHome || id == R.id.navHistory) {
                Intent intent = new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_INSIGHT_DESTINATION, id);
                startActivity(intent);
            }
            return false;
        });
    }

    private void addBudgetCard(CashScreen screen) {
        long budget = MainActivity.monthlyBudget(preferences, monthKey);
        double ratio = budget > 0 ? expense.doubleValue() / budget : 0;
        boolean exceeded = ratio > 1;
        int white = 0xFFFFFFFF;
        int pale = getColor(R.color.cash_budget_text);
        int progress = exceeded ? 0xFFFFF2D8 : white;
        LinearLayout card = screen.column();
        card.setPadding(screen.dp(16), screen.dp(14), screen.dp(16), screen.dp(12));
        card.setBackground(screen.shape(screen.green, 16, 0));
        card.addView(screen.text("Pengeluaran bulan ini", 14, pale, true));
        card.addView(screen.text(Transaction.rupiah(expense), 32, white, true));
        screen.space(card, 8);

        LinearProgressIndicator bar = new LinearProgressIndicator(this);
        bar.setMax(1000);
        bar.setTrackThickness(screen.dp(8));
        bar.setTrackCornerRadius(screen.dp(4));
        bar.setTrackColor(getColor(R.color.cash_budget_track));
        bar.setIndicatorColor(progress);
        bar.setProgress((int) Math.round(Math.min(ratio, 1) * 1000));
        card.addView(bar, screen.size(MATCH, WRAP));
        screen.space(card, 4);
        String usage = budget == 0 ? "Anggaran belum diatur"
                : (exceeded ? "Melebihi anggaran · " : "Terpakai · ") + percent.format(ratio);
        card.addView(screen.text(usage, 12, progress, true));
        screen.space(card, 8);

        LinearLayout figures = screen.horizontal();
        BigInteger remainder = BigInteger.valueOf(budget).subtract(expense);
        addFigure(screen, figures, "Anggaran", budget == 0 ? "—" : Transaction.rupiah(budget), false);
        addFigure(screen, figures, exceeded ? "Kelebihan" : "Sisa anggaran",
                budget == 0 ? "—" : Transaction.rupiah(remainder.abs()), true);
        card.addView(figures);
        screen.body.addView(card, screen.size(MATCH, WRAP));
    }

    private void addFigure(CashScreen screen, LinearLayout figures, String label, String value, boolean end) {
        LinearLayout figure = screen.column();
        if (end) figure.setGravity(Gravity.END);
        figure.addView(screen.text(label, 12, getColor(R.color.cash_budget_text), false));
        figure.addView(screen.text(value, 16, 0xFFFFFFFF, true));
        figures.addView(figure, new LinearLayout.LayoutParams(0, WRAP, 1f));
    }

    private void addTrend(CashScreen screen) {
        BigInteger maximum = BigInteger.ZERO;
        for (BigInteger week : weeks) maximum = maximum.max(week);
        LinearLayout trend = screen.horizontal();
        trend.setGravity(Gravity.BOTTOM);
        for (int index = 0; index < weeks.length; index++) {
            LinearLayout column = screen.column();
            BigInteger amount = weeks[index];
            int height = amount.signum() == 0 ? 0
                    : Math.max(3, (int) Math.round(68 * amount.doubleValue() / maximum.doubleValue()));
            View bar = new View(this);
            bar.setBackground(screen.shape(screen.green, 5, 0));
            LinearLayout.LayoutParams barParams = screen.size(40, height);
            barParams.gravity = Gravity.CENTER_HORIZONTAL;
            column.addView(bar, barParams);
            TextView label = screen.text("M" + (index + 1), 12, screen.muted, true);
            int firstDay = index * 7 + 1;
            String lastDay = index == 3 ? "akhir bulan" : Integer.toString(firstDay + 6);
            label.setContentDescription("Minggu " + (index + 1) + ", tanggal " + firstDay + " sampai " + lastDay);
            label.setPadding(0, screen.dp(4), 0, 0);
            label.setGravity(Gravity.CENTER);
            column.addView(label, screen.size(MATCH, WRAP));
            TextView value = screen.text(Transaction.rupiah(amount), 12, screen.ink, true);
            value.setGravity(Gravity.CENTER);
            column.addView(value, screen.size(MATCH, WRAP));
            trend.addView(column, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        trend.setMinimumHeight(screen.dp(118));
        screen.body.addView(trend, screen.size(MATCH, WRAP));
    }

    private CashScreen breakdown() {
        CashScreen screen = new CashScreen(this, "Rincian pengeluaran", this::back);
        screen.space(screen.body, 24);
        screen.body.addView(screen.text("Pengeluaran per kategori", 20, screen.ink, true));
        addBreakdown(screen, totals, expense);
        screen.space(screen.body, 28);
        screen.body.addView(screen.text(monthComparison(), 14, screen.muted, false));
        screen.space(screen.body, 12);
        screen.body.addView(screen.button("Analisis bulanan", false, () -> show("monthly")));
        return screen;
    }

    private String monthComparison() {
        Calendar previous = Calendar.getInstance();
        int day = previous.get(Calendar.DAY_OF_MONTH);
        previous.set(Calendar.DAY_OF_MONTH, 1);
        previous.add(Calendar.MONTH, -1);
        int cutoff = Math.min(day, previous.getActualMaximum(Calendar.DAY_OF_MONTH));
        String previousKey = new SimpleDateFormat("yyyy-MM", Locale.US).format(previous.getTime());
        BigInteger currentTotal = BigInteger.ZERO;
        BigInteger previousTotal = BigInteger.ZERO;
        for (Transaction transaction : transactions) {
            if (transaction.income || transaction.amount <= 0) continue;
            boolean current = inMonth(transaction, monthKey);
            if (!current && !inMonth(transaction, previousKey)) continue;
            if (Integer.parseInt(transaction.date.substring(8)) > cutoff) continue;
            BigInteger amount = BigInteger.valueOf(transaction.amount);
            if (current) currentTotal = currentTotal.add(amount);
            else previousTotal = previousTotal.add(amount);
        }
        if (previousTotal.signum() == 0) {
            return "Perbandingan bulanan muncul setelah tersedia periode dengan cakupan yang sepadan.";
        }
        return "Tanggal 1–" + cutoff + " · Bulan ini " + Transaction.rupiah(currentTotal)
                + " · Bulan lalu " + Transaction.rupiah(previousTotal);
    }

    private CashScreen monthly() {
        CashScreen screen = new CashScreen(this, "Analisis bulanan", this::back);
        LinearLayout month = screen.horizontal();
        View previous = screen.button("‹", false, () -> changeMonth(-1));
        previous.setContentDescription("Bulan sebelumnya");
        month.addView(previous, screen.size(52, 48));
        TextView date = screen.text(new SimpleDateFormat("MMMM yyyy", MainActivity.INDONESIAN)
                .format(reportMonth.getTime()), 20, screen.ink, true);
        date.setGravity(Gravity.CENTER);
        month.addView(date, new LinearLayout.LayoutParams(0, WRAP, 1f));
        View next = screen.button("›", false, () -> changeMonth(1));
        next.setContentDescription("Bulan berikutnya");
        month.addView(next, screen.size(52, 48));
        screen.body.addView(month);
        LinearLayout types = screen.horizontal();
        types.addView(screen.button("Pengeluaran", !reportIncome, () -> {
            reportIncome = false;
            render();
        }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        types.addView(screen.button("Pemasukan", reportIncome, () -> {
            reportIncome = true;
            render();
        }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        screen.body.addView(types);
        screen.space(screen.body, 16);
        String monthId = new SimpleDateFormat("yyyy-MM", Locale.US).format(reportMonth.getTime());
        Map<String, BigInteger> groups = new HashMap<>();
        BigInteger total = BigInteger.ZERO;
        for (Transaction transaction : transactions) {
            if (transaction.income != reportIncome || transaction.amount <= 0 || !inMonth(transaction, monthId)) continue;
            BigInteger amount = BigInteger.valueOf(transaction.amount);
            groups.merge(Transaction.displayCategory(transaction.category), amount, BigInteger::add);
            total = total.add(amount);
        }
        screen.body.addView(screen.text("Total bulan ini", 14, screen.muted, false));
        screen.body.addView(screen.text(Transaction.rupiah(total), 24, screen.green, true));
        screen.space(screen.body, 12);
        if (total.signum() > 0) {
            int[] palette = {0xFF164938, 0xFF3F725C, 0xFF62957B, 0xFF81B098, 0xFFA5CDB9, 0xFFC3DDCE, 0xFFD9EBDF};
            List<Map.Entry<String, BigInteger>> entries = ordered(groups);
            float[] slices = new float[entries.size()];
            int[] colors = new int[entries.size()];
            for (int index = 0; index < entries.size(); index++) {
                slices[index] = (float) (360 * entries.get(index).getValue().doubleValue() / total.doubleValue());
                colors[index] = palette[index % palette.length];
            }
            CategoryPieView pie = new CategoryPieView(this, null);
            pie.setSlices(slices, colors);
            pie.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            screen.body.addView(pie, screen.size(MATCH, 160));
            screen.space(screen.body, 12);
        }
        addBreakdown(screen, groups, total);
        return screen;
    }

    private void changeMonth(int delta) {
        reportMonth.add(Calendar.MONTH, delta);
        render();
    }

    private void addBreakdown(CashScreen screen, Map<String, BigInteger> groups, BigInteger total) {
        List<Map.Entry<String, BigInteger>> ordered = ordered(groups);
        if (ordered.isEmpty()) {
            screen.body.addView(screen.text("Belum ada transaksi untuk periode ini.", 14, screen.muted, false));
            return;
        }
        Map.Entry<String, BigInteger> largest = ordered.get(0);
        for (Map.Entry<String, BigInteger> item : ordered) {
            ItemCashCategoryBinding row = ItemCashCategoryBinding.inflate(getLayoutInflater(), screen.body, false);
            row.categoryName.setText(item.getKey());
            row.categoryAmount.setText(Transaction.rupiah(item.getValue()));
            row.categoryProgress.setProgress((int) Math.round(
                    row.categoryProgress.getMax() * item.getValue().doubleValue() / largest.getValue().doubleValue()));
            screen.body.addView(row.getRoot());
        }
        screen.space(screen.body, 34);
        screen.body.addView(screen.text(largest.getKey() + " · "
                + percent.format(largest.getValue().doubleValue() / total.doubleValue())
                + " dari total " + Transaction.rupiah(total) + ".", 16, screen.ink, false));
    }

    private List<Map.Entry<String, BigInteger>> ordered(Map<String, BigInteger> groups) {
        List<Map.Entry<String, BigInteger>> items = new ArrayList<>(groups.entrySet());
        items.sort(Map.Entry.<String, BigInteger>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        return items;
    }

    private CashScreen categories() {
        CashScreen screen = new CashScreen(this, "Anggaran kategori", this::back);
        screen.space(screen.body, 22);
        for (String category : Transaction.CATEGORIES) {
            BigInteger used = totals.getOrDefault(category, BigInteger.ZERO);
            long limit = preferences.getLong(categoryKey(category), 0);
            String detail = Transaction.rupiah(used) + (limit == 0 ? " · belum ada batas"
                    : " dari " + Transaction.rupiah(limit) + " · " + percent.format(used.doubleValue() / limit));
            addRow(screen, category, detail, R.drawable.ic_cash_categories, () -> {
                editingCategory = category;
                show("categoryEdit");
            });
        }
        return screen;
    }

    private String categoryKey(String category) {
        return "category_budget_" + monthKey + "_" + category;
    }

    private CashScreen limitEditor(String category) {
        boolean monthly = category == null;
        CashScreen screen = new CashScreen(this, monthly ? "Atur anggaran" : "Batas kategori", this::back);
        String key = monthly ? MainActivity.budgetKey(monthKey) : categoryKey(category);
        long current = monthly ? MainActivity.monthlyBudget(preferences, monthKey) : preferences.getLong(key, 0);
        screen.space(screen.body, 20);
        screen.body.addView(screen.text(monthly ? "Anggaran bulan ini" : category, 26, screen.ink, true));
        screen.space(screen.body, 36);
        activeLimit = screen.currency(monthly ? "Batas bulanan" : "Batas kategori",
                "Masukkan batas positif dalam rupiah");
        if (limitDraft == null) activeLimit.amount(current);
        else activeLimit.input.setText(limitDraft);
        screen.body.addView(activeLimit.group);
        screen.space(screen.body, 32);
        screen.body.addView(screen.text(monthly ? "Masukkan jumlah positif. Perubahan berlaku untuk periode "
                + "yang ditampilkan." : "Batas kategori menambah konteks pengeluaran pada bulan ini.",
                16, screen.ink, false));

        LinearLayout actions = screen.column();
        actions.addView(screen.button(monthly ? "Simpan anggaran" : "Simpan batas", true, () -> {
            try {
                long value = Long.parseLong(CashScreen.digits(activeLimit.value()));
                if (value <= 0) {
                    activeLimit.error("Masukkan batas lebih dari Rp0");
                    return;
                }
                preferences.edit().putLong(key, value).apply();
                Toast.makeText(this, monthly ? "Anggaran tersimpan" : "Batas tersimpan", Toast.LENGTH_SHORT).show();
                show(monthly ? "budget" : "categories");
            } catch (NumberFormatException e) {
                activeLimit.error("Masukkan batas positif yang valid");
            }
        }), screen.size(MATCH, WRAP));
        if (!monthly) {
            actions.addView(screen.button("Batalkan", false, this::back), screen.size(MATCH, WRAP));
        }
        screen.footer(actions);
        return screen;
    }

    private void addRow(CashScreen screen, String title, String detail, int icon, Runnable click) {
        screen.body.addView(screen.row(title, detail, icon, click), screen.size(MATCH, WRAP));
        screen.space(screen.body, 5);
    }
}
