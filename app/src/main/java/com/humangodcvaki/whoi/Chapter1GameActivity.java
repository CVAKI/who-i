package com.humangodcvaki.whoi;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Chapter1GameActivity extends AppCompatActivity {

    private static final String TAG = "Chapter1GameActivity";
    private static final String GAME_ROOMS_NODE = "gameRooms";

    // Enhanced Game constants for Chapter 1 - Forest Adventure with sprite variety
    private static final String[] BACKGROUND_OPTIONS = {
            "background_color_trees", "background_fade_trees", "background_solid_grass"
    };
    private static final String[] PLAYER_COLORS = {
            "green", "pink", "purple", "yellow", "beige"
    };
    private static final String[] OBJECTIVE_OPTIONS = {
            "flag_green_a", "flag_blue_a", "flag_yellow_a"
    };

    // Selected sprites for this game session
    private String selectedBackground;
    private String selectedObjective;
    private String playerCharacterColor;
    private String partnerCharacterColor;

    // UI Elements
    private GameView gameView;
    private TextView statusText, timerText, scoreText;
    private TextView player1StatusText, player2StatusText;
    private ImageButton btnLeft, btnRight, btnJump;
    private Button btnPause;

    // Firebase
    private FirebaseFirestore firestore;
    private DatabaseReference realtimeDb;
    private String uid;
    private String currentUserName;

    // Game Data
    private String gameRoomId;
    private String partnerId;
    private String partnerName;
    private boolean isInitiator;
    private int selectedChapter;

    // Game State
    private boolean gameActive = false;
    private boolean gameEnded = false;
    private boolean isInitialized = false;
    private boolean spriteSystemReady = false;
    private long gameStartTime;
    private int playerScore = 0;
    private int partnerScore = 0;

    // Player positions (normalized 0-1)
    private float myPlayerX = 0.1f;
    private float myPlayerY = 0.8f;
    private float partnerPlayerX = 0.1f;
    private float partnerPlayerY = 0.8f;

    // Game objects
    private float objectiveDoorX = 0.9f;
    private float objectiveDoorY = 0.8f;

    // Enhanced movement and control
    private boolean movingLeft = false;
    private boolean movingRight = false;
    private boolean jumping = false;
    private float playerSpeed = 0.005f; // Optimized speed for better gameplay
    private float jumpVelocity = 0f;
    private float gravity = 0.0050f; // Improved gravity feel
    private float maxJumpHeight = 0.3f; // Prevent jumping too high

    private ValueEventListener gameRoomListener;
    private Handler gameUpdateHandler;
    private Handler uiHandler;
    private SpriteManager spriteManager;
    private boolean listenersActive = false;

    // Performance optimization
    private long lastPositionUpdate = 0;
    private static final long POSITION_UPDATE_INTERVAL = 100; // ms
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Chapter1GameActivity onCreate started");

        try {
            setContentView(R.layout.activity_chapter1_game);
        } catch (Exception e) {
            Log.e(TAG, "Error setting content view", e);
            showErrorAndFinish("Layout error occurred");
            return;
        }

        // Initialize handlers first
        gameUpdateHandler = new Handler(Looper.getMainLooper());
        uiHandler = new Handler(Looper.getMainLooper());

        if (!initializeFirebase()) {
            return;
        }

        if (!validateIntentData()) {
            return;
        }

        // Select random sprites for variety
        selectGameSprites();

        if (!initializeUI()) {
            return;
        }

        // Initialize sprite manager with progress callback
        initializeSpriteManagerAsync();

        // Setup game room listener after sprite system is ready
        uiHandler.postDelayed(() -> {
            if (!isFinishing()) {
                setupGameRoomListener();
                markPlayerReady();
                isInitialized = true;
            }
        }, 1500); // Increased delay to allow sprite loading

        // Auto-start timeout with better messaging
        uiHandler.postDelayed(() -> {
            if (!gameActive && !gameEnded && !isFinishing()) {
                Log.w(TAG, "Auto-starting game after timeout");
                runOnUiThread(() -> {
                    if (statusText != null) {
                        statusText.setText("Starting game automatically...");
                    }
                });
                forceStartGame();
            }
        }, 7000); // Increased timeout for sprite loading

        Log.d(TAG, "Chapter1GameActivity onCreate completed");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Chapter1GameActivity onDestroy");

        try {
            gameActive = false;
            gameEnded = true;
            listenersActive = false;

            if (gameUpdateHandler != null) {
                gameUpdateHandler.removeCallbacksAndMessages(null);
            }
            if (uiHandler != null) {
                uiHandler.removeCallbacksAndMessages(null);
            }

            if (gameRoomListener != null && gameRoomId != null && realtimeDb != null) {
                realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).removeEventListener(gameRoomListener);
            }

            if (spriteManager != null) {
                spriteManager.cleanup();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }

    @Override
    public void onBackPressed() {
        if (!isFinishing()) {
            showPauseDialog();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Chapter1GameActivity onPause");

        // Temporarily pause game updates to save battery
        if (gameUpdateHandler != null) {
            gameUpdateHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Chapter1GameActivity onResume");

        // Resume game updates if game is active
        if (gameActive && !gameEnded && gameUpdateHandler != null) {
            gameUpdateHandler.postDelayed(() -> {
                if (gameActive && !gameEnded && !isFinishing()) {
                    startGameLoop();
                }
            }, 100);
        }
    }

    // Utility method to check if custom sprites are being used
    public boolean isUsingCustomSprites() {
        return spriteManager != null && spriteManager.areAssetsLoaded();
    }

    // Method to get current game sprite information (useful for debugging)
    public Map<String, String> getCurrentSpriteInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("background", selectedBackground);
        info.put("objective", selectedObjective);
        info.put("playerColor", playerCharacterColor);
        info.put("partnerColor", partnerCharacterColor);
        info.put("spritesLoaded", String.valueOf(isUsingCustomSprites()));
        return info;
    }

    private boolean initializeFirebase() {
        try {
            firestore = FirebaseFirestore.getInstance();
            realtimeDb = FirebaseDatabase.getInstance().getReference();

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Log.e(TAG, "User not authenticated!");
                showErrorAndFinish("Authentication error");
                return false;
            }

            uid = currentUser.getUid();
            currentUserName = currentUser.getDisplayName();
            if (currentUserName == null || currentUserName.trim().isEmpty()) {
                currentUserName = "You";
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization error", e);
            showErrorAndFinish("Firebase connection error");
            return false;
        }
    }

    private boolean validateIntentData() {
        try {
            Intent intent = getIntent();
            gameRoomId = intent.getStringExtra("gameRoomId");
            partnerId = intent.getStringExtra("partnerId");
            partnerName = intent.getStringExtra("partnerName");
            isInitiator = intent.getBooleanExtra("isInitiator", false);
            selectedChapter = intent.getIntExtra("selectedChapter", 1);

            if (gameRoomId == null || partnerId == null) {
                Log.e(TAG, "Invalid game data from Intent");
                showErrorAndFinish("Invalid game data");
                return false;
            }

            if (partnerName == null || partnerName.trim().isEmpty()) {
                partnerName = "Partner";
            }

            Log.d(TAG, "Intent data validated - Room: " + gameRoomId + ", Partner: " + partnerId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error validating intent data", e);
            showErrorAndFinish("Data validation error");
            return false;
        }
    }

    private void selectGameSprites() {
        try {
            // Select random sprites for variety in each game
            selectedBackground = BACKGROUND_OPTIONS[random.nextInt(BACKGROUND_OPTIONS.length)];
            selectedObjective = OBJECTIVE_OPTIONS[random.nextInt(OBJECTIVE_OPTIONS.length)];

            // Assign different colors to players
            playerCharacterColor = PLAYER_COLORS[random.nextInt(PLAYER_COLORS.length)];
            do {
                partnerCharacterColor = PLAYER_COLORS[random.nextInt(PLAYER_COLORS.length)];
            } while (partnerCharacterColor.equals(playerCharacterColor)); // Ensure different colors

            Log.d(TAG, String.format("Selected sprites - Background: %s, Objective: %s, Player: %s, Partner: %s",
                    selectedBackground, selectedObjective, playerCharacterColor, partnerCharacterColor));
        } catch (Exception e) {
            Log.e(TAG, "Error selecting game sprites", e);
            // Fallback to default sprites
            selectedBackground = "background_color_trees";
            selectedObjective = "flag_green_a";
            playerCharacterColor = "green";
            partnerCharacterColor = "beige";
        }
    }

    private boolean initializeUI() {
        try {
            // Find all UI elements with null checks
            gameView = findViewById(R.id.gameView);
            statusText = findViewById(R.id.statusText);
            timerText = findViewById(R.id.timerText);
            scoreText = findViewById(R.id.scoreText);
            player1StatusText = findViewById(R.id.player1StatusText);
            player2StatusText = findViewById(R.id.player2StatusText);
            btnLeft = findViewById(R.id.btnLeft);
            btnRight = findViewById(R.id.btnRight);
            btnJump = findViewById(R.id.btnJump);
            btnPause = findViewById(R.id.btnPause);

            // Check for critical null elements
            if (gameView == null || statusText == null || btnLeft == null ||
                    btnRight == null || btnJump == null || btnPause == null) {
                Log.e(TAG, "Critical UI elements are null");
                showErrorAndFinish("UI initialization failed");
                return false;
            }

            // Set enhanced initial texts
            runOnUiThread(() -> {
                try {
                    statusText.setText("Chapter 1: Forest Adventure - Loading sprites...");
                    if (player1StatusText != null) {
                        player1StatusText.setText(currentUserName + " (" + playerCharacterColor + "): Preparing...");
                    }
                    if (player2StatusText != null) {
                        player2StatusText.setText(partnerName + " (" + partnerCharacterColor + "): Preparing...");
                    }
                    if (timerText != null) {
                        timerText.setText("00:00");
                    }
                    if (scoreText != null) {
                        scoreText.setText("Scores - You: 0 | " + partnerName + ": 0");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error setting initial UI texts", e);
                }
            });

            setupEnhancedControlButtons();
            btnPause.setOnClickListener(v -> showPauseDialog());

            Log.d(TAG, "UI initialized successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing UI", e);
            showErrorAndFinish("UI setup error");
            return false;
        }
    }

    private void setupEnhancedControlButtons() {
        try {
            // Enhanced left button with visual feedback
            btnLeft.setOnTouchListener((v, event) -> {
                try {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            movingLeft = true;
                            v.setAlpha(0.7f); // Visual feedback
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            movingLeft = false;
                            v.setAlpha(1.0f); // Reset visual feedback
                            return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in left button touch", e);
                }
                return false;
            });

            // Enhanced right button with visual feedback
            btnRight.setOnTouchListener((v, event) -> {
                try {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            movingRight = true;
                            v.setAlpha(0.7f); // Visual feedback
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            movingRight = false;
                            v.setAlpha(1.0f); // Reset visual feedback
                            return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in right button touch", e);
                }
                return false;
            });

            // Enhanced jump button with improved jumping mechanics
            btnJump.setOnTouchListener((v, event) -> {
                try {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            if (!jumping && myPlayerY >= 0.75f) { // Better ground detection
                                jumping = true;
                                jumpVelocity = -0.025f; // Improved jump velocity
                                v.setAlpha(0.7f); // Visual feedback
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.setAlpha(1.0f); // Reset visual feedback
                            return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in jump button touch", e);
                }
                return false;
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up control buttons", e);
        }
    }

    private void initializeSpriteManagerAsync() {
        // Show loading indicator
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText("Loading game sprites...");
            }
        });

        // Initialize sprite manager in background thread
        new Thread(() -> {
            try {
                Log.d(TAG, "Initializing sprite manager...");
                spriteManager = new SpriteManager(this);

                runOnUiThread(() -> {
                    if (gameView != null && !isFinishing()) {
                        gameView.setSpriteManager(spriteManager);

                        // Set chapter data with selected sprites
                        String playerSpriteBase = "character_" + playerCharacterColor + "_idle";
                        gameView.setChapterData(selectedBackground, selectedObjective, playerSpriteBase);

                        spriteSystemReady = true;

                        if (spriteManager.areAssetsLoaded()) {
                            statusText.setText("Sprites loaded! Waiting for partner...");
                            Log.d(TAG, "Sprite system ready with custom sprites");

                            // Print loading report for debugging
                            spriteManager.printLoadingReport();
                        } else {
                            statusText.setText("Using fallback graphics. Waiting for partner...");
                            Log.w(TAG, "Sprite system ready with fallback rendering");
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error initializing sprite manager", e);
                runOnUiThread(() -> {
                    if (gameView != null && statusText != null) {
                        // Set fallback chapter data (empty strings trigger fallback rendering)
                        gameView.setChapterData("", "", "");
                        statusText.setText("Using fallback graphics. Waiting for partner...");
                        spriteSystemReady = true;
                        Log.d(TAG, "Using fallback rendering due to sprite loading error");
                    }
                });
            }
        }).start();
    }

    private void setupGameRoomListener() {
        if (isFinishing()) {
            Log.w(TAG, "Skipping listener setup - activity finishing");
            return;
        }

        try {
            DatabaseReference gameRoomRef = realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId);
            listenersActive = true;

            gameRoomListener = gameRoomRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (!listenersActive || isFinishing()) {
                        return;
                    }

                    try {
                        if (!dataSnapshot.exists()) {
                            Log.w(TAG, "Game room no longer exists");
                            runOnUiThread(() -> {
                                if (!isFinishing()) {
                                    showGameEndDialog("Game Ended", "Game room was closed.");
                                }
                            });
                            return;
                        }

                        processGameRoomData(dataSnapshot);
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing game room data", e);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Game room listener cancelled", databaseError.toException());
                    if (!isFinishing()) {
                        runOnUiThread(() -> {
                            Toast.makeText(Chapter1GameActivity.this,
                                    "Connection error: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
            Log.d(TAG, "Game room listener setup complete");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up game room listener", e);
        }
    }

    private void processGameRoomData(DataSnapshot dataSnapshot) {
        try {
            // Enhanced game start detection
            Boolean gameStarted = dataSnapshot.child("gameStarted").getValue(Boolean.class);
            Boolean chapterStarted = dataSnapshot.child("chapterStarted").getValue(Boolean.class);
            String gamePhase = dataSnapshot.child("gamePhase").getValue(String.class);

            Log.d(TAG, "Processing game data - gameStarted: " + gameStarted +
                    ", chapterStarted: " + chapterStarted + ", gamePhase: " + gamePhase);

            boolean shouldStartGame = Boolean.TRUE.equals(gameStarted) ||
                    Boolean.TRUE.equals(chapterStarted) ||
                    "chapter_ready".equals(gamePhase) ||
                    "game_active".equals(gamePhase) ||
                    "chapter1_active".equals(gamePhase);

            if (shouldStartGame && !gameActive && !gameEnded && spriteSystemReady) {
                Log.d(TAG, "Starting chapter game based on Firebase conditions");
                gameActive = true;
                gameStartTime = System.currentTimeMillis();
                runOnUiThread(() -> startChapterGame());
            }

            // Handle game end
            Boolean gameEnded = dataSnapshot.child("gameEnded").getValue(Boolean.class);
            if (Boolean.TRUE.equals(gameEnded)) {
                handleGameEnd(dataSnapshot);
                return;
            }

            // Update partner position safely
            updatePartnerPosition(dataSnapshot);

            // Update scores safely
            updateScores(dataSnapshot);

        } catch (Exception e) {
            Log.e(TAG, "Error in processGameRoomData", e);
        }
    }

    private void updatePartnerPosition(DataSnapshot dataSnapshot) {
        try {
            DataSnapshot partnerPos = dataSnapshot.child("playerPositions").child(partnerId);
            if (partnerPos.exists()) {
                Float partnerX = partnerPos.child("x").getValue(Float.class);
                Float partnerY = partnerPos.child("y").getValue(Float.class);
                if (partnerX != null && partnerY != null) {
                    partnerPlayerX = partnerX;
                    partnerPlayerY = partnerY;

                    runOnUiThread(() -> {
                        if (gameView != null && !isFinishing()) {
                            gameView.updatePartnerPosition(partnerPlayerX, partnerPlayerY);
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating partner position", e);
        }
    }

    private void updateScores(DataSnapshot dataSnapshot) {
        try {
            Long myScore = dataSnapshot.child("scores").child(uid).getValue(Long.class);
            Long pScore = dataSnapshot.child("scores").child(partnerId).getValue(Long.class);

            if (myScore != null) playerScore = myScore.intValue();
            if (pScore != null) partnerScore = pScore.intValue();

            runOnUiThread(() -> {
                if (!isFinishing()) {
                    updateScoreDisplay();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating scores", e);
        }
    }

    private void markPlayerReady() {
        if (isFinishing()) {
            return;
        }

        try {
            Log.d(TAG, "Marking player as ready");
            Map<String, Object> readyData = new HashMap<>();
            readyData.put("chapterPlayers/" + uid + "/ready", true);
            readyData.put("chapterPlayers/" + uid + "/name", currentUserName);
            readyData.put("chapterPlayers/" + uid + "/characterColor", playerCharacterColor);
            readyData.put("chapterPlayers/" + uid + "/readyTime", ServerValue.TIMESTAMP);
            readyData.put("playerPositions/" + uid + "/x", myPlayerX);
            readyData.put("playerPositions/" + uid + "/y", myPlayerY);
            readyData.put("playerPositions/" + uid + "/lastUpdate", ServerValue.TIMESTAMP);
            readyData.put("scores/" + uid, 0);
            readyData.put("gamePhase", "chapter1_active");
            readyData.put("chapterStarted", true);

            // Store selected sprites for this game
            readyData.put("gameSettings/background", selectedBackground);
            readyData.put("gameSettings/objective", selectedObjective);
            readyData.put("gameSettings/playerColors/" + uid, playerCharacterColor);

            realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId)
                    .updateChildren(readyData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Player marked as ready for chapter");
                        // Give partner time to be ready, then start if we're the initiator
                        if (isInitiator && !gameActive && spriteSystemReady) {
                            gameUpdateHandler.postDelayed(() -> {
                                if (!isFinishing() && !gameActive && spriteSystemReady) {
                                    forceStartGame();
                                }
                            }, 3000);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error marking player ready", e);
                        // Fallback start mechanism
                        gameUpdateHandler.postDelayed(() -> {
                            if (!isFinishing() && !gameActive && spriteSystemReady) {
                                forceStartGame();
                            }
                        }, 4000);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in markPlayerReady", e);
        }
    }

    private void forceStartGame() {
        if (gameActive || gameEnded || isFinishing() || !spriteSystemReady) {
            return;
        }

        Log.d(TAG, "Force starting chapter game");
        gameActive = true;
        gameStartTime = System.currentTimeMillis();
        runOnUiThread(() -> startChapterGame());
    }

    private void startChapterGame() {
        if (isFinishing()) {
            return;
        }

        try {
            Log.d(TAG, "Starting chapter game UI");

            if (statusText != null) {
                if (spriteManager != null && spriteManager.areAssetsLoaded()) {
                    statusText.setText("Game Started! Race to the " + selectedObjective.replace("_", " ") + "!");
                } else {
                    statusText.setText("Game Started! Race to the goal flag!");
                }
            }

            if (gameView != null) {
                gameView.setGameActive(true);
                gameView.updatePlayerPositions(myPlayerX, myPlayerY, partnerPlayerX, partnerPlayerY);
            }

            String toastMessage = spriteManager != null && spriteManager.areAssetsLoaded() ?
                    "Chapter 1 Started with custom sprites!" : "Chapter 1 Started!";
            Toast.makeText(this, toastMessage + " Race to the goal!", Toast.LENGTH_LONG).show();

            // Start game loop with delay to ensure everything is ready
            gameUpdateHandler.postDelayed(() -> {
                if (!isFinishing() && gameActive) {
                    startGameLoop();
                }
            }, 1000);

            Log.d(TAG, "Chapter game started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting chapter game", e);
        }
    }

    private void startGameLoop() {
        if (!gameActive || gameEnded || isFinishing()) {
            return;
        }

        gameUpdateHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gameActive && !gameEnded && !isFinishing()) {
                        updatePlayerMovement();
                        checkObjectiveReached();
                        updateUI();
                        // Optimized update interval for better performance
                        gameUpdateHandler.postDelayed(this, 33); // ~30 FPS for smooth animation
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in game loop", e);
                    // Continue game loop despite errors
                    if (gameActive && !gameEnded && !isFinishing()) {
                        gameUpdateHandler.postDelayed(this, 50);
                    }
                }
            }
        });
    }

    private void updatePlayerMovement() {
        try {
            boolean positionChanged = false;

            // Enhanced horizontal movement with boundaries
            if (movingLeft && myPlayerX > 0.02f) {
                myPlayerX = Math.max(0.02f, myPlayerX - playerSpeed);
                positionChanged = true;
            }
            if (movingRight && myPlayerX < 0.93f) {
                myPlayerX = Math.min(0.93f, myPlayerX + playerSpeed);
                positionChanged = true;
            }

            // Enhanced jumping and gravity with improved physics
            if (jumping) {
                myPlayerY += jumpVelocity;
                jumpVelocity += gravity;

                // Improved ground collision detection
                if (myPlayerY >= 0.8f) {
                    myPlayerY = 0.8f;
                    jumping = false;
                    jumpVelocity = 0f;
                }

                // Prevent jumping too high
                if (myPlayerY <= maxJumpHeight) {
                    jumpVelocity = Math.max(jumpVelocity, 0);
                }

                positionChanged = true;
            }

            // Optimized position updates to Firebase (reduced frequency for better performance)
            if (positionChanged) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastPositionUpdate > POSITION_UPDATE_INTERVAL) {
                    updatePositionInFirebase();
                    lastPositionUpdate = currentTime;
                }

                if (gameView != null) {
                    gameView.updatePlayerPositions(myPlayerX, myPlayerY, partnerPlayerX, partnerPlayerY);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating player movement", e);
        }
    }

    private void updatePositionInFirebase() {
        if (isFinishing()) {
            return;
        }

        try {
            Map<String, Object> positionData = new HashMap<>();
            positionData.put("playerPositions/" + uid + "/x", myPlayerX);
            positionData.put("playerPositions/" + uid + "/y", myPlayerY);
            positionData.put("playerPositions/" + uid + "/lastUpdate", ServerValue.TIMESTAMP);

            realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId)
                    .updateChildren(positionData)
                    .addOnFailureListener(e -> Log.e(TAG, "Error updating position", e));
        } catch (Exception e) {
            Log.e(TAG, "Error in updatePositionInFirebase", e);
        }
    }

    private void checkObjectiveReached() {
        try {
            // Enhanced objective collision detection with better hitbox
            float doorDistance = Math.abs(myPlayerX - objectiveDoorX) + Math.abs(myPlayerY - objectiveDoorY);
            if (doorDistance < 0.1f) { // Generous hitbox for user-friendly gameplay
                reachObjective();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking objective", e);
        }
    }

    private void reachObjective() {
        if (gameEnded || isFinishing()) {
            return;
        }

        try {
            Log.d(TAG, "Player reached objective!");
            gameActive = false;
            gameEnded = true;

            // Calculate completion bonus based on time
            long completionTime = System.currentTimeMillis() - gameStartTime;
            int timeBonus = Math.max(0, 200 - (int)(completionTime / 1000)); // Bonus decreases over time
            int totalScore = playerScore + 100 + timeBonus;

            Map<String, Object> endData = new HashMap<>();
            endData.put("gameEnded", true);
            endData.put("winner", uid);
            endData.put("winnerName", currentUserName);
            endData.put("gameEndTime", ServerValue.TIMESTAMP);
            endData.put("scores/" + uid, totalScore);
            endData.put("gameEndReason", "objective_reached");
            endData.put("completionTime", completionTime);
            endData.put("timeBonus", timeBonus);

            realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId)
                    .updateChildren(endData)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Objective reached, game ended"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error ending game", e));
        } catch (Exception e) {
            Log.e(TAG, "Error in reachObjective", e);
        }
    }

    private void handleGameEnd(DataSnapshot dataSnapshot) {
        if (!gameActive && gameEnded) {
            return;
        }

        try {
            Log.d(TAG, "Handling game end");
            gameActive = false;
            gameEnded = true;

            String winnerId = dataSnapshot.child("winner").getValue(String.class);
            String winnerName = dataSnapshot.child("winnerName").getValue(String.class);
            String endReason = dataSnapshot.child("gameEndReason").getValue(String.class);
            Long completionTime = dataSnapshot.child("completionTime").getValue(Long.class);
            Long timeBonus = dataSnapshot.child("timeBonus").getValue(Long.class);

            runOnUiThread(() -> {
                if (!isFinishing()) {
                    String message;
                    boolean playerWon = false;

                    if (uid.equals(winnerId)) {
                        playerWon = true;
                        message = "🎉 Congratulations! You won Chapter 1!\n\n";
                        message += "You reached the " + selectedObjective.replace("_", " ") + " first!\n";
                        if (completionTime != null) {
                            message += "Completion time: " + (completionTime / 1000) + " seconds\n";
                        }
                        if (timeBonus != null && timeBonus > 0) {
                            message += "Time bonus: +" + timeBonus + " points!";
                        }
                    } else {
                        String reason = "player_left".equals(endReason) ? " left the game" : " reached the goal first!";
                        message = (winnerName != null ? winnerName : "Partner");
                        if ("player_left".equals(endReason)) {
                            playerWon = true; // Win by default
                            message += " left the game.\n🎉 You win by default!";
                        } else {
                            message += " won Chapter 1!\n";
                            message += "They reached the " + selectedObjective.replace("_", " ") + " first!";
                            if (completionTime != null) {
                                message += "\nTheir time: " + (completionTime / 1000) + " seconds";
                            }
                        }
                    }

                    updateUserStats(playerWon);
                    showGameEndDialog("Chapter 1 Complete", message);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error handling game end", e);
        }
    }

    private void updateUserStats(boolean won) {
        if (isFinishing()) {
            return;
        }

        try {
            DocumentReference userDocRef = firestore.collection("users").document(uid);
            firestore.runTransaction(transaction -> {
                        transaction.update(userDocRef, "totalGamesPlayed", FieldValue.increment(1));
                        if (won) {
                            transaction.update(userDocRef, "wins", FieldValue.increment(1));
                            transaction.update(userDocRef, "xp", FieldValue.increment(75)); // Increased XP for sprite gameplay
                            transaction.update(userDocRef, "gameTokens", FieldValue.increment(25));
                            transaction.update(userDocRef, "chapter1Wins", FieldValue.increment(1));
                        } else {
                            transaction.update(userDocRef, "xp", FieldValue.increment(25));
                            transaction.update(userDocRef, "gameTokens", FieldValue.increment(10));
                        }
                        return null;
                    }).addOnSuccessListener(aVoid -> Log.d(TAG, "User stats updated"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update user stats", e));
        } catch (Exception e) {
            Log.e(TAG, "Error updating user stats", e);
        }
    }

    private void updateUI() {
        if (isFinishing()) {
            return;
        }

        try {
            if (gameActive && gameStartTime > 0 && timerText != null) {
                long elapsedTime = System.currentTimeMillis() - gameStartTime;
                long seconds = elapsedTime / 1000;
                timerText.setText(String.format("Time: %02d:%02d", seconds / 60, seconds % 60));
            }
            updateScoreDisplay();
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI", e);
        }
    }

    private void updateScoreDisplay() {
        try {
            if (scoreText != null) {
                scoreText.setText(String.format("Scores - You: %d | %s: %d", playerScore, partnerName, partnerScore));
            }

            // Enhanced player status with progress and character info
            float myProgress = myPlayerX * 100;
            float partnerProgress = partnerPlayerX * 100;

            if (player1StatusText != null) {
                player1StatusText.setText(String.format("%s (%s): %.0f%% to goal",
                        currentUserName, playerCharacterColor, myProgress));
            }
            if (player2StatusText != null) {
                player2StatusText.setText(String.format("%s (%s): %.0f%% to goal",
                        partnerName, partnerCharacterColor, partnerProgress));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating score display", e);
        }
    }

    private void showErrorAndFinish(String message) {
        runOnUiThread(() -> {
            if (!isFinishing()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                uiHandler.postDelayed(() -> {
                    if (!isFinishing()) {
                        returnToChatActivity();
                    }
                }, 2000);
            }
        });
    }

    private void showPauseDialog() {
        if (isFinishing()) {
            return;
        }

        try {
            new AlertDialog.Builder(this)
                    .setTitle("Game Paused")
                    .setMessage("Game is paused. What would you like to do?")
                    .setPositiveButton("Resume", (dialog, which) -> {
                        // Resume game - nothing to do, game loop continues
                    })
                    .setNeutralButton("Sprite Info", (dialog, which) -> {
                        showSpriteInfo();
                    })
                    .setNegativeButton("Leave Game", (dialog, which) -> {
                        leaveGame();
                    })
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing pause dialog", e);
        }
    }

    private void showSpriteInfo() {
        if (spriteManager != null) {
            String info = "Sprite System Status\n\n";
            info += "Sprites Loaded: " + (spriteManager.areAssetsLoaded() ? "✓" : "✗") + "\n";
            info += "Total Sprites: " + spriteManager.getTotalSpritesLoaded() + "\n";
            info += "Loading Time: " + spriteManager.getLoadingTime() + "ms\n\n";
            info += "Current Game:\n";
            info += "Background: " + selectedBackground + "\n";
            info += "Objective: " + selectedObjective + "\n";
            info += "Your Character: " + playerCharacterColor + "\n";
            info += "Partner Character: " + partnerCharacterColor;

            new AlertDialog.Builder(this)
                    .setTitle("Sprite Information")
                    .setMessage(info)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void showGameEndDialog(String title, String message) {
        if (isFinishing()) {
            return;
        }

        try {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Return to Chat", (dialog, which) -> {
                        returnToChatActivity();
                    })
                    .setNegativeButton("Play Again", (dialog, which) -> {
                        // Optional: Implement play again functionality
                        returnToChatActivity();
                    })
                    .setCancelable(false)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing game end dialog", e);
            returnToChatActivity(); // Fallback to return to chat
        }
    }

    private void leaveGame() {
        Log.d(TAG, "Player leaving game");

        try {
            if (gameActive && !isFinishing()) {
                Map<String, Object> leaveData = new HashMap<>();
                leaveData.put("gameEnded", true);
                leaveData.put("winner", partnerId);
                leaveData.put("winnerName", partnerName);
                leaveData.put("gameEndReason", "player_left");
                leaveData.put("gameEndTime", ServerValue.TIMESTAMP);

                realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).updateChildren(leaveData);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in leaveGame", e);
        }

        returnToChatActivity();
    }

    private void returnToChatActivity() {
        Log.d(TAG, "Returning to ChatActivity");

        try {
            // Clean up first
            finishGame();

            // Create intent to return to ChatActivity with partner info
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("partnerId", partnerId);
            intent.putExtra("partnerName", partnerName);
            intent.putExtra("gameCompleted", true);
            intent.putExtra("chapterPlayed", selectedChapter);

            // Clear the activity stack and start ChatActivity fresh
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error returning to ChatActivity", e);
            // Fallback - just finish this activity
            finish();
        }
    }

    private void finishGame() {
        if (isFinishing()) {
            return;
        }

        Log.d(TAG, "Finishing game");

        try {
            gameActive = false;
            gameEnded = true;
            listenersActive = false;

            // Clean up handlers
            if (gameUpdateHandler != null) {
                gameUpdateHandler.removeCallbacksAndMessages(null);
            }
            if (uiHandler != null) {
                uiHandler.removeCallbacksAndMessages(null);
            }

            // Remove Firebase listeners
            if (gameRoomListener != null && gameRoomId != null && realtimeDb != null) {
                try {
                    realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).removeEventListener(gameRoomListener);
                    Log.d(TAG, "Firebase listeners removed");
                } catch (Exception e) {
                    Log.e(TAG, "Error removing Firebase listeners", e);
                }
            }

            // Clean up sprite manager
            if (spriteManager != null) {
                try {
                    spriteManager.cleanup();
                    Log.d(TAG, "Sprite manager cleaned up");
                } catch (Exception e) {
                    Log.e(TAG, "Error cleaning up sprite manager", e);
                }
            }

            Log.d(TAG, "Game finish cleanup completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in finishGame", e);
        }
    }
}