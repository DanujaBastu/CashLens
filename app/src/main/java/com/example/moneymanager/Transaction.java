package com.example.moneymanager;

import java.text.NumberFormat;
import java.util.Locale;

public class Transaction {
    static final String OTHER_CATEGORY = "Lainnya";
    static final String[] CATEGORIES = {
            "Makanan dan minuman", "Transportasi", "Belanja", "Pendidikan", "Hiburan",
            "Kesehatan", OTHER_CATEGORY
    };

    static String displayCategory(String category) {
        switch (category) {
            case "Makanan": return "Makanan dan minuman";
            case "Kebutuhan Rumah":
            case "Pakaian": return "Belanja";
            default: return category;
        }
    }
    public long id;
    public String date;      // yyyy-MM-dd
    public long amount;
    public boolean income;
    public String category = OTHER_CATEGORY;
    public String method;    // "Cash" / "Card"
    public String notes;
    public String title;
    public String store;
    public String location;
    public String photoPath; // nullable

    public static String rupiah(Number value) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("in", "ID"));
        nf.setMaximumFractionDigits(0);
        return nf.format(value);
    }
}
