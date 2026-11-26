package com.example.moneymanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.moneymanager.databinding.ItemTransactionBinding;

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
    public void onBindViewHolder(@NonNull VH h, int position) {
        Transaction t = data.get(position);
        boolean startsDay = position == 0 || !t.date.equals(data.get(position - 1).date);
        h.b.tvDay.setVisibility(startsDay ? View.VISIBLE : View.GONE);
        h.b.tvDay.setText(t.date);
        h.b.dayDivider.setVisibility(startsDay && position > 0 ? View.VISIBLE : View.GONE);
        h.b.tvNotes.setText(t.notes.isEmpty() ? "(tanpa catatan)" : t.notes);
        h.b.tvCategory.setText(t.category + (t.photoPath != null ? "  •  ada struk" : ""));
        h.b.tvAmount.setText((t.income ? "+" : "−") + Transaction.rupiah(t.amount));
        h.b.tvAmount.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                t.income ? R.color.income : R.color.expense));
        h.b.tvMethod.setTextColor(h.b.tvAmount.getCurrentTextColor());
        h.b.transactionCard.setCardBackgroundColor(ContextCompat.getColor(h.itemView.getContext(),
                t.income ? R.color.income_surface : R.color.expense_surface));
        h.b.tvMethod.setText(t.method.toUpperCase(Locale.ROOT));
        h.b.transactionCard.setOnClickListener(v -> listener.onClick(t));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemTransactionBinding b;

        VH(ItemTransactionBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
