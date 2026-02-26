package com.notes.app;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.*;
import java.util.*;

public class SnakeView extends View {

    public interface OnGameEventListener {
        void onScoreUpdate(int score);
        void onGameOver();
    }

    private static final int COLS  = 20;
    private static final int ROWS  = 32;
    private static final int DELAY = 160;

    // Направления
    private static final int UP=0, RIGHT=1, DOWN=2, LEFT=3;

    private final List<Point>            snake  = new ArrayList<>();
    private final HashMap<Point,Integer> bombs  = new HashMap<>();
    private final Set<Point>             walls  = new HashSet<>();
    private Point food;

    private int dir = RIGHT, nextDir = RIGHT;
    private boolean gameOver = false;
    private int score = 0;
    private int tick  = 0;

    // Переменные для обработки свайпов вручную
    private float swipeStartX, swipeStartY;

    // Сложность
    private static final int BOMB_INTERVAL_BASE = 22;
    private static final int BOMB_COUNTDOWN     = 14;
    private static final int WALL_INTERVAL      = 40;

    private final Paint pBg    = new Paint();
    private final Paint pSnakeH= new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSnakeB= new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFood  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBomb  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pWall  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFuse  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNum   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Handler handler = new Handler();
    private OnGameEventListener listener;

    public SnakeView(Context ctx) { super(ctx); init(ctx); }
    public SnakeView(Context ctx, AttributeSet a) { super(ctx,a); init(ctx); }

    public void setOnGameEventListener(OnGameEventListener l){ listener=l; }

    void init(Context ctx){
        pBg.setColor(Color.parseColor("#1A1A2E"));
        pSnakeH.setColor(Color.parseColor("#00E676"));
        pSnakeB.setColor(Color.parseColor("#388E3C"));
        pFood.setColor(Color.parseColor("#FF1744"));
        pBomb.setColor(Color.parseColor("#212121"));
        pWall.setColor(Color.parseColor("#78909C"));
        pFuse.setColor(Color.parseColor("#FF6D00"));
        pFuse.setStrokeWidth(3f);
        pFuse.setStyle(Paint.Style.STROKE);
        pNum.setColor(Color.WHITE);
        pNum.setTextAlign(Paint.Align.CENTER);
        pNum.setTypeface(Typeface.DEFAULT_BOLD);

        resetGame();
    }

    public void resetGame(){
        snake.clear(); bombs.clear(); walls.clear();
        score=0; tick=0; gameOver=false;
        dir=RIGHT; nextDir=RIGHT;

        snake.add(new Point(7,10));
        snake.add(new Point(6,10));
        snake.add(new Point(5,10));
        spawnFood();

        handler.removeCallbacks(loop);
        handler.postDelayed(loop, DELAY);
    }

    public void stopGame(){ handler.removeCallbacks(loop); }

    private final Runnable loop = new Runnable(){
        @Override public void run(){
            if(!gameOver){ step(); postInvalidate(); }
            if(!gameOver) handler.postDelayed(this, DELAY);
        }
    };

    void step(){
        tick++;
        dir = nextDir;

        Point head = snake.get(0);
        Point nh   = new Point(head.x, head.y);
        if(dir==UP)    nh.y--;
        if(dir==DOWN)  nh.y++;
        if(dir==LEFT)  nh.x--;
        if(dir==RIGHT) nh.x++;

        // Стена экрана
        if(nh.x<0||nh.x>=COLS||nh.y<0||nh.y>=ROWS){ die(); return; }
        // Себя
        for(Point p:snake) if(p.x==nh.x&&p.y==nh.y){ die(); return; }
        // Бомба
        for(Point b:bombs.keySet()) if(b.x==nh.x&&b.y==nh.y){ die(); return; }
        // Стена
        for(Point w:walls) if(w.x==nh.x&&w.y==nh.y){ die(); return; }

        snake.add(0, nh);
        if(nh.x==food.x&&nh.y==food.y){
            score++;
            if(listener!=null) listener.onScoreUpdate(score);
            spawnFood();
        } else {
            snake.remove(snake.size()-1);
        }

        // Тик бомб
        List<Point> exp = new ArrayList<>();
        for(Map.Entry<Point,Integer> e:bombs.entrySet()){
            int v = e.getValue()-1;
            if(v<=0) exp.add(e.getKey());
            else     e.setValue(v);
        }
        for(Point p:exp) bombs.remove(p);

        // Спавн бомбы
        if(score>=2){
            int interval = Math.max(8, BOMB_INTERVAL_BASE - score/3);
            if(tick % interval == 0){
                int count = 1 + score/7;
                for(int i=0;i<count;i++) spawnBomb();
            }
        }

        // Спавн стен
        if(tick % WALL_INTERVAL == 0 && score>=4) spawnWall();
    }

    void spawnFood(){
        Random r=new Random(); Point p;
        do{ p=new Point(r.nextInt(COLS),r.nextInt(ROWS)); } while(occupied(p));
        food=p;
    }

    void spawnBomb(){
        Point head = snake.get(0);
        Set<String> danger = new HashSet<>();
        int ddx=(dir==RIGHT?1:dir==LEFT?-1:0);
        int ddy=(dir==DOWN ?1:dir==UP  ?-1:0);
        for(int i=1;i<=4;i++) danger.add((head.x+ddx*i)+","+(head.y+ddy*i));

        Random r=new Random(); Point p; int tries=0;
        do{
            p=new Point(r.nextInt(COLS),r.nextInt(ROWS));
            tries++;
        } while(tries<80&&(occupied(p)||danger.contains(p.x+","+p.y)));
        if(tries<80) bombs.put(p,BOMB_COUNTDOWN);
    }

    void spawnWall(){
        Random r=new Random();
        boolean horiz = r.nextBoolean();
        int len = 3+r.nextInt(3);
        int sx=r.nextInt(COLS-len), sy=r.nextInt(ROWS-len);
        for(int i=0;i<len;i++){
            Point w = horiz ? new Point(sx+i,sy) : new Point(sx,sy+i);
            if(!occupied(w)) walls.add(w);
        }
    }

    boolean occupied(Point p){
        if(food!=null&&p.x==food.x&&p.y==food.y) return true;
        for(Point s:snake)    if(s.x==p.x&&s.y==p.y) return true;
        for(Point b:bombs.keySet()) if(b.x==p.x&&b.y==p.y) return true;
        for(Point w:walls)    if(w.x==p.x&&w.y==p.y) return true;
        return false;
    }

    void die(){
        gameOver=true;
        handler.removeCallbacks(loop);
        if(listener!=null) listener.onGameOver();
    }

    @Override
    protected void onDraw(Canvas canvas){
        float sz = getWidth()/(float)COLS;
        pNum.setTextSize(sz*0.62f);

        canvas.drawRect(0,0,getWidth(),getHeight(),pBg);

        // Сетка
        Paint grid=new Paint(); grid.setColor(Color.parseColor("#22FFFFFF"));
        for(int x=0;x<COLS;x++) canvas.drawLine(x*sz,0,x*sz,getHeight(),grid);
        for(int y=0;y<ROWS;y++) canvas.drawLine(0,y*sz,getWidth(),y*sz,grid);

        // Стены
        for(Point w:walls){
            RectF r=new RectF(w.x*sz+1,w.y*sz+1,(w.x+1)*sz-1,(w.y+1)*sz-1);
            canvas.drawRoundRect(r,4,4,pWall);
        }

        // Еда
        if(food!=null){
            float cx=food.x*sz+sz/2f, cy=food.y*sz+sz/2f;
            Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG);
            glow.setColor(Color.parseColor("#33FF1744"));
            canvas.drawCircle(cx,cy,sz*0.7f,glow);
            canvas.drawCircle(cx,cy,sz/2f-2,pFood);
        }

        // Бомбы
        for(Map.Entry<Point,Integer> e:bombs.entrySet()){
            float cx=e.getKey().x*sz+sz/2f, cy=e.getKey().y*sz+sz/2f;
            float r=sz/2f;
            int cnt=e.getValue();

            Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG);
            float ratio=(float)cnt/BOMB_COUNTDOWN;
            int alpha=(int)(80*(1f-ratio))+20;
            glow.setColor(Color.argb(alpha,255,100,0));
            canvas.drawCircle(cx,cy,r*1.4f,glow);

            canvas.drawCircle(cx,cy,r,pBomb);
            canvas.drawLine(cx,cy-r+2,cx+sz*0.2f,cy-r-sz*0.2f,pFuse);

            Paint spark=new Paint(Paint.ANTI_ALIAS_FLAG);
            spark.setColor(cnt%2==0 ? Color.parseColor("#FFEB3B") : Color.parseColor("#FF6D00"));
            canvas.drawCircle(cx+sz*0.2f,cy-r-sz*0.2f,sz*0.12f,spark);

            if(ratio>0.6f)      pNum.setColor(Color.parseColor("#B9F6CA"));
            else if(ratio>0.3f) pNum.setColor(Color.parseColor("#FFE57F"));
            else                pNum.setColor(Color.parseColor("#FF5252"));
            canvas.drawText(String.valueOf(cnt),cx,cy+pNum.getTextSize()/3f,pNum);
        }

        // Змейка
        for(int i=0;i<snake.size();i++){
            Point p=snake.get(i);
            Paint paint=(i==0)?pSnakeH:pSnakeB;
            if(i>0){
                float t=1f-((float)i/snake.size())*0.5f;
                paint.setAlpha((int)(255*t));
            }
            RectF r=new RectF(p.x*sz+2,p.y*sz+2,(p.x+1)*sz-2,(p.y+1)*sz-2);
            canvas.drawRoundRect(r,sz*0.3f,sz*0.3f,paint);
            paint.setAlpha(255);

            if(i==0){
                Paint eye=new Paint(Paint.ANTI_ALIAS_FLAG);
                eye.setColor(Color.WHITE);
                float ex=p.x*sz+sz/2f, ey=p.y*sz+sz/2f;
                canvas.drawCircle(ex-sz*0.15f,ey-sz*0.15f,sz*0.1f,eye);
                canvas.drawCircle(ex+sz*0.15f,ey-sz*0.15f,sz*0.1f,eye);
                eye.setColor(Color.BLACK);
                canvas.drawCircle(ex-sz*0.15f,ey-sz*0.15f,sz*0.05f,eye);
                canvas.drawCircle(ex+sz*0.15f,ey-sz*0.15f,sz*0.05f,eye);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gameOver) return true;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Запоминаем точку начала нажатия
                swipeStartX = event.getX();
                swipeStartY = event.getY();
                return true; // ВАЖНО: возвращаем true, чтобы система отслеживала палец дальше

            case MotionEvent.ACTION_UP:
                float dx = event.getX() - swipeStartX;
                float dy = event.getY() - swipeStartY;
                
                // Проверяем, в какую сторону свайп был длиннее
                if (Math.abs(dx) > Math.abs(dy)) {
                    // Горизонтальный свайп
                    if (Math.abs(dx) > 30) { // Минимальная длина свайпа
                        if (dx > 0 && dir != LEFT) nextDir = RIGHT;
                        else if (dx < 0 && dir != RIGHT) nextDir = LEFT;
                    }
                } else {
                    // Вертикальный свайп
                    if (Math.abs(dy) > 30) {
                        if (dy > 0 && dir != UP) nextDir = DOWN;
                        else if (dy < 0 && dir != DOWN) nextDir = UP;
                    }
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}