package com.example.moneymanager;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.moneymanager.databinding.ActivityMainBinding;
import com.example.moneymanager.databinding.ItemCashCategoryBinding;
import com.example.moneymanager.databinding.ItemTransactionBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    static final Locale INDONESIAN = new Locale("id", "ID");
    static final String EXTRA_INSIGHT_DESTINATION = "insight_destination";
    private static final String BUDGET_KEY = "monthly_budget";
    private static final String HISTORY_KEY = "showing_history";
    private static final int RECENT_LIMIT = 3;

    private ActivityMainBinding b;
    private DBHelper db;
    private TransactionAdapter adapter;
    private SharedPreferences preferences;
    private BigInteger monthlyExpense = BigInteger.ZERO;
    private ObjectAnimator saveAnimation;
    private OnBackPressedCallback historyBack;

    private final ActivityResultLauncher<Intent> addTransaction =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    showSaved(data.getBooleanExtra(FormActivity.EXTRA_INCOME, false));
                }
            });

    private final ActivityResultLauncher<Intent> recognizeVoice =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;

                ArrayList<String> matches = result.getData()
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) {
                    addTransaction.launch(new Intent(this, FormActivity.class)
                            .putExtra(FormActivity.EXTRA_NOTES, matches.get(0)));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && savedInstanceState == null) {
            getSplashScreen().setOnExitAnimationListener(splash -> splash.animate()
                    .alpha(0f)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(splash::remove)
                    .start());
        }
        setTheme(R.style.Theme_CashLens_Home);
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && savedInstanceState == null) {
            b.getRoot().setAlpha(0f);
            b.getRoot().animate().alpha(1f).setDuration(240).start();
        }

        db = new DBHelper(this);
        preferences = getSharedPreferences("cash_lens", MODE_PRIVATE);
        adapter = new TransactionAdapter(this::editTransaction);
        b.recycler.setLayoutManager(new LinearLayoutManager(this));
        b.recycler.setAdapter(adapter);

        historyBack = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                b.navigation.setSelectedItemId(R.id.navHome);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, historyBack);

        b.navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navInsight) {
                startActivity(new Intent(this, AnalysisActivity.class));
                return false;
            }
            showHistory(id == R.id.navHistory);
            return true;
        });

        if (savedInstanceState != null && savedInstanceState.getBoolean(HISTORY_KEY)) {
            b.navigation.setSelectedItemId(R.id.navHistory);
        }
        applyInsightDestination(getIntent());

        b.btnAll.setOnClickListener(v -> b.navigation.setSelectedItemId(R.id.navHistory));
        View.OnClickListener manualEntry = v -> addTransaction.launch(new Intent(this, FormActivity.class));
        b.btnAdd.setOnClickListener(manualEntry);
        b.btnAddHistory.setOnClickListener(manualEntry);
        b.btnManual.setOnClickListener(manualEntry);
        b.btnScan.setOnClickListener(v -> addTransaction.launch(new Intent(this, FormActivity.class)
                .putExtra(FormActivity.EXTRA_OPEN_RECEIPT, true)));
        b.btnVoice.setOnClickListener(v -> startVoiceEntry());
        b.btnSettings.setOnClickListener(v -> editBudget());
        b.budgetCard.setOnClickListener(v -> editBudget());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(HISTORY_KEY, historyBack.isEnabled());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyInsightDestination(intent);
    }

    private void applyInsightDestination(Intent intent) {
        int item = intent.getIntExtra(EXTRA_INSIGHT_DESTINATION, 0);
        if (item == R.id.navHome || item == R.id.navHistory) {
            b.navigation.setSelectedItemId(item);
            intent.removeExtra(EXTRA_INSIGHT_DESTINATION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(db.getAll());
    }

    @Override
    protected void onDestroy() {
        if (saveAnimation != null) saveAnimation.cancel();
        db.close();
        super.onDestroy();
    }

    private void showHistory(boolean show) {
        b.historyContent.setVisibility(show ? View.VISIBLE : View.GONE);
        b.homeContent.setVisibility(show ? View.GONE : View.VISIBLE);
        historyBack.setEnabled(show);
    }

    private void editTransaction(Transaction transaction) {
        startActivity(new Intent(this, FormActivity.class).putExtra("id", transaction.id));
    }

    private void startVoiceEntry() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, INDONESIAN.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.cash_voice_prompt));
        try {
            recognizeVoice.launch(intent);
        } catch (ActivityNotFoundException e) {
            Snackbar.make(b.getRoot(), R.string.cash_voice_unavailable, Snackbar.LENGTH_SHORT)
                    .setAnchorView(b.navigation)
                    .show();
        }
    }

    private void showSaved(boolean income) {
        if (!income && b.homeContent.getVisibility() == View.VISIBLE) {
            if (saveAnimation != null) saveAnimation.cancel();
            float offset = 12 * getResources().getDisplayMetrics().density;
            saveAnimation = ObjectAnimator.ofPropertyValuesHolder(b.tvTotal,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -offset, 0f),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0.45f, 1f));
            saveAnimation.setDuration(320);
            saveAnimation.setInterpolator(new DecelerateInterpolator());
            saveAnimation.start();
        }

        Snackbar.make(b.getRoot(),
                        income ? "Pemasukan ditambahkan" : "Pengeluaran ditambahkan",
                        Snackbar.LENGTH_SHORT)
                .setAnchorView(b.navigation)
                .show();
    }

    private void render(List<Transaction> transactions) {
        Date now = new Date();
        String monthPrefix = new SimpleDateFormat("yyyy-MM-", Locale.US).format(now);
        b.tvMonth.setText(new SimpleDateFormat("MMMM yyyy", INDONESIAN).format(now));

        Map<String, BigInteger> categoryTotals = new HashMap<>();
        BigInteger income = BigInteger.ZERO;
        BigInteger expense = BigInteger.ZERO;
        monthlyExpense = BigInteger.ZERO;
        for (Transaction transaction : transactions) {
            BigInteger amount = BigInteger.valueOf(transaction.amount);
            if (transaction.income) {
                income = income.add(amount);
            } else {
                expense = expense.add(amount);
                if (transaction.date.startsWith(monthPrefix)) {
                    monthlyExpense = monthlyExpense.add(amount);
                    if (amount.signum() > 0) {
                        categoryTotals.merge(Transaction.displayCategory(transaction.category), amount, BigInteger::add);
                    }
                }
            }
        }

        b.tvTotal.setText(Transaction.rupiah(monthlyExpense));
        b.tvHistorySummary.setText(getString(R.string.cash_history_summary,
                Transaction.rupiah(income.subtract(expense)), Transaction.rupiah(income),
                Transaction.rupiah(expense)));
        renderBudget();
        renderCategories(categoryTotals);
        renderTransactions(transactions);
    }

    private void renderBudget() {
        long budget = monthlyBudget(preferences, currentMonthKey());
        b.budgetProgress.setProgress(0);
        if (budget == 0) {
            b.tvBudgetUsed.setText(R.string.cash_budget_unset);
            b.tvBudget.setText(R.string.cash_placeholder);
            b.tvBudgetRemaining.setText(R.string.cash_placeholder);
            return;
        }

        double used = monthlyExpense.doubleValue() / budget;
        NumberFormat percent = NumberFormat.getPercentInstance(INDONESIAN);
        percent.setMaximumFractionDigits(1);
        b.budgetProgress.setProgress((int) Math.round(Math.min(used, 1d) * b.budgetProgress.getMax()));
        b.tvBudgetUsed.setText(getString(R.string.cash_budget_used, percent.format(used)));
        b.tvBudget.setText(Transaction.rupiah(budget));
        b.tvBudgetRemaining.setText(Transaction.rupiah(BigInteger.valueOf(budget).subtract(monthlyExpense)));
    }

    private void editBudget() {
        String month = currentMonthKey();
        long budget = monthlyBudget(preferences, month);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.cash_budget_hint);
        if (budget > 0) {
            input.setText(String.valueOf(budget));
            input.setSelection(input.length());
        }

        FrameLayout content = new FrameLayout(this);
        int inset = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(inset, 0, inset, 0);
        content.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cash_settings)
                .setView(content)
                .setNegativeButton(R.string.cash_cancel, null)
                .setPositiveButton(R.string.cash_save, null);
        if (budget > 0) {
            builder.setNeutralButton(R.string.cash_clear, (dialog, which) -> {
                preferences.edit().putLong(budgetKey(month), 0).apply();
                renderBudget();
            });
        }
        AlertDialog dialog = builder.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                long updated = Long.parseLong(input.getText().toString());
                if (updated <= 0) {
                    input.setError(getString(R.string.cash_budget_error));
                    return;
                }
                preferences.edit().putLong(budgetKey(month), updated).apply();
                renderBudget();
                dialog.dismiss();
            } catch (NumberFormatException e) {
                input.setError(getString(R.string.cash_budget_error));
            }
        });
    }

    static String budgetKey(String month) {
        return BUDGET_KEY + "_" + month;
    }

    static long monthlyBudget(SharedPreferences preferences, String month) {
        return preferences.getLong(budgetKey(month), preferences.getLong(BUDGET_KEY, 0));
    }

    private String currentMonthKey() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }

    private void renderCategories(Map<String, BigInteger> totals) {
        b.categoryRows.removeAllViews();
        b.categoriesEmpty.setVisibility(totals.isEmpty() ? View.VISIBLE : View.GONE);
        if (totals.isEmpty()) return;

        List<Map.Entry<String, BigInteger>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort(Map.Entry.<String, BigInteger>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        double largest = sorted.get(0).getValue().doubleValue();
        for (Map.Entry<String, BigInteger> entry : sorted) {
            ItemCashCategoryBinding row = ItemCashCategoryBinding.inflate(getLayoutInflater(), b.categoryRows, false);
            row.categoryName.setText(entry.getKey());
            row.categoryAmount.setText(Transaction.rupiah(entry.getValue()));
            double share = entry.getValue().doubleValue() / largest;
            row.categoryProgress.setProgress((int) Math.round(share * row.categoryProgress.getMax()));
            b.categoryRows.addView(row.getRoot());
        }
    }

    private void renderTransactions(List<Transaction> transactions) {
        adapter.submit(transactions);
        int emptyVisibility = transactions.isEmpty() ? View.VISIBLE : View.GONE;
        b.recentEmpty.setVisibility(emptyVisibility);
        b.historyEmpty.setVisibility(emptyVisibility);
        b.recentRows.removeAllViews();
        for (int index = 0; index < Math.min(RECENT_LIMIT, transactions.size()); index++) {
            ItemTransactionBinding row = ItemTransactionBinding.inflate(getLayoutInflater(), b.recentRows, false);
            TransactionAdapter.bind(row, transactions.get(index), this::editTransaction);
            b.recentRows.addView(row.getRoot());
        }
    }
}
