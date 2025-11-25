package com.humangodcvaki.whoi;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class GameActivity extends AppCompatActivity {

    private static final String TAG = "GameActivity";
    private static final String GAME_ROOMS_NODE = "gameRooms";
    private static final String USER_PRESENCE_NODE = "userPresence";

    // Game constants
    private static final int ROUNDS_TO_WIN = 3;
    private static final int COUNTDOWN_TIME = 10; // seconds
    private static final String CHOICE_STONE = "stone";
    private static final String CHOICE_PAPER = "paper";
    private static final String CHOICE_SCISSORS = "scissors";

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

    // UI Elements
    private TextView userNameText1, userNameText2, userLevelText1, userLevelText2, userTokenText1, userTokenText2;
    private TextView countdownText, scoreText, roundText, gameStatusText;
    private CardView userCard1, userCard2;
    private ImageView userChoice1, userChoice2;
    private TextView choiceText1, choiceText2;
    private CardView stoneCard, paperCard, scissorsCard;
    private LinearLayout choiceButtons;
    private Button btnBack;

    // Game State
    private int currentRound = 1;
    private int userScore = 0;
    private int partnerScore = 0;
    private boolean isGameActive = false;
    private boolean hasSubmittedChoice = false;
    private String userCurrentChoice = null;
    private String partnerCurrentChoice = null;

    // Listeners
    private ValueEventListener gameRoomListener;
    private ValueEventListener partnerPresenceListener;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        firestore = FirebaseFirestore.getInstance();
        realtimeDb = FirebaseDatabase.getInstance().getReference();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated!");
            Toast.makeText(this, "Authentication error.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        uid = currentUser.getUid();
        currentUserName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "You";

        // Get data from Intent
        Intent intent = getIntent();
        gameRoomId = intent.getStringExtra("gameRoomId");
        partnerId = intent.getStringExtra("partnerId");
        partnerName = intent.getStringExtra("partnerName");
        isInitiator = intent.getBooleanExtra("isInitiator", false);

        if (gameRoomId == null || partnerId == null) {
            Log.e(TAG, "Invalid game data from Intent");
            Toast.makeText(this, "Game data error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (partnerName == null || partnerName.trim().isEmpty()) {
            partnerName = "Partner";
        }

        initializeUI();
        setupGameRoom();
        loadUserStats();
        monitorConnectionQuality();
    }

    private void initializeUI() {
        // Player info
        userNameText1 = findViewById(R.id.userNameText1);
        userNameText2 = findViewById(R.id.userNameText2);
        userLevelText1 = findViewById(R.id.userLevelText1);
        userLevelText2 = findViewById(R.id.userLevelText2);
        userTokenText1 = findViewById(R.id.userTokenText1);
        userTokenText2 = findViewById(R.id.userTokenText2);

        // Game UI
        countdownText = findViewById(R.id.countdownText);
        scoreText = findViewById(R.id.scoreText);
        roundText = findViewById(R.id.roundText);
        gameStatusText = findViewById(R.id.gameStatusText);

        // Choice cards
        userCard1 = findViewById(R.id.userCard1);
        userCard2 = findViewById(R.id.userCard2);
        userChoice1 = findViewById(R.id.userChoice1);
        userChoice2 = findViewById(R.id.userChoice2);
        choiceText1 = findViewById(R.id.choiceText1);
        choiceText2 = findViewById(R.id.choiceText2);

        // Choice buttons
        stoneCard = findViewById(R.id.stoneCard);
        paperCard = findViewById(R.id.paperCard);
        scissorsCard = findViewById(R.id.scissorsCard);
        choiceButtons = findViewById(R.id.choiceButtons);
        btnBack = findViewById(R.id.btnBack);

        // Set player names
        userNameText1.setText(currentUserName);
        userNameText2.setText(partnerName);

        // Setup choice buttons
        setupChoiceButtons();

        // Setup back button
        btnBack.setOnClickListener(v -> showLeaveGameDialog());

        // Initial UI state
        updateScoreText();
        updateRoundText();
        gameStatusText.setText("Connecting to game room...");
        choiceButtons.setVisibility(View.GONE);
    }

    private void setupChoiceButtons() {
        stoneCard.setOnClickListener(v -> makeChoice(CHOICE_STONE, R.drawable.whoi_stone));
        paperCard.setOnClickListener(v -> makeChoice(CHOICE_PAPER, R.drawable.whoi_papper));
        scissorsCard.setOnClickListener(v -> makeChoice(CHOICE_SCISSORS, R.drawable.whoi_sizor));
    }

    private void loadUserStats() {
        // Load current user stats
        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Long level = documentSnapshot.getLong("level");
                        Long gameTokens = documentSnapshot.getLong("gameTokens");
                        Long xp = documentSnapshot.getLong("xp");

                        if (level == null && xp != null) {
                            level = (xp / 100) + 1;
                        }

                        userLevelText1.setText("Lv. " + (level != null ? level : 1));
                        userTokenText1.setText(String.valueOf(gameTokens != null ? gameTokens : 0));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error loading user stats", e));

        // Load partner stats
        firestore.collection("users").document(partnerId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Long level = documentSnapshot.getLong("level");
                        Long gameTokens = documentSnapshot.getLong("gameTokens");
                        Long xp = documentSnapshot.getLong("xp");

                        if (level == null && xp != null) {
                            level = (xp / 100) + 1;
                        }

                        userLevelText2.setText("Lv. " + (level != null ? level : 1));
                        userTokenText2.setText(String.valueOf(gameTokens != null ? gameTokens : 0));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error loading partner stats", e));
    }

    // Enhanced setupGameRoom method with better error handling and player validation
    private void setupGameRoom() {
        DatabaseReference gameRoomRef = realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId);

        // First, check if game room exists
        gameRoomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    Log.e(TAG, "Game room does not exist: " + gameRoomId);
                    showErrorAndExit("Game room not found. Please try again.");
                    return;
                }

                // Game room exists, set up listener
                setupGameRoomListener(gameRoomRef);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error checking game room", databaseError.toException());
                showErrorAndExit("Failed to connect to game. Please try again.");
            }
        });
    }

    private void setupGameRoomListener(DatabaseReference gameRoomRef) {
        gameRoomListener = gameRoomRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    if (!isGameActive) {
                        showPartnerLeftEarly("Game room was removed.");
                    } else {
                        showPartnerDisconnected("Game room was deleted.");
                    }
                    return;
                }

                // Handle game end first
                Boolean gameEnded = dataSnapshot.child("gameEnded").getValue(Boolean.class);
                if (gameEnded != null && gameEnded) {
                    handleGameEnd(dataSnapshot);
                    return;
                }

                // Check if both players are present
                if (!validatePlayersInRoom(dataSnapshot)) {
                    return;
                }

                // Handle game phases
                String gamePhase = dataSnapshot.child("gamePhase").getValue(String.class);
                Boolean gameStarted = dataSnapshot.child("gameStarted").getValue(Boolean.class);

                if (gameStarted != null && gameStarted && !isGameActive) {
                    isGameActive = true;
                    Log.d(TAG, "Game is now active. Starting first round.");
                    startNewRound();
                }

                // Handle different game phases
                switch (gamePhase != null ? gamePhase : "") {
                    case "waiting_choices":
                        handleWaitingChoices(dataSnapshot);
                        break;
                    case "reveal_results":
                        handleRevealResults(dataSnapshot);
                        break;
                    default:
                        Log.d(TAG, "Game phase: " + gamePhase);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Game room listener cancelled", databaseError.toException());
                showErrorAndExit("Connection error. Please try again.");
            }
        });

        // Mark player as connected and ready
        joinGameRoom(gameRoomRef);
        // Monitor partner presence
        monitorPartnerPresence();
    }

    private boolean validatePlayersInRoom(DataSnapshot dataSnapshot) {
        DataSnapshot playersSnapshot = dataSnapshot.child("players");

        // Check if both required players are in the room
        boolean currentUserExists = playersSnapshot.child(uid).exists();
        boolean partnerExists = playersSnapshot.child(partnerId).exists();

        if (!currentUserExists || !partnerExists) {
            Log.w(TAG, "Missing players in game room. Current user: " + currentUserExists +
                    ", Partner: " + partnerExists);
            return false;
        }

        return true;
    }

    private void joinGameRoom(DatabaseReference gameRoomRef) {
        Map<String, Object> playerJoinData = new HashMap<>();
        playerJoinData.put("players/" + uid + "/connected", true);
        playerJoinData.put("players/" + uid + "/name", currentUserName);
        playerJoinData.put("players/" + uid + "/joinedAt", ServerValue.TIMESTAMP);

        // Initiator can set initial game state
        if (isInitiator) {
            playerJoinData.put("initiatorId", uid);
            playerJoinData.put("gamePhase", "waiting_players");
            playerJoinData.put("currentRound", currentRound);
            playerJoinData.put("gameStarted", false);
            playerJoinData.put("gameEnded", false);
        }

        gameRoomRef.updateChildren(playerJoinData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Successfully joined game room: " + gameRoomId);
                    // Check if we can start the game (if initiator and both players ready)
                    if (isInitiator) {
                        checkAndStartGameIfReady(gameRoomRef);
                    } else {
                        // Non-initiator marks as ready
                        gameRoomRef.child("players").child(uid).child("ready").setValue(true);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error joining game room", e);
                    showErrorAndExit("Failed to join game. Please try again.");
                });
    }

    // Enhanced ready check with better validation
    private void checkAndStartGameIfReady(DatabaseReference gameRoomRef) {
        if (!isInitiator) return;

        gameRoomRef.child("players").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.getChildrenCount() < 2) {
                    Log.d(TAG, "Waiting for both players to join. Current count: " +
                            dataSnapshot.getChildrenCount());
                    updateGameStatus("Waiting for partner to join...");
                    // Set a timeout to wait for second player
                    setPlayerJoinTimeout(gameRoomRef);
                    return;
                }

                boolean allPlayersReady = true;
                boolean allPlayersConnected = true;

                for (DataSnapshot playerSnapshot : dataSnapshot.getChildren()) {
                    Boolean ready = playerSnapshot.child("ready").getValue(Boolean.class);
                    Boolean connected = playerSnapshot.child("connected").getValue(Boolean.class);

                    if (ready == null || !ready) {
                        allPlayersReady = false;
                    }
                    if (connected == null || !connected) {
                        allPlayersConnected = false;
                    }
                }

                if (allPlayersConnected && allPlayersReady) {
                    Log.d(TAG, "All players ready and connected. Starting game.");
                    startGameForBothPlayers(gameRoomRef);
                } else {
                    Log.d(TAG, "Waiting for players. Ready: " + allPlayersReady +
                            ", Connected: " + allPlayersConnected);
                    updateGameStatus("Waiting for both players to be ready...");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error checking player readiness", databaseError.toException());
            }
        });
    }

    private void startGameForBothPlayers(DatabaseReference gameRoomRef) {
        Map<String, Object> gameStartData = new HashMap<>();
        gameStartData.put("gameStarted", true);
        gameStartData.put("gamePhase", "waiting_choices");
        gameStartData.put("currentRound", 1);

        // Initialize scores for both players
        gameStartData.put("scores/" + uid, 0);
        gameStartData.put("scores/" + partnerId, 0);
        gameStartData.put("gameStartedAt", ServerValue.TIMESTAMP);

        gameRoomRef.updateChildren(gameStartData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Game started successfully by initiator.");
                    runOnUiThread(() -> {
                        updateGameStatus("Game starting...");
                        Toast.makeText(GameActivity.this, "Both players ready! Game starting!",
                                Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error starting game", e);
                    showErrorAndExit("Failed to start game. Please try again.");
                });
    }

    private void setPlayerJoinTimeout(DatabaseReference gameRoomRef) {
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutHandler.postDelayed(() -> {
            // Check if second player joined
            gameRoomRef.child("players").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getChildrenCount() < 2) {
                        Log.w(TAG, "Second player never joined. Ending game.");
                        showPartnerLeftEarly("Partner failed to join the game.");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Error in timeout check", databaseError.toException());
                }
            });
        }, 30000); // 30 second timeout
    }

    private void monitorPartnerPresence() {
        partnerPresenceListener = realtimeDb.child(USER_PRESENCE_NODE).child(partnerId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Boolean isOnline = dataSnapshot.child("online").getValue(Boolean.class);
                        if (isOnline == null || !isOnline) {
                            if (isGameActive) {
                                showPartnerDisconnected("Partner went offline.");
                            }
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error monitoring partner presence", databaseError.toException());
                    }
                });
    }

    // Add connection quality monitoring
    private void monitorConnectionQuality() {
        DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(Boolean.class);
                if (connected) {
                    Log.d(TAG, "Connected to Firebase");
                } else {
                    Log.w(TAG, "Disconnected from Firebase");
                    if (isGameActive) {
                        runOnUiThread(() -> {
                            Toast.makeText(GameActivity.this,
                                    "Connection lost. Reconnecting...", Toast.LENGTH_LONG).show();
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Connection monitoring cancelled", error.toException());
            }
        });
    }

    private void startNewRound() {
        runOnUiThread(() -> {
            if (!isGameActive) return;

            updateRoundText();
            updateGameStatus("Round " + currentRound + " - Make your choice!");
            choiceButtons.setVisibility(View.VISIBLE);
            hasSubmittedChoice = false;
            userCurrentChoice = null;
            partnerCurrentChoice = null;

            // Reset choice displays
            userChoice1.setImageResource(R.drawable.whoi_tag);
            userChoice2.setImageResource(R.drawable.whoi_tag);
            choiceText1.setText("?");
            choiceText2.setText("?");

            startCountdown();
        });
    }

    private void startCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        countdownText.setVisibility(View.VISIBLE);
        countDownTimer = new CountDownTimer(COUNTDOWN_TIME * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                countdownText.setText(String.valueOf(seconds));
                animateCountdown();
            }

            @Override
            public void onFinish() {
                countdownText.setText("0");
                if (!hasSubmittedChoice && isGameActive) {
                    String[] choices = {CHOICE_STONE, CHOICE_PAPER, CHOICE_SCISSORS};
                    String randomChoice = choices[new Random().nextInt(choices.length)];
                    Log.d(TAG, "Countdown finished. Auto-selecting: " + randomChoice);
                    makeChoice(randomChoice, getChoiceIcon(randomChoice));
                }
            }
        };
        countDownTimer.start();
    }

    private void animateCountdown() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(countdownText, "scaleX", 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(countdownText, "scaleY", 1.2f, 1f);
        scaleX.setDuration(500);
        scaleY.setDuration(500);
        scaleX.start();
        scaleY.start();
    }

    private void makeChoice(String choice, int iconResId) {
        if (hasSubmittedChoice || !isGameActive) return;

        hasSubmittedChoice = true;
        userCurrentChoice = choice;

        runOnUiThread(() -> {
            choiceButtons.setVisibility(View.GONE);
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            countdownText.setText("Choice Made!");

            userChoice1.setImageResource(iconResId);
            choiceText1.setText(choice.substring(0, 1).toUpperCase() + choice.substring(1));
            updateGameStatus("Waiting for partner's choice...");
        });

        // Submit choice to Firebase
        DatabaseReference gameRoomRef = realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId);
        Map<String, Object> choiceData = new HashMap<>();
        choiceData.put("choices/" + currentRound + "/" + uid + "/choice", choice);
        choiceData.put("choices/" + currentRound + "/" + uid + "/timestamp", ServerValue.TIMESTAMP);

        gameRoomRef.updateChildren(choiceData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Choice submitted: " + choice + " for round " + currentRound))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error submitting choice", e);
                    runOnUiThread(() -> {
                        hasSubmittedChoice = false;
                        choiceButtons.setVisibility(View.VISIBLE);
                        updateGameStatus("Error. Try again.");
                    });
                });
    }

    private void handleWaitingChoices(DataSnapshot dataSnapshot) {
        if (!isGameActive) return;

        DataSnapshot choicesSnapshot = dataSnapshot.child("choices").child(String.valueOf(currentRound));
        if (!choicesSnapshot.exists()) {
            Log.d(TAG, "Waiting for choices for round " + currentRound + ", choices node doesn't exist yet.");
            return;
        }

        String myChoice = choicesSnapshot.child(uid).child("choice").getValue(String.class);
        String opponentChoice = choicesSnapshot.child(partnerId).child("choice").getValue(String.class);

        if (myChoice != null && opponentChoice != null) {
            Log.d(TAG, "Both players made choices for round " + currentRound + ". My: " + myChoice + ", Partner: " + opponentChoice);
            if (isInitiator) {
                realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId)
                        .child("gamePhase").setValue("reveal_results");
            }
        } else {
            if (myChoice != null) {
                updateGameStatus("Waiting for partner's choice...");
            } else {
                updateGameStatus("Make your choice!");
            }
        }
    }

    private void handleRevealResults(DataSnapshot dataSnapshot) {
        if (!isGameActive) return;
        Log.d(TAG, "Handling reveal results for round " + currentRound);

        DataSnapshot choicesSnapshot = dataSnapshot.child("choices").child(String.valueOf(currentRound));
        String myChoice = choicesSnapshot.child(uid).child("choice").getValue(String.class);
        partnerCurrentChoice = choicesSnapshot.child(partnerId).child("choice").getValue(String.class);

        if (myChoice == null || partnerCurrentChoice == null) {
            Log.e(TAG, "Choices missing in reveal_results phase for round " + currentRound);
            if(isInitiator) {
                realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).child("gamePhase").setValue("waiting_choices");
            }
            return;
        }

        // Update UI with partner's choice
        runOnUiThread(() -> {
            userChoice2.setImageResource(getChoiceIcon(partnerCurrentChoice));
            choiceText2.setText(partnerCurrentChoice.substring(0, 1).toUpperCase() + partnerCurrentChoice.substring(1));
        });

        String result = calculateResult(myChoice, partnerCurrentChoice);
        String roundWinnerId = null;
        String resultMessage;

        if ("win".equals(result)) {
            userScore++;
            roundWinnerId = uid;
            resultMessage = currentUserName + " wins this round!";
        } else if ("loss".equals(result)) {
            partnerScore++;
            roundWinnerId = partnerId;
            resultMessage = partnerName + " wins this round!";
        } else {
            roundWinnerId = "draw";
            resultMessage = "It's a draw!";
        }

        Log.d(TAG, "Round " + currentRound + " result: " + resultMessage + ". Scores: User=" + userScore + ", Partner=" + partnerScore);

        // Update UI
        runOnUiThread(() -> {
            updateGameStatus(resultMessage);
            updateScoreText();
        });

        // Only initiator updates scores and round winner in Firebase
        if (isInitiator) {
            Map<String, Object> roundResultData = new HashMap<>();
            roundResultData.put("results/" + currentRound + "/winner", roundWinnerId);
            roundResultData.put("results/" + currentRound + "/userChoice", myChoice);
            roundResultData.put("results/" + currentRound + "/partnerChoice", partnerCurrentChoice);
            roundResultData.put("scores/" + uid, userScore);
            roundResultData.put("scores/" + partnerId, partnerScore);

            DatabaseReference gameRoomRef = realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId);
            gameRoomRef.updateChildren(roundResultData)
                    .addOnSuccessListener(aVoid -> {
                        if (userScore >= ROUNDS_TO_WIN || partnerScore >= ROUNDS_TO_WIN) {
                            Log.d(TAG, "Game end condition met. Setting gameEnded to true.");
                            gameRoomRef.child("gameEnded").setValue(true);
                            gameRoomRef.child("gamePhase").setValue("game_over");
                        } else {
                            gameRoomRef.child("currentRound").setValue(currentRound + 1);
                            gameRoomRef.child("gamePhase").setValue("waiting_choices");
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error updating round results", e));
        }

        // Update currentRound for next round
        Long firebaseRound = dataSnapshot.child("currentRound").getValue(Long.class);
        if (firebaseRound != null && firebaseRound > currentRound) {
            currentRound = firebaseRound.intValue();
        }

        // Add delay before next round
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isGameActive && (userScore < ROUNDS_TO_WIN && partnerScore < ROUNDS_TO_WIN)) {
                Log.d(TAG, "Reveal results done. Waiting for Firebase to trigger next round.");
            } else if (isGameActive){
                Log.d(TAG, "Reveal results done. Game should be ending.");
            }
        }, 3000);
    }

    private void handleGameEnd(DataSnapshot dataSnapshot) {
        if (!isGameActive) return;
        isGameActive = false;
        Log.d(TAG, "Handling game end.");

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        // Determine winner
        String winnerId = null;
        String winnerName = "No one";
        if (userScore >= ROUNDS_TO_WIN) {
            winnerId = uid;
            winnerName = currentUserName;
        } else if (partnerScore >= ROUNDS_TO_WIN) {
            winnerId = partnerId;
            winnerName = partnerName;
        } else {
            Long finalUserScore = dataSnapshot.child("scores").child(uid).getValue(Long.class);
            Long finalPartnerScore = dataSnapshot.child("scores").child(partnerId).getValue(Long.class);
            if (finalUserScore != null && finalPartnerScore != null) {
                if (finalUserScore > finalPartnerScore) {
                    winnerId = uid; winnerName = currentUserName;
                } else if (finalPartnerScore > finalUserScore) {
                    winnerId = partnerId; winnerName = partnerName;
                }
            }
        }

        final String finalWinnerName = winnerName;
        final String finalWinnerId = winnerId;

        runOnUiThread(() -> {
            choiceButtons.setVisibility(View.GONE);
            countdownText.setVisibility(View.GONE);
            updateGameStatus("Game Over! " + finalWinnerName + " wins!");
            updateScoreText();

            new AlertDialog.Builder(this)
                    .setTitle("Game Over")
                    .setMessage(finalWinnerName + " won the game!\n\nYour Score: " + userScore + "\nPartner's Score: " + partnerScore)
                    .setPositiveButton("OK", (dialog, which) -> finishGameCleanup())
                    .setCancelable(false)
                    .show();
        });

        // Update user stats
        if (finalWinnerId != null) {
            updateUserGameStats(finalWinnerId.equals(uid));
        }

        // Initiator cleans up the game room after a delay
        if (isInitiator) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).removeValue()
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Game room removed by initiator."))
                        .addOnFailureListener(e -> Log.e(TAG, "Error removing game room", e));
            }, 10000); // 10 seconds delay before removing room
        }
    }

    private void updateUserGameStats(boolean didIWin) {
        DocumentReference userDocRef = firestore.collection("users").document(uid);
        firestore.runTransaction(transaction -> {
                    transaction.update(userDocRef, "totalGamesPlayed", FieldValue.increment(1));
                    if (didIWin) {
                        transaction.update(userDocRef, "wins", FieldValue.increment(1));
                        transaction.update(userDocRef, "xp", FieldValue.increment(20)); // XP for win
                        transaction.update(userDocRef, "winStreak", FieldValue.increment(1));
                    } else {
                        transaction.update(userDocRef, "xp", FieldValue.increment(5));  // XP for loss/draw
                        transaction.update(userDocRef, "winStreak", 0); // Reset win streak
                    }
                    return null;
                }).addOnSuccessListener(aVoid -> Log.d(TAG, "User stats updated successfully."))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update user stats.", e));
    }

    private String calculateResult(String myChoice, String partnerChoice) {
        if (myChoice.equals(partnerChoice)) return "draw";
        if ((myChoice.equals(CHOICE_STONE) && partnerChoice.equals(CHOICE_SCISSORS)) ||
                (myChoice.equals(CHOICE_PAPER) && partnerChoice.equals(CHOICE_STONE)) ||
                (myChoice.equals(CHOICE_SCISSORS) && partnerChoice.equals(CHOICE_PAPER))) {
            return "win";
        }
        return "loss";
    }

    private int getChoiceIcon(String choice) {
        if (choice == null) return R.drawable.whoi_tag;
        switch (choice) {
            case CHOICE_STONE:
                return R.drawable.whoi_stone;
            case CHOICE_PAPER:
                return R.drawable.whoi_papper;
            case CHOICE_SCISSORS:
                return R.drawable.whoi_sizor;
            default:
                return R.drawable.whoi_tag;
        }
    }

    private void updateScoreText() {
        scoreText.setText(currentUserName + ": " + userScore + "  |  " + partnerName + ": " + partnerScore);
    }

    private void updateRoundText() {
        roundText.setText("Round: " + currentRound + "/" + ROUNDS_TO_WIN);
    }

    // Enhanced UI updates for better user feedback
    private void updateGameStatus(String status) {
        runOnUiThread(() -> {
            if (gameStatusText != null) {
                gameStatusText.setText(status);
            }
        });
    }

    private void showLeaveGameDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Leave Game?")
                .setMessage("Are you sure you want to leave the game? This may result in a loss.")
                .setPositiveButton("Leave", (dialog, which) -> {
                    if (isGameActive && isInitiator) {
                        Map<String, Object> gameEndData = new HashMap<>();
                        gameEndData.put("gameEnded", true);
                        gameEndData.put("gamePhase", "game_over");
                        gameEndData.put("winnerOnLeave", partnerId);
                        realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).updateChildren(gameEndData);
                    } else if (isGameActive) {
                        Map<String, Object> leaveData = new HashMap<>();
                        leaveData.put("players/" + uid + "/connected", false);
                        realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).updateChildren(leaveData);
                    }
                    finishGameCleanup();
                })
                .setNegativeButton("Stay", null)
                .show();
    }

    private void showErrorAndExit(String message) {
        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                    .setTitle("Game Error")
                    .setMessage(message)
                    .setPositiveButton("OK", (dialog, which) -> finishGameCleanup())
                    .setCancelable(false)
                    .show();
        });
    }

    private void showPartnerLeftEarly(String message) {
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                    .setTitle("Partner Left")
                    .setMessage(message)
                    .setPositiveButton("OK", (dialog, which) -> finishGameCleanup())
                    .setCancelable(false)
                    .show();
        }
        isGameActive = false;
    }

    private void showPartnerDisconnected(String message) {
        if (!isGameActive || isFinishing()) return;
        isGameActive = false;

        runOnUiThread(() -> {
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            choiceButtons.setVisibility(View.GONE);
            updateGameStatus("Partner disconnected. You win!");

            new AlertDialog.Builder(GameActivity.this)
                    .setTitle("Partner Disconnected")
                    .setMessage(message + "\nYou are considered the winner.")
                    .setPositiveButton("OK", (dialog, which) -> finishGameCleanup())
                    .setCancelable(false)
                    .show();
        });

        // Initiator sets the game as ended if partner disconnects
        if (isInitiator) {
            Map<String, Object> gameEndData = new HashMap<>();
            gameEndData.put("gameEnded", true);
            gameEndData.put("gamePhase", "game_over");
            gameEndData.put("winnerOnDisconnect", uid);
            realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).updateChildren(gameEndData);
        } else {
            realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).child("players").child(uid).child("connected").setValue(false);
        }
    }

    private void finishGameCleanup() {
        Log.d(TAG, "Cleaning up game resources.");
        isGameActive = false;

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (gameRoomListener != null && gameRoomId != null) {
            realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).removeEventListener(gameRoomListener);
            gameRoomListener = null;
        }
        if (partnerPresenceListener != null && partnerId != null) {
            realtimeDb.child(USER_PRESENCE_NODE).child(partnerId).removeEventListener(partnerPresenceListener);
            partnerPresenceListener = null;
        }

        // Mark user as disconnected from this specific game room
        if (uid != null && gameRoomId != null) {
            realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).child("players").child(uid).child("connected").setValue(false)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Marked player " + uid + " as disconnected from game " + gameRoomId))
                    .addOnFailureListener(e -> Log.e(TAG, "Error marking player disconnected.", e));
        }

        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Handle app backgrounding if needed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "GameActivity onDestroy called.");

        if (isGameActive) {
            if (uid != null && gameRoomId != null) {
                realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).child("players").child(uid).child("connected").setValue(false);
            }
        }

        // Remove listeners for sure if not already removed
        if (gameRoomListener != null && gameRoomId != null && realtimeDb != null) {
            realtimeDb.child(GAME_ROOMS_NODE).child(gameRoomId).removeEventListener(gameRoomListener);
        }
        if (partnerPresenceListener != null && partnerId != null && realtimeDb != null) {
            realtimeDb.child(USER_PRESENCE_NODE).child(partnerId).removeEventListener(partnerPresenceListener);
        }
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}