package com.humangodcvaki.whoi;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
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

public class OrientationCheckActivity extends AppCompatActivity {

    private static final String TAG = "OrientationCheck";
    private static final String GAME_ROOMS_NODE = "gameRooms";

    // UI Elements
    private TextView statusText, instructionText;
    private ImageView orientationIcon, partnerStatusIcon;
    private CardView myStatusCard, partnerStatusCard;
    private TextView myStatusText, partnerStatusText;
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

    // State
    private boolean isLandscape = false;
    private boolean partnerIsLandscape = false;
    private ValueEventListener gameRoomListener;
    private boolean isActivityDestroyed = false;
    private boolean hasStartedLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "OrientationCheckActivity onCreate started");

        try {
            setContentView(R.layout.activity_orientation_check);
        } catch (Exception e) {
            Log.e(TAG, "Error setting content view", e);
            Toast.makeText(this, "Layout error. Please restart the app.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firebase
        try {
            realtimeDb = FirebaseDatabase.getInstance().getReference();
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

            if (currentUser == null) {
                Log.e(TAG, "User not authenticated!");
                showErrorAndFinish("Authentication error. Please login again.");
                return;
            }

            uid = currentUser.getUid();
            currentUserName = currentUser.getDisplayName();

            if (currentUserName == null || currentUserName.trim().isEmpty()) {
                currentUserName = "You";
            }

            Log.d(TAG, "Firebase initialized for user: " + uid + " (" + currentUserName + ")");

        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization error", e);
            showErrorAndFinish("Firebase connection error. Please check your internet.");
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

        // Check current orientation and setup
        checkCurrentOrientation();

        // Setup Firebase listener with delay to ensure everything is ready
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isActivityDestroyed) {
                setupGameRoomListener();
            }
        }, 1000);

        Log.d(TAG, "OrientationCheckActivity onCreate completed successfully");
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
            statusText = findViewById(R.id.statusText);
            instructionText = findViewById(R.id.instructionText);
            orientationIcon = findViewById(R.id.orientationIcon);
            partnerStatusIcon = findViewById(R.id.partnerStatusIcon);

            myStatusCard = findViewById(R.id.myStatusCard);
            partnerStatusCard = findViewById(R.id.partnerStatusCard);
            myStatusText = findViewById(R.id.myStatusText);
            partnerStatusText = findViewById(R.id.partnerStatusText);

            btnCancel = findViewById(R.id.btnCancel);

            // Check if any views are null
            if (statusText == null || instructionText == null || orientationIcon == null ||
                    partnerStatusIcon == null || myStatusCard == null || partnerStatusCard == null ||
                    myStatusText == null || partnerStatusText == null || btnCancel == null) {

                Log.e(TAG, "One or more UI elements are null");
                showErrorAndFinish("UI initialization error");
                return false;
            }

            // Setup click listeners
            btnCancel.setOnClickListener(v -> {
                Log.d(TAG, "Cancel button clicked");
                cleanupAndFinish();
            });

            // Set initial texts
            instructionText.setText("Please rotate your phone to landscape mode to continue");
            myStatusText.setText(currentUserName);
            partnerStatusText.setText(partnerName);

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

    private void checkCurrentOrientation() {
        try {
            isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            Log.d(TAG, "Current orientation - Landscape: " + isLandscape);

            updateOrientationUI();
            updateOrientationInFirebase();

        } catch (Exception e) {
            Log.e(TAG, "Error checking orientation", e);
            // Default to portrait if there's an error
            isLandscape = false;
            updateOrientationUI();
        }
    }

    private void updateOrientationUI() {
        runOnUiThread(() -> {
            try {
                if (isLandscape) {
                    statusText.setText("Great! You're in landscape mode");
                    orientationIcon.setImageResource(R.drawable.whoi_sizor);
                    myStatusCard.setCardBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
                    instructionText.setText("Waiting for partner to rotate to landscape mode...");
                } else {
                    statusText.setText("Please rotate to landscape mode");
                    orientationIcon.setImageResource(R.drawable.whoi_ads);
                    myStatusCard.setCardBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
                    instructionText.setText("Rotate your phone to landscape mode to continue");
                }

                // Update partner status
                if (partnerIsLandscape) {
                    partnerStatusCard.setCardBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
                    partnerStatusIcon.setImageResource(R.drawable.whoi_gear);
                } else {
                    partnerStatusCard.setCardBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
                    partnerStatusIcon.setImageResource(R.drawable.whoi_profile);
                }

                // Check if both are ready - FIXED THE LOGIC ERROR
                if (isLandscape && partnerIsLandscape && !hasStartedLoading) {
                    statusText.setText("Both players ready! Starting game...");
                    instructionText.setText("Game will begin shortly...");

                    // Set the flag AFTER the delay to prevent multiple triggers
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!isActivityDestroyed && !hasStartedLoading) {
                            hasStartedLoading = true; // Set flag here to prevent multiple calls
                            startLoadingActivity();
                        }
                    }, 2500);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error updating orientation UI", e);
            }
        });
    }

    private void setupGameRoomListener() {
        if (gameRoomId == null || uid == null || realtimeDb == null) {
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
                    if (isActivityDestroyed) {
                        return;
                    }

                    try {
                        if (!dataSnapshot.exists()) {
                            Log.w(TAG, "Game room no longer exists: " + gameRoomId);
                            runOnUiThread(() -> {
                                Toast.makeText(OrientationCheckActivity.this,
                                        "Game room ended by partner", Toast.LENGTH_SHORT).show();
                                cleanupAndFinish();
                            });
                            return;
                        }

                        // Check partner's orientation status
                        DataSnapshot orientationStatus = dataSnapshot.child("orientationStatus");
                        DataSnapshot partnerOrientation = orientationStatus.child(partnerId);

                        Boolean partnerOrientationValue = partnerOrientation.child("isLandscape").getValue(Boolean.class);

                        if (partnerOrientationValue != null) {
                            boolean newPartnerOrientation = partnerOrientationValue;
                            if (newPartnerOrientation != partnerIsLandscape) {
                                Log.d(TAG, "Partner orientation changed: " + newPartnerOrientation);
                                partnerIsLandscape = newPartnerOrientation;
                                updateOrientationUI();
                            }
                        }

                        // Check if partner is connected
                        Boolean partnerConnected = partnerOrientation.child("connected").getValue(Boolean.class);
                        if (partnerConnected != null && !partnerConnected) {
                            Log.w(TAG, "Partner disconnected from orientation check");
                            runOnUiThread(() -> {
                                Toast.makeText(OrientationCheckActivity.this,
                                        "Partner disconnected", Toast.LENGTH_SHORT).show();
                                cleanupAndFinish();
                            });
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
                            Toast.makeText(OrientationCheckActivity.this,
                                    "Connection error", Toast.LENGTH_SHORT).show();
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

    private void updateOrientationInFirebase() {
        if (gameRoomId == null || uid == null || realtimeDb == null) {
            Log.w(TAG, "Cannot update orientation - missing data");
            return;
        }

        try {
            Map<String, Object> orientationData = new HashMap<>();
            orientationData.put("orientationStatus/" + uid + "/isLandscape", isLandscape);
            orientationData.put("orientationStatus/" + uid + "/lastUpdated", ServerValue.TIMESTAMP);
            orientationData.put("orientationStatus/" + uid + "/name", currentUserName);
            orientationData.put("orientationStatus/" + uid + "/connected", true);

            realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId)
                    .updateChildren(orientationData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Orientation status updated successfully: " + isLandscape);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating orientation status", e);
                        if (!isActivityDestroyed) {
                            runOnUiThread(() -> {
                                Toast.makeText(OrientationCheckActivity.this,
                                        "Connection error", Toast.LENGTH_SHORT).show();
                            });
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error in updateOrientationInFirebase", e);
        }
    }

    private void startLoadingActivity() {
        // FIXED: Removed the hasStartedLoading check since we set it in the delayed call
        if (isActivityDestroyed) {
            return;
        }

        try {
            Log.d(TAG, "Starting loading activity");

            Intent loadingIntent = new Intent(this, StartLoadingActivity.class);
            loadingIntent.putExtra("gameRoomId", gameRoomId);
            loadingIntent.putExtra("partnerId", partnerId);
            loadingIntent.putExtra("partnerName", partnerName);
            loadingIntent.putExtra("isInitiator", isInitiator);

            if (chatRoomId != null) {
                loadingIntent.putExtra("chatRoomId", chatRoomId);
            }

            // Add flags to prevent issues
            loadingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            startActivity(loadingIntent);
            finish();

        } catch (Exception e) {
            Log.e(TAG, "Error starting loading activity", e);
            hasStartedLoading = false;
            runOnUiThread(() -> {
                Toast.makeText(this, "Error starting game", Toast.LENGTH_SHORT).show();
                cleanupAndFinish();
            });
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
                disconnectData.put("orientationStatus/" + uid + "/connected", false);
                disconnectData.put("orientationStatus/" + uid + "/lastUpdated", ServerValue.TIMESTAMP);

                realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId)
                        .updateChildren(disconnectData)
                        .addOnCompleteListener(task -> {
                            Log.d(TAG, "Disconnect status updated");
                        });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        } finally {
            isActivityDestroyed = true;
            finish();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (isActivityDestroyed) {
            return;
        }

        try {
            // Update orientation status when configuration changes
            boolean wasLandscape = isLandscape;
            isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;

            if (wasLandscape != isLandscape) {
                Log.d(TAG, "Orientation changed to: " + (isLandscape ? "landscape" : "portrait"));
                updateOrientationUI();
                updateOrientationInFirebase();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling orientation change", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "OrientationCheckActivity onDestroy");

        isActivityDestroyed = true;

        try {
            if (gameRoomListener != null && gameRoomId != null && realtimeDb != null) {
                realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId)
                        .removeEventListener(gameRoomListener);
                gameRoomListener = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing listener in onDestroy", e);
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
        Log.d(TAG, "OrientationCheckActivity onPause");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "OrientationCheckActivity onResume");

        if (!isActivityDestroyed) {
            // Recheck orientation in case it changed while paused
            checkCurrentOrientation();
        }
    }
}