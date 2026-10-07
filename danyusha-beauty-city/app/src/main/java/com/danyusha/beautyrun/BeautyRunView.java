package com.danyusha.beautyrun;

import android.content.Context;
import android.graphics.Canvas;
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
        buildLevel();
        resetPlayer(false);
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
        player.y = 500f;
        player.vx = 0f;
        player.vy = 0f;
        player.facing = 1;
        player.onGround = false;
        if (!checkpoint) player.checkpointX = 120f;
        cameraX = clamp(player.x - 320f, 0f, WORLD_W - VW);
    }

    public void pauseGame() {
        if (state == PLAYING) state = PAUSED;
    }

    public void resumeGame() {
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

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

            if (overlap(player.x, player.y, player.w, player.h, e.x, e.y, e.w, e.h)) {
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

        if (player.x > 6120f) {
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

        if (state == PLAYING && player.onGround) {
            player.vy = -650f;
            player.onGround = false;
            tones.startTone(ToneGenerator.TONE_PROP_PROMPT, 55);
        }
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
        p.setShader(new LinearGradient(0f, 0f, 0f, VH,
                new int[]{0xFFF8DBE7, 0xFFF4C6D9, 0xFFFFEEE8},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, VW, VH, p);
        p.setShader(null);

        p.setColor(0x66FFFFFF);
        c.drawCircle(1030f, 105f, 92f, p);

        float parallax = -(cameraX * 0.16f) % 420f;
        for (int i = -2; i < 6; i++) {
            float x = parallax + i * 320f;

            p.setColor(0x55FFFFFF);
            c.drawRoundRect(new RectF(x, 235f, x + 155f, 570f), 24f, 24f, p);
            c.drawRoundRect(new RectF(x + 175f, 295f, x + 300f, 570f), 18f, 18f, p);

            p.setColor(0x55D88AA8);
            c.drawRect(x + 24f, 275f, x + 40f, 540f, p);
            c.drawRect(x + 66f, 260f, x + 82f, 540f, p);
            c.drawRect(x + 108f, 290f, x + 124f, 540f, p);

            p.setColor(0x448B4A67);
            c.drawCircle(x + 245f, 272f, 30f, p);
            c.drawRect(x + 238f, 295f, x + 252f, 565f, p);
        }

        p.setColor(0xFFB85F83);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(32f);
        c.drawText("BEAUTY CITY", 640f, 112f, p);
        p.setFakeBoldText(false);
    }

    private void drawDecor(Canvas c) {
        for (int i = 0; i < 14; i++) {
            float x = 160f + i * 455f;
            p.setColor(0x33B14E75);
            c.drawCircle(x, 590f, 36f, p);
            c.drawCircle(x + 22f, 568f, 28f, p);
            p.setColor(0x665F8D68);
            c.drawRect(x - 5f, 585f, x + 7f, 620f, p);
        }

        drawBillboard(c, 860f, 180f, "BRAVE", "KIND");
        drawBillboard(c, 2890f, 170f, "GLOW", "HIGHER");
        drawBillboard(c, 4930f, 180f, "BEAUTY", "ADVENTURE");
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
        p.setColor(q.h > 40f ? 0xFF4A3343 : 0xFF6A4055);
        c.drawRoundRect(new RectF(q.x, q.y, q.x + q.w, q.y + q.h), 14f, 14f, p);

        p.setColor(0xFFE58BAB);
        c.drawRoundRect(new RectF(q.x, q.y, q.x + q.w, q.y + Math.min(q.h, 18f)), 12f, 12f, p);

        if (q.h > 40f) {
            p.setColor(0xFF5E4053);
            for (float xx = q.x + 35f; xx < q.x + q.w; xx += 68f) {
                c.drawCircle(xx, q.y + 48f, 3f, p);
                c.drawLine(xx - 18f, q.y + 28f, xx + 18f, q.y + 68f, p);
                c.drawLine(xx + 18f, q.y + 28f, xx - 18f, q.y + 68f, p);
            }
        }
    }

    private void drawBrush(Canvas c, float x, float y) {
        c.save();
        c.rotate(-22f, x, y);

        p.setColor(0x44FFF2C7);
        c.drawCircle(x, y, 32f, p);

        p.setColor(0xFFD98B34);
        c.drawRoundRect(new RectF(x - 5f, y - 20f, x + 5f, y + 26f), 5f, 5f, p);

        p.setColor(0xFFFFC7D7);
        Path bristles = new Path();
        bristles.moveTo(x - 12f, y - 23f);
        bristles.quadTo(x, y - 43f, x + 12f, y - 23f);
        bristles.lineTo(x + 5f, y - 12f);
        bristles.lineTo(x - 5f, y - 12f);
        bristles.close();
        c.drawPath(bristles, p);

        p.setColor(0xFFFFFFFF);
        c.drawCircle(x + 20f, y - 22f, 3.5f, p);
        c.drawCircle(x - 20f, y + 2f, 2.5f, p);
        c.restore();
    }

    private void drawEnemy(Canvas c, Enemy e) {
        float bounce = (float) Math.sin(elapsed * 5f + e.x * 0.01f) * 2f;
        float y = e.y + bounce;

        p.setColor(0xFF7B4363);
        c.drawRoundRect(new RectF(e.x, y, e.x + e.w, y + e.h), 12f, 12f, p);
        p.setColor(0xFFE97DA3);
        c.drawRoundRect(new RectF(e.x + 5f, y + 5f, e.x + e.w - 5f, y + e.h - 9f), 9f, 9f, p);

        p.setColor(0xFF382432);
        c.drawLine(e.x + 12f, y + 17f, e.x + 23f, y + 22f, p);
        c.drawLine(e.x + e.w - 12f, y + 17f, e.x + e.w - 23f, y + 22f, p);
        c.drawCircle(e.x + 20f, y + 24f, 3f, p);
        c.drawCircle(e.x + e.w - 20f, y + 24f, 3f, p);

        p.setColor(0xFFD7A044);
        Path crown = new Path();
        crown.moveTo(e.x + 13f, y + 2f);
        crown.lineTo(e.x + 18f, y - 11f);
        crown.lineTo(e.x + 26f, y - 2f);
        crown.lineTo(e.x + 34f, y - 11f);
        crown.lineTo(e.x + 40f, y + 2f);
        crown.close();
        c.drawPath(crown, p);
    }

    private void drawGoal(Canvas c, float x, float y) {
        p.setColor(0x55FFE09B);
        c.drawCircle(x + 42f, y + 35f, 78f, p);

        p.setColor(0xFFE76995);
        c.drawRoundRect(new RectF(x, y, x + 92f, y + 82f), 18f, 18f, p);
        p.setColor(0xFFFFD99C);
        c.drawRoundRect(new RectF(x + 8f, y + 12f, x + 84f, y + 28f), 8f, 8f, p);

        p.setColor(0xFFD09A3D);
        Path crown = new Path();
        crown.moveTo(x + 26f, y + 55f);
        crown.lineTo(x + 32f, y + 38f);
        crown.lineTo(x + 45f, y + 50f);
        crown.lineTo(x + 57f, y + 38f);
        crown.lineTo(x + 65f, y + 55f);
        crown.close();
        c.drawPath(crown, p);

        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(18f);
        p.setFakeBoldText(true);
        c.drawText("FINISH", x + 46f, y - 20f, p);
        p.setFakeBoldText(false);
    }

    private void drawDanyusha(Canvas c, float x, float y, int facing, float vx, float vy) {
        c.save();
        c.translate(x + player.w / 2f, y);
        c.scale(facing, 1f);
        c.translate(-player.w / 2f, 0f);

        float run = Math.min(1f, Math.abs(vx) / 280f);
        float phase = (float) Math.sin(elapsed * 12f) * run;
        float armSwing = phase * 8f;
        float legSwing = phase * 9f;
        boolean airborne = Math.abs(vy) > 60f && !player.onGround;

        p.setColor(0xFF392730);
        c.drawOval(new RectF(7f, 0f, 49f, 48f), p);
        c.drawOval(new RectF(34f, 10f, 56f, 52f), p);

        p.setColor(0xFFF1C8B6);
        c.drawOval(new RectF(16f, 8f, 45f, 42f), p);

        p.setColor(0xFF6F8054);
        c.drawCircle(34f, 22f, 2.2f, p);
        p.setColor(0xFF34252A);
        c.drawCircle(34f, 22f, 0.8f, p);

        p.setColor(0xFFD46B82);
        c.drawRoundRect(new RectF(29f, 31f, 37f, 33.5f), 2f, 2f, p);

        p.setColor(0xFFE05E8B);
        c.drawRoundRect(new RectF(8f, 39f, 50f, 67f), 8f, 8f, p);
        p.setColor(0xFFFFF0EA);
        c.drawRoundRect(new RectF(21f, 41f, 38f, 66f), 5f, 5f, p);

        p.setColor(0xFFF0C4B0);
        float armY = airborne ? 48f : 50f;
        c.drawRoundRect(new RectF(2f, armY + armSwing * 0.4f, 12f, 70f + armSwing), 5f, 5f, p);
        c.drawRoundRect(new RectF(46f, armY - armSwing * 0.4f, 56f, 70f - armSwing), 5f, 5f, p);

        p.setColor(0xFF302B34);
        if (airborne) {
            c.drawRoundRect(new RectF(13f, 64f, 28f, 82f), 6f, 6f, p);
            c.drawRoundRect(new RectF(31f, 64f, 47f, 82f), 6f, 6f, p);
        } else {
            c.drawRoundRect(new RectF(13f + legSwing, 64f, 28f + legSwing, 84f), 6f, 6f, p);
            c.drawRoundRect(new RectF(31f - legSwing, 64f, 47f - legSwing, 84f), 6f, 6f, p);
        }

        p.setColor(0xFFE05E8B);
        c.drawRect(16f, 70f, 23f, 76f, p);
        c.drawRect(37f, 70f, 44f, 76f, p);

        p.setColor(0xFFFFF3EE);
        if (airborne) {
            c.drawRoundRect(new RectF(10f, 80f, 30f, 88f), 5f, 5f, p);
            c.drawRoundRect(new RectF(29f, 80f, 50f, 88f), 5f, 5f, p);
        } else {
            c.drawRoundRect(new RectF(9f + legSwing, 80f, 31f + legSwing, 88f), 5f, 5f, p);
            c.drawRoundRect(new RectF(28f - legSwing, 80f, 50f - legSwing, 88f), 5f, 5f, p);
        }

        p.setColor(0xFF9D5C77);
        c.drawRoundRect(new RectF(45f, 52f, 58f, 69f), 4f, 4f, p);
        p.setColor(0xFFD5A140);
        c.drawCircle(51.5f, 59f, 2.5f, p);

        c.restore();
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
        c.drawText(collected + " / " + totalBrushes, 257f, 65f, p);

        p.setColor(0xFFFF719A);
        p.setTextSize(28f);
        String hearts = "";
        for (int i = 0; i < lives; i++) hearts += "♥";
        c.drawText(hearts, 382f, 66f, p);
        p.setFakeBoldText(false);

        p.setColor(0xC42F2330);
        c.drawRoundRect(new RectF(914f, 25f, 1252f, 82f), 18f, 18f, p);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(19f);
        c.drawText("← → движение     OK прыжок", 1083f, 60f, p);
    }

    private void drawTitle(Canvas c) {
        p.setColor(0xB8221824);
        c.drawRect(0f, 0f, VW, VH, p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFFFFFFF);
        p.setFakeBoldText(true);
        p.setTextSize(76f);
        c.drawText("ДАНЮША", VW / 2f, 178f, p);

        p.setColor(0xFFFF91B2);
        p.setTextSize(36f);
        c.drawText("BEAUTY CITY ADVENTURE", VW / 2f, 230f, p);
        p.setFakeBoldText(false);

        drawDanyusha(c, VW / 2f - 29f, 280f, 1, 0f, 0f);

        p.setColor(0xFFFFFFFF);
        p.setTextSize(24f);
        c.drawText("Собирай кисти, прыгай по платформам и доберись до косметички", VW / 2f, 470f, p);

        p.setColor(0xFFE05E8B);
        c.drawRoundRect(new RectF(455f, 530f, 825f, 605f), 24f, 24f, p);
        p.setColor(0xFFFFFFFF);
        p.setFakeBoldText(true);
        p.setTextSize(31f);
        c.drawText("OK — ИГРАТЬ", VW / 2f, 578f, p);
        p.setFakeBoldText(false);

        p.setColor(0xFFEFE1E7);
        p.setTextSize(18f);
        c.drawText("Android TV: ← → движение  •  OK прыжок  •  Back пауза", VW / 2f, 665f, p);
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
                if (event.getRepeatCount() == 0) jump();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (state == PLAYING) {
                    state = PAUSED;
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
        final float h = 46f;
        float speed = 88f;
        int dir = 1;
        boolean alive = true;

        Enemy(float x, float y, float minX, float maxX) {
            this.x = x;
            this.y = y;
            this.minX = minX;
            this.maxX = maxX;
        }
    }

    private static class Player {
        float x;
        float y;
        float vx;
        float vy;
        final float w = 58f;
        final float h = 88f;
        boolean onGround = false;
        int facing = 1;
        float checkpointX = 120f;
    }
}
