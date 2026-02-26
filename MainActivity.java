package com.notes.app;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.*;
import android.content.Intent;
import android.content.SharedPreferences;

public class MainActivity extends AppCompatActivity {
    
    RecyclerView recyclerView;
    View mainLayout, topBar;
    TextView tvMainTitle;
    ImageButton btnMainBack;
    
    // Кнопки нового меню (названия теперь строго соответствуют XML)
    FloatingActionButton fabMain, fabNote, fabSnake, fabMusic;
    boolean isMenuOpen = false;

    List<NoteItem> notes = new ArrayList<>();
    NoteAdapter adapter;
    SharedPreferences prefs;
    long currentFolderId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        prefs = getSharedPreferences("notes_prefs", MODE_PRIVATE);
        
        recyclerView = findViewById(R.id.recyclerView);
        mainLayout = findViewById(R.id.mainLayout);
        topBar = findViewById(R.id.topBar);
        tvMainTitle = findViewById(R.id.tvMainTitle);
        btnMainBack = findViewById(R.id.btnMainBack);

        // Инициализация кнопок (ID изменены на те, что в новом XML)
        fabMain = findViewById(R.id.fabMain);
        fabNote = findViewById(R.id.fabNote);
        fabSnake = findViewById(R.id.fabSnake);
        fabMusic = findViewById(R.id.fabMusic);

        fabMain.setOnClickListener(v -> toggleMenu());
        
        fabNote.setOnClickListener(v -> {
            toggleMenu();
            showCreateDialog();
        });
        
        fabSnake.setOnClickListener(v -> {
            toggleMenu();
            startActivity(new Intent(this, SnakeActivity.class));
        });
        
        fabMusic.setOnClickListener(v -> {
            Toast.makeText(this, "Музыка скоро будет!", Toast.LENGTH_SHORT).show();
        });

        btnMainBack.setOnClickListener(v -> goUp());

        adapter = new NoteAdapter(notes, new NoteAdapter.OnNoteClickListener() {
            @Override
            public void onNoteClick(long id) {
                if (prefs.getBoolean("isFolder_" + id, false)) {
                    currentFolderId = id; loadNotes();
                } else {
                    Intent i = new Intent(MainActivity.this, NoteEditorActivity.class);
                    i.putExtra("note_id", id); startActivity(i);
                }
            }
            @Override public void onDeleteClick(long id) { handleDelete(id); }
            @Override public void onLongClick(long id) { showContextMenu(id); }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition();
                int to = target.getAdapterPosition();
                Collections.swap(notes, from, to);
                adapter.notifyItemMoved(from, to);
                saveOrder();
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
        });
        touchHelper.attachToRecyclerView(recyclerView);

        findViewById(R.id.btnTheme).setOnClickListener(v -> {
            boolean isPink = !prefs.getBoolean("isPinkTheme", false);
            prefs.edit().putBoolean("isPinkTheme", isPink).apply();
            applyTheme();
        });

        findViewById(R.id.btnBubble).setOnClickListener(v -> {
            Intent intent = new Intent(this, BubbleActivity.class);
            intent.putExtra("open_folder_id", currentFolderId);
            startActivity(intent);
        });
    }

    private void toggleMenu() {
        if (!isMenuOpen) {
            // ПОКАЗЫВАЕМ
            fabNote.setVisibility(View.VISIBLE);
            fabSnake.setVisibility(View.VISIBLE);
            fabMusic.setVisibility(View.VISIBLE);

            // Выносим на передний план
            fabNote.bringToFront();
            fabSnake.bringToFront();
            fabMusic.bringToFront();
            fabMain.bringToFront();

            // Анимация: едем вверх и проявляемся
            fabNote.animate().translationY(-180f).alpha(1f).setDuration(300).start();
            fabSnake.animate().translationY(-340f).alpha(1f).setDuration(300).start();
            fabMusic.animate().translationY(-500f).alpha(1f).setDuration(300).start();
            
            fabMain.animate().rotation(45f).setDuration(300).start();
            isMenuOpen = true;
        } else {
            // ПРЯЧЕМ
            fabNote.animate().translationY(0).alpha(0f).setDuration(300).start();
            fabSnake.animate().translationY(0).alpha(0f).setDuration(300).start();
            fabMusic.animate().translationY(0).alpha(0f).setDuration(300).withEndAction(() -> {
                fabNote.setVisibility(View.INVISIBLE);
                fabSnake.setVisibility(View.INVISIBLE);
                fabMusic.setVisibility(View.INVISIBLE);
            }).start();

            fabMain.animate().rotation(0f).setDuration(300).start();
            isMenuOpen = false;
        }
    }

    void showCreateDialog() {
        String[] options = {"Новая заметка", "Новая группа"};
        new AlertDialog.Builder(this)
            .setTitle("Создать...")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    prefs.edit().putLong("temp_new_note_parent", currentFolderId).apply();
                    startActivity(new Intent(this, NoteEditorActivity.class));
                } else createFolder();
            }).show();
    }

    void createFolder() {
        final EditText input = new EditText(this);
        input.setHint("Название группы");
        new AlertDialog.Builder(this).setTitle("Новая группа").setView(input)
            .setPositiveButton("Создать", (d, w) -> {
                String name = input.getText().toString().trim();
                long id = System.currentTimeMillis();
                String ids = prefs.getString("all_ids", "");
                prefs.edit().putString("all_ids", (ids.isEmpty() ? "" : ids + ",") + id)
                     .putString("title_" + id, name.isEmpty() ? "Группа" : name)
                     .putBoolean("isFolder_" + id, true)
                     .putLong("parentId_" + id, currentFolderId)
                     .putInt("color_" + id, Color.parseColor("#FFD54F")).apply();
                loadNotes();
            }).show();
    }

    void showContextMenu(long id) {
        boolean isFolder = prefs.getBoolean("isFolder_" + id, false);
        String[] options = isFolder ? new String[]{"Удалить", "Переименовать", "Размер"} : 
                                     new String[]{"Удалить", "Переименовать", "Размер", "Вынести"};
        
        new AlertDialog.Builder(this).setTitle("Действия").setItems(options, (dialog, which) -> {
            String op = options[which];
            if (op.equals("Удалить")) handleDelete(id);
            else if (op.equals("Переименовать")) renameFolder(id);
            else if (op.equals("Размер")) showSizeDialog(id);
            else if (op.equals("Вынести")) moveNoteUp(id);
        }).show();
    }

    void showSizeDialog(long id) {
        String[] sizes = {"Маленький", "Средний", "Большой", "Огромный"};
        new AlertDialog.Builder(this).setTitle("Размер").setItems(sizes, (d, w) -> {
            float s = 1.0f;
            if (w == 0) s = 0.6f; else if (w == 2) s = 1.6f; else if (w == 3) s = 2.4f;
            prefs.edit().putFloat("scale_" + id, s).apply();
            loadNotes();
        }).show();
    }

    void handleDelete(long id) {
        if (prefs.getBoolean("isFolder_" + id, false)) {
            String allIds = prefs.getString("all_ids", "");
            for (String sId : allIds.split(",")) {
                if (!sId.isEmpty() && prefs.getLong("parentId_" + Long.parseLong(sId), -1) == id) {
                    prefs.edit().putLong("parentId_" + Long.parseLong(sId), currentFolderId).apply();
                }
            }
        }
        deleteNoteFromPrefs(id);
        loadNotes();
    }

    void deleteNoteFromPrefs(long id) {
        String ids = prefs.getString("all_ids", "");
        ids = ids.replace(id + ",", "").replace("," + id, "").replace(String.valueOf(id), "");
        prefs.edit().putString("all_ids", ids).remove("title_" + id).remove("note_" + id)
                    .remove("color_" + id).remove("isFolder_" + id).remove("parentId_" + id).remove("scale_" + id).apply();
    }

    void saveOrder() {
        String idString = prefs.getString("all_ids", "");
        if (idString.isEmpty()) return;
        List<String> allIds = new ArrayList<>(Arrays.asList(idString.split(",")));
        StringBuilder sb = new StringBuilder();
        for (String sId : allIds) {
            long id = Long.parseLong(sId);
            if (prefs.getLong("parentId_" + id, -1) != currentFolderId) {
                if (sb.length() > 0) sb.append(",");
                sb.append(id);
            }
        }
        for (NoteItem n : notes) {
            if (sb.length() > 0) sb.append(",");
            sb.append(n.id);
        }
        prefs.edit().putString("all_ids", sb.toString()).apply();
    }

    void renameFolder(long id) {
        final EditText input = new EditText(this);
        input.setText(prefs.getString("title_" + id, ""));
        new AlertDialog.Builder(this).setTitle("Переименовать").setView(input)
            .setPositiveButton("Готово", (d, w) -> {
                prefs.edit().putString("title_" + id, input.getText().toString()).apply();
                loadNotes();
            }).show();
    }

    void moveNoteUp(long id) {
        long grandParent = prefs.getLong("parentId_" + currentFolderId, -1);
        prefs.edit().putLong("parentId_" + id, grandParent).apply();
        loadNotes();
    }

    void goUp() {
        if (currentFolderId != -1) {
            currentFolderId = prefs.getLong("parentId_" + currentFolderId, -1);
            loadNotes();
        }
    }

    @Override public void onBackPressed() { if (currentFolderId != -1) goUp(); else super.onBackPressed(); }

    @Override protected void onResume() {
        super.onResume();
        long newParent = prefs.getLong("temp_new_note_parent", -2);
        if (newParent != -2) {
            String allIds = prefs.getString("all_ids", "");
            if (!allIds.isEmpty()) {
                String[] idsArray = allIds.split(",");
                long lastId = Long.parseLong(idsArray[idsArray.length - 1]);
                if (prefs.getLong("parentId_" + lastId, -1) == -1) prefs.edit().putLong("parentId_" + lastId, newParent).apply();
            }
            prefs.edit().remove("temp_new_note_parent").apply();
        }
        loadNotes();
        applyTheme();
    }

    void loadNotes() {
        notes.clear();
        if (currentFolderId == -1) { tvMainTitle.setText("Мои заметки"); btnMainBack.setVisibility(View.GONE); }
        else { tvMainTitle.setText("📁 " + prefs.getString("title_" + currentFolderId, "Папка")); btnMainBack.setVisibility(View.VISIBLE); }
        String idString = prefs.getString("all_ids", "");
        if (!idString.isEmpty()) {
            for (String sId : idString.split(",")) {
                if (sId.isEmpty()) continue;
                long id = Long.parseLong(sId);
                if (prefs.getLong("parentId_" + id, -1) == currentFolderId) {
                    NoteItem item = new NoteItem(id, prefs.getString("title_"+id,""), "", prefs.getString("date_"+id,""), prefs.getInt("color_"+id, Color.WHITE));
                    item.isFolder = prefs.getBoolean("isFolder_"+id, false);
                    notes.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    void applyTheme() {
        boolean isPink = prefs.getBoolean("isPinkTheme", false);
        int color = isPink ? Color.parseColor("#F48FB1") : Color.parseColor("#00897B");
        mainLayout.setBackgroundColor(isPink ? Color.parseColor("#FCE4EC") : Color.parseColor("#E0F2F1"));
        topBar.setBackgroundColor(color);
        if (android.os.Build.VERSION.SDK_INT >= 21) getWindow().setStatusBarColor(color);
        fabMain.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
    }
}