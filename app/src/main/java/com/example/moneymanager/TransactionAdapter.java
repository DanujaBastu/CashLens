package com.example.moneymanager;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.moneymanager.databinding.ItemTransactionBinding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.VH> {

    public interface OnItemClick {
        void onClick(Transaction t);
    }

    private final List<Transaction> data = new ArrayList<>();
    private final OnItemClick listener;

    public TransactionAdapter(OnItemClick listener) {
        this.listener = listener;
    }

    public void submit(List<Transaction> items) {
        data.clear();
        data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemTransactionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        bind(holder.binding, data.get(position), listener);
    }

    static void bind(ItemTransactionBinding row, Transaction transaction, OnItemClick listener) {
        Context context = row.getRoot().getContext();
        String category = Transaction.displayCategory(transaction.category);
        String previousTitle = transaction.notes.isEmpty() ? category : transaction.notes;
        row.tvNotes.setText(transaction.title.isEmpty() ? previousTitle : transaction.title);
        String amount = Transaction.rupiah(transaction.amount);
        row.tvAmount.setText(transaction.income ? context.getString(R.string.cash_transaction_income, amount) : amount);

        int method = "Card".equals(transaction.method) ? R.string.cash_card : R.string.cash_cash;
        String details = context.getString(method);
        if (transaction.photoPath != null) {
            details = context.getString(R.string.cash_transaction_detail, details,
                    context.getString(R.string.cash_receipt_detail));
        }
        row.tvDetails.setText(context.getString(R.string.cash_transaction_detail,
                displayDate(transaction.date), details));
        row.categoryIcon.setImageResource(iconFor(category));
        row.getRoot().setOnClickListener(v -> listener.onClick(transaction));
    }

    private static String displayDate(String storedDate) {
        try {
            return new SimpleDateFormat("d MMM yyyy", MainActivity.INDONESIAN)
                    .format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(storedDate));
        } catch (ParseException e) {
            return storedDate;
        }
    }

    private static int iconFor(String category) {
        switch (category) {
            case "Makanan dan minuman":
                return R.drawable.ic_cash_food;
            case "Transportasi":
                return R.drawable.ic_cash_transport;
            case "Belanja":
                return R.drawable.ic_cash_shopping;
            default:
                return R.drawable.ic_cash_receipt;
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemTransactionBinding binding;

        VH(ItemTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
