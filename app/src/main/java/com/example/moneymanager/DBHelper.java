package com.example.moneymanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "money.db";
    private static final int DB_VERSION = 2;
    private static final String TABLE = "transactions";

    public DBHelper(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "date TEXT NOT NULL,"
                + "amount INTEGER NOT NULL,"
                + "income INTEGER NOT NULL DEFAULT 0,"
                + "category TEXT NOT NULL DEFAULT 'Lainnya',"
                + "method TEXT NOT NULL,"
                + "notes TEXT,"
                + "photo TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN income INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN category TEXT NOT NULL DEFAULT 'Lainnya'");
        }
    }

    private ContentValues toValues(Transaction t) {
        ContentValues v = new ContentValues();
        v.put("date", t.date);
        v.put("amount", t.amount);
        v.put("income", t.income ? 1 : 0);
        v.put("category", t.category);
        v.put("method", t.method);
        v.put("notes", t.notes);
        v.put("photo", t.photoPath);
        return v;
    }

    private Transaction fromCursor(Cursor c) {
        Transaction t = new Transaction();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.date = c.getString(c.getColumnIndexOrThrow("date"));
        t.amount = c.getLong(c.getColumnIndexOrThrow("amount"));
        t.income = c.getInt(c.getColumnIndexOrThrow("income")) == 1;
        t.category = c.getString(c.getColumnIndexOrThrow("category"));
        t.method = c.getString(c.getColumnIndexOrThrow("method"));
        t.notes = c.getString(c.getColumnIndexOrThrow("notes"));
        t.photoPath = c.getString(c.getColumnIndexOrThrow("photo"));
        if (t.notes == null) t.notes = "";
        return t;
    }

    // CREATE
    public long insert(Transaction t) {
        return getWritableDatabase().insert(TABLE, null, toValues(t));
    }

    // READ
    public List<Transaction> getAll() {
        List<Transaction> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE, null, null, null,
                null, null, "date DESC, id DESC")) {
            while (c.moveToNext()) list.add(fromCursor(c));
        }
        return list;
    }

    public Transaction getById(long id) {
        try (Cursor c = getReadableDatabase().query(TABLE, null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    // UPDATE
    public int update(Transaction t) {
        return getWritableDatabase().update(TABLE, toValues(t), "id=?",
                new String[]{String.valueOf(t.id)});
    }

    // DELETE
    public int delete(long id) {
        return getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }
}
