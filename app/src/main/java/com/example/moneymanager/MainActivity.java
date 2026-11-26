package com.example.moneymanager;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.moneymanager.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;

import java.math.BigInteger;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private DBHelper db;
    private TransactionAdapter adapter;
    private ObjectAnimator saveAnimation;
    private final ActivityResultLauncher<Intent> addTransaction =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    showSaved(data.getBooleanExtra(FormActivity.EXTRA_INCOME, false));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        db = new DBHelper(this);
        adapter = new TransactionAdapter(t -> {
            Intent i = new Intent(this, FormActivity.class);
            i.putExtra("id", t.id);
            startActivity(i);
        });
        b.recycler.setLayoutManager(new LinearLayoutManager(this));
        b.recycler.setAdapter(adapter);

        b.fabAdd.setOnClickListener(v -> addTransaction.launch(new Intent(this, FormActivity.class)));
        b.btnAnalysis.setOnClickListener(v -> startActivity(new Intent(this, AnalysisActivity.class)));
    }

    private void showSaved(boolean income) {
        if (saveAnimation != null) saveAnimation.cancel();
        float offset = 12 * getResources().getDisplayMetrics().density * (income ? 1 : -1);
        saveAnimation = ObjectAnimator.ofPropertyValuesHolder(b.tvTotal,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, offset, 0f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.45f, 1f));
        saveAnimation.setDuration(320);
        saveAnimation.setInterpolator(new DecelerateInterpolator());
        saveAnimation.start();

        Snackbar.make(b.getRoot(),
                        income ? "Pemasukan ditambahkan" : "Pengeluaran ditambahkan",
                        Snackbar.LENGTH_SHORT)
                .setAnchorView(b.fabAdd)
                .setBackgroundTint(ContextCompat.getColor(this,
                        income ? R.color.income_surface : R.color.expense_surface))
                .setTextColor(ContextCompat.getColor(this, income ? R.color.income : R.color.expense))
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<Transaction> list = db.getAll();
        adapter.submit(list);

        BigInteger income = BigInteger.ZERO;
        BigInteger expense = BigInteger.ZERO;
        for (Transaction t : list) {
            BigInteger amount = BigInteger.valueOf(t.amount);
            if (t.income) income = income.add(amount);
            else expense = expense.add(amount);
        }
        b.tvTotal.setText(Transaction.rupiah(income.subtract(expense)));
        b.tvExpense.setText(Transaction.rupiah(expense));
        b.tvIncome.setText(Transaction.rupiah(income));
        b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
