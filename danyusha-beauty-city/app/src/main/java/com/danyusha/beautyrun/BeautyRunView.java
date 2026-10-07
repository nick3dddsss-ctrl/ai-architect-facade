package com.danyusha.beautyrun;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.io.IOException;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BeautyRunView extends View {
    private static final float VW = 1280f;
    private static final float VH = 720f;
    private static final float WORLD_W = 6400f;
    private static final float GROUND_Y = 620f;

    private static final int TITLE = 0;
    private static final int PLAYING = 1;
    private static final int PAUSED = 2;
    private static final int WON = 3;
    private static final int GAME_OVER = 4;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ToneGenerator tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 40);

    private final List<Platform> platforms = new ArrayList<>();
    private final List<BrushPickup> brushes = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final Player player = new Player();

    private final Bitmap[] hero = new Bitmap[8];
    private Bitmap cityArt, brushArt, enemyArt, goalArt, checkpointArt;
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private float invincible = 0f;
    private float runClock = 0f;
    private float coyote = 0f;
    private float jumpBuffer = 0f;
    private final float[] checkpoints = {1010,1720,2580,3240,4110,4980};
    private int state = TITLE;
    private int lives = 3;
    private int collected = 0;
    private int totalBrushes = 0;
    private boolean leftHeld = false;
    private boolean rightHeld = false;

    private long lastFrame = SystemClock.uptimeMillis();
    private float elapsed = 0f;
    private float cameraX = 0f;

    public BeautyRunView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        for (int i=0; i<hero.length; i++) hero[i] = loadArt("hero_"+i+".webp");
        cityArt=loadArt("city.webp"); brushArt=loadArt("brush.webp");
        enemyArt=loadArt("enemy.webp"); goalArt=loadArt("goal.webp"); checkpointArt=loadArt("checkpoint.webp");
        buildLevel();
        resetPlayer(false);
    }

    private Bitmap loadArt(String name) {
        try (InputStream in=getContext().getAssets().open(name)) {
            Bitmap b=BitmapFactory.decodeStream(in);
            if (b==null) throw new IllegalStateException("Invalid art: "+name);
            return b;
        } catch(IOException ex) { throw new IllegalStateException("Missing art: "+name,ex); }
    }

    private void art(Canvas c, Bitmap b, float x, float y, float w, float h) {
        c.drawBitmap(b,null,new RectF(x,y,x+w,y+h),artPaint);
    }

    private void buildLevel() {
        platforms.clear();
        brushes.clear();
        enemies.clear();

        addPlatform(0, GROUND_Y, 860, 100);
        addPlatform(970, GROUND_Y, 610, 100);
        addPlatform(1680, GROUND_Y, 760, 100);
        addPlatform(2540, GROUND_Y, 560, 100);
        addPlatform(3200, GROUND_Y, 760, 100);
        addPlatform(4070, GROUND_Y, 650, 100);
        addPlatform(4830, GROUND_Y, 1570, 100);

        addPlatform(380, 505, 235, 28);
        addPlatform(720, 420, 220, 28);
        addPlatform(1120, 485, 250, 28);
        addPlatform(1450, 385, 230, 28);
        addPlatform(1880, 480, 265, 28);
        addPlatform(2220, 390, 235, 28);
        addPlatform(2670, 485, 220, 28);
        addPlatform(3030, 390, 250, 28);
        addPlatform(3440, 470, 260, 28);
        addPlatform(3800, 365, 240, 28);
        addPlatform(4260, 470, 255, 28);
        addPlatform(4620, 390, 220, 28);
        addPlatform(5080, 500, 260, 28);
        addPlatform(5460, 415, 250, 28);
        addPlatform(5810, 335, 240, 28);

        float[][] brushPositions = {
                {220,545},{465,440},{790,355},{1040,545},{1215,420},
                {1515,320},{1770,545},{1970,415},{2290,325},{2630,545},
                {2755,420},{3090,325},{3340,545},{3510,405},{3880,300},
                {4160,545},{4360,405},{4700,325},{4930,545},{5180,435},
                {5525,350},{5880,270},{6120,545},{6230,545}
        };
        for (float[] pos : brushPositions) {
            brushes.add(new BrushPickup(pos[0], pos[1]));
        }
        totalBrushes = brushes.size();

        enemies.add(new Enemy(690, 574, 610, 820));
        enemies.add(new Enemy(1270, 574, 1080, 1500));
        enemies.add(new Enemy(2100, 574, 1810, 2350));
        enemies.add(new Enemy(2810, 574, 2630, 3020));
        enemies.add(new Enemy(3590, 574, 3320, 3860));
        enemies.add(new Enemy(4450, 574, 4180, 4620));
        enemies.add(new Enemy(5320, 574, 5000, 5630));
    }

    private void addPlatform(float x, float y, float w, float h) {
        platforms.add(new Platform(x, y, w, h));
    }

    private void startGame() {
        leftHeld = rightHeld = false;
        lives = 3;
        collected = 0;
        buildLevel();
        resetPlayer(false);
        state = PLAYING;
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    private void resetPlayer(boolean checkpoint) {
        player.x = checkpoint ? Math.max(120f, player.checkpointX) : 120f;
        player.y = GROUND_Y - player.h;
        invincible = checkpoint ? 1.8f : 0f;
        coyote=0f; jumpBuffer=0f;
        player.vx = 0f;
        player.vy = 0f;
        player.facing = 1;
        player.onGround = false;
        if (!checkpoint) player.checkpointX = 120f;
        cameraX = clamp(player.x - 320f, 0f, WORLD_W - VW);
    }

    public void pauseGame() {
        leftHeld=rightHeld=false;
        if (state == PLAYING) state = PAUSED;
    }

    public void resumeGame() {
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xFF251B2A);

        float sx = getWidth() / VW;
        float sy = getHeight() / VH;
        float scale = Math.min(sx, sy);
        float ox = (getWidth() - VW * scale) * 0.5f;
        float oy = (getHeight() - VH * scale) * 0.5f;

        canvas.save();
        canvas.translate(ox, oy);
        canvas.scale(scale, scale);

        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.033f, Math.max(0.001f, (now - lastFrame) / 1000f));
        lastFrame = now;
        elapsed += dt;

        if (state == PLAYING) update(dt);
        drawScene(canvas);

        if (state == TITLE) drawTitle(canvas);
        else if (state == PAUSED) drawPause(canvas);
        else if (state == WON) drawWin(canvas);
        else if (state == GAME_OVER) drawGameOver(canvas);

        canvas.restore();
        postInvalidateOnAnimation();
    }

    private void update(float dt) {
        invincible=Math.max(0f,invincible-dt);
        runClock+=Math.abs(player.vx)*dt/34f;
        coyote=player.onGround ? 0.10f : Math.max(0f,coyote-dt);
        jumpBuffer=Math.max(0f,jumpBuffer-dt);
        if(jumpBuffer>0f && coyote>0f) {
            player.vy=-650f; player.onGround=false; coyote=0f; jumpBuffer=0f;
            tones.startTone(ToneGenerator.TONE_PROP_PROMPT,55);
        }
        final float accel = 1700f;
        final float maxSpeed = 370f;
        final float friction = 2100f;

        if (leftHeld && !rightHeld) {
            player.vx -= accel * dt;
            player.facing = -1;
        } else if (rightHeld && !leftHeld) {
            player.vx += accel * dt;
            player.facing = 1;
        } else {
            if (player.vx > 0f) player.vx = Math.max(0f, player.vx - friction * dt);
            if (player.vx < 0f) player.vx = Math.min(0f, player.vx + friction * dt);
        }
        player.vx = clamp(player.vx, -maxSpeed, maxSpeed);

        float oldY = player.y;
        float oldBottom = oldY + player.h;

        player.x += player.vx * dt;
        player.x = clamp(player.x, 0f, WORLD_W - player.w);

        player.vy += 1500f * dt;
        player.vy = Math.min(player.vy, 900f);
        player.y += player.vy * dt;
        player.onGround = false;

        if (player.vy >= 0f) {
            float newBottom = player.y + player.h;
            for (Platform q : platforms) {
                boolean horizontal = player.x + player.w > q.x + 4f && player.x < q.x + q.w - 4f;
                if (horizontal && oldBottom <= q.y + 5f && newBottom >= q.y) {
                    player.y = q.y - player.h;
                    player.vy = 0f;
                    player.onGround = true;
                    break;
                }
            }
        }

        if (player.y > VH + 180f) {
            loseLife();
            return;
        }

        for (Enemy e : enemies) {
            if (!e.alive) continue;

            e.x += e.speed * e.dir * dt;
            if (e.x < e.minX) {
                e.x = e.minX;
                e.dir = 1;
            } else if (e.x > e.maxX) {
                e.x = e.maxX;
                e.dir = -1;
            }

            if (invincible<=0f && overlap(player.x, player.y, player.w, player.h, e.x, e.y, e.w, e.h)) {
                if (player.vy > 80f && oldBottom <= e.y + 12f) {
                    e.alive = false;
                    player.vy = -520f;
                    tones.startTone(ToneGenerator.TONE_PROP_ACK, 70);
                } else {
                    loseLife();
                    return;
                }
            }
        }

        Iterator<BrushPickup> it = brushes.iterator();
        while (it.hasNext()) {
            BrushPickup b = it.next();
            float bobY = b.y + (float) Math.sin(elapsed * 4.5f + b.phase) * 7f;
            if (overlap(player.x, player.y, player.w, player.h, b.x - 17f, bobY - 34f, 34f, 68f)) {
                it.remove();
                collected++;
                tones.startTone(ToneGenerator.TONE_PROP_BEEP, 60);
            }
        }

        if (player.x > 5050f) player.checkpointX = 4980f;
        else if (player.x > 4070f) player.checkpointX = 4070f;
        else if (player.x > 3200f) player.checkpointX = 3200f;
        else if (player.x > 2540f) player.checkpointX = 2540f;
        else if (player.x > 1680f) player.checkpointX = 1680f;
        else if (player.x > 970f) player.checkpointX = 970f;

        if (player.x + player.w > 6190f && player.y + player.h > 500f) {
            state = WON;
            player.vx = 0f;
            tones.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 280);
        }

        float desiredCamera = player.x - 360f;
        cameraX += (desiredCamera - cameraX) * Math.min(1f, dt * 5.5f);
        cameraX = clamp(cameraX, 0f, WORLD_W - VW);
    }

    private void loseLife() {
        lives--;
        tones.startTone(ToneGenerator.TONE_PROP_NACK, 120);
        if (lives <= 0) {
            state = GAME_OVER;
            player.vx = 0f;
            player.vy = 0f;
        } else {
            resetPlayer(true);
        }
    }

    private void jump() {
        if (state == TITLE || state == WON || state == GAME_OVER) {
            startGame();
            return;
        }

        if (state == PAUSED) {
            state = PLAYING;
            lastFrame = SystemClock.uptimeMillis();
            return;
        }

        if (state == PLAYING) jumpBuffer=0.12f;
    }

    private void drawScene(Canvas c) {
        drawBackground(c);

        c.save();
        c.translate(-cameraX, 0f);

        drawDecor(c);
        for (Platform q : platforms) drawPlatform(c, q);

        for (BrushPickup b : brushes) {
            float bobY = b.y + (float) Math.sin(elapsed * 4.5f + b.phase) * 7f;
            drawBrush(c, b.x, bobY);
        }

        for (Enemy e : enemies) {
            if (e.alive) drawEnemy(c, e);
        }

        drawGoal(c, 6210f, 503f);
        drawDanyusha(c, player.x, player.y, player.facing, player.vx, player.vy);

        c.restore();

        if (state == PLAYING || state == PAUSED) drawHud(c);
    }

    private void drawBackground(Canvas c) {
        float width=1080f;
        float offset=-(cameraX*0.32f)%width;
        int first=(int)Math.floor(cameraX*0.32f/width);
        for(int i=-1;i<3;i++) {
            float x=offset+i*width;
            c.save();
            if(((first+i)&1)!=0) { c.translate(x+width,0); c.scale(-1,1); art(c,cityArt,0,0,width,720); }
            else art(c,cityArt,x,0,width,720);
            c.restore();
        }
        // Warm atmospheric veil keeps the playable foreground legible.
        p.setColor(0x18FFF0E7); c.drawRect(0,0,VW,VH,p);
    }

    private void drawDecor(Canvas c) {
        for(float x:checkpoints) {
            if(x<cameraX-120 || x>cameraX+VW+120) continue;
            artPaint.setAlpha(player.checkpointX>=x-60 ? 255 : 155);
            art(c,checkpointArt,x,546,54,74); artPaint.setAlpha(255);
        }
    }

    private void drawBillboard(Canvas c, float x, float y, String a, String b) {
        p.setColor(0xFFD76991);
        c.drawRoundRect(new RectF(x, y, x + 165f, y + 120f), 18f, 18f, p);
        p.setColor(0xFFFFF0E9);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(24f);
        c.drawText(a, x + 82f, y + 50f, p);
        c.drawText(b, x + 82f, y + 82f, p);
        p.setFakeBoldText(false);
    }

    private void drawPlatform(Canvas c, Platform q) {
        if(q.x+q.w<cameraX-20 || q.x>cameraX+VW+20) return;
        p.setAlpha(255);
        p.setShader(new LinearGradient(0,q.y,0,q.y+q.h,0xFF9C4563,0xFF452C43,Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(q.x,q.y,q.x+q.w,q.y+q.h),8,8,p);p.setShader(null);
        p.setColor(0xFFB78045);c.drawRoundRect(new RectF(q.x-2,q.y,q.x+q.w+2,q.y+10),5,5,p);
        p.setColor(0xFFFFE2A9);c.drawRect(q.x+2,q.y,q.x+q.w-2,q.y+3,p);
        p.setColor(0xFFE9ADB7);c.drawRect(q.x+4,q.y+10,q.x+q.w-4,q.y+16,p);
        if(q.h>40) {
            p.setColor(0xFF795164);
            for(float x=q.x+30;x<q.x+q.w;x+=70) {
                c.drawLine(x,q.y+22,x,q.y+q.h,p);
                c.drawLine(x-30,q.y+52,x+30,q.y+52,p);
            }
        } else {
            p.setColor(0xFFD8AB6E);
            for(float x=q.x+24;x<q.x+q.w-10;x+=48)c.drawCircle(x,q.y+22,2.5f,p);
        }
    }

    private void drawBrush(Canvas c, float x, float y) {
        p.setColor(0x36FFF0A6);c.drawCircle(x,y,29,p);
        art(c,brushArt,x-23,y-30,46,60);
        p.setColor(0xFFFFE7A6);
        float sparkle=3f+(float)Math.sin(elapsed*5+x)*1.5f;
        c.drawCircle(x+25,y-20,sparkle,p);
    }

    private void drawEnemy(Canvas c, Enemy e) {
        if(e.x<cameraX-100 || e.x>cameraX+VW+100)return;
        float bob=(float)Math.sin(elapsed*9+e.x*0.01f)*2f;
        c.save();c.translate(e.x+e.w/2,e.y+e.h);
        c.scale(e.dir,1);
        art(c,enemyArt,-e.w/2,-e.h+bob,e.w,e.h);
        c.restore();
    }

    private void drawGoal(Canvas c, float x, float y) {
        p.setColor(0x55FFE09B); c.drawCircle(x+42,y+35,80,p);
        art(c,goalArt,x-20,y-15,145,115);
        p.setColor(0xFF552C44);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(22);p.setFakeBoldText(true);
        c.drawText("ФИНИШ",x+50,y-27,p);p.setFakeBoldText(false);
    }

    private void drawDanyusha(Canvas c, float x, float y, int facing, float vx, float vy) {
        int frame=0;
        if(!player.onGround && state==PLAYING) frame=vy<0?5:6;
        else if(Math.abs(vx)>20) frame=1+((int)runClock%4);
        if(invincible>0 && ((int)(elapsed*12)%2)==0)artPaint.setAlpha(115);
        c.save();c.translate(x+player.w/2,y+player.h);c.scale(facing,1);
        art(c,hero[frame],-54,-137,108,140);
        c.restore();artPaint.setAlpha(255);
    }

    private void drawHud(Canvas c) {
        p.setColor(0xE02F2330);
        c.drawRoundRect(new RectF(22f, 18f, 520f, 94f), 23f, 23f, p);

        p.setColor(0xFFE05E8B);
        c.drawRoundRect(new RectF(32f, 28f, 194f, 84f), 16f, 16f, p);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(25f);
        p.setFakeBoldText(true);
        c.drawText("DANYUSHA", 113f, 64f, p);

        drawBrush(c, 227f, 57f);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(24f);
        p.setColor(0xFFFFFFFF);
        c.drawText(collected + " / " + totalBrushes, 257f, 65f, p);

        p.setColor(0xFFFF719A);
        p.setTextSize(28f);
        String hearts = "";
        for (int i = 0; i < lives; i++) hearts += "♥";
        c.drawText(hearts, 382f, 66f, p);
        p.setFakeBoldText(false);

        p.setColor(0xAA39273A);c.drawRoundRect(new RectF(550,35,860,66),12,12,p);
        p.setColor(0xFFE8B771);c.drawRoundRect(new RectF(554,39,554+302*clamp(player.x/6190f,0,1),62),9,9,p);
        p.setColor(0xC42F2330);
        c.drawRoundRect(new RectF(914f, 25f, 1252f, 82f), 18f, 18f, p);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(19f);
        c.drawText("← → движение     OK прыжок", 1083f, 60f, p);
    }

    private void drawTitle(Canvas c) {
        p.setShader(new LinearGradient(0,0,1080,0,0xF52D1C30,0x763A2334,Shader.TileMode.CLAMP));
        c.drawRect(0,0,VW,VH,p);p.setShader(null);
        art(c,hero[0],815,150,355,462);
        p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(true);p.setColor(0xFFFFD6A5);p.setTextSize(19);
        c.drawText("BEAUTY CITY  /  ПРИКЛЮЧЕНИЕ 01",86,145,p);
        p.setColor(0xFFFFF2EB);p.setTextSize(86);c.drawText("ДАНЮША",80,260,p);
        p.setColor(0xFFF4A5BB);p.setTextSize(31);c.drawText("Город, в котором начинается магия",86,320,p);
        p.setFakeBoldText(false);p.setTextSize(25);p.setColor(0xFFFFE9E5);
        c.drawText("Собирай кисти. Перепрыгивай препятствия.",86,392,p);
        c.drawText("Найди волшебную косметичку!",86,429,p);
        p.setColor(0xFFE06A91);c.drawRoundRect(new RectF(82,490,440,570),22,22,p);
        p.setColor(0xFFFFE2B7);c.drawRoundRect(new RectF(86,494,436,500),3,3,p);
        p.setColor(0xFFFFFFFF);p.setTextSize(29);p.setFakeBoldText(true);c.drawText("OK  —  НАЧАТЬ",132,542,p);
        p.setFakeBoldText(false);p.setTextSize(19);p.setColor(0xFFE8CEDA);
        c.drawText("← →  движение    •    OK  прыжок    •    Назад  пауза",86,635,p);
        p.setTextSize(15);c.drawText("v0.4 · По первоначальному образу Данюши",86,675,p);
    }

    private void drawPause(Canvas c) {
        p.setColor(0xB91C151E);
        c.drawRect(0f, 0f, VW, VH, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFFFFFFF);
        p.setFakeBoldText(true);
        p.setTextSize(58f);
        c.drawText("ПАУЗА", VW / 2f, 330f, p);
        p.setFakeBoldText(false);
        p.setTextSize(26f);
        c.drawText("Нажми OK, чтобы продолжить", VW / 2f, 390f, p);
    }

    private void drawWin(Canvas c) {
        p.setColor(0xC21C151E);
        c.drawRect(0f, 0f, VW, VH, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFFF92B2);
        p.setFakeBoldText(true);
        p.setTextSize(56f);
        c.drawText("ДАНЮША У ФИНИША!", VW / 2f, 250f, p);

        p.setColor(0xFFFFFFFF);
        p.setTextSize(34f);
        c.drawText("Кистей собрано: " + collected + " из " + totalBrushes, VW / 2f, 320f, p);
        p.setFakeBoldText(false);
        p.setTextSize(25f);
        c.drawText(collected == totalBrushes
                ? "Идеальный beauty-забег!"
                : "Можно пройти ещё раз и собрать все кисти", VW / 2f, 378f, p);

        p.setColor(0xFFE05E8B);
        c.drawRoundRect(new RectF(452f, 440f, 828f, 515f), 24f, 24f, p);
        p.setColor(0xFFFFFFFF);
        p.setFakeBoldText(true);
        p.setTextSize(29f);
        c.drawText("OK — ЕЩЁ РАЗ", VW / 2f, 488f, p);
        p.setFakeBoldText(false);
    }

    private void drawGameOver(Canvas c) {
        p.setColor(0xC81A131D);
        c.drawRect(0f, 0f, VW, VH, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFFF8CAB);
        p.setFakeBoldText(true);
        p.setTextSize(60f);
        c.drawText("ПОПРОБУЕМ ЕЩЁ?", VW / 2f, 300f, p);

        p.setColor(0xFFFFFFFF);
        p.setFakeBoldText(false);
        p.setTextSize(27f);
        c.drawText("Нажми OK — Данюша начнёт уровень заново", VW / 2f, 365f, p);

        p.setColor(0xFFE05E8B);
        c.drawRoundRect(new RectF(470f, 420f, 810f, 495f), 24f, 24f, p);
        p.setColor(0xFFFFFFFF);
        p.setFakeBoldText(true);
        p.setTextSize(29f);
        c.drawText("OK — СТАРТ", VW / 2f, 468f, p);
        p.setFakeBoldText(false);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                leftHeld = true;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                rightHeld = true;
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_SPACE:
                if (event.getRepeatCount() == 0) jump();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (state == PLAYING) {
                    pauseGame();
                    return true;
                }
                if (state == PAUSED) {
                    state = PLAYING;
                    lastFrame = SystemClock.uptimeMillis();
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            leftHeld = false;
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            rightHeld = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDetachedFromWindow() {
        tones.release();
        super.onDetachedFromWindow();
    }

    private static boolean overlap(
            float ax, float ay, float aw, float ah,
            float bx, float by, float bw, float bh
    ) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class Platform {
        final float x;
        final float y;
        final float w;
        final float h;

        Platform(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static class BrushPickup {
        final float x;
        final float y;
        final float phase;

        BrushPickup(float x, float y) {
            this.x = x;
            this.y = y;
            this.phase = (x * 0.0137f + y * 0.0073f) % 6.28f;
        }
    }

    private static class Enemy {
        float x;
        final float y;
        final float minX;
        final float maxX;
        final float w = 52f;
        final float h = 68f;
        float speed = 88f;
        int dir = 1;
        boolean alive = true;

        Enemy(float x, float y, float minX, float maxX) {
            this.x = x;
            this.y = GROUND_Y - h;
            this.minX = minX;
            this.maxX = maxX;
        }
    }

    private static class Player {
        float x;
        float y;
        float vx;
        float vy;
        final float w = 54f;
        final float h = 124f;
        boolean onGround = false;
        int facing = 1;
        float checkpointX = 120f;
    }
}
