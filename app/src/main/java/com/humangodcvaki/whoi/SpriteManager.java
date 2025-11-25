package com.humangodcvaki.whoi;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;

public class SpriteManager {
    private static final String TAG = "SpriteManager";

    private Context context;
    private Map<String, Bitmap> spriteCache;
    private Paint spritePaint;

    // Enhanced loading state tracking
    private boolean assetsLoaded = false;
    private boolean backgroundsLoaded = false;
    private boolean charactersLoaded = false;
    private boolean tilesLoaded = false;
    private boolean enemiesLoaded = false;

    // Loading statistics for debugging
    private int totalSpritesLoaded = 0;
    private long loadingStartTime = 0;
    private long loadingEndTime = 0;

    // Sprite categories and their file lists
    private static final String SPRITES_BASE_PATH = "Sprites/";

    // Character sprites (128x128)
    private static final String[] CHARACTER_COLORS = {"beige", "green", "pink", "purple", "yellow"};
    private static final String[] CHARACTER_ACTIONS = {
            "climb_a", "climb_b", "duck", "front", "hit", "idle", "jump", "walk_a", "walk_b"
    };

    // Background sprites (256x256)
    private static final String[] BACKGROUND_SPRITES = {
            "background_clouds", "background_color_desert", "background_color_hills",
            "background_color_mushrooms", "background_color_trees", "background_fade_desert",
            "background_fade_hills", "background_fade_mushrooms", "background_fade_trees",
            "background_solid_cloud", "background_solid_dirt", "background_solid_grass",
            "background_solid_sand", "background_solid_sky"
    };

    // Enemy sprites (64x64)
    private static final String[] ENEMY_SPRITES = {
            // Barnacle
            "barnacle_attack_a", "barnacle_attack_b", "barnacle_attack_rest",
            // Bee
            "bee_a", "bee_b", "bee_rest",
            // Block
            "block_fall", "block_idle", "block_rest",
            // Fish
            "fish_blue_rest", "fish_blue_swim_a", "fish_blue_swim_b",
            "fish_purple_down", "fish_purple_rest", "fish_purple_up",
            "fish_yellow_rest", "fish_yellow_swim_a", "fish_yellow_swim_b",
            // Fly
            "fly_a", "fly_b", "fly_rest",
            // Frog
            "frog_idle", "frog_jump", "frog_rest",
            // Ladybug
            "ladybug_fly", "ladybug_rest", "ladybug_walk_a", "ladybug_walk_b",
            // Mouse
            "mouse_rest", "mouse_walk_a", "mouse_walk_b",
            // Saw
            "saw_a", "saw_b", "saw_rest",
            // Slimes
            "slime_block_jump", "slime_block_rest", "slime_block_walk_a", "slime_block_walk_b",
            "slime_fire_flat", "slime_fire_rest", "slime_fire_walk_a", "slime_fire_walk_b",
            "slime_normal_flat", "slime_normal_rest", "slime_normal_walk_a", "slime_normal_walk_b",
            "slime_spike_flat", "slime_spike_rest", "slime_spike_walk_a", "slime_spike_walk_b",
            // Snail
            "snail_rest", "snail_shell", "snail_walk_a", "snail_walk_b",
            // Worms
            "worm_normal_move_a", "worm_normal_move_b", "worm_normal_rest",
            "worm_ring_move_a", "worm_ring_move_b", "worm_ring_rest"
    };

    // Tile sprites (64x64) - Main categories
    private static final String[] TILE_SPRITES = {
            // Basic blocks
            "block_blue", "block_coin", "block_coin_active", "block_empty", "block_empty_warning",
            "block_exclamation", "block_exclamation_active", "block_green", "block_plank",
            "block_planks", "block_red", "block_spikes", "block_yellow",
            // Strong blocks
            "block_strong_coin", "block_strong_coin_active", "block_strong_danger",
            "block_strong_danger_active", "block_strong_empty", "block_strong_empty_active",
            "block_strong_exclamation", "block_strong_exclamation_active",
            // Items
            "bomb", "bomb_active", "bridge", "bridge_logs", "bush", "cactus", "chain",
            // Coins
            "coin_bronze", "coin_bronze_side", "coin_gold", "coin_gold_side",
            "coin_silver", "coin_silver_side",
            // Doors
            "door_closed", "door_closed_top", "door_open", "door_open_top",
            // Flags
            "flag_blue_a", "flag_blue_b", "flag_green_a", "flag_green_b", "flag_off",
            "flag_red_a", "flag_red_b", "flag_yellow_a", "flag_yellow_b",
            // Gems
            "gem_blue", "gem_green", "gem_red", "gem_yellow",
            // Keys
            "key_blue", "key_green", "key_red", "key_yellow",
            // Locks
            "lock_blue", "lock_green", "lock_red", "lock_yellow",
            // Common terrain blocks
            "terrain_dirt_block", "terrain_grass_block", "terrain_stone_block", "terrain_sand_block"
    };

    public SpriteManager(Context context) {
        this.context = context;
        this.spriteCache = new HashMap<>();
        this.spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        spritePaint.setFilterBitmap(true);

        loadingStartTime = System.currentTimeMillis();
        Log.d(TAG, "Starting SpriteManager initialization...");

        loadAllSprites();

        assetsLoaded = backgroundsLoaded && charactersLoaded;
        loadingEndTime = System.currentTimeMillis();

        if (assetsLoaded) {
            Log.i(TAG, String.format("SpriteManager initialization complete in %dms. Loaded %d sprites.",
                    (loadingEndTime - loadingStartTime), totalSpritesLoaded));
        } else {
            Log.w(TAG, "Critical assets not loaded, sprites will use fallback rendering");
        }
    }

    private void loadAllSprites() {
        Log.d(TAG, "Loading individual sprite files...");

        // Load character sprites (critical)
        charactersLoaded = loadCharacterSprites();

        // Load background sprites (critical)
        backgroundsLoaded = loadBackgroundSprites();

        // Load tile sprites (optional but recommended)
        tilesLoaded = loadTileSprites();

        // Load enemy sprites (optional)
        enemiesLoaded = loadEnemySprites();

        Log.d(TAG, String.format("Sprite loading complete - Characters: %s, Backgrounds: %s, Tiles: %s, Enemies: %s",
                charactersLoaded ? "✓" : "✗", backgroundsLoaded ? "✓" : "✗",
                tilesLoaded ? "✓" : "✗", enemiesLoaded ? "✓" : "✗"));
    }

    private boolean loadCharacterSprites() {
        Log.d(TAG, "Loading character sprites...");
        int loadedCount = 0;
        int totalCharacterSprites = CHARACTER_COLORS.length * CHARACTER_ACTIONS.length;

        for (String color : CHARACTER_COLORS) {
            for (String action : CHARACTER_ACTIONS) {
                String spriteName = "character_" + color + "_" + action;
                String filePath = SPRITES_BASE_PATH + "Characters/" + spriteName + ".png";

                if (loadSingleSprite(spriteName, filePath)) {
                    loadedCount++;
                }
            }
        }

        Log.d(TAG, String.format("Character sprites loaded: %d/%d", loadedCount, totalCharacterSprites));
        totalSpritesLoaded += loadedCount;
        return loadedCount >= (totalCharacterSprites * 0.8f); // Success if 80% loaded
    }

    private boolean loadBackgroundSprites() {
        Log.d(TAG, "Loading background sprites...");
        int loadedCount = 0;

        for (String spriteName : BACKGROUND_SPRITES) {
            String filePath = SPRITES_BASE_PATH + "Backgrounds/" + spriteName + ".png";

            if (loadSingleSprite(spriteName, filePath)) {
                loadedCount++;
            }
        }

        Log.d(TAG, String.format("Background sprites loaded: %d/%d", loadedCount, BACKGROUND_SPRITES.length));
        totalSpritesLoaded += loadedCount;
        return loadedCount >= (BACKGROUND_SPRITES.length * 0.7f); // Success if 70% loaded
    }

    private boolean loadEnemySprites() {
        Log.d(TAG, "Loading enemy sprites...");
        int loadedCount = 0;

        for (String spriteName : ENEMY_SPRITES) {
            String filePath = SPRITES_BASE_PATH + "Enemies/" + spriteName + ".png";

            if (loadSingleSprite(spriteName, filePath)) {
                loadedCount++;
            }
        }

        Log.d(TAG, String.format("Enemy sprites loaded: %d/%d", loadedCount, ENEMY_SPRITES.length));
        totalSpritesLoaded += loadedCount;
        return loadedCount > 0; // Any enemy sprites loaded is considered success
    }

    private boolean loadTileSprites() {
        Log.d(TAG, "Loading tile sprites...");
        int loadedCount = 0;

        for (String spriteName : TILE_SPRITES) {
            String filePath = SPRITES_BASE_PATH + "Tiles/" + spriteName + ".png";

            if (loadSingleSprite(spriteName, filePath)) {
                loadedCount++;
            }
        }

        // Also try to load additional terrain sprites dynamically
        loadedCount += loadTerrainSprites();

        Log.d(TAG, String.format("Tile sprites loaded: %d total", loadedCount));
        totalSpritesLoaded += loadedCount;
        return loadedCount > 0; // Any tile sprites loaded is considered success
    }

    private int loadTerrainSprites() {
        int loadedCount = 0;
        String[] terrainTypes = {"dirt", "grass", "purple", "sand", "snow", "stone"};
        String[] terrainVariations = {"block", "cloud", "horizontal", "vertical", "ramp_left", "ramp_right"};

        for (String type : terrainTypes) {
            for (String variation : terrainVariations) {
                String spriteName = "terrain_" + type + "_" + variation;
                String filePath = SPRITES_BASE_PATH + "Tiles/" + spriteName + ".png";

                if (loadSingleSprite(spriteName, filePath)) {
                    loadedCount++;
                }
            }
        }

        return loadedCount;
    }

    private boolean loadSingleSprite(String spriteName, String filePath) {
        try {
            InputStream stream = context.getAssets().open(filePath);
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            stream.close();

            if (bitmap != null && !bitmap.isRecycled()) {
                spriteCache.put(spriteName, bitmap);
                return true;
            } else {
                Log.w(TAG, String.format("Sprite %s is null after loading from %s", spriteName, filePath));
            }
        } catch (IOException e) {
            // Silently fail for optional sprites, only log for critical ones
            if (spriteName.contains("character_green") || spriteName.contains("character_beige") ||
                    spriteName.contains("background_solid_sky")) {
                Log.w(TAG, String.format("Failed to load critical sprite %s from %s: %s",
                        spriteName, filePath, e.getMessage()));
            }
        } catch (Exception e) {
            Log.e(TAG, String.format("Error loading sprite %s: %s", spriteName, e.getMessage()));
        }

        return false;
    }

    public Bitmap getSprite(String spriteName) {
        if (!assetsLoaded || spriteName == null || spriteName.isEmpty()) {
            return null;
        }

        Bitmap sprite = spriteCache.get(spriteName);
        if (sprite != null && !sprite.isRecycled()) {
            return sprite;
        } else if (sprite != null && sprite.isRecycled()) {
            spriteCache.remove(spriteName); // Remove recycled sprite
            Log.w(TAG, "Removed recycled sprite: " + spriteName);
        }

        Log.w(TAG, "Sprite not found: " + spriteName);
        return null;
    }

    public void drawSprite(Canvas canvas, String spriteName, float x, float y, float scale) {
        if (!assetsLoaded || canvas == null) {
            return;
        }

        Bitmap sprite = getSprite(spriteName);
        if (sprite != null && !sprite.isRecycled()) {
            try {
                Rect destRect = new Rect(
                        (int) x,
                        (int) y,
                        (int) (x + sprite.getWidth() * scale),
                        (int) (y + sprite.getHeight() * scale)
                );
                canvas.drawBitmap(sprite, null, destRect, spritePaint);
            } catch (Exception e) {
                Log.e(TAG, "Error drawing sprite: " + spriteName, e);
            }
        }
    }

    public void drawSprite(Canvas canvas, String spriteName, float x, float y) {
        drawSprite(canvas, spriteName, x, y, 1.0f);
    }

    public void drawSpriteFlipped(Canvas canvas, String spriteName, float x, float y, boolean flipX, boolean flipY) {
        if (!assetsLoaded || canvas == null) {
            return;
        }

        Bitmap sprite = getSprite(spriteName);
        if (sprite == null || sprite.isRecycled()) return;

        try {
            canvas.save();
            canvas.translate(x + sprite.getWidth() / 2f, y + sprite.getHeight() / 2f);
            canvas.scale(flipX ? -1 : 1, flipY ? -1 : 1);
            canvas.translate(-sprite.getWidth() / 2f, -sprite.getHeight() / 2f);
            canvas.drawBitmap(sprite, 0, 0, spritePaint);
            canvas.restore();
        } catch (Exception e) {
            Log.e(TAG, "Error drawing flipped sprite: " + spriteName, e);
        }
    }

    public void drawSpriteWithRotation(Canvas canvas, String spriteName, float x, float y, float rotation, float scale) {
        if (!assetsLoaded || canvas == null) {
            return;
        }

        Bitmap sprite = getSprite(spriteName);
        if (sprite == null || sprite.isRecycled()) return;

        try {
            canvas.save();
            canvas.translate(x + (sprite.getWidth() * scale) / 2f, y + (sprite.getHeight() * scale) / 2f);
            canvas.rotate(rotation);
            canvas.scale(scale, scale);
            canvas.translate(-sprite.getWidth() / 2f, -sprite.getHeight() / 2f);
            canvas.drawBitmap(sprite, 0, 0, spritePaint);
            canvas.restore();
        } catch (Exception e) {
            Log.e(TAG, "Error drawing rotated sprite: " + spriteName, e);
        }
    }

    public int getSpriteWidth(String spriteName) {
        if (!assetsLoaded) {
            return getDefaultSpriteSize(spriteName);
        }

        Bitmap sprite = getSprite(spriteName);
        return sprite != null ? sprite.getWidth() : getDefaultSpriteSize(spriteName);
    }

    public int getSpriteHeight(String spriteName) {
        if (!assetsLoaded) {
            return getDefaultSpriteSize(spriteName);
        }

        Bitmap sprite = getSprite(spriteName);
        return sprite != null ? sprite.getHeight() : getDefaultSpriteSize(spriteName);
    }

    private int getDefaultSpriteSize(String spriteName) {
        if (spriteName != null) {
            if (spriteName.startsWith("Character_")) return 128;
            if (spriteName.startsWith("background_")) return 256;
            if (spriteName.startsWith("terrain_") || spriteName.startsWith("tile_") ||
                    spriteName.startsWith("coin_") || spriteName.startsWith("gem_") ||
                    spriteName.startsWith("flag_") || spriteName.startsWith("key_") ||
                    spriteName.startsWith("lock_") || spriteName.contains("enemy")) return 64;
        }
        return 64; // Default size
    }

    public boolean areAssetsLoaded() {
        return assetsLoaded;
    }

    public boolean hasSprite(String spriteName) {
        return assetsLoaded && spriteCache.containsKey(spriteName);
    }

    public int getTotalSpritesLoaded() {
        return totalSpritesLoaded;
    }

    public long getLoadingTime() {
        return loadingEndTime - loadingStartTime;
    }

    // Get loading status for different sprite categories
    public boolean areBackgroundsLoaded() { return backgroundsLoaded; }
    public boolean areCharactersLoaded() { return charactersLoaded; }
    public boolean areTilesLoaded() { return tilesLoaded; }
    public boolean areEnemiesLoaded() { return enemiesLoaded; }

    // Get alternative character sprites for variety
    public String[] getAvailableCharacterColors() {
        return CHARACTER_COLORS.clone();
    }

    public String[] getAvailableBackgrounds() {
        return new String[]{
                "background_color_trees", "background_color_desert", "background_color_hills",
                "background_color_mushrooms", "background_solid_sky", "background_solid_grass"
        };
    }

    public String[] getAvailableFlags() {
        return new String[]{
                "flag_green_a", "flag_blue_a", "flag_red_a", "flag_yellow_a"
        };
    }

    // New method to get all loaded sprite names
    public String[] getLoadedSpriteNames() {
        return spriteCache.keySet().toArray(new String[0]);
    }

    // Method to get sprites by category
    @SuppressLint("NewApi")
    @RequiresApi(api = Build.VERSION_CODES.N)
    public String[] getSpritesByCategory(String category) {
        return spriteCache.keySet().stream()
                .filter(name -> name.startsWith(category + "_"))
                .toArray(String[]::new);
    }

    public void cleanup() {
        try {
            Log.d(TAG, "Starting SpriteManager cleanup...");

            // Clean up all cached sprites
            for (Map.Entry<String, Bitmap> entry : spriteCache.entrySet()) {
                Bitmap bitmap = entry.getValue();
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
            spriteCache.clear();

            assetsLoaded = false;
            backgroundsLoaded = false;
            charactersLoaded = false;
            tilesLoaded = false;
            enemiesLoaded = false;

            Log.d(TAG, "SpriteManager cleanup completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }

    // Diagnostic method for debugging
    public void printLoadingReport() {
        Log.i(TAG, "=== SpriteManager Loading Report ===");
        Log.i(TAG, "Assets Loaded: " + assetsLoaded);
        Log.i(TAG, "Loading Time: " + getLoadingTime() + "ms");
        Log.i(TAG, "Total Sprites: " + totalSpritesLoaded);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Log.i(TAG, "Backgrounds Loaded: " + backgroundsLoaded + " (" + getSpritesByCategory("background").length + " sprites)");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Log.i(TAG, "Characters Loaded: " + charactersLoaded + " (" + getSpritesByCategory("character").length + " sprites)");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Log.i(TAG, "Tiles Loaded: " + tilesLoaded + " (" + (getSpritesByCategory("terrain").length + getSpritesByCategory("block").length + getSpritesByCategory("coin").length) + " sprites)");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Log.i(TAG, "Enemies Loaded: " + enemiesLoaded + " (" + (getSpritesByCategory("slime").length + getSpritesByCategory("fish").length + getSpritesByCategory("mouse").length) + " sprites)");
        }
        Log.i(TAG, "Total Cached Sprites: " + spriteCache.size());
        Log.i(TAG, "====================================");
    }
    // Add these methods to your SpriteManager.java class

    public void drawSprite(Canvas canvas, String spriteName, Rect src, Rect dst) {
        if (!assetsLoaded || canvas == null) {
            return;
        }

        Bitmap sprite = getSprite(spriteName);
        if (sprite != null && !sprite.isRecycled()) {
            try {
                canvas.drawBitmap(sprite, src, dst, spritePaint);
            } catch (Exception e) {
                Log.e(TAG, "Error drawing sprite with rects: " + spriteName, e);
            }
        }
    }

    public void drawSprite(Canvas canvas, String spriteName, float x, float y, float width, float height) {
        if (!assetsLoaded || canvas == null) {
            return;
        }

        Bitmap sprite = getSprite(spriteName);
        if (sprite != null && !sprite.isRecycled()) {
            try {
                Rect destRect = new Rect((int)x, (int)y, (int)(x + width), (int)(y + height));
                canvas.drawBitmap(sprite, null, destRect, spritePaint);
            } catch (Exception e) {
                Log.e(TAG, "Error drawing sprite with dimensions: " + spriteName, e);
            }
        }
    }

    // Enhanced sprite drawing with alpha
    public void drawSpriteWithAlpha(Canvas canvas, String spriteName, float x, float y, float scale, int alpha) {
        if (!assetsLoaded || canvas == null) {
            return;
        }

        Bitmap sprite = getSprite(spriteName);
        if (sprite == null || sprite.isRecycled()) return;

        try {
            Paint alphaPaint = new Paint(spritePaint);
            alphaPaint.setAlpha(alpha);

            Rect destRect = new Rect(
                    (int) x,
                    (int) y,
                    (int) (x + sprite.getWidth() * scale),
                    (int) (y + sprite.getHeight() * scale)
            );
            canvas.drawBitmap(sprite, null, destRect, alphaPaint);
        } catch (Exception e) {
            Log.e(TAG, "Error drawing sprite with alpha: " + spriteName, e);
        }
    }

    // Tinted sprite drawing
    public void drawSpriteWithTint(Canvas canvas, String spriteName, float x, float y, float scale, int tintColor) {
        if (!assetsLoaded || canvas == null) {
            return;
        }

        Bitmap sprite = getSprite(spriteName);
        if (sprite == null || sprite.isRecycled()) return;

        try {
            Paint tintPaint = new Paint(spritePaint);
            tintPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(tintColor, android.graphics.PorterDuff.Mode.MULTIPLY));

            Rect destRect = new Rect(
                    (int) x,
                    (int) y,
                    (int) (x + sprite.getWidth() * scale),
                    (int) (y + sprite.getHeight() * scale)
            );
            canvas.drawBitmap(sprite, null, destRect, tintPaint);
        } catch (Exception e) {
            Log.e(TAG, "Error drawing tinted sprite: " + spriteName, e);
        }
    }

}