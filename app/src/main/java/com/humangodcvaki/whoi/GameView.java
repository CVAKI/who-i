package com.humangodcvaki.whoi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class GameView extends View {
    private static final String TAG = "GameView";

    private Paint paint;
    private Paint backgroundPaint;
    private Paint playerPaint;
    private Paint partnerPaint;
    private Paint objectivePaint;
    private Paint debugPaint;

    private SpriteManager spriteManager;
    private String backgroundSprite;
    private String objectiveSprite;
    private String playerSprite;
    private String partnerSprite;

    private float playerX = 0.1f;
    private float playerY = 0.8f;
    private float partnerX = 0.1f;
    private float partnerY = 0.8f;

    private float objectiveX = 0.9f;
    private float objectiveY = 0.8f;

    private boolean gameActive = false;
    private boolean useSprites = false;

    // Animation state tracking
    private long lastAnimationUpdate = 0;
    private int currentPlayerFrame = 0;
    private int currentPartnerFrame = 0;
    private boolean playerMoving = false;
    private boolean partnerMoving = false;
    private float lastPlayerX = 0.1f;
    private float lastPartnerX = 0.1f;

    // Sprite scale factors for better visual presentation
    private static final float PLAYER_SCALE = 1.5f;
    private static final float OBJECTIVE_SCALE = 2.0f;
    private static final float BACKGROUND_SCALE = 1.0f;

    // Animation timing
    private static final long ANIMATION_FRAME_DURATION = 400; // milliseconds per frame

    // Fallback colors when sprites aren't available
    private int playerColor = Color.GREEN;
    private int partnerColor = Color.BLUE;
    private int objectiveColor = Color.YELLOW;
    private int backgroundColor = Color.parseColor("#87CEEB"); // Sky blue

    public GameView(Context context) {
        super(context);
        init();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true); // Smooth sprite scaling

        backgroundPaint = new Paint();
        backgroundPaint.setColor(backgroundColor);

        playerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerPaint.setColor(playerColor);

        partnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        partnerPaint.setColor(partnerColor);

        objectivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        objectivePaint.setColor(objectiveColor);

        debugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        debugPaint.setColor(Color.WHITE);
        debugPaint.setTextSize(24);

        // Set different sprites for player and partner
        playerSprite = "character_green_idle";
        partnerSprite = "character_beige_idle";
    }

    public void setSpriteManager(SpriteManager spriteManager) {
        this.spriteManager = spriteManager;
        if (spriteManager != null && spriteManager.areAssetsLoaded()) {
            useSprites = true;
            Log.d(TAG, "SpriteManager set successfully, enabling sprite rendering");
        } else {
            useSprites = false;
            Log.w(TAG, "SpriteManager assets not loaded, using fallback rendering");
        }
        invalidate();
    }

    public void setChapterData(String backgroundSprite, String objectiveSprite, String playerSprite) {
        this.backgroundSprite = backgroundSprite;
        this.objectiveSprite = objectiveSprite;

        // Set different player sprites for variety
        this.playerSprite = playerSprite != null && !playerSprite.isEmpty() ?
                playerSprite : "character_green_idle";
        this.partnerSprite = "character_beige_idle"; // Different sprite for partner

        Log.d(TAG, "Chapter data set - Background: " + backgroundSprite +
                ", Objective: " + objectiveSprite +
                ", Player: " + this.playerSprite +
                ", Partner: " + this.partnerSprite);
        invalidate();
    }

    public void setGameActive(boolean active) {
        this.gameActive = active;
        if (active) {
            lastAnimationUpdate = System.currentTimeMillis();
        }
        invalidate();
    }

    public void updatePlayerPositions(float playerX, float playerY, float partnerX, float partnerY) {
        // Detect movement for animation
        playerMoving = Math.abs(playerX - lastPlayerX) > 0.001f;
        partnerMoving = Math.abs(partnerX - lastPartnerX) > 0.001f;

        this.playerX = playerX;
        this.playerY = playerY;
        this.partnerX = partnerX;
        this.partnerY = partnerY;

        lastPlayerX = playerX;
        lastPartnerX = partnerX;

        invalidate();
    }

    public void updatePartnerPosition(float partnerX, float partnerY) {
        // Detect partner movement for animation
        partnerMoving = Math.abs(partnerX - lastPartnerX) > 0.001f;

        this.partnerX = partnerX;
        this.partnerY = partnerY;

        lastPartnerX = partnerX;

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (canvas == null) return;

        int width = getWidth();
        int height = getHeight();

        try {
            if (width <= 0 || height <= 0) return;

            // Update animation frames
            updateAnimations();

            // Draw background
            drawBackground(canvas, width, height);

            // Draw simple ground/platform
            drawGround(canvas, width, height);

            // Draw objective (door/flag)
            drawObjective(canvas, width, height);

            // Draw players with proper sprite selection
            drawPlayer(canvas, width, height, playerX, playerY, true, "You");
            drawPlayer(canvas, width, height, partnerX, partnerY, false, "Partner");

            // Draw debug info if needed
            if (!gameActive) {
                drawDebugInfo(canvas, width, height);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in onDraw", e);
            // Draw error state
            canvas.drawColor(backgroundColor);
            paint.setColor(Color.RED);
            paint.setTextSize(32);
            canvas.drawText("Rendering Error", width/2f - 100, height/2f, paint);
        }
    }

    private void updateAnimations() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAnimationUpdate > ANIMATION_FRAME_DURATION) {
            // Update player animation frame
            if (playerMoving) {
                currentPlayerFrame = (currentPlayerFrame + 1) % 2; // Cycle between 0 and 1
            } else {
                currentPlayerFrame = 0; // Idle frame
            }

            // Update partner animation frame
            if (partnerMoving) {
                currentPartnerFrame = (currentPartnerFrame + 1) % 2; // Cycle between 0 and 1
            } else {
                currentPartnerFrame = 0; // Idle frame
            }

            lastAnimationUpdate = currentTime;
        }
    }

    private void drawBackground(Canvas canvas, int width, int height) {
        if (useSprites && spriteManager != null && backgroundSprite != null && !backgroundSprite.isEmpty()) {
            try {
                Bitmap bg = spriteManager.getSprite(backgroundSprite);
                if (bg != null && !bg.isRecycled()) {
                    // Scale background to fill screen while maintaining aspect ratio
                    RectF destRect = new RectF(0, 0, width, height);
                    canvas.drawBitmap(bg, null, destRect, paint);

                    // Add some atmospheric effects if using tree background
                    if (backgroundSprite.contains("trees")) {
                        addForestAtmosphere(canvas, width, height);
                    }
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to draw background sprite, using fallback", e);
            }
        }

        // Enhanced fallback background for forest theme
        drawFallbackBackground(canvas, width, height);
    }

    private void addForestAtmosphere(Canvas canvas, int width, int height) {
        // Add subtle overlay effects for forest atmosphere
        paint.setColor(Color.parseColor("#90228B22")); // Semi-transparent green
        canvas.drawRect(0, 0, width, height, paint);

        // Add some light rays effect
        paint.setColor(Color.parseColor("#40FFFFFF")); // Semi-transparent white
        for (int i = 0; i < 3; i++) {
            float x = width * (0.2f + i * 0.3f);
            canvas.drawRect(x, 0, x + 20, height, paint);
        }
    }

    private void drawFallbackBackground(Canvas canvas, int width, int height) {
        // Enhanced gradient sky background
        canvas.drawColor(backgroundColor);

        // Draw sun
        paint.setColor(Color.YELLOW);
        paint.setAlpha(180);
        canvas.drawCircle(width * 0.85f, height * 0.15f, 50, paint);
        paint.setAlpha(255);

        // Draw enhanced clouds
        paint.setColor(Color.WHITE);
        paint.setAlpha(120);
        drawCloud(canvas, width * 0.2f, height * 0.15f, 1.0f);
        drawCloud(canvas, width * 0.7f, height * 0.1f, 1.2f);
        drawCloud(canvas, width * 0.5f, height * 0.2f, 0.8f);
        paint.setAlpha(255);

        // Draw layered hills/trees for depth
        paint.setColor(Color.parseColor("#32CD32")); // Lime green
        paint.setAlpha(100);
        drawHill(canvas, width * 0.1f, height * 0.9f, 150);
        paint.setColor(Color.parseColor("#228B22")); // Forest green
        paint.setAlpha(150);
        drawHill(canvas, width * 0.3f, height * 0.9f, 120);
        drawHill(canvas, width * 0.6f, height * 0.9f, 160);
        paint.setColor(Color.parseColor("#006400")); // Dark green
        paint.setAlpha(200);
        drawHill(canvas, width * 0.85f, height * 0.9f, 130);
        paint.setAlpha(255);
    }

    private void drawCloud(Canvas canvas, float centerX, float centerY, float scale) {
        float baseRadius = 40 * scale;
        canvas.drawCircle(centerX, centerY, baseRadius, paint);
        canvas.drawCircle(centerX - baseRadius * 0.6f, centerY, baseRadius * 0.7f, paint);
        canvas.drawCircle(centerX + baseRadius * 0.6f, centerY, baseRadius * 0.7f, paint);
        canvas.drawCircle(centerX, centerY - baseRadius * 0.4f, baseRadius * 0.6f, paint);
    }

    private void drawHill(Canvas canvas, float centerX, float centerY, float radius) {
        canvas.drawCircle(centerX, centerY, radius, paint);
    }

    private void drawGround(Canvas canvas, int width, int height) {
        // Draw layered ground for more visual appeal
        paint.setColor(Color.parseColor("#8B4513")); // Brown dirt
        float groundY = height * 0.85f;
        canvas.drawRect(0, groundY, width, height, paint);

        // Draw grass layer on top
        paint.setColor(Color.parseColor("#228B22")); // Forest green
        canvas.drawRect(0, groundY, width, groundY + 15, paint);

        // Add some grass texture
        paint.setColor(Color.parseColor("#32CD32")); // Lime green
        for (int i = 0; i < width; i += 20) {
            canvas.drawRect(i, groundY, i + 10, groundY + 8, paint);
        }
    }

    private void drawObjective(Canvas canvas, int width, int height) {
        float objX = width * objectiveX;
        float objY = height * objectiveY;

        if (useSprites && spriteManager != null && objectiveSprite != null && !objectiveSprite.isEmpty()) {
            try {
                Bitmap obj = spriteManager.getSprite(objectiveSprite);
                if (obj != null && !obj.isRecycled()) {
                    // Draw objective sprite with scaling
                    spriteManager.drawSprite(canvas, objectiveSprite,
                            objX - (obj.getWidth() * OBJECTIVE_SCALE / 2),
                            objY - (obj.getHeight() * OBJECTIVE_SCALE), OBJECTIVE_SCALE);

                    // Add glowing effect for objective
                    addObjectiveGlow(canvas, objX, objY);
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to draw objective sprite, using fallback", e);
            }
        }

        // Enhanced fallback objective
        drawFallbackObjective(canvas, objX, objY);
    }

    private void addObjectiveGlow(Canvas canvas, float objX, float objY) {
        // Add pulsing glow effect
        long time = System.currentTimeMillis();
        float pulse = (float) (0.5f + 0.5f * Math.sin(time * 0.005f));

        paint.setColor(Color.YELLOW);
        paint.setAlpha((int) (100 * pulse));
        canvas.drawCircle(objX, objY - 40, 60 + 20 * pulse, paint);
        paint.setAlpha(255);
    }

    private void drawFallbackObjective(Canvas canvas, float objX, float objY) {
        // Enhanced flag design
        paint.setColor(objectiveColor);

        // Flag pole with gradient effect
        paint.setColor(Color.parseColor("#8B4513")); // Brown pole
        canvas.drawRect(objX - 3, objY - 90, objX + 3, objY, paint);

        // Flag with animation
        long time = System.currentTimeMillis();
        float wave = (float) Math.sin(time * 0.01f) * 5;

        paint.setColor(objectiveColor);
        float[] flagPoints = {
                objX + 3, objY - 90,
                objX + 50 + wave, objY - 85,
                objX + 45 + wave, objY - 65,
                objX + 3, objY - 60
        };

        canvas.drawRect(objX + 3, objY - 85, objX + 45 + wave, objY - 65, paint);

        // Add "GOAL" text with outline
        paint.setColor(Color.BLACK);
        paint.setTextSize(18);
        paint.setStrokeWidth(3);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawText("GOAL", objX - 15, objY + 20, paint);

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText("GOAL", objX - 15, objY + 20, paint);
        paint.setStrokeWidth(1);
    }

    private void drawPlayer(Canvas canvas, int width, int height, float x, float y, boolean isMainPlayer, String label) {
        float playerPixelX = width * x;
        float playerPixelY = height * y;

        if (useSprites && spriteManager != null) {
            String currentSprite = getCurrentPlayerSprite(isMainPlayer);

            if (currentSprite != null && !currentSprite.isEmpty()) {
                try {
                    Bitmap playerBitmap = spriteManager.getSprite(currentSprite);
                    if (playerBitmap != null && !playerBitmap.isRecycled()) {
                        // Draw player sprite with proper positioning
                        spriteManager.drawSprite(canvas, currentSprite,
                                playerPixelX - (playerBitmap.getWidth() * PLAYER_SCALE / 2),
                                playerPixelY - (playerBitmap.getHeight() * PLAYER_SCALE), PLAYER_SCALE);

                        // Draw enhanced name label
                        drawPlayerLabel(canvas, playerPixelX, playerPixelY, label, isMainPlayer);
                        return;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to draw player sprite, using fallback", e);
                }
            }
        }

        // Enhanced fallback character
        drawFallbackPlayer(canvas, playerPixelX, playerPixelY, isMainPlayer ? playerColor : partnerColor, label);
    }

    private String getCurrentPlayerSprite(boolean isMainPlayer) {
        if (isMainPlayer) {
            // Player sprites based on movement state
            if (playerMoving) {
                return currentPlayerFrame == 0 ? "character_green_walk_a" : "character_green_walk_b";
            } else {
                return "character_green_idle";
            }
        } else {
            // Partner sprites based on movement state
            if (partnerMoving) {
                return currentPartnerFrame == 0 ? "character_beige_walk_a" : "character_beige_walk_b";
            } else {
                return "character_beige_idle";
            }
        }
    }

    private void drawPlayerLabel(Canvas canvas, float playerPixelX, float playerPixelY, String label, boolean isMainPlayer) {
        // Enhanced label with background
        paint.setColor(Color.parseColor("#80000000")); // Semi-transparent black background
        float textWidth = paint.measureText(label);
        canvas.drawRoundRect(playerPixelX - textWidth/2 - 10, playerPixelY - 100,
                playerPixelX + textWidth/2 + 10, playerPixelY - 75, 5, 5, paint);

        paint.setColor(isMainPlayer ? Color.CYAN : Color.WHITE);
        paint.setTextSize(16);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, playerPixelX, playerPixelY - 82, paint);
        paint.setTextAlign(Paint.Align.LEFT); // Reset alignment
    }

    private void drawFallbackPlayer(Canvas canvas, float playerPixelX, float playerPixelY, int color, String label) {
        paint.setColor(color);

        // Enhanced stick figure with animation
        boolean isMoving = (color == playerColor) ? playerMoving : partnerMoving;
        int frame = (color == playerColor) ? currentPlayerFrame : currentPartnerFrame;

        float bodyOffset = isMoving ? (float) Math.sin(System.currentTimeMillis() * 0.01f) * 2 : 0;

        // Body (rectangle with slight movement)
        canvas.drawRect(playerPixelX - 15, playerPixelY - 40 + bodyOffset,
                playerPixelX + 15, playerPixelY + bodyOffset, paint);

        // Head (circle)
        canvas.drawCircle(playerPixelX, playerPixelY - 50 + bodyOffset, 12, paint);

        // Animated arms and legs
        paint.setStrokeWidth(6);

        if (isMoving) {
            float armSwing = frame == 0 ? -10 : 10;
            float legSwing = frame == 0 ? -8 : 8;

            // Arms with swing
            canvas.drawLine(playerPixelX - 15, playerPixelY - 30 + bodyOffset,
                    playerPixelX - 25 + armSwing, playerPixelY - 15 + bodyOffset, paint);
            canvas.drawLine(playerPixelX + 15, playerPixelY - 30 + bodyOffset,
                    playerPixelX + 25 - armSwing, playerPixelY - 15 + bodyOffset, paint);

            // Legs with walking motion
            canvas.drawLine(playerPixelX - 8, playerPixelY + bodyOffset,
                    playerPixelX - 15 + legSwing, playerPixelY + 20, paint);
            canvas.drawLine(playerPixelX + 8, playerPixelY + bodyOffset,
                    playerPixelX + 15 - legSwing, playerPixelY + 20, paint);
        } else {
            // Static arms and legs
            canvas.drawLine(playerPixelX - 15, playerPixelY - 30 + bodyOffset,
                    playerPixelX - 25, playerPixelY - 15 + bodyOffset, paint);
            canvas.drawLine(playerPixelX + 15, playerPixelY - 30 + bodyOffset,
                    playerPixelX + 25, playerPixelY - 15 + bodyOffset, paint);
            canvas.drawLine(playerPixelX - 8, playerPixelY + bodyOffset,
                    playerPixelX - 15, playerPixelY + 20, paint);
            canvas.drawLine(playerPixelX + 8, playerPixelY + bodyOffset,
                    playerPixelX + 15, playerPixelY + 20, paint);
        }

        paint.setStrokeWidth(1);

        // Draw enhanced name label
        drawPlayerLabel(canvas, playerPixelX, playerPixelY, label, color == playerColor);
    }

    private void drawDebugInfo(Canvas canvas, int width, int height) {
        // Enhanced debug info with background
        paint.setColor(Color.parseColor("#80000000")); // Semi-transparent background
        canvas.drawRoundRect(10, 10, 400, 180, 10, 10, paint);

        debugPaint.setColor(Color.WHITE);
        debugPaint.setShadowLayer(2, 1, 1, Color.BLACK);

        canvas.drawText("Waiting for game to start...", 20, 40, debugPaint);
        canvas.drawText("Sprites: " + (useSprites ? "Enabled ✓" : "Fallback Mode"), 20, 70, debugPaint);
        canvas.drawText("Assets Loaded: " + (spriteManager != null && spriteManager.areAssetsLoaded()), 20, 100, debugPaint);
        canvas.drawText("Player: " + String.format("%.2f, %.2f", playerX, playerY), 20, 130, debugPaint);
        canvas.drawText("Partner: " + String.format("%.2f, %.2f", partnerX, partnerY), 20, 160, debugPaint);

        debugPaint.clearShadowLayer();
    }

    // Public method to check sprite system status
    public boolean isUsingSpriteSystem() {
        return useSprites && spriteManager != null && spriteManager.areAssetsLoaded();
    }

    // Method to manually refresh sprite system (useful for debugging)
    public void refreshSpriteSystem() {
        if (spriteManager != null) {
            useSprites = spriteManager.areAssetsLoaded();
            Log.d(TAG, "Sprite system refreshed. Using sprites: " + useSprites);
            invalidate();
        }
    }
}