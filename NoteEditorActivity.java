package com.notes.app;

import androidx.appcompat.app.AppCompatActivity;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.view.ScaleGestureDetector;
import android.widget.*;
import java.util.*;
import java.text.SimpleDateFormat;

public class NoteEditorActivity extends AppCompatActivity {

    EditText editTitle, editContent;
    LinearLayout editorTopBar;
    View editorLayout;
    FrameLayout floatingLayer;
    long noteId = -1;
    int selectedColor = Color.WHITE;
    int selectedFontId = 0;
    SharedPreferences prefs;

    private static final String[] COLOR_NAMES = {
        "Белый","Розовый","Оранжевый","Жёлтый","Мятный","Голубой","Лавандовый"
    };
    private static final int[] COLOR_VALUES = {
        Color.WHITE,
        Color.parseColor("#F8BBD0"),
        Color.parseColor("#FFE0B2"),
        Color.parseColor("#FFF9C4"),
        Color.parseColor("#C8E6C9"),
        Color.parseColor("#B3E5FC"),
        Color.parseColor("#E1BEE7")
    };

    private static final String[] FONT_NAMES = {"Обычный","Моноширинный","С засечками"};
    private static final int REQUEST_PICK_IMAGE = 1001;
    private static final int REQUEST_PICK_AUDIO = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_editor);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        prefs = getSharedPreferences("notes_prefs", MODE_PRIVATE);
        noteId = getIntent().getLongExtra("note_id", -1);

        editTitle     = findViewById(R.id.editTitle);
        editContent   = findViewById(R.id.editContent);
        editorLayout  = findViewById(R.id.editorLayout);
        editorTopBar  = findViewById(R.id.editorTopBar);
        floatingLayer = findViewById(R.id.floatingLayer);

        if (noteId != -1) {
            editTitle.setText(prefs.getString("title_" + noteId, ""));
            editContent.setText(prefs.getString("note_" + noteId, ""));
            selectedColor  = prefs.getInt("color_" + noteId, Color.WHITE);
            selectedFontId = prefs.getInt("font_"  + noteId, 0);
            restoreMedia();
        }

        editorLayout.setBackgroundColor(selectedColor);
        applyFont();
        applyTheme();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnPalette).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Цвет заметки")
                .setItems(COLOR_NAMES, (d, which) -> {
                    selectedColor = COLOR_VALUES[which];
                    editorLayout.setBackgroundColor(selectedColor);
                }).show()
        );

        findViewById(R.id.btnFont).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Шрифт")
                .setItems(FONT_NAMES, (d, which) -> {
                    selectedFontId = which;
                    applyFont();
                }).show()
        );

        findViewById(R.id.btnMedia).setOnClickListener(v -> {
            String[] opts = {"Прикрепить фото", "Прикрепить аудио"};
            new AlertDialog.Builder(this)
                .setTitle("Добавить медиа")
                .setItems(opts, (d, which) -> {
                    if (which == 0) {
                        Intent pick = new Intent(Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        startActivityForResult(pick, REQUEST_PICK_IMAGE);
                    } else {
                        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                        pick.setType("audio/*");
                        startActivityForResult(
                            Intent.createChooser(pick, "Выбрать аудио"), REQUEST_PICK_AUDIO);
                    }
                }).show();
        });

        findViewById(R.id.btnSetReminder).setOnClickListener(v -> showReminderDialog());
    }

    void restoreMedia() {
        String saved = prefs.getString("media_img_" + noteId, "");
        if (saved.isEmpty()) return;
        // Восстанавливаем позиции если сохранены
        String[] uris = saved.split("\\|");
        String posSaved = prefs.getString("media_pos_" + noteId, "");
        String[] positions = posSaved.isEmpty() ? new String[0] : posSaved.split(";");
        for (int i = 0; i < uris.length; i++) {
            if (uris[i].isEmpty()) continue;
            float px = 40f, py = 80f + i * 20f;
            if (i < positions.length && !positions[i].isEmpty()) {
                try {
                    String[] xy = positions[i].split(",");
                    px = Float.parseFloat(xy[0]);
                    py = Float.parseFloat(xy[1]);
                } catch (Exception ignored) {}
            }
            addFloatingImage(Uri.parse(uris[i]), px, py);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == REQUEST_PICK_IMAGE) {
            long id = ensureNoteId();
            String existing = prefs.getString("media_img_" + id, "");
            String updated  = existing.isEmpty() ? uri.toString() : existing + "|" + uri;
            prefs.edit().putString("media_img_" + id, updated).apply();
            // Начальная позиция — по центру экрана чуть ниже тулбара
            addFloatingImage(uri, 40f, 100f);
            Toast.makeText(this, "Фото прикреплено — перетащи куда нужно", Toast.LENGTH_SHORT).show();
        } else if (requestCode == REQUEST_PICK_AUDIO) {
            prefs.edit().putString("media_audio_" + ensureNoteId(), uri.toString()).apply();
            Toast.makeText(this, "Аудио прикреплено", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Добавляет фото как свободно плавающий элемент поверх заметки.
     * Можно перетаскивать по всему экрану и зумить.
     */
    void addFloatingImage(Uri uri, float startX, float startY) {
        // Контейнер: тень + кнопка закрыть
        FrameLayout card = new FrameLayout(this);
        int sizePx = (int)(200 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sizePx, sizePx);
        card.setLayoutParams(lp);
        card.setElevation(8 * getResources().getDisplayMetrics().density);
        card.setX(startX);
        card.setY(startY);

        // Фото
        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try { iv.setImageURI(uri); } catch (Exception e) { return; }

        // Кнопка удалить
        TextView btnDel = new TextView(this);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.END);
        btnDel.setLayoutParams(dlp);
        btnDel.setText("✕");
        btnDel.setTextSize(14);
        btnDel.setTextColor(Color.WHITE);
        btnDel.setBackgroundColor(Color.parseColor("#CC000000"));
        btnDel.setPadding(10, 4, 10, 4);
        btnDel.setOnClickListener(v -> floatingLayer.removeView(card));

        card.addView(iv);
        card.addView(btnDel);

        // Drag всей карточки + pinch-zoom ImageView
        attachDragAndZoom(card, iv);

        floatingLayer.addView(card);
    }

    void attachDragAndZoom(FrameLayout card, ImageView iv) {
        final float[] scale  = {1f};
        final float[] downX  = {0f};
        final float[] downY  = {0f};
        final float[] cardX0 = {0f};
        final float[] cardY0 = {0f};

        ScaleGestureDetector sgd = new ScaleGestureDetector(this,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    scale[0] = Math.max(0.4f, Math.min(4f, scale[0] * d.getScaleFactor()));
                    card.setScaleX(scale[0]);
                    card.setScaleY(scale[0]);
                    return true;
                }
            });

        card.setOnTouchListener((v, event) -> {
            sgd.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0]  = event.getRawX();
                    downY[0]  = event.getRawY();
                    cardX0[0] = card.getX();
                    cardY0[0] = card.getY();
                    card.bringToFront(); // поднять над другими фото
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 1) {
                        float nx = cardX0[0] + (event.getRawX() - downX[0]);
                        float ny = cardY0[0] + (event.getRawY() - downY[0]);
                        card.setX(nx);
                        card.setY(ny);
                    }
                    break;
            }
            return true;
        });
    }

    long ensureNoteId() {
        if (noteId == -1) {
            noteId = System.currentTimeMillis();
            String allIds = prefs.getString("all_ids", "");
            prefs.edit().putString("all_ids",
                allIds.isEmpty() ? String.valueOf(noteId) : allIds + "," + noteId).apply();
        }
        return noteId;
    }

    void showReminderDialog() {
        final Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) ->
            new TimePickerDialog(this, (tv, h, min) -> {
                cal.set(y, m, d, h, min, 0);
                long triggerMs = cal.getTimeInMillis();
                if (triggerMs <= System.currentTimeMillis()) {
                    Toast.makeText(this, "Время уже прошло!", Toast.LENGTH_SHORT).show();
                    return;
                }
                scheduleReminder(triggerMs);
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show(),
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    void scheduleReminder(long triggerMs) {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderBroadcast.class);
        intent.putExtra("note_title", editTitle.getText().toString());
        PendingIntent pi = PendingIntent.getBroadcast(this, (int) ensureNoteId(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) am.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        String time = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
            .format(new Date(triggerMs));
        Toast.makeText(this, "Напоминание: " + time, Toast.LENGTH_SHORT).show();
    }

    void applyFont() {
        if (editContent == null) return;
        Typeface tf;
        switch (selectedFontId) {
            case 1:  tf = Typeface.MONOSPACE; break;
            case 2:  tf = Typeface.SERIF;     break;
            default: tf = Typeface.DEFAULT;   break;
        }
        editTitle.setTypeface(tf);
        editContent.setTypeface(tf);
    }

    void applyTheme() {
        if (editorTopBar != null) {
            boolean isPink = prefs.getBoolean("isPinkTheme", false);
            editorTopBar.setBackgroundColor(
                isPink ? Color.parseColor("#F48FB1") : Color.parseColor("#00897B"));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNote();
        saveMediaPositions();
    }

    void saveMediaPositions() {
        if (noteId == -1) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < floatingLayer.getChildCount(); i++) {
            View v = floatingLayer.getChildAt(i);
            if (i > 0) sb.append(";");
            sb.append(v.getX()).append(",").append(v.getY());
        }
        prefs.edit().putString("media_pos_" + noteId, sb.toString()).apply();
    }

    void saveNote() {
        if (editTitle == null || editContent == null) return;
        String t = editTitle.getText().toString().trim();
        String c = editContent.getText().toString().trim();
        if (t.isEmpty() && c.isEmpty()) return;
        long id = ensureNoteId();
        prefs.edit()
            .putString("title_" + id, t)
            .putString("note_"  + id, c)
            .putInt   ("color_" + id, selectedColor)
            .putInt   ("font_"  + id, selectedFontId)
            .putString("date_"  + id,
                new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(new Date()))
            .apply();
    }
}