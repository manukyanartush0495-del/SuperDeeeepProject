package com.notes.app;

public class NoteItem {
    public long id;
    public String title, text, date;
    public float x, y;
    public int color;
    public boolean isFolder = false;
    public long parentId = -1;
    public float scale = 1.0f;
    
    // НОВЫЕ ПОЛЯ
    public String imagePath = ""; // Путь к фото
    public String videoPath = ""; // Путь к видео
    public String audioPath = ""; // Путь к аудио/голосовому
    public int fontId = 0;        // 0 - обычный, 1 - моноширинный, 2 - с засечками

    public NoteItem(long id, String title, String text, String date, int color) {
        this.id = id;
        this.title = title;
        this.text = text;
        this.date = date;
        this.color = color;
    }
}