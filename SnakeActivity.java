package com.notes.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public class SnakeActivity extends Activity {
    private SnakeView snakeView;
    private LinearLayout gameOverLayout;
    private TextView tvScore;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_snake);

        snakeView = findViewById(R.id.snakeView);
        gameOverLayout = findViewById(R.id.gameOverLayout);
        tvScore = findViewById(R.id.tvScore);
        progressBar = findViewById(R.id.snakeProgress);

        snakeView.setOnGameEventListener(new SnakeView.OnGameEventListener() {
            @Override
            public void onScoreUpdate(int score) {
                tvScore.setText("Счёт: " + score);
                progressBar.setProgress(score);
            }

            @Override
            public void onGameOver() {
                gameOverLayout.setVisibility(View.VISIBLE);
            }
        });

        findViewById(R.id.btnRestart).setOnClickListener(v -> {
            gameOverLayout.setVisibility(View.GONE);
            snakeView.resetGame();
            progressBar.setProgress(0);
        });

        findViewById(R.id.btnExit).setOnClickListener(v -> finish());
    }
}