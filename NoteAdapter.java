package com.notes.app;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.cardview.widget.CardView;
import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    public interface OnNoteClickListener {
        void onNoteClick(long id);
        void onDeleteClick(long id);
        void onLongClick(long id);
    }

    private List<NoteItem> notes;
    private OnNoteClickListener listener;
    private Handler handler = new Handler(); // Таймер для длинного зажима

    public NoteAdapter(List<NoteItem> notes, OnNoteClickListener listener) {
        this.notes = notes;
        this.listener = listener;
    }

    @Override
    public NoteViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(NoteViewHolder holder, int position) {
        NoteItem item = notes.get(position);
        holder.tvTitle.setText(item.title);
        holder.tvDate.setText(item.date);
        
        ((CardView)holder.itemView).setCardBackgroundColor(item.color);

        // ОБЫЧНЫЙ КЛИК
        holder.itemView.setOnClickListener(v -> listener.onNoteClick(item.id));

        // КАСТОМНЫЙ ДЛИННЫЙ ЗАЖИМ (1.3 СЕКУНДЫ)
        holder.itemView.setOnTouchListener(new View.OnTouchListener() {
            private Runnable longClickRunnable = () -> listener.onLongClick(item.id);
            private float startX, startY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        // Запускаем таймер на 1300 мс (1.3 сек)
                        handler.postDelayed(longClickRunnable, 1300);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        // Если палец сдвинулся больше чем на 10 пикселей - отменяем меню
                        // (Значит пользователь начал ПЕРЕТАСКИВАНИЕ)
                        if (Math.abs(event.getX() - startX) > 10 || Math.abs(event.getY() - startY) > 10) {
                            handler.removeCallbacks(longClickRunnable);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // Палец убран раньше времени - отменяем меню
                        handler.removeCallbacks(longClickRunnable);
                        break;
                }
                return false; // Возвращаем false, чтобы сработал обычный Click или Drag & Drop
            }
        });

        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(item.id));
    }

    @Override
    public int getItemCount() { return notes.size(); }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate;
        ImageButton btnDelete;
        NoteViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDate = itemView.findViewById(R.id.tvDate);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}