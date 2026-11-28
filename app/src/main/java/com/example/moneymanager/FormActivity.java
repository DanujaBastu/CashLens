package com.example.moneymanager;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static com.example.moneymanager.CashScreen.MATCH;
import static com.example.moneymanager.CashScreen.WRAP;

public class FormActivity extends AppCompatActivity {
    static final String EXTRA_INCOME = "income";
    static final String EXTRA_NOTES = "notes";
    static final String EXTRA_OPEN_RECEIPT = "open_receipt";

    private final Calendar date = Calendar.getInstance();
    private final Calendar dateDraft = Calendar.getInstance();
    private final SimpleDateFormat storedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat visibleDate = new SimpleDateFormat("d MMM yyyy", MainActivity.INDONESIAN);
    private DBHelper db;
    private long id;
    private String category = "";
    private String originalCategory = "";
    private String location = "";
    private String photoPath;
    private String originalPhoto;
    private File pendingFile;
    private boolean income;
    private boolean card;
    private CashScreen form;
    private CashScreen.Field amount;
    private CashScreen.Field title;
    private CashScreen.Field store;
    private CashScreen.Field notes;
    private TextView amountLabel;
    private TextView categoryLabel;
    private TextView categoryHelp;
    private TextView dateLabel;
    private TextView locationLabel;
    private LinearLayout categoryButton;
    private MaterialButton example;
    private ImageView receiptView;
    private String page = "form";

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (pendingFile == null) return;
                if (ok) {
                    replacePhoto(pendingFile.getAbsolutePath());
                    showPhoto();
                } else {
                    pendingFile.delete();
                }
                pendingFile = null;
            });

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) copyFromUri(uri);
            });

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new DBHelper(this);
        id = getIntent().getLongExtra("id", -1);
        Transaction existing = id == -1 ? null : db.getById(id);
        if (id != -1 && existing == null) {
            finish();
            return;
        }
        if (existing != null) {
            try {
                date.setTime(storedDate.parse(existing.date));
            } catch (ParseException ignored) {
            }
            originalCategory = existing.category;
            category = Transaction.displayCategory(originalCategory);
            location = existing.location;
            photoPath = existing.photoPath;
            originalPhoto = photoPath;
            income = existing.income;
            card = "Card".equals(existing.method);
        }
        buildForm(existing);
        if (state != null) {
            amount.input.setText(state.getString("amount", ""));
            title.input.setText(state.getString("title", ""));
            store.input.setText(state.getString("store", ""));
            notes.input.setText(state.getString("notes", ""));
            category = state.getString("category", "");
            location = state.getString("location", "");
            income = state.getBoolean("income");
            card = state.getBoolean("card");
            date.setTimeInMillis(state.getLong("date"));
            dateDraft.setTimeInMillis(state.getLong("dateDraft"));
            photoPath = state.getString("photo");
            String pending = state.getString("pending");
            if (pending != null) pendingFile = new File(pending);
            page = state.getString("page", "form");
            if (state.getBoolean("exampleUsed")) example.setVisibility(View.GONE);
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        switch (page) {
            case "category": showCategories(); break;
            case "date": showDate(); break;
            case "details": showDetails(); break;
            default: showForm();
        }
        if (state == null && existing == null && getIntent().getBooleanExtra(EXTRA_OPEN_RECEIPT, false)) {
            chooseReceiptPhoto();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (form == null) return;
        state.putString("amount", amount.value());
        state.putString("title", title.value());
        state.putString("store", store.value());
        state.putString("notes", notes.value());
        state.putString("category", category);
        state.putString("location", location);
        state.putBoolean("income", income);
        state.putBoolean("card", card);
        state.putLong("date", date.getTimeInMillis());
        state.putLong("dateDraft", dateDraft.getTimeInMillis());
        state.putString("photo", photoPath);
        state.putString("page", page);
        state.putBoolean("exampleUsed", example.getVisibility() == View.GONE);
        if (pendingFile != null) state.putString("pending", pendingFile.getAbsolutePath());
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) replacePhoto(originalPhoto);
        db.close();
        super.onDestroy();
    }

    private void buildForm(Transaction existing) {
        form = new CashScreen(this, existing == null ? "Tambah transaksi" : "Edit transaksi", this::back);
        amount = form.currency("Jumlah pengeluaran", "Masukkan jumlah dalam rupiah");
        amountLabel = amount.label;
        title = form.field("Judul transaksi", "Contoh: Makan siang", "", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 16);
        store = form.field("Toko (opsional)", "Nama toko", "", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS, 16);
        notes = form.field("Catatan (opsional)", "Tambahkan catatan", "", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 16);

        form.body.addView(amount.group);
        form.space(form.body, 8);
        form.body.addView(title.group);
        form.space(form.body, 14);
        categoryLabel = form.text("Kategori", 12, form.muted, true);
        form.body.addView(categoryLabel);
        categoryButton = picker(form, "Pilih kategori", R.drawable.ic_cash_categories, this::showCategories);
        form.body.addView(categoryButton);
        categoryHelp = form.text("Pilih kategori transaksi", 12, form.muted, true);
        categoryHelp.setPadding(0, form.dp(3), 0, 0);
        categoryHelp.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.body.addView(categoryHelp);
        form.space(form.body, 8);
        form.body.addView(form.text("Tanggal", 12, form.muted, true));
        LinearLayout dateButton = picker(form, "", R.drawable.ic_cash_calendar, () -> {
            dateDraft.setTimeInMillis(date.getTimeInMillis());
            showDate();
        });
        dateLabel = (TextView) dateButton.getChildAt(1);
        form.body.addView(dateButton);
        form.body.addView(form.text("Gunakan tanggal transaksi", 12, form.muted, true));
        form.space(form.body, 8);
        form.body.addView(store.group);
        form.space(form.body, 8);
        form.body.addView(notes.group);
        form.space(form.body, 10);
        LinearLayout locationRow = form.row("Lokasi transaksi", "Opsional · pilih atau lewati",
                R.drawable.ic_cash_map, this::editLocation);
        locationRow.setMinimumHeight(form.dp(48));
        locationLabel = (TextView) ((LinearLayout) locationRow.getChildAt(1)).getChildAt(1);
        form.body.addView(locationRow);

        LinearLayout actions = form.horizontal();
        example = form.button("Isi contoh", false, this::fillExample);
        example.setVisibility(existing == null ? View.VISIBLE : View.GONE);
        actions.addView(example, new LinearLayout.LayoutParams(0, WRAP, 1f));
        MaterialButton next = form.button("Lanjut", true, () -> {
            if (validate() > 0) showDetails();
        });
        actions.addView(next, new LinearLayout.LayoutParams(0, WRAP, 1f));
        form.footer(actions);

        if (existing != null) {
            amount.input.setText(Long.toString(existing.amount));
            title.input.setText(existing.title.isEmpty()
                    ? (existing.notes.isEmpty() ? category : existing.notes) : existing.title);
            store.input.setText(existing.store);
            notes.input.setText(existing.notes);
        } else {
            notes.input.setText(getIntent().getStringExtra(EXTRA_NOTES));
        }
    }

    private LinearLayout picker(CashScreen screen, String value, int icon, Runnable action) {
        LinearLayout row = screen.horizontal();
        row.setMinimumHeight(screen.dp(48));
        row.setPadding(screen.dp(12), 0, screen.dp(12), 0);
        row.setBackground(screen.clickable(screen.surface, 8, CashScreen.FIELD_OUTLINE));
        row.setOnClickListener(v -> action.run());
        row.setFocusable(true);
        row.addView(screen.icon(icon), screen.size(22, 22));
        TextView label = screen.text(value, 16, screen.ink, false);
        label.setPadding(screen.dp(10), 0, screen.dp(8), 0);
        row.addView(label, new LinearLayout.LayoutParams(0, WRAP, 1f));
        ImageView arrow = screen.icon(R.drawable.ic_cash_chevron);
        arrow.setRotation(90);
        row.addView(arrow, screen.size(20, 20));
        return row;
    }

    private void showForm() {
        page = "form";
        amountLabel.setText(income ? "Jumlah pemasukan" : "Jumlah pengeluaran");
        ((TextView) categoryButton.getChildAt(1)).setText(category.isEmpty() ? "Pilih kategori" : category);
        dateLabel.setText(visibleDate.format(date.getTime()));
        locationLabel.setText(location.isEmpty() ? "Opsional · pilih atau lewati" : location);
        setContentView(form.root);
        form.root.requestFocus();
    }

    private void showCategories() {
        hideKeyboard();
        page = "category";
        CashScreen screen = new CashScreen(this, "Pilih kategori", this::back);
        screen.space(screen.body, 10);
        for (String value : Transaction.CATEGORIES) {
            screen.body.addView(screen.row(value, "Pilih kategori", R.drawable.ic_cash_categories, () -> {
                category = value;
                setCategoryError(false);
                showForm();
            }), screen.size(MATCH, WRAP));
            screen.space(screen.body, 5);
        }
        setContentView(screen.root);
    }

    private void showDate() {
        hideKeyboard();
        page = "date";
        CashScreen screen = new CashScreen(this, "Pilih tanggal", this::back);
        screen.body.addView(screen.text("Gunakan tanggal ketika transaksi terjadi.", 14, screen.muted, false));
        screen.space(screen.body, 32);

        LinearLayout month = screen.horizontal();
        String label = new SimpleDateFormat("MMMM yyyy", MainActivity.INDONESIAN).format(dateDraft.getTime());
        month.addView(screen.text(label, 20, screen.ink, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        addMonthButton(screen, month, "‹", "Bulan sebelumnya", -1);
        addMonthButton(screen, month, "›", "Bulan berikutnya", 1);
        screen.body.addView(month);
        screen.space(screen.body, 8);

        LinearLayout weekday = screen.horizontal();
        for (String value : new String[]{"Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"}) {
            TextView day = screen.text(value, 12, screen.muted, true);
            day.setGravity(Gravity.CENTER);
            weekday.addView(day, new LinearLayout.LayoutParams(0, screen.dp(36), 1f));
        }
        screen.body.addView(weekday);
        Calendar first = (Calendar) dateDraft.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int cell = 0; cell < offset + days; ) {
            LinearLayout week = screen.horizontal();
            for (int column = 0; column < 7; column++, cell++) {
                final int dayNumber = cell - offset + 1;
                TextView day = screen.text("", 14, screen.ink, true);
                day.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, screen.dp(48), 1f);
                params.setMargins(screen.dp(1), 0, screen.dp(1), screen.dp(4));
                week.addView(day, params);
                if (dayNumber < 1 || dayNumber > days) continue;
                boolean selected = dayNumber == dateDraft.get(Calendar.DAY_OF_MONTH);
                day.setText(String.valueOf(dayNumber));
                day.setBackground(screen.clickable(selected ? screen.mint : screen.surface, 8, 0));
                day.setSelected(selected);
                day.setFocusable(true);
                day.setOnClickListener(view -> {
                    dateDraft.set(Calendar.DAY_OF_MONTH, dayNumber);
                    showDate();
                });
            }
            screen.body.addView(week);
        }
        screen.footer(screen.button("Gunakan tanggal", true, () -> {
            date.setTimeInMillis(dateDraft.getTimeInMillis());
            showForm();
        }));
        setContentView(screen.root);
    }

    private void addMonthButton(CashScreen screen, LinearLayout month, String label, String description, int delta) {
        MaterialButton button = screen.button(label, false, () -> {
            dateDraft.add(Calendar.MONTH, delta);
            showDate();
        });
        button.setContentDescription(description);
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setTextSize(22);
        month.addView(button, screen.size(48, 48));
    }

    private void editLocation() {
        CashScreen.Field input = form.field("Lokasi transaksi", "Nama tempat atau alamat", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 16);
        input.input.setText(location);
        LinearLayout container = form.column();
        container.setPadding(form.dp(24), form.dp(8), form.dp(24), 0);
        container.addView(input.group);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Lokasi transaksi")
                .setView(container)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Gunakan lokasi", (dialog, which) -> {
                    location = input.value();
                    locationLabel.setText(location.isEmpty() ? "Opsional · pilih atau lewati" : location);
                }).show();
    }

    private void fillExample() {
        amount.amount(39000);
        title.input.setText("Makan siang");
        category = Transaction.CATEGORIES[0];
        ((TextView) categoryButton.getChildAt(1)).setText(category);
        store.input.setText("Toko Harmoni");
        notes.input.setText("Roti dan susu");
        amount.error(null);
        title.error(null);
        setCategoryError(false);
        example.setVisibility(View.GONE);
    }

    private void showDetails() {
        hideKeyboard();
        page = "details";
        CashScreen screen = new CashScreen(this, "Detail transaksi", this::back);
        screen.body.addView(screen.text(title.value(), 24, screen.ink, true));
        screen.body.addView(screen.text(amount.value(), 20, screen.green, true));
        screen.space(screen.body, 20);
        screen.body.addView(screen.text("Jenis transaksi", 14, screen.muted, true));
        addToggle(screen, "Pengeluaran", "Pemasukan", income, checked -> income = checked);
        screen.space(screen.body, 12);
        screen.body.addView(screen.text("Metode pembayaran", 14, screen.muted, true));
        addToggle(screen, "Tunai", "Kartu", card, checked -> card = checked);
        screen.space(screen.body, 16);
        screen.body.addView(screen.row("Foto struk", "Opsional · kamera atau galeri",
                R.drawable.ic_cash_receipt, this::chooseReceiptPhoto), screen.size(MATCH, WRAP));
        receiptView = new ImageView(this);
        receiptView.setAdjustViewBounds(true);
        receiptView.setMaxHeight(screen.dp(220));
        receiptView.setContentDescription("Foto struk");
        screen.body.addView(receiptView, screen.size(MATCH, WRAP));
        showPhoto();
        LinearLayout actions = screen.column();
        actions.addView(screen.button("Simpan", true, this::save), screen.size(MATCH, WRAP));
        if (id != -1) {
            MaterialButton delete = screen.button("Hapus", false, () -> new MaterialAlertDialogBuilder(this)
                    .setTitle("Hapus transaksi?")
                    .setMessage("Data dan foto struk akan dihapus.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Hapus", (dialog, which) -> delete()).show());
            delete.setTextColor(CashScreen.ERROR);
            actions.addView(delete, screen.size(MATCH, WRAP));
        }
        screen.footer(actions);
        setContentView(screen.root);
    }

    private interface Choice { void change(boolean second); }

    private void addToggle(CashScreen screen, String first, String second, boolean secondSelected, Choice choice) {
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(this);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        ColorStateList fill = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{screen.mint, screen.surface});
        MaterialButton firstButton = screen.button(first, false, () -> {});
        MaterialButton secondButton = screen.button(second, false, () -> {});
        for (MaterialButton button : new MaterialButton[]{firstButton, secondButton}) {
            button.setId(View.generateViewId());
            button.setBackgroundTintList(fill);
            button.setStrokeColor(ColorStateList.valueOf(screen.outline));
            button.setStrokeWidth(screen.dp(1));
            group.addView(button, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        group.check(secondSelected ? secondButton.getId() : firstButton.getId());
        group.addOnButtonCheckedListener((buttons, checkedId, checked) -> {
            if (checked) choice.change(checkedId == secondButton.getId());
        });
        screen.body.addView(group, screen.size(MATCH, WRAP));
    }

    private void chooseReceiptPhoto() {
        new MaterialAlertDialogBuilder(this).setTitle("Foto struk")
                .setItems(new String[]{"Kamera", "Galeri"}, (dialog, which) -> {
                    if (which == 0) openCamera();
                    else pickImage.launch("image/*");
                }).show();
    }

    private void setCategoryError(boolean invalid) {
        categoryLabel.setTextColor(invalid ? CashScreen.ERROR : form.muted);
        categoryHelp.setTextColor(invalid ? CashScreen.ERROR : form.muted);
        categoryHelp.setText(invalid ? "Pilih satu kategori" : "Pilih kategori transaksi");
        categoryButton.setBackground(form.clickable(form.surface, 8,
                invalid ? CashScreen.ERROR : CashScreen.FIELD_OUTLINE));
    }

    private long validate() {
        long value = 0;
        String error = null;
        String digits = CashScreen.digits(amount.value());
        try {
            if (!digits.isEmpty()) value = Long.parseLong(digits);
            if (value <= 0) error = getString(R.string.cash_amount_positive);
        } catch (NumberFormatException e) {
            error = "Angka tidak valid";
        }
        amount.error(error);
        boolean titleMissing = title.value().isEmpty();
        title.error(titleMissing ? "Masukkan judul transaksi" : null);
        setCategoryError(category.isEmpty());
        return error == null && !titleMissing && !category.isEmpty() ? value : 0;
    }

    private void save() {
        long value = validate();
        if (value == 0) {
            showForm();
            return;
        }
        Transaction t = new Transaction();
        t.id = id;
        t.date = storedDate.format(date.getTime());
        t.amount = value;
        t.income = income;
        t.category = category.equals(Transaction.displayCategory(originalCategory)) ? originalCategory : category;
        t.method = card ? "Card" : "Cash";
        t.title = title.value();
        t.store = store.value();
        t.notes = notes.value();
        t.location = location;
        t.photoPath = photoPath;

        boolean isNew = id == -1;
        if (!(isNew ? db.insert(t) != -1 : db.update(t) == 1)) {
            Toast.makeText(this, "Transaksi gagal disimpan", Toast.LENGTH_SHORT).show();
            return;
        }
        if (originalPhoto != null && !originalPhoto.equals(photoPath)) new File(originalPhoto).delete();
        originalPhoto = photoPath;
        if (isNew) setResult(RESULT_OK, new Intent().putExtra(EXTRA_INCOME, income));
        else Toast.makeText(this, "Perubahan tersimpan", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void delete() {
        if (db.delete(id) != 1) {
            Toast.makeText(this, "Transaksi gagal dihapus", Toast.LENGTH_SHORT).show();
            return;
        }
        if (originalPhoto != null) new File(originalPhoto).delete();
        finish();
    }

    private void replacePhoto(String replacement) {
        if (photoPath != null && !photoPath.equals(originalPhoto) && !photoPath.equals(replacement)) {
            new File(photoPath).delete();
        }
        photoPath = replacement;
    }

    private void back() {
        if (page.equals("form")) finish();
        else showForm();
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) getSystemService(InputMethodManager.class)
                .hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }


    private File newImageFile() throws IOException {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("struk_", ".jpg", dir);
    }

    private void openCamera() {
        try {
            pendingFile = newImageFile();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pendingFile);
            takePicture.launch(uri);
        } catch (IOException | ActivityNotFoundException e) {
            if (pendingFile != null) {
                pendingFile.delete();
                pendingFile = null;
            }
            Toast.makeText(this, "Gagal membuka kamera", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyFromUri(Uri uri) {
        File file = null;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Image could not be opened");
            file = newImageFile();
            try (OutputStream out = new FileOutputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        } catch (IOException | SecurityException e) {
            if (file != null) file.delete();
            Toast.makeText(this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show();
            return;
        }
        replacePhoto(file.getAbsolutePath());
        showPhoto();
    }

    private void showPhoto() {
        if (receiptView == null) return;
        if (photoPath == null || !new File(photoPath).exists()) {
            receiptView.setVisibility(View.GONE);
            return;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(photoPath, o);
        int sample = 1;
        while (o.outWidth / sample > 1600) sample *= 2;

        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(photoPath, o);
        if (bmp == null) {
            receiptView.setVisibility(View.GONE);
            return;
        }

        try {
            ExifInterface exif = new ExifInterface(photoPath);
            int ori = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int deg = ori == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : ori == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : ori == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (deg != 0) {
                Matrix m = new Matrix();
                m.postRotate(deg);
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            }
        } catch (IOException ignored) {
        }

        receiptView.setImageBitmap(bmp);
        receiptView.setVisibility(View.VISIBLE);
    }
}
