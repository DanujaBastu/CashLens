package com.example.moneymanager;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import com.example.moneymanager.databinding.ActivityFormBinding;
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

public class FormActivity extends AppCompatActivity {

    static final String EXTRA_INCOME = "income";

    private ActivityFormBinding b;
    private DBHelper db;

    private long id = -1;
    private String photoPath;      // foto yang sedang dipilih
    private String originalPhoto;  // foto yang tersimpan di database
    private File pendingFile;      // file tujuan kamera

    private final Calendar cal = Calendar.getInstance();
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok && pendingFile != null) {
                    photoPath = pendingFile.getAbsolutePath();
                    showPhoto();
                } else if (pendingFile != null) {
                    pendingFile.delete();
                }
            });

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) copyFromUri(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = new DBHelper(this);
        b.inputCategory.setSimpleItems(Transaction.CATEGORIES);
        b.inputCategory.setText(Transaction.OTHER_CATEGORY, false);
        b.toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) updateSaveColor(checkedId == R.id.btnIncome);
        });
        updateSaveColor(false);
        b.etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable text) {
                String digits = text.toString().replaceAll("[^0-9]", "");
                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i > 0 && (digits.length() - i) % 3 == 0) formatted.append('.');
                    formatted.append(digits.charAt(i));
                }
                if (formatted.toString().contentEquals(text)) return;

                int cursor = Math.max(0, Math.min(text.length(), b.etAmount.getSelectionStart()));
                int digitsBeforeCursor = text.subSequence(0, cursor).toString()
                        .replaceAll("[^0-9]", "").length();
                int newCursor = 0;
                while (digitsBeforeCursor > 0 && newCursor < formatted.length()) {
                    if (formatted.charAt(newCursor++) != '.') digitsBeforeCursor--;
                }
                text.replace(0, text.length(), formatted);
                b.etAmount.setSelection(newCursor);
            }
        });

        id = getIntent().getLongExtra("id", -1);
        if (id != -1) {
            Transaction t = db.getById(id);
            if (t != null) fillForm(t);
            b.tvTitle.setText("Edit Transaksi");
            b.btnDelete.setVisibility(View.VISIBLE);
        } else {
            b.etDate.setText(fmt.format(cal.getTime()));
            b.toggleMethod.check(R.id.btnCash);
        }

        b.etDate.setOnClickListener(v -> new DatePickerDialog(this, (view, y, m, d) -> {
            cal.set(y, m, d);
            b.etDate.setText(fmt.format(cal.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show());

        b.btnPhoto.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("Foto Struk")
                .setItems(new String[]{"Kamera", "Galeri"}, (d, which) -> {
                    if (which == 0) openCamera();
                    else pickImage.launch("image/*");
                }).show());

        b.btnSave.setOnClickListener(v -> save());
        b.btnDelete.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("Hapus transaksi?")
                .setMessage("Data dan foto struk akan dihapus.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (d, w) -> delete())
                .show());
    }

    private void fillForm(Transaction t) {
        try {
            cal.setTime(fmt.parse(t.date));
        } catch (ParseException ignored) {
        }
        b.etDate.setText(t.date);
        b.etAmount.setText(String.valueOf(t.amount));
        b.toggleType.check(t.income ? R.id.btnIncome : R.id.btnExpense);
        b.inputCategory.setText(t.category, false);
        b.etNotes.setText(t.notes);
        b.toggleMethod.check("Card".equals(t.method) ? R.id.btnCard : R.id.btnCash);
        photoPath = t.photoPath;
        originalPhoto = t.photoPath;
        showPhoto();
    }

    private void updateSaveColor(boolean income) {
        int color = ContextCompat.getColor(this, income ? R.color.income : R.color.expense);
        b.btnSave.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    // ---------- CREATE / UPDATE ----------
    private void save() {
        String amountStr = b.etAmount.getText().toString().replace(".", "");
        if (amountStr.isEmpty()) {
            b.tilAmount.setError("Jumlah uang wajib diisi");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(amountStr);
        } catch (NumberFormatException e) {
            b.tilAmount.setError("Angka tidak valid");
            return;
        }
        b.tilAmount.setError(null);

        Transaction t = new Transaction();
        t.id = id;
        t.date = b.etDate.getText().toString();
        t.amount = amount;
        t.income = b.toggleType.getCheckedButtonId() == R.id.btnIncome;
        t.category = b.inputCategory.getText().toString();
        t.method = b.toggleMethod.getCheckedButtonId() == R.id.btnCard ? "Card" : "Cash";
        t.notes = b.etNotes.getText().toString().trim();
        t.photoPath = photoPath;

        boolean isNew = id == -1;
        boolean saved = isNew ? db.insert(t) != -1 : db.update(t) == 1;
        if (!saved) {
            Toast.makeText(this, "Transaksi gagal disimpan", Toast.LENGTH_SHORT).show();
            return;
        }

        // foto lama diganti -> hapus file lamanya
        if (originalPhoto != null && !originalPhoto.equals(photoPath)) new File(originalPhoto).delete();

        if (isNew) setResult(RESULT_OK, new Intent().putExtra(EXTRA_INCOME, t.income));
        else Toast.makeText(this, "Perubahan tersimpan", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ---------- DELETE ----------
    private void delete() {
        db.delete(id);
        if (originalPhoto != null) new File(originalPhoto).delete();
        finish();
    }

    // ---------- FOTO STRUK ----------
    private File newImageFile() throws IOException {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("struk_", ".jpg", dir);
    }

    private void openCamera() {
        try {
            pendingFile = newImageFile();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pendingFile);
            takePicture.launch(uri);
        } catch (IOException e) {
            Toast.makeText(this, "Gagal membuka kamera", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyFromUri(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            File f = newImageFile();
            try (OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            photoPath = f.getAbsolutePath();
            showPhoto();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPhoto() {
        if (photoPath == null || !new File(photoPath).exists()) {
            b.imgReceipt.setVisibility(View.GONE);
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
            b.imgReceipt.setVisibility(View.GONE);
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

        b.imgReceipt.setImageBitmap(bmp);
        b.imgReceipt.setVisibility(View.VISIBLE);
    }
}
