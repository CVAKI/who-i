package com.humangodcvaki.whoi;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class StartLoadingActivity extends AppCompatActivity {

    private static final String TAG = "StartLoadingActivity";
    private static final String GAME_ROOMS_NODE = "gameRooms";

    // UI Elements
    private TextView titleText, statusText, loadingText;
    private TextView player1Name, player2Name;
    private TextView player1Status, player2Status;
    private CardView player1Card, player2Card;
    private ImageView player1Avatar, player2Avatar;
    private ProgressBar loadingProgress;
    private ImageView loadingIcon;
    private Button btnCancel;

    // Firebase
    private DatabaseReference realtimeDb;
    private String uid;
    private String currentUserName;

    // Game Data
    private String gameRoomId;
    private String partnerId;
    private String partnerName;
    private boolean isInitiator;
    private String chatRoomId;
    private int selectedChapter = 1;

    // Loading States
    private boolean player1Ready = false;
    private boolean player2Ready = false;
    private boolean assetsLoaded = false;
    private boolean gameDataInitialized = false;
    private boolean isGameStarting = false;
    private boolean isActivityDestroyed = false;
    private boolean chapterSelected = false;

    private ValueEventListener gameRoomListener;
    private Handler loadingHandler;
    private int currentProgress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "StartLoadingActivity onCreate started");

        try {
            setContentView(R.layout.activity_start_loading);
        } catch (Exception e) {
            Log.e(TAG, "Error setting content view", e);
            showErrorAndFinish("Layout error. Please restart the app.");
            return;
        }

        // Initialize Firebase
        if (!initializeFirebase()) {
            return;
        }

        // Get and validate data from Intent
        if (!validateIntentData()) {
            return;
        }

        // Initialize UI
        if (!initializeUI()) {
            return;
        }

        // Setup Firebase listener and start loading
        setupGameRoomListener();
        updateConnectionStatus();
        startLoadingSequence();

        // Auto-trigger after 10 seconds if nothing happens
        loadingHandler.postDelayed(() -> {
            if (!isActivityDestroyed && !isGameStarting) {
                Log.w(TAG, "Game hasn't started after 10 seconds, force starting...");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Starting game...", Toast.LENGTH_SHORT).show();
                    forceStartGame();
                });
            }
        }, 10000);

        Log.d(TAG, "StartLoadingActivity onCreate completed successfully");
    }

    private boolean initializeFirebase() {
        try {
            realtimeDb = FirebaseDatabase.getInstance().getReference();
            loadingHandler = new Handler(Looper.getMainLooper());

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Log.e(TAG, "User not authenticated!");
                showErrorAndFinish("Authentication error. Please login again.");
                return false;
            }

            uid = currentUser.getUid();
            currentUserName = currentUser.getDisplayName();

            if (currentUserName == null || currentUserName.trim().isEmpty()) {
                currentUserName = "You";
            }

            Log.d(TAG, "Firebase initialized for user: " + uid + " (" + currentUserName + ")");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization error", e);
            showErrorAndFinish("Firebase connection error. Please check your internet.");
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
            chatRoomId = intent.getStringExtra("chatRoomId");

            if (gameRoomId == null || gameRoomId.trim().isEmpty()) {
                Log.e(TAG, "Invalid or missing gameRoomId");
                showErrorAndFinish("Game room ID is missing");
                return false;
            }

            if (partnerId == null || partnerId.trim().isEmpty()) {
                Log.e(TAG, "Invalid or missing partnerId");
                showErrorAndFinish("Partner information is missing");
                return false;
            }

            if (partnerName == null || partnerName.trim().isEmpty()) {
                partnerName = "Partner";
            }

            Log.d(TAG, "Intent data validated - GameRoom: " + gameRoomId +
                    ", Partner: " + partnerId + ", Initiator: " + isInitiator);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error validating intent data", e);
            showErrorAndFinish("Invalid game data");
            return false;
        }
    }

    private boolean initializeUI() {
        try {
            titleText = findViewById(R.id.titleText);
            statusText = findViewById(R.id.statusText);
            loadingText = findViewById(R.id.loadingText);

            player1Name = findViewById(R.id.player1Name);
            player2Name = findViewById(R.id.player2Name);
            player1Status = findViewById(R.id.player1Status);
            player2Status = findViewById(R.id.player2Status);

            player1Card = findViewById(R.id.player1Card);
            player2Card = findViewById(R.id.player2Card);
            player1Avatar = findViewById(R.id.player1Avatar);
            player2Avatar = findViewById(R.id.player2Avatar);

            loadingProgress = findViewById(R.id.loadingProgress);
            loadingIcon = findViewById(R.id.loadingIcon);
            btnCancel = findViewById(R.id.btnCancel);

            // Check if any critical views are null
            if (statusText == null || loadingText == null || player1Name == null ||
                    player2Name == null || loadingProgress == null || btnCancel == null) {
                Log.e(TAG, "One or more critical UI elements are null");
                showErrorAndFinish("UI initialization error");
                return false;
            }

            // Set player names
            player1Name.setText(currentUserName);
            player2Name.setText(partnerName);

            // Initial status
            statusText.setText("Preparing game environment...");
            loadingText.setText("Initializing...");

            if (player1Status != null) player1Status.setText("Connecting...");
            if (player2Status != null) player2Status.setText("Connecting...");

            btnCancel.setOnClickListener(v -> {
                Log.d(TAG, "Cancel button clicked");
                cleanupAndFinish();
            });

            // Start loading animation
            startLoadingAnimation();

            Log.d(TAG, "UI initialized successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error initializing UI", e);
            showErrorAndFinish("UI setup error");
            return false;
        }
    }

    private void showErrorAndFinish(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error: " + message);

            // Small delay before finishing to ensure toast is shown
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isActivityDestroyed) {
                    finish();
                }
            }, 2000);
        });
    }

    private void startLoadingAnimation() {
        if (loadingIcon != null && !isActivityDestroyed) {
            try {
                RotateAnimation rotate = new RotateAnimation(0, 360,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f);
                rotate.setDuration(1000);
                rotate.setRepeatCount(Animation.INFINITE);
                loadingIcon.startAnimation(rotate);
            } catch (Exception e) {
                Log.e(TAG, "Error starting loading animation", e);
            }
        }
    }

    private void setupGameRoomListener() {
        if (gameRoomId == null || realtimeDb == null) {
            Log.e(TAG, "Cannot setup game room listener - missing required data");
            showErrorAndFinish("Setup error occurred");
            return;
        }

        Log.d(TAG, "Setting up game room listener for: " + gameRoomId);

        try {
            DatabaseReference gameRoomRef = realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId);

            gameRoomListener = gameRoomRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (isActivityDestroyed || isGameStarting) {
                        Log.d(TAG, "Ignoring data change - activity destroyed: " + isActivityDestroyed + ", game starting: " + isGameStarting);
                        return;
                    }

                    try {
                        if (!dataSnapshot.exists()) {
                            Log.w(TAG, "Game room no longer exists: " + gameRoomId);
                            runOnUiThread(() -> {
                                Toast.makeText(StartLoadingActivity.this,
                                        "Game room ended by partner", Toast.LENGTH_SHORT).show();
                                cleanupAndFinish();
                            });
                            return;
                        }

                        // Debug: Log the entire game room state
                        Log.d(TAG, "Game room data changed: " + dataSnapshot.getValue());

                        // Check player connection status
                        DataSnapshot loadingStatus = dataSnapshot.child("loadingStatus");
                        Log.d(TAG, "Loading status: " + loadingStatus.getValue());

                        Boolean myConnection = loadingStatus.child(uid).child("connected").getValue(Boolean.class);
                        Boolean partnerConnection = loadingStatus.child(partnerId).child("connected").getValue(Boolean.class);

                        Log.d(TAG, "My connection: " + myConnection + ", Partner connection: " + partnerConnection);

                        boolean newPlayer1Ready = myConnection != null && myConnection;
                        boolean newPlayer2Ready = partnerConnection != null && partnerConnection;

                        if (newPlayer1Ready != player1Ready || newPlayer2Ready != player2Ready) {
                            player1Ready = newPlayer1Ready;
                            player2Ready = newPlayer2Ready;
                            Log.d(TAG, "Player status updated - Player1: " + player1Ready + ", Player2: " + player2Ready);
                            runOnUiThread(() -> updatePlayerStatus());
                        }

                        // Check if we can start the chapter - FIXED CONDITIONS
                        Boolean gameReadyToStart = dataSnapshot.child("gameReadyToStart").getValue(Boolean.class);
                        Boolean gameStarted = dataSnapshot.child("gameStarted").getValue(Boolean.class);
                        Boolean chapterStarted = dataSnapshot.child("chapterStarted").getValue(Boolean.class);
                        String gamePhase = dataSnapshot.child("gamePhase").getValue(String.class);

                        Log.d(TAG, "Game ready to start: " + gameReadyToStart +
                                ", Game started: " + gameStarted +
                                ", Chapter started: " + chapterStarted +
                                ", Game phase: " + gamePhase +
                                ", Assets loaded: " + assetsLoaded +
                                ", Data initialized: " + gameDataInitialized);

                        // Check if ready to start - SIMPLIFIED CONDITIONS
                        boolean canStartGame = false;

                        // Method 1: Check for explicit ready flags
                        if (gameReadyToStart != null && gameReadyToStart) {
                            canStartGame = true;
                        }

                        // Method 2: Check if game started
                        if (gameStarted != null && gameStarted) {
                            canStartGame = true;
                        }

                        // Method 3: Check if chapter started
                        if (chapterStarted != null && chapterStarted) {
                            canStartGame = true;
                        }

                        // Method 4: Check specific game phases
                        if ("loading_complete".equals(gamePhase) || "chapter_ready".equals(gamePhase) || "game_active".equals(gamePhase)) {
                            canStartGame = true;
                        }

                        // Method 5: If both players connected and assets loaded, prepare to start
                        if (player1Ready && player2Ready && assetsLoaded && gameDataInitialized && !canStartGame) {
                            Log.d(TAG, "All conditions met, triggering game start sequence");
                            if (isInitiator && !chapterSelected) {
                                selectRandomChapter();
                            } else if (!isInitiator) {
                                // Non-initiator waits for initiator to set things up
                                canStartGame = true;
                            }
                        }

                        if (canStartGame && !isGameStarting) {
                            // Get selected chapter
                            Integer chapter = dataSnapshot.child("selectedChapter").getValue(Integer.class);
                            if (chapter != null) {
                                selectedChapter = chapter;
                            } else {
                                selectedChapter = 1; // Default to chapter 1
                            }

                            Log.d(TAG, "Starting chapter activity with chapter: " + selectedChapter);
                            isGameStarting = true;
                            runOnUiThread(() -> startChapterActivity());
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error processing game room data", e);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Game room listener cancelled", databaseError.toException());
                    if (!isActivityDestroyed) {
                        runOnUiThread(() -> {
                            Toast.makeText(StartLoadingActivity.this,
                                    "Connection error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
                            cleanupAndFinish();
                        });
                    }
                }
            });

            Log.d(TAG, "Game room listener setup completed");

        } catch (Exception e) {
            Log.e(TAG, "Error setting up game room listener", e);
            showErrorAndFinish("Failed to connect to game room");
        }
    }

    private void updateConnectionStatus() {
        if (gameRoomId == null || uid == null || realtimeDb == null) {
            Log.w(TAG, "Cannot update connection status - missing data");
            return;
        }

        try {
            Map<String, Object> connectionData = new HashMap<>();
            connectionData.put("loadingStatus/" + uid + "/connected", true);
            connectionData.put("loadingStatus/" + uid + "/name", currentUserName);
            connectionData.put("loadingStatus/" + uid + "/timestamp", ServerValue.TIMESTAMP);

            realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId)
                    .updateChildren(connectionData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Connection status updated successfully");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating connection status", e);
                        if (!isActivityDestroyed) {
                            runOnUiThread(() -> {
                                Toast.makeText(StartLoadingActivity.this,
                                        "Connection error", Toast.LENGTH_SHORT).show();
                            });
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error in updateConnectionStatus", e);
        }
    }

    private void updatePlayerStatus() {
        try {
            if (player1Status != null && player1Card != null) {
                if (player1Ready) {
                    player1Status.setText("Ready");
                    player1Card.setCardBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
                } else {
                    player1Status.setText("Connecting...");
                    player1Card.setCardBackgroundColor(getResources().getColor(android.R.color.darker_gray));
                }
            }

            if (player2Status != null && player2Card != null) {
                if (player2Ready) {
                    player2Status.setText("Ready");
                    player2Card.setCardBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
                } else {
                    player2Status.setText("Connecting...");
                    player2Card.setCardBackgroundColor(getResources().getColor(android.R.color.darker_gray));
                }
            }

            if (player1Ready && player2Ready) {
                statusText.setText("Both players connected! Loading game assets...");
                if (!assetsLoaded) {
                    accelerateAssetLoading();
                }
            } else {
                statusText.setText("Waiting for players to connect...");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating player status", e);
        }
    }

    private void startLoadingSequence() {
        if (isActivityDestroyed) return;

        // Simulate loading steps with faster timings
        loadingHandler.postDelayed(() -> {
            if (!isActivityDestroyed) {
                updateLoadingStep("Loading sprites...", 20);
            }
        }, 300);

        loadingHandler.postDelayed(() -> {
            if (!isActivityDestroyed) {
                updateLoadingStep("Initializing game world...", 40);
            }
        }, 800);

        loadingHandler.postDelayed(() -> {
            if (!isActivityDestroyed) {
                updateLoadingStep("Setting up multiplayer...", 60);
            }
        }, 1500);

        loadingHandler.postDelayed(() -> {
            if (!isActivityDestroyed) {
                updateLoadingStep("Preparing chapter data...", 80);
                gameDataInitialized = true;
            }
        }, 2200);

        loadingHandler.postDelayed(() -> {
            if (!isActivityDestroyed) {
                updateLoadingStep("Almost ready...", 100);
                assetsLoaded = true;
                checkReadyToStart();
            }
        }, 3000);
    }

    private void accelerateAssetLoading() {
        // Speed up remaining loading when both players are connected
        if (currentProgress < 100) {
            loadingHandler.removeCallbacksAndMessages(null);

            loadingHandler.postDelayed(() -> {
                if (!isActivityDestroyed) {
                    updateLoadingStep("Finalizing setup...", 100);
                    assetsLoaded = true;
                    gameDataInitialized = true;
                    checkReadyToStart();
                }
            }, 800);
        }
    }

    private void updateLoadingStep(String text, int progress) {
        runOnUiThread(() -> {
            if (!isActivityDestroyed) {
                loadingText.setText(text);
                currentProgress = progress;
                loadingProgress.setProgress(progress);
            }
        });
    }

    private void selectRandomChapter() {
        if (chapterSelected) {
            Log.d(TAG, "Chapter already selected, skipping");
            return;
        }

        try {
            chapterSelected = true;
            // Select random chapter (1-5 for example)
            Random random = new Random();
            selectedChapter = random.nextInt(1) + 1; // For now, only chapter 1

            Map<String, Object> chapterData = new HashMap<>();
            chapterData.put("selectedChapter", selectedChapter);
            chapterData.put("chapterSelectedBy", uid);
            chapterData.put("chapterSelectedAt", ServerValue.TIMESTAMP);
            chapterData.put("gameReadyToStart", true);
            chapterData.put("gameStarted", true);
            chapterData.put("gamePhase", "chapter_ready");

            realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId)
                    .updateChildren(chapterData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Chapter selected and game marked ready: " + selectedChapter);
                        runOnUiThread(() -> {
                            if (!isActivityDestroyed) {
                                statusText.setText("Chapter " + selectedChapter + " selected! Starting game...");
                                loadingText.setText("Launching chapter...");
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error selecting chapter", e);
                        chapterSelected = false; // Reset flag to allow retry
                        if (!isActivityDestroyed) {
                            runOnUiThread(() -> {
                                Toast.makeText(StartLoadingActivity.this,
                                        "Error selecting chapter: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error in selectRandomChapter", e);
            chapterSelected = false; // Reset flag to allow retry
        }
    }

    private void checkReadyToStart() {
        Log.d(TAG, "Checking ready to start: player1Ready=" + player1Ready +
                ", player2Ready=" + player2Ready + ", assetsLoaded=" + assetsLoaded +
                ", gameDataInitialized=" + gameDataInitialized + ", isGameStarting=" + isGameStarting +
                ", isInitiator=" + isInitiator + ", chapterSelected=" + chapterSelected);

        if (player1Ready && player2Ready && assetsLoaded && gameDataInitialized && !isGameStarting) {
            if (isInitiator && !chapterSelected) {
                Log.d(TAG, "Initiator selecting chapter...");
                selectRandomChapter();
            } else if (!isInitiator) {
                Log.d(TAG, "Non-initiator waiting for game to be marked ready...");
                // Non-initiator waits for the Firebase listener to detect game ready
            } else if (chapterSelected) {
                Log.d(TAG, "Chapter already selected, should start soon...");
            }
        }
    }

    private void forceStartGame() {
        Log.d(TAG, "Force starting game - current state: player1Ready=" + player1Ready +
                ", player2Ready=" + player2Ready + ", assetsLoaded=" + assetsLoaded +
                ", gameDataInitialized=" + gameDataInitialized);

        if (!isGameStarting) {
            // Force set everything to ready
            player1Ready = true;
            player2Ready = true;
            assetsLoaded = true;
            gameDataInitialized = true;
            selectedChapter = 1; // Default chapter

            runOnUiThread(() -> {
                updatePlayerStatus();
                statusText.setText("Starting game...");
                isGameStarting = true;
                startChapterActivity();
            });
        }
    }

    private void startChapterActivity() {
        if (isActivityDestroyed) {
            Log.w(TAG, "Cannot start chapter activity - activity destroyed");
            return;
        }

        try {
            statusText.setText("Starting Chapter " + selectedChapter + "...");

            // Determine which chapter activity to start
            Class<?> chapterActivityClass = getChapterActivityClass(selectedChapter);

            Intent chapterIntent = new Intent(this, chapterActivityClass);
            chapterIntent.putExtra("gameRoomId", gameRoomId);
            chapterIntent.putExtra("partnerId", partnerId);
            chapterIntent.putExtra("partnerName", partnerName);
            chapterIntent.putExtra("isInitiator", isInitiator);
            chapterIntent.putExtra("selectedChapter", selectedChapter);

            if (chatRoomId != null) {
                chapterIntent.putExtra("chatRoomId", chatRoomId);
            }

            // Add flags to prevent issues
            chapterIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // Delay to show the final message
            loadingHandler.postDelayed(() -> {
                if (!isActivityDestroyed) {
                    Log.d(TAG, "Starting chapter activity: " + chapterActivityClass.getSimpleName());
                    startActivity(chapterIntent);
                    finish();
                }
            }, 1000);

        } catch (Exception e) {
            Log.e(TAG, "Error starting chapter activity", e);
            isGameStarting = false;
            runOnUiThread(() -> {
                Toast.makeText(this, "Error starting game: " + e.getMessage(), Toast.LENGTH_LONG).show();
                cleanupAndFinish();
            });
        }
    }

    private Class<?> getChapterActivityClass(int chapter) {
        // Map chapters to their respective activity classes
        switch (chapter) {
            case 1:
                return Chapter1GameActivity.class;
            case 2:
                // return Chapter2GameActivity.class; // Uncomment when available
                return Chapter1GameActivity.class; // Fallback for now
            case 3:
                // return Chapter3GameActivity.class; // Uncomment when available
                return Chapter1GameActivity.class; // Fallback for now
            case 4:
                // return Chapter4GameActivity.class; // Uncomment when available
                return Chapter1GameActivity.class; // Fallback for now
            case 5:
                // return Chapter5GameActivity.class; // Uncomment when available
                return Chapter1GameActivity.class; // Fallback for now
            default:
                Log.w(TAG, "Unknown chapter: " + chapter + ", defaulting to Chapter1");
                return Chapter1GameActivity.class;
        }
    }

    private void cleanupAndFinish() {
        if (isActivityDestroyed) {
            return;
        }

        Log.d(TAG, "Cleaning up and finishing activity");

        try {
            // Remove listeners
            if (gameRoomListener != null && gameRoomId != null && realtimeDb != null) {
                realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId)
                        .removeEventListener(gameRoomListener);
                gameRoomListener = null;
            }

            // Mark as disconnected
            if (uid != null && gameRoomId != null && realtimeDb != null) {
                Map<String, Object> disconnectData = new HashMap<>();
                disconnectData.put("loadingStatus/" + uid + "/connected", false);
                disconnectData.put("loadingStatus/" + uid + "/timestamp", ServerValue.TIMESTAMP);

                realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId)
                        .updateChildren(disconnectData)
                        .addOnCompleteListener(task -> {
                            Log.d(TAG, "Disconnect status updated");
                        });
            }

            // Stop handlers
            if (loadingHandler != null) {
                loadingHandler.removeCallbacksAndMessages(null);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        } finally {
            isActivityDestroyed = true;
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "StartLoadingActivity onDestroy");

        isActivityDestroyed = true;

        try {
            if (loadingHandler != null) {
                loadingHandler.removeCallbacksAndMessages(null);
                loadingHandler = null;
            }

            if (gameRoomListener != null && gameRoomId != null && realtimeDb != null) {
                realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId)
                        .removeEventListener(gameRoomListener);
                gameRoomListener = null;
            }

            // Clean up loading animations
            if (loadingIcon != null) {
                loadingIcon.clearAnimation();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "Back button pressed");
        cleanupAndFinish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "StartLoadingActivity onPause");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "StartLoadingActivity onResume");
    }
}