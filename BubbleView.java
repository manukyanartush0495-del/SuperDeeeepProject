package com.notes.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

public class BubbleView extends View {
    public interface Listener {
        void onTap(NoteItem n);
        void onDragEnd(long id, float x, float y);
        void onMerge(NoteItem dragged, NoteItem target);
        void onLongPress(NoteItem n);
    }

    private List<NoteItem> allNotes;
    private long curFolderId = -1;
    private Listener listener;
    private Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Matrix matrix = new Matrix();
    private float[] mValues = new float[9];
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private NoteItem dragNote = null;
    private NoteItem highlightNote = null;
    private float lastX, lastY;

    public BubbleView(Context context, List<NoteItem> notes, Listener l) {
        super(context);
        this.allNotes = notes; this.listener = l;
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(12);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setAlpha(180);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                matrix.getValues(mValues);
                float curScale = mValues[Matrix.MSCALE_X];
                float factor = detector.getScaleFactor();
                if ((curScale * factor > 4.0f && factor > 1) || (curScale * factor < 0.3f && factor < 1)) return true;
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                invalidate();
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (dragNote == null) { matrix.postTranslate(-dx, -dy); invalidate(); }
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                float[] pts = mapCoords(e.getX(), e.getY());
                for (NoteItem n : getVisibleNotes()) {
                    // Используем новую функцию getRadius для точности клика
                    if (Math.hypot(pts[0] - n.x, pts[1] - n.y) < getRadius(n)) {
                        listener.onTap(n); return true;
                    }
                }
                return false;
            }
            @Override public void onLongPress(MotionEvent e) {
                float[] pts = mapCoords(e.getX(), e.getY());
                for (NoteItem n : getVisibleNotes()) {
                    if (Math.hypot(pts[0] - n.x, pts[1] - n.y) < getRadius(n)) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        listener.onLongPress(n); break;
                    }
                }
            }
        });
    }

    // Вспомогательная функция: считает реальный радиус с учетом длины текста
    private float getRadius(NoteItem n) {
        float baseRadius = 130 * n.scale;
        float extraRadius = Math.min(n.title.length() * 2f, baseRadius * 0.4f);
        return baseRadius + extraRadius;
    }

    public void setFolder(long id) { this.curFolderId = id; invalidate(); }

    private List<NoteItem> getVisibleNotes() {
        List<NoteItem> visible = new ArrayList<>();
        for (NoteItem n : allNotes) if (n.parentId == curFolderId) visible.add(n);
        return visible;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.concat(matrix);

        for (NoteItem n : getVisibleNotes()) {
            float radius = getRadius(n);

            if (n == highlightNote) {
                canvas.drawCircle(n.x, n.y, radius + 20, strokePaint);
            }

            // Рисуем основной шар
            p.setColor(n.color);
            p.setStyle(Paint.Style.FILL);
            canvas.drawCircle(n.x, n.y, radius, p);

            // Если это папка - рисуем золотую рамку
            if (n.isFolder) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(10);
                p.setColor(Color.parseColor("#FFCA28"));
                canvas.drawCircle(n.x, n.y, radius + 10, p);
                p.setStyle(Paint.Style.FILL);
            }

            // УМНЫЙ ТЕКСТ (Многострочность и авто-размер)
            p.setColor(isColorLight(n.color) ? Color.parseColor("#333333") : Color.WHITE);
            p.setFakeBoldText(true);
            
            drawSmartText(canvas, n.isFolder ? "[" + n.title + "]" : n.title, n.x, n.y, radius);
        }
        canvas.restore();
    }

    private void drawSmartText(Canvas canvas, String text, float cx, float cy, float radius) {
        float maxWidth = radius * 1.6f;
        float fontSize = radius * 0.35f; 
        p.setTextSize(fontSize);

        String[] words = text.split(" ");
        // Если есть пробел и текст длинноват - делим на 2 строки
        if (words.length > 1 && text.length() > 8) {
            String line1 = words[0];
            String line2 = text.substring(line1.length()).trim();
            
            while (p.measureText(line1) > maxWidth || p.measureText(line2) > maxWidth) {
                fontSize -= 1;
                p.setTextSize(fontSize);
                if (fontSize < 18) break; 
            }
            
            canvas.drawText(line1, cx, cy - (fontSize * 0.2f), p);
            canvas.drawText(line2, cx, cy + (fontSize * 0.8f), p);
        } else {
            while (p.measureText(text) > maxWidth) {
                fontSize -= 1;
                p.setTextSize(fontSize);
                if (fontSize < 18) {
                    text = text.substring(0, Math.max(0, text.length() - 3)) + "..";
                    break;
                }
            }
            canvas.drawText(text, cx, cy + (fontSize / 3f), p);
        }
    }

    private boolean isColorLight(int color) {
        return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255 > 0.7;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        float[] pts = mapCoords(event.getX(), event.getY());
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                for (NoteItem n : getVisibleNotes()) {
                    if (Math.hypot(pts[0] - n.x, pts[1] - n.y) < getRadius(n)) {
                        dragNote = n; lastX = pts[0]; lastY = pts[1]; break;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragNote != null) {
                    dragNote.x += (pts[0] - lastX); dragNote.y += (pts[1] - lastY);
                    lastX = pts[0]; lastY = pts[1];
                    
                    highlightNote = null;
                    for (NoteItem n : getVisibleNotes()) {
                        // Расстояние для слияния тоже учитывает динамический радиус
                        if (n != dragNote && Math.hypot(dragNote.x - n.x, dragNote.y - n.y) < (getRadius(n) * 0.9f)) {
                            highlightNote = n; break;
                        }
                    }
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (dragNote != null) {
                    if (highlightNote != null) listener.onMerge(dragNote, highlightNote);
                    else listener.onDragEnd(dragNote.id, dragNote.x, dragNote.y);
                }
                dragNote = null; highlightNote = null; invalidate();
                break;
        }
        return true;
    }

    private float[] mapCoords(float x, float y) {
        Matrix inv = new Matrix(); matrix.invert(inv);
        float[] pts = {x, y}; inv.mapPoints(pts);
        return pts;
    }
}