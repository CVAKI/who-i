package com.humangodcvaki.whoi;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final String WAITING_POOL_NODE = "waitingPool";
    private static final String CHAT_ROOMS_NODE = "chatRooms";
    private static final String MESSAGES_NODE = "messages";
    private static final String USER_PRESENCE_NODE = "userPresence";
    private static final String ROOM_PARTICIPANTS_NODE = "participants";
    private static final long MATCHMAKING_TIMEOUT = 45000; // 45 seconds
    private static final long PRESENCE_TIMEOUT = 15000; // 15 seconds for presence detection
    private static final long INVITATION_TIMEOUT = 60000; // 60 seconds for game invitations

    // Two databases
    private FirebaseFirestore firestore; // For coins
    private DatabaseReference realtimeDb; // For chat
    private DatabaseReference presenceRef; // For user presence

    private String uid;
    private String currentUserName;
    private CoinsManager coinsManager;
    private GameTokenManager gameTokenManager;

    // UI Components
    private ImageView sendBtn;
    private ImageView btnGame;
    private ImageView btnBack;
    private EditText inputMessage;
    private TextView statusText;
    private RecyclerView recyclerView;
    private MessageAdapter messageAdapter;

    // Chat state
    private long userCoins;
    private boolean hasCheckedCoins = false;
    private String chatRoomId = null;
    private String partnerId = null;
    private String partnerName = null;
    private boolean isInWaitingPool = false;
    private boolean isChatActive = false;
    private String waitingPoolKey = null;
    private boolean isUserOnline = false;

    // Game invitation state
    private boolean gameInvitationSent = false;
    private ValueEventListener gameInvitationListener;
    private Handler invitationTimeoutHandler;
    private Runnable invitationTimeoutRunnable;

    // Listeners for Realtime Database
    private ValueEventListener waitingPoolListener;
    private ChildEventListener messagesListener;
    private ValueEventListener chatRoomListener;
    private ValueEventListener partnerPresenceListener;
    private ValueEventListener roomParticipantsListener;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Validate Firebase Auth
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.e(TAG, "User not authenticated");
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize both databases
        firestore = FirebaseFirestore.getInstance(); // For coins
        realtimeDb = FirebaseDatabase.getInstance().getReference(); // For chat

        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        currentUserName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();

        if (uid == null || uid.isEmpty()) {
            Log.e(TAG, "Invalid user ID");
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (currentUserName == null || currentUserName.trim().isEmpty()) {
            currentUserName = "Anonymous";
        }

        Log.d(TAG, "ChatActivity started for user: " + uid + " (" + currentUserName + ")");

        initializeUI();
        setupUserPresence(); // Set up presence detection first
        coinsManager = new CoinsManager(this, firestore, uid); // Uses Firestore
        initializeGameFeatures(); // Initialize game token manager and features

        sendBtn.setEnabled(false);
        inputMessage.setEnabled(false);
        btnGame.setEnabled(false);

        checkCoinsAndStart();
    }

    private void setupUserPresence() {
        // Set up user presence in Realtime Database
        presenceRef = realtimeDb.child(USER_PRESENCE_NODE).child(uid);

        // Set user as online when they connect
        Map<String, Object> presenceData = new HashMap<>();
        presenceData.put("online", true);
        presenceData.put("lastSeen", ServerValue.TIMESTAMP);
        presenceData.put("userName", currentUserName);

        presenceRef.setValue(presenceData);

        // Set user as offline when they disconnect
        presenceRef.onDisconnect().setValue(false);

        isUserOnline = true;
        Log.d(TAG, "User presence set up for: " + uid);
    }

    private void initializeUI() {
        sendBtn = findViewById(R.id.sendBtn);
        btnGame = findViewById(R.id.btnGame);
        btnBack = findViewById(R.id.btnBack);
        inputMessage = findViewById(R.id.inputMessage);
        statusText = findViewById(R.id.statusText);
        recyclerView = findViewById(R.id.recyclerView);

        // Setup RecyclerView
        messageAdapter = new MessageAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(messageAdapter);

        // Set the game invitation click listener
        messageAdapter.setGameInvitationClickListener(new MessageAdapter.GameInvitationClickListener() {
            @Override
            public void onGameInvitationClicked(String gameRoomId, String inviterName) {
                handleGameInvitationAccept(gameRoomId, inviterName);
            }
        });

        // Setup send button click
        sendBtn.setOnClickListener(v -> sendMessage());

        // Setup back button click
        btnBack.setOnClickListener(v -> onBackPressed());

        // Setup enter key to send message
        inputMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    // Initialize GameTokenManager
    private void initializeGameTokenManager() {
        gameTokenManager = new GameTokenManager(this, firestore, uid);
    }

    // Setup game button click listener with invitation functionality
    private void setupGameButtonClickListener() {
        btnGame.setOnClickListener(v -> {
            if (isChatActive && partnerId != null) {
                if (!gameInvitationSent) {
                    sendGameInvitation();
                } else {
                    Toast.makeText(this, "Game invitation already sent", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Connect with a partner first to play games", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Initialize game features
    private void initializeGameFeatures() {
        initializeGameTokenManager();
        setupGameButtonClickListener();
    }

    // Handle game invitations
    private void sendGameInvitation() {
        gameTokenManager.checkGameTokens(GameTokenManager.GAME_TOKEN_COST_INITIATOR,
                new GameTokenManager.GameTokenCheckListener() {
                    @Override
                    public void onSufficientTokens(long currentTokens) {
                        gameTokenManager.showGameInviteConfirmationDialog(currentTokens,
                                () -> deductTokensAndSendInvite(),
                                () -> {/* User cancelled */});
                    }

                    @Override
                    public void onInsufficientTokens(long currentTokens) {
                        gameTokenManager.showInsufficientGameTokensDialog(currentTokens,
                                GameTokenManager.GAME_TOKEN_COST_INITIATOR,
                                gameTokenManager::navigateToAdsActivity,
                                gameTokenManager::navigateToBuyActivity,
                                () -> {/* User cancelled */});
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Deduct tokens and send invitation
    private void deductTokensAndSendInvite() {
        gameTokenManager.deductGameTokens(GameTokenManager.GAME_TOKEN_COST_INITIATOR,
                new GameTokenManager.GameTokenDeductionListener() {
                    @Override
                    public void onDeductionSuccess(long newTokenBalance) {
                        createGameInvitationMessage();
                        gameInvitationSent = true;
                        btnGame.setImageResource(R.drawable.whoi_sizor);
                        btnGame.setEnabled(false);
                        Toast.makeText(ChatActivity.this, "Game tokens deducted. Invitation sent!", Toast.LENGTH_SHORT).show();

                        // Set invitation timeout
                        setGameInvitationTimeout();
                    }

                    @Override
                    public void onDeductionFailure(String message) {
                        Toast.makeText(ChatActivity.this, "Failed to send invitation: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Create game invitation message
    private void createGameInvitationMessage() {
        String messageId = realtimeDb.child(CHAT_ROOMS_NODE)
                .child(chatRoomId)
                .child(MESSAGES_NODE)
                .push()
                .getKey();

        String gameRoomId = UUID.randomUUID().toString();

        // Create GameInvitationMessage with proper fields
        Map<String, Object> invitationMessageData = new HashMap<>();
        invitationMessageData.put("senderId", uid);
        invitationMessageData.put("senderName", currentUserName);
        invitationMessageData.put("text", "🎮 " + currentUserName + " invited you to play Stone Paper Scissors!");
        invitationMessageData.put("timestamp", System.currentTimeMillis());
        invitationMessageData.put("gameRoomId", gameRoomId);
        invitationMessageData.put("invitationStatus", "pending");
        invitationMessageData.put("messageType", "game_invitation");

        realtimeDb.child(CHAT_ROOMS_NODE)
                .child(chatRoomId)
                .child(MESSAGES_NODE)
                .child(messageId)
                .setValue(invitationMessageData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Game invitation message sent");
                    listenForGameInvitationResponse(gameRoomId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending game invitation", e);
                    Toast.makeText(this, "Failed to send game invitation", Toast.LENGTH_SHORT).show();
                    resetGameButton();
                });
    }

    // Enhanced game room creation with better synchronization
    private void createGameRoom(String gameRoomId) {
        Map<String, Object> gameRoomData = new HashMap<>();
        gameRoomData.put("gameId", gameRoomId);
        gameRoomData.put("chatRoomId", chatRoomId);
        gameRoomData.put("createdAt", ServerValue.TIMESTAMP);
        gameRoomData.put("gameStarted", false);
        gameRoomData.put("gameEnded", false);
        gameRoomData.put("gamePhase", "waiting_players");
        gameRoomData.put("currentRound", 1);

        // Initialize scores properly
        Map<String, Object> scores = new HashMap<>();
        scores.put(uid, 0);
        scores.put(partnerId, 0);
        gameRoomData.put("scores", scores);

        // Initialize players with proper structure
        Map<String, Object> players = new HashMap<>();
        Map<String, Object> player1 = new HashMap<>();
        player1.put("name", currentUserName);
        player1.put("ready", false);
        player1.put("connected", false);
        player1.put("joinedAt", ServerValue.TIMESTAMP);
        players.put(uid, player1);

        Map<String, Object> player2 = new HashMap<>();
        player2.put("name", partnerName);
        player2.put("ready", false);
        player2.put("connected", false);
        player2.put("joinedAt", ServerValue.TIMESTAMP);
        players.put(partnerId, player2);

        gameRoomData.put("players", players);

        // Set initiator
        gameRoomData.put("initiatorId", uid);

        realtimeDb.child("gameRooms")
                .child(gameRoomId)
                .setValue(gameRoomData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Game room created successfully: " + gameRoomId);
                    // Update the invitation message status
                    updateInvitationMessageStatus(gameRoomId, "room_created");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating game room", e);
                    Toast.makeText(this, "Failed to create game room", Toast.LENGTH_SHORT).show();
                });
    }

    // Update invitation message status in chat
    private void updateInvitationMessageStatus(String gameRoomId, String status) {
        // Find and update the invitation message
        realtimeDb.child(CHAT_ROOMS_NODE)
                .child(chatRoomId)
                .child(MESSAGES_NODE)
                .orderByChild("gameRoomId")
                .equalTo(gameRoomId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                            messageSnapshot.getRef().child("invitationStatus").setValue(status);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error updating invitation status", databaseError.toException());
                    }
                });
    }

    // Enhanced listener for game invitation responses
    private void listenForGameInvitationResponse(String gameRoomId) {
        gameInvitationListener = realtimeDb.child(CHAT_ROOMS_NODE)
                .child(chatRoomId)
                .child("gameInvitation")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        String status = dataSnapshot.child("status").getValue(String.class);
                        String acceptedBy = dataSnapshot.child("acceptedBy").getValue(String.class);

                        if ("accepted".equals(status) && acceptedBy != null) {
                            // Remove listener to prevent duplicate calls
                            if (gameInvitationListener != null) {
                                realtimeDb.child(CHAT_ROOMS_NODE)
                                        .child(chatRoomId)
                                        .child("gameInvitation")
                                        .removeEventListener(gameInvitationListener);
                                gameInvitationListener = null;
                            }

                            // Cancel timeout
                            cancelInvitationTimeout();

                            // Partner accepted - create game room and start orientation check
                            Log.d(TAG, "Game invitation accepted by: " + acceptedBy);
                            createGameRoom(gameRoomId);

                            // Add delay to ensure game room creation
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                startOrientationCheckActivity(gameRoomId, true); // This now goes to OrientationCheckActivity
                            }, 1500);

                        } else if ("declined".equals(status)) {
                            // Partner declined
                            Toast.makeText(ChatActivity.this,
                                    "Partner declined the game invitation", Toast.LENGTH_SHORT).show();
                            resetGameButton();
                            cancelInvitationTimeout();

                            // Remove listener
                            if (gameInvitationListener != null) {
                                realtimeDb.child(CHAT_ROOMS_NODE)
                                        .child(chatRoomId)
                                        .child("gameInvitation")
                                        .removeEventListener(gameInvitationListener);
                                gameInvitationListener = null;
                            }
                        } else if ("timeout".equals(status)) {
                            // Invitation timed out
                            Toast.makeText(ChatActivity.this,
                                    "Game invitation timed out", Toast.LENGTH_SHORT).show();
                            resetGameButton();

                            // Remove listener
                            if (gameInvitationListener != null) {
                                realtimeDb.child(CHAT_ROOMS_NODE)
                                        .child(chatRoomId)
                                        .child("gameInvitation")
                                        .removeEventListener(gameInvitationListener);
                                gameInvitationListener = null;
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Game invitation listener cancelled", databaseError.toException());
                        Toast.makeText(ChatActivity.this,
                                "Error monitoring game invitation", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Add timeout for game invitations
    private void setGameInvitationTimeout() {
        invitationTimeoutHandler = new Handler(Looper.getMainLooper());
        invitationTimeoutRunnable = () -> {
            if (gameInvitationListener != null) {
                // Invitation timed out
                realtimeDb.child(CHAT_ROOMS_NODE)
                        .child(chatRoomId)
                        .child("gameInvitation")
                        .child("status")
                        .setValue("timeout");

                Toast.makeText(this, "Game invitation timed out", Toast.LENGTH_SHORT).show();
                resetGameButton();

                // Clean up listener
                if (gameInvitationListener != null) {
                    realtimeDb.child(CHAT_ROOMS_NODE)
                            .child(chatRoomId)
                            .child("gameInvitation")
                            .removeEventListener(gameInvitationListener);
                    gameInvitationListener = null;
                }
            }
        };
        invitationTimeoutHandler.postDelayed(invitationTimeoutRunnable, INVITATION_TIMEOUT);
    }

    // Cancel invitation timeout
    private void cancelInvitationTimeout() {
        if (invitationTimeoutHandler != null && invitationTimeoutRunnable != null) {
            invitationTimeoutHandler.removeCallbacks(invitationTimeoutRunnable);
        }
    }

    // Handle game invitation acceptance
    private void handleGameInvitationAccept(String gameRoomId, String inviterName) {
        gameTokenManager.checkGameTokens(GameTokenManager.GAME_TOKEN_COST_ACCEPTOR,
                new GameTokenManager.GameTokenCheckListener() {
                    @Override
                    public void onSufficientTokens(long currentTokens) {
                        gameTokenManager.showGameAcceptConfirmationDialog(currentTokens, inviterName,
                                () -> acceptGameInvitation(gameRoomId),
                                () -> declineGameInvitation(gameRoomId));
                    }

                    @Override
                    public void onInsufficientTokens(long currentTokens) {
                        gameTokenManager.showInsufficientGameTokensDialog(currentTokens,
                                GameTokenManager.GAME_TOKEN_COST_ACCEPTOR,
                                gameTokenManager::navigateToAdsActivity,
                                gameTokenManager::navigateToBuyActivity,
                                () -> declineGameInvitation(gameRoomId));
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                        declineGameInvitation(gameRoomId);
                    }
                });
    }

    // Enhanced game invitation acceptance with orientation check flow
    private void acceptGameInvitation(String gameRoomId) {
        gameTokenManager.deductGameTokens(GameTokenManager.GAME_TOKEN_COST_ACCEPTOR,
                new GameTokenManager.GameTokenDeductionListener() {
                    @Override
                    public void onDeductionSuccess(long newTokenBalance) {
                        // Mark invitation as accepted
                        Map<String, Object> acceptData = new HashMap<>();
                        acceptData.put("gameInvitation/status", "accepted");
                        acceptData.put("gameInvitation/acceptedBy", uid);
                        acceptData.put("gameInvitation/acceptedAt", ServerValue.TIMESTAMP);

                        realtimeDb.child(CHAT_ROOMS_NODE)
                                .child(chatRoomId)
                                .updateChildren(acceptData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Game invitation accepted, preparing for orientation check");

                                    // Check if game room already exists, if not create it
                                    realtimeDb.child("gameRooms").child(gameRoomId)
                                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                                @Override
                                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                                    if (!dataSnapshot.exists()) {
                                                        // Create game room if it doesn't exist
                                                        createGameRoom(gameRoomId);
                                                    }

                                                    // Show loading message
                                                    Toast.makeText(ChatActivity.this,
                                                            "Game tokens deducted. Preparing orientation check...",
                                                            Toast.LENGTH_SHORT).show();

                                                    // Add small delay to ensure game room is created
                                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                                        startOrientationCheckActivity(gameRoomId, false); // Goes to OrientationCheckActivity
                                                    }, 1500);
                                                }

                                                @Override
                                                public void onCancelled(@NonNull DatabaseError databaseError) {
                                                    Log.e(TAG, "Error checking game room", databaseError.toException());
                                                    Toast.makeText(ChatActivity.this,
                                                            "Error starting game", Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error accepting invitation", e);
                                    Toast.makeText(ChatActivity.this,
                                            "Failed to accept invitation", Toast.LENGTH_SHORT).show();
                                });
                    }

                    @Override
                    public void onDeductionFailure(String message) {
                        Toast.makeText(ChatActivity.this,
                                "Failed to accept invitation: " + message, Toast.LENGTH_SHORT).show();
                        declineGameInvitation(gameRoomId);
                    }
                });
    }

    // Decline game invitation
    private void declineGameInvitation(String gameRoomId) {
        Map<String, Object> declineData = new HashMap<>();
        declineData.put("gameInvitation/status", "declined");
        declineData.put("gameInvitation/declinedBy", uid);
        declineData.put("gameInvitation/declinedAt", ServerValue.TIMESTAMP);

        realtimeDb.child(CHAT_ROOMS_NODE)
                .child(chatRoomId)
                .updateChildren(declineData);
    }

    // Enhanced orientation check activity starting with better data validation
    private void startOrientationCheckActivity(String gameRoomId, boolean isInitiator) {
        if (gameRoomId == null || gameRoomId.trim().isEmpty()) {
            Log.e(TAG, "Invalid game room ID");
            Toast.makeText(this, "Invalid game room", Toast.LENGTH_SHORT).show();
            return;
        }

        if (partnerId == null || partnerId.trim().isEmpty()) {
            Log.e(TAG, "Invalid partner ID");
            Toast.makeText(this, "Partner information missing", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Starting orientation check activity - Room: " + gameRoomId +
                ", Partner: " + partnerId + ", Initiator: " + isInitiator);

        // Start OrientationCheckActivity instead of GameActivity directly
        Intent intent = new Intent(ChatActivity.this, OrientationCheckActivity.class);
        intent.putExtra("gameRoomId", gameRoomId);
        intent.putExtra("partnerId", partnerId);
        intent.putExtra("partnerName", partnerName != null ? partnerName : "Partner");
        intent.putExtra("isInitiator", isInitiator);
        intent.putExtra("chatRoomId", chatRoomId); // Add chat room reference

        // Add flags to prevent multiple instances
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        startActivity(intent);
        resetGameButton();

        // Optionally disable chat UI while game is active
        disableChatForGame();
    }

    // Update the disable chat method to reflect orientation check phase
    private void disableChatForGame() {
        sendBtn.setEnabled(false);
        inputMessage.setEnabled(false);
        btnGame.setEnabled(false);
        statusText.setText("Preparing for game - checking orientation...");
    }

    // Re-enable chat UI when returning from game
    private void enableChatAfterGame() {
        if (isChatActive) {
            sendBtn.setEnabled(true);
            inputMessage.setEnabled(true);
            btnGame.setEnabled(true);
            statusText.setText("Connected with friend");
        }
    }

    // Reset game button
    private void resetGameButton() {
        gameInvitationSent = false;
        btnGame.setImageResource(R.drawable.whoi_female);
        btnGame.setEnabled(true);
    }

    /** Step 1: Check coins using Firestore */
    private void checkCoinsAndStart() {
        coinsManager.checkUserCoins(new CoinsManager.CoinCheckListener() {
            @Override
            public void onSufficientCoins(long currentCoins) {
                userCoins = currentCoins;
                coinsManager.showCoinConfirmationDialog(userCoins,
                        ChatActivity.this::deductCoinsAndEnterChat,
                        ChatActivity.this::finish);
            }

            @Override
            public void onInsufficientCoins(long currentCoins) {
                coinsManager.showInsufficientCoinsDialog(currentCoins,
                        coinsManager::navigateToAdsActivity,
                        coinsManager::navigateToBuyActivity,
                        ChatActivity.this::finish);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /** Step 2: Deduct coins using Firestore */
    private void deductCoinsAndEnterChat() {
        coinsManager.deductCoinsForChat(new CoinsManager.CoinDeductionListener() {
            @Override
            public void onDeductionSuccess(long newCoinBalance) {
                userCoins = newCoinBalance;
                hasCheckedCoins = true;
                statusText.setText("Coins deducted. Looking for a partner...");
                Toast.makeText(ChatActivity.this, CoinsManager.CHAT_COST + " coins deducted. Remaining: " + userCoins, Toast.LENGTH_SHORT).show();
                enterWaitingPool(); // Now use Realtime Database
            }

            @Override
            public void onDeductionFailure(String message) {
                Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                coinsManager.showInsufficientCoinsDialog(userCoins,
                        coinsManager::navigateToAdsActivity,
                        coinsManager::navigateToBuyActivity,
                        ChatActivity.this::finish);
            }
        });
    }

    /** Step 3: Enter waiting pool with truly random matching */
    private void enterWaitingPool() {
        statusText.setText("Looking for random online partners...");

        // Clean up old entries first
        cleanUpOldEntries();

        // Try to find online partners
        findRandomOnlinePartner();

        // Set up timeout
        timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutRunnable = () -> {
            if (isInWaitingPool && !isChatActive) {
                statusText.setText("No partners found. Try again later.");
                removeFromWaitingPool();
                Toast.makeText(this, "No online users found. Please try again later.", Toast.LENGTH_LONG).show();
                finish();
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, MATCHMAKING_TIMEOUT);
    }

    private void findRandomOnlinePartner() {
        Log.d(TAG, "Looking for random online partners...");

        // First, get all online users
        realtimeDb.child(USER_PRESENCE_NODE)
                .orderByChild("online")
                .equalTo(true)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        List<String> onlineUsers = new ArrayList<>();

                        for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                            String userId = userSnapshot.getKey();
                            if (userId != null && !userId.equals(uid)) {
                                onlineUsers.add(userId);
                            }
                        }

                        Log.d(TAG, "Found " + onlineUsers.size() + " online users");

                        if (!onlineUsers.isEmpty()) {
                            // Now check which online users are in waiting pool
                            findWaitingOnlineUsers(onlineUsers);
                        } else {
                            // No online users, add self to waiting pool
                            addToWaitingPool();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error getting online users", databaseError.toException());
                        addToWaitingPool();
                    }
                });
    }

    private void findWaitingOnlineUsers(List<String> onlineUsers) {
        realtimeDb.child(WAITING_POOL_NODE)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        List<DataSnapshot> waitingOnlineUsers = new ArrayList<>();

                        for (DataSnapshot waitingSnapshot : dataSnapshot.getChildren()) {
                            String waitingUserId = waitingSnapshot.child("userId").getValue(String.class);
                            Long timestamp = waitingSnapshot.child("timestamp").getValue(Long.class);

                            // Check if user is online and recently active
                            if (waitingUserId != null &&
                                    onlineUsers.contains(waitingUserId) &&
                                    timestamp != null &&
                                    (System.currentTimeMillis() - timestamp) < PRESENCE_TIMEOUT) {
                                waitingOnlineUsers.add(waitingSnapshot);
                            }
                        }

                        if (!waitingOnlineUsers.isEmpty()) {
                            // Truly random selection
                            Random random = new Random();
                            DataSnapshot randomPartner = waitingOnlineUsers.get(random.nextInt(waitingOnlineUsers.size()));

                            partnerId = randomPartner.child("userId").getValue(String.class);
                            partnerName = randomPartner.child("userName").getValue(String.class);
                            String partnerWaitingKey = randomPartner.getKey();

                            Log.d(TAG, "Randomly selected partner: " + partnerId + " from " + waitingOnlineUsers.size() + " waiting online users");

                            if (partnerId != null && !partnerId.isEmpty()) {
                                createChatRoom(partnerWaitingKey);
                            } else {
                                addToWaitingPool();
                            }
                        } else {
                            Log.d(TAG, "No online users in waiting pool, adding self");
                            addToWaitingPool();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error checking waiting pool", databaseError.toException());
                        addToWaitingPool();
                    }
                });
    }

    private void addToWaitingPool() {
        if (uid == null || uid.isEmpty()) {
            Log.e(TAG, "User ID is null or empty");
            Toast.makeText(this, "Authentication error. Please login again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Map<String, Object> waitingData = new HashMap<>();
        waitingData.put("userId", uid);
        waitingData.put("userName", currentUserName);
        waitingData.put("timestamp", ServerValue.TIMESTAMP);

        Log.d(TAG, "Adding user to waiting pool: " + uid);

        DatabaseReference waitingRef = realtimeDb.child(WAITING_POOL_NODE).push();
        waitingPoolKey = waitingRef.getKey();

        waitingRef.setValue(waitingData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Successfully added to waiting pool with key: " + waitingPoolKey);
                    isInWaitingPool = true;
                    statusText.setText("Waiting for a random online partner...");
                    listenForMatch();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding to waiting pool", e);
                    handleWaitingPoolError(e);
                });
    }

    private void handleWaitingPoolError(Exception e) {
        String errorMessage = "Error joining waiting pool";
        if (e.getMessage() != null) {
            errorMessage += ": " + e.getMessage();
            Log.e(TAG, "Detailed error: " + e.getMessage());
        }

        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        statusText.setText("Failed to join waiting pool. Please try again.");

        // Retry after delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isChatActive && !isInWaitingPool) {
                Log.d(TAG, "Retrying to add to waiting pool...");
                addToWaitingPool();
            }
        }, 3000);
    }

    private void cleanUpOldEntries() {
        long cutoffTime = System.currentTimeMillis() - 300000; // 5 minutes ago

        // Clean up old waiting pool entries
        realtimeDb.child(WAITING_POOL_NODE)
                .orderByChild("timestamp")
                .endAt(cutoffTime)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for (DataSnapshot child : dataSnapshot.getChildren()) {
                            child.getRef().removeValue();
                        }
                        if (dataSnapshot.getChildrenCount() > 0) {
                            Log.d(TAG, "Cleaned up " + dataSnapshot.getChildrenCount() + " old waiting pool entries");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error cleaning up waiting pool", databaseError.toException());
                    }
                });

        // Clean up inactive chat rooms
        realtimeDb.child(CHAT_ROOMS_NODE)
                .orderByChild("createdAt")
                .endAt(cutoffTime)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for (DataSnapshot child : dataSnapshot.getChildren()) {
                            Boolean active = child.child("active").getValue(Boolean.class);
                            if (active == null || !active) {
                                child.getRef().removeValue();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error cleaning up chat rooms", databaseError.toException());
                    }
                });
    }

    private void listenForMatch() {
        // Listen for chat rooms where we are participant1
        chatRoomListener = realtimeDb.child(CHAT_ROOMS_NODE)
                .orderByChild("participant1")
                .equalTo(uid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                            if (!isChatActive) {
                                chatRoomId = chatSnapshot.getKey();
                                partnerId = chatSnapshot.child("participant2").getValue(String.class);
                                partnerName = chatSnapshot.child("participant2Name").getValue(String.class);
                                startChat();
                                return;
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Listen for match failed", databaseError.toException());
                    }
                });

        // Also listen for chat rooms where we are participant2
        realtimeDb.child(CHAT_ROOMS_NODE)
                .orderByChild("participant2")
                .equalTo(uid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                            if (!isChatActive) {
                                chatRoomId = chatSnapshot.getKey();
                                partnerId = chatSnapshot.child("participant1").getValue(String.class);
                                partnerName = chatSnapshot.child("participant1Name").getValue(String.class);
                                startChat();
                                return;
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Listen for match failed", databaseError.toException());
                    }
                });
    }

    private void createChatRoom(String partnerWaitingKey) {
        chatRoomId = realtimeDb.child(CHAT_ROOMS_NODE).push().getKey();

        Map<String, Object> chatRoomData = new HashMap<>();
        chatRoomData.put("participant1", uid);
        chatRoomData.put("participant1Name", currentUserName);
        chatRoomData.put("participant2", partnerId);
        chatRoomData.put("participant2Name", partnerName);
        chatRoomData.put("createdAt", ServerValue.TIMESTAMP);
        chatRoomData.put("active", true);

        // Track participants for presence monitoring
        Map<String, Boolean> participants = new HashMap<>();
        participants.put(uid, true);
        participants.put(partnerId, true);
        chatRoomData.put("participants", participants);

        // Create chat room and remove both users from waiting pool
        Map<String, Object> updates = new HashMap<>();
        updates.put(CHAT_ROOMS_NODE + "/" + chatRoomId, chatRoomData);
        updates.put(WAITING_POOL_NODE + "/" + partnerWaitingKey, null); // Remove partner
        if (waitingPoolKey != null) {
            updates.put(WAITING_POOL_NODE + "/" + waitingPoolKey, null); // Remove self
        }

        realtimeDb.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Chat room created successfully");
                    startChat();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating chat room", e);
                    Toast.makeText(this, "Error creating chat room", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void startChat() {
        isChatActive = true;
        isInWaitingPool = false;

        // Cancel timeout
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }

        // Remove from waiting pool
        removeFromWaitingPool();

        // Remove waiting pool listener
        if (chatRoomListener != null) {
            realtimeDb.child(CHAT_ROOMS_NODE).removeEventListener(chatRoomListener);
        }

        // Enable chat UI
        sendBtn.setEnabled(true);
        inputMessage.setEnabled(true);
        btnGame.setEnabled(true); // Enable game button when connected

        // Update status text to show generic connection message
        statusText.setText("Connected with friend");

        // Start listening for messages and partner presence
        listenForMessages();
        monitorPartnerPresence();
        monitorRoomParticipants();

        Toast.makeText(this, "Chat started with your friend!", Toast.LENGTH_SHORT).show();
    }

    private void monitorPartnerPresence() {
        if (partnerId == null) return;

        partnerPresenceListener = realtimeDb.child(USER_PRESENCE_NODE)
                .child(partnerId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Boolean isOnline = dataSnapshot.child("online").getValue(Boolean.class);
                        if (isOnline != null && !isOnline) {
                            // Partner went offline
                            showPartnerLeftMessage();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error monitoring partner presence", databaseError.toException());
                    }
                });
    }

    private void monitorRoomParticipants() {
        if (chatRoomId == null) return;

        roomParticipantsListener = realtimeDb.child(CHAT_ROOMS_NODE)
                .child(chatRoomId)
                .child("participants")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (!dataSnapshot.exists()) {
                            // Room was deleted
                            return;
                        }

                        Boolean partnerInRoom = dataSnapshot.child(partnerId).getValue(Boolean.class);
                        if (partnerInRoom == null || !partnerInRoom) {
                            showPartnerLeftMessage();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error monitoring room participants", databaseError.toException());
                    }
                });
    }

    private void showPartnerLeftMessage() {
        if (!isChatActive) return;

        runOnUiThread(() -> {
            statusText.setText("Friend has left the chat");
            btnGame.setEnabled(false); // Disable game button when partner leaves

            // Add system message
            Message systemMessage = new Message("system", "System",
                    "Your friend has left the chat", System.currentTimeMillis());
            messageAdapter.addMessage(systemMessage);
            recyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);

            Toast.makeText(this, "Your friend has left the chat", Toast.LENGTH_SHORT).show();
        });
    }

    // Listen for messages method to handle GameInvitationMessage properly
    private void listenForMessages() {
        if (chatRoomId == null) return;

        Log.d(TAG, "Starting to listen for messages in chat room: " + chatRoomId);

        messagesListener = realtimeDb.child(CHAT_ROOMS_NODE)
                .child(chatRoomId)
                .child(MESSAGES_NODE)
                .orderByChild("timestamp")
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(@NonNull DataSnapshot dataSnapshot, String previousChildName) {
                        try {
                            // Check if it's a game invitation message
                            String messageType = dataSnapshot.child("messageType").getValue(String.class);

                            Message message;
                            if ("game_invitation".equals(messageType)) {
                                // Create GameInvitationMessage
                                String senderId = dataSnapshot.child("senderId").getValue(String.class);
                                String senderName = dataSnapshot.child("senderName").getValue(String.class);
                                String text = dataSnapshot.child("text").getValue(String.class);
                                Long timestamp = dataSnapshot.child("timestamp").getValue(Long.class);
                                String gameRoomId = dataSnapshot.child("gameRoomId").getValue(String.class);
                                String invitationStatus = dataSnapshot.child("invitationStatus").getValue(String.class);

                                message = new GameInvitationMessage(
                                        senderId != null ? senderId : "",
                                        senderName != null ? senderName : "",
                                        text != null ? text : "",
                                        timestamp != null ? timestamp : 0,
                                        gameRoomId != null ? gameRoomId : "",
                                        invitationStatus != null ? invitationStatus : "pending"
                                );
                            } else {
                                // Regular message
                                message = dataSnapshot.getValue(Message.class);
                            }

                            if (message != null) {
                                Log.d(TAG, "New message received: " + message.getText() + " (Type: " + messageType + ")");
                                messageAdapter.addMessage(message);
                                recyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing message", e);
                            // Fallback to regular message parsing
                            Message message = dataSnapshot.getValue(Message.class);
                            if (message != null) {
                                messageAdapter.addMessage(message);
                                recyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
                            }
                        }
                    }

                    @Override
                    public void onChildChanged(@NonNull DataSnapshot dataSnapshot, String previousChildName) {}

                    @Override
                    public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {}

                    @Override
                    public void onChildMoved(@NonNull DataSnapshot dataSnapshot, String previousChildName) {}

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Listen for messages failed", databaseError.toException());
                    }
                });
    }

    private void sendMessage() {
        String messageText = inputMessage.getText().toString().trim();
        if (messageText.isEmpty() || chatRoomId == null || !isChatActive) {
            return;
        }

        String messageId = realtimeDb.child(CHAT_ROOMS_NODE)
                .child(chatRoomId)
                .child(MESSAGES_NODE)
                .push()
                .getKey();

        Message message = new Message(uid, currentUserName, messageText, System.currentTimeMillis());

        realtimeDb.child(CHAT_ROOMS_NODE)
                .child(chatRoomId)
                .child(MESSAGES_NODE)
                .child(messageId)
                .setValue(message)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Message sent successfully");
                    inputMessage.setText("");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending message", e);
                    Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show();
                });
    }

    private void removeFromWaitingPool() {
        if (!isInWaitingPool || waitingPoolKey == null) return;

        Log.d(TAG, "Removing from waiting pool: " + waitingPoolKey);
        realtimeDb.child(WAITING_POOL_NODE)
                .child(waitingPoolKey)
                .removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Successfully removed from waiting pool");
                    isInWaitingPool = false;
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error removing from waiting pool", e));
    }

    private void leaveChat() {
        if (chatRoomId == null || !isChatActive) return;

        Log.d(TAG, "User leaving chat: " + chatRoomId);

        // Remove user from room participants
        realtimeDb.child(CHAT_ROOMS_NODE)
                .child(chatRoomId)
                .child("participants")
                .child(uid)
                .setValue(false)
                .addOnSuccessListener(aVoid -> {
                    // Check if both users have left
                    checkAndCleanupRoom();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error leaving chat", e));
    }

    private void checkAndCleanupRoom() {
        if (chatRoomId == null) return;

        realtimeDb.child(CHAT_ROOMS_NODE)
                .child(chatRoomId)
                .child("participants")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        boolean anyUserPresent = false;
                        for (DataSnapshot child : dataSnapshot.getChildren()) {
                            Boolean present = child.getValue(Boolean.class);
                            if (present != null && present) {
                                anyUserPresent = true;
                                break;
                            }
                        }

                        if (!anyUserPresent) {
                            // Both users have left, delete the room
                            Log.d(TAG, "Both users left, deleting room: " + chatRoomId);
                            realtimeDb.child(CHAT_ROOMS_NODE)
                                    .child(chatRoomId)
                                    .removeValue()
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Room deleted successfully"))
                                    .addOnFailureListener(e -> Log.e(TAG, "Error deleting room", e));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error checking room participants", databaseError.toException());
                    }
                });
    }

    // Clean up game listeners
    private void cleanupGameListeners() {
        if (gameInvitationListener != null && chatRoomId != null) {
            realtimeDb.child(CHAT_ROOMS_NODE)
                    .child(chatRoomId)
                    .child("gameInvitation")
                    .removeEventListener(gameInvitationListener);
            gameInvitationListener = null;
        }

        // Cancel any pending timeouts
        cancelInvitationTimeout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-enable chat UI when returning from game
        enableChatAfterGame();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ChatActivity onDestroy");

        // Set user as offline
        if (presenceRef != null) {
            presenceRef.setValue(false);
        }

        // Leave chat if active
        leaveChat();

        // Clean up listeners
        if (chatRoomListener != null) {
            realtimeDb.child(CHAT_ROOMS_NODE).removeEventListener(chatRoomListener);
        }
        if (messagesListener != null && chatRoomId != null) {
            realtimeDb.child(CHAT_ROOMS_NODE)
                    .child(chatRoomId)
                    .child(MESSAGES_NODE)
                    .removeEventListener(messagesListener);
        }
        if (partnerPresenceListener != null && partnerId != null) {
            realtimeDb.child(USER_PRESENCE_NODE)
                    .child(partnerId)
                    .removeEventListener(partnerPresenceListener);
        }
        if (roomParticipantsListener != null && chatRoomId != null) {
            realtimeDb.child(CHAT_ROOMS_NODE)
                    .child(chatRoomId)
                    .child("participants")
                    .removeEventListener(roomParticipantsListener);
        }

        // Clean up game listeners
        cleanupGameListeners();

        // Cancel timeout
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }

        // Clean up waiting pool
        removeFromWaitingPool();
    }

    @Override
    public void onBackPressed() {
        if (isChatActive) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Leave Chat")
                    .setMessage("Are you sure you want to leave this chat?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        super.onBackPressed();
                    })
                    .setNegativeButton("No", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    // GameInvitationMessage class with proper getters for Firebase
    public static class GameInvitationMessage extends Message {
        public String gameRoomId;
        public String invitationStatus;
        public String messageType;

        public GameInvitationMessage() {
            super();
            this.messageType = "game_invitation";
        }

        public GameInvitationMessage(String senderId, String senderName, String text, long timestamp, String gameRoomId, String status) {
            super(senderId, senderName, text, timestamp);
            this.gameRoomId = gameRoomId;
            this.invitationStatus = status;
            this.messageType = "game_invitation";
        }

        // Add getters for Firebase
        public String getGameRoomId() {
            return gameRoomId;
        }

        public String getInvitationStatus() {
            return invitationStatus;
        }

        public String getMessageType() {
            return messageType;
        }

        // Add setters for Firebase
        public void setGameRoomId(String gameRoomId) {
            this.gameRoomId = gameRoomId;
        }

        public void setInvitationStatus(String invitationStatus) {
            this.invitationStatus = invitationStatus;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }
    }
}