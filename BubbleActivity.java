package com.notes.app;

import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.*;

public class BubbleActivity extends AppCompatActivity {
    FrameLayout bubblesLayer;
    List<NoteItem> notesList = new ArrayList<>();
    SharedPreferences prefs;
    BubbleView canvas;
    long currentFolderId = -1;

    // Музыка
    MediaPlayer mediaPlayer;
    boolean isMusicPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bubble);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        bubblesLayer = findViewById(R.id.bubblesLayer);
        prefs = getSharedPreferences("notes_prefs", MODE_PRIVATE);
        currentFolderId = getIntent().getLongExtra("open_folder_id", -1);
        findViewById(R.id.btnBackToList).setOnClickListener(v -> finish());
        findViewById(R.id.btnExitFolder).setOnClickListener(v -> {
            currentFolderId = prefs.getLong("parentId_" + currentFolderId, -1);
            if (currentFolderId == -1) v.setVisibility(View.GONE);
            refresh();
        });

        // Кнопка музыки
        ImageButton btnMusic = findViewById(R.id.btnMusic);
        btnMusic.setOnClickListener(v -> toggleMusic(btnMusic));
    }

    void toggleMusic(ImageButton btn) {
        if (isMusicPlaying) {
            // Останавливаем музыку
            if (mediaPlayer != null) {
                mediaPlayer.pause();
            }
            isMusicPlaying = false;
            btn.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
        } else {
            // Запускаем музыку
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(this, R.raw.background_music);
                mediaPlayer.setLooping(true); // зацикливаем
                mediaPlayer.setVolume(0.7f, 0.7f); // громкость 70%
            }
            mediaPlayer.start();
            isMusicPlaying = true;
            btn.setImageResource(android.R.drawable.ic_lock_silent_mode);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Приостанавливаем музыку когда уходим с экрана
        if (mediaPlayer != null && isMusicPlaying) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        // Возобновляем музыку если она играла
        if (mediaPlayer != null && isMusicPlaying) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Освобождаем ресурсы
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    void refresh() {
        bubblesLayer.removeAllViews();
        notesList.clear();
        String ids = prefs.getString("all_ids", "");
        if (!ids.isEmpty()) {
            Random r = new Random();
            for (String sId : ids.split(",")) {
                long id = Long.parseLong(sId);
                NoteItem item = new NoteItem(id, prefs.getString("title_"+id,""), "", "", prefs.getInt("color_"+id, Color.GRAY));
                item.x = prefs.getFloat("bubble_x_"+id, 300 + r.nextInt(300));
                item.y = prefs.getFloat("bubble_y_"+id, 500 + r.nextInt(400));
                item.isFolder = prefs.getBoolean("isFolder_"+id, false);
                item.parentId = prefs.getLong("parentId_"+id, -1);
                item.scale = prefs.getFloat("scale_" + id, 1.0f);
                notesList.add(item);
            }
        }

        canvas = new BubbleView(this, notesList, new BubbleView.Listener() {
            @Override public void onTap(NoteItem n) {
                if (n.isFolder) { currentFolderId = n.id; refresh(); }
                else {
                    Intent i = new Intent(BubbleActivity.this, NoteEditorActivity.class);
                    i.putExtra("note_id", n.id); startActivity(i);
                }
            }
            @Override public void onDragEnd(long id, float x, float y) {
                prefs.edit().putFloat("bubble_x_" + id, x).putFloat("bubble_y_" + id, y).apply();
            }
            @Override public void onMerge(NoteItem dragged, NoteItem target) {
                if (target.isFolder) { prefs.edit().putLong("parentId_"+dragged.id, target.id).apply(); refresh(); }
                else showMergeDialog(dragged, target);
            }
            @Override public void onLongPress(NoteItem n) {
                String[] opts = {"Маленький", "Средний", "Большой", "Огромный", "Вынести из группы"};
                new AlertDialog.Builder(BubbleActivity.this).setTitle("Настройка бабла")
                    .setItems(opts, (d, w) -> {
                        if (w == 4) {
                            if (currentFolderId != -1) {
                                long gpId = prefs.getLong("parentId_" + currentFolderId, -1);
                                prefs.edit().putLong("parentId_" + n.id, gpId).apply();
                                refresh();
                            }
                        } else {
                            float s = 1.0f; if(w==0)s=0.6f; else if(w==2)s=1.6f; else if(w==3)s=2.4f;
                            prefs.edit().putFloat("scale_"+n.id, s).apply(); refresh();
                        }
                    }).show();
            }
        });
        canvas.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        bubblesLayer.addView(canvas);
        canvas.setFolder(currentFolderId);
        findViewById(R.id.btnExitFolder).setVisibility(currentFolderId == -1 ? View.GONE : View.VISIBLE);
        applyTheme();
    }

    void showMergeDialog(NoteItem n1, NoteItem n2) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        final EditText input = new EditText(this); input.setHint("Название группы");
        b.setTitle("Новая группа").setView(input).setPositiveButton("Ок", (d, w) -> {
            long fId = System.currentTimeMillis();
            SharedPreferences.Editor e = prefs.edit();
            String ids = prefs.getString("all_ids", "");
            e.putString("all_ids", ids + "," + fId).putString("title_" + fId, input.getText().toString().isEmpty() ? "Группа" : input.getText().toString())
             .putBoolean("isFolder_" + fId, true).putInt("color_" + fId, Color.parseColor("#FFCA28"))
             .putFloat("bubble_x_" + fId, n2.x).putFloat("bubble_y_" + fId, n2.y)
             .putLong("parentId_" + n1.id, fId).putLong("parentId_" + n2.id, fId).putLong("parentId_" + fId, currentFolderId).apply();
            refresh();
        }).show();
    }

    void applyTheme() {
        boolean isPink = prefs.getBoolean("isPinkTheme", false);
        int c = isPink ? Color.parseColor("#F48FB1") : Color.parseColor("#00897B");
        findViewById(R.id.tvHeader).setBackgroundColor(c);
        findViewById(R.id.btnBackToList).setBackgroundColor(c);
        findViewById(R.id.btnExitFolder).setBackgroundColor(c);
        findViewById(R.id.btnMusic).setBackgroundColor(c);
    }
}