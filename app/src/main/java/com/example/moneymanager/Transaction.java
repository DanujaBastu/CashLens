package com.example.moneymanager;

import java.text.NumberFormat;
import java.util.Locale;

public class Transaction {
    static final String OTHER_CATEGORY = "Lainnya";
    static final String[] CATEGORIES = {
            "Makanan", "Transportasi", "Kebutuhan Rumah", "Pakaian", "Hiburan", OTHER_CATEGORY
    };
    public long id;
    public String date;      // yyyy-MM-dd
    public long amount;
    public boolean income;
    public String category = OTHER_CATEGORY;
    public String method;    // "Cash" / "Card"
    public String notes;
    public String photoPath; // nullable

    public static String rupiah(Number value) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("in", "ID"));
        nf.setMaximumFractionDigits(0);
        return nf.format(value);
    }
}
