package com.humangodcvaki.whoi;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class DashboardActivity extends AppCompatActivity {

    // UI Components
    private TextView userNameText, userLevelText, userCoinsText, userXPText, gameTokenText;
    private TextView gamesPlayedText, winStreakText;
    private ImageView userProfileImage, settingsIcon;
    private ProgressBar levelProgressBar;

    // Content frames
    private FrameLayout profileContent, leaderboardContent, chatContent, storageContent;

    // Bottom navigation
    private LinearLayout navLeaderboard, navChat, navProfile, navStorage;
    private ImageView navLeaderboardIcon, navChatIcon, navProfileIcon, navStorageIcon;
    private TextView navLeaderboardText, navChatText, navProfileText, navStorageText;

    // Firebase
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    // Current selected tab
    private int currentTab = 2; // Start with Profile tab (index 2)

    // Animation constants
    private static final int ANIMATION_DURATION = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        initViews();
        setupBottomNavigation();
        loadUserData();

        // Show profile tab by default
        showTab(2);
    }

    private void initViews() {
        // Profile content views
        userNameText = findViewById(R.id.userNameText);
        userLevelText = findViewById(R.id.userLevelText);
        userCoinsText = findViewById(R.id.userCoinsText);
        userXPText = findViewById(R.id.userXPText);
        gameTokenText = findViewById(R.id.gameTokenText);
        userProfileImage = findViewById(R.id.userProfileImage);
        levelProgressBar = findViewById(R.id.levelProgressBar);
        settingsIcon = findViewById(R.id.settingsIcon);
        gamesPlayedText = findViewById(R.id.gamesPlayedText);
        winStreakText = findViewById(R.id.winStreakText);

        // Content frames
        profileContent = findViewById(R.id.profile_content);
        leaderboardContent = findViewById(R.id.leaderboard_content);
        chatContent = findViewById(R.id.chat_content);
        storageContent = findViewById(R.id.storage_content);

        // Bottom navigation views
        navLeaderboard = findViewById(R.id.nav_leaderboard);
        navChat = findViewById(R.id.nav_chat);
        navProfile = findViewById(R.id.nav_profile);
        navStorage = findViewById(R.id.nav_storage);

        navLeaderboardIcon = findViewById(R.id.nav_leaderboard_icon);
        navChatIcon = findViewById(R.id.nav_chat_icon);
        navProfileIcon = findViewById(R.id.nav_profile_icon);
        navStorageIcon = findViewById(R.id.nav_storage_icon);

        navLeaderboardText = findViewById(R.id.nav_leaderboard_text);
        navChatText = findViewById(R.id.nav_chat_text);
        navProfileText = findViewById(R.id.nav_profile_text);
        navStorageText = findViewById(R.id.nav_storage_text);
    }

    private void setupBottomNavigation() {
        // Leaderboard tab
        navLeaderboard.setOnClickListener(v -> {
            animateTabClick(v);
            showTab(0);
        });

        // Chat tab
        navChat.setOnClickListener(v -> {
            animateTabClick(v);
            // Navigate to ChatActivity instead of showing tab
            startActivity(new Intent(this, ChatActivity.class));
        });

        // Profile tab
        navProfile.setOnClickListener(v -> {
            animateTabClick(v);
            showTab(2);
        });

        // Storage tab
        navStorage.setOnClickListener(v -> {
            animateTabClick(v);
            // Navigate to StorageActivity
            startActivity(new Intent(this, StorageActivity.class));
        });

        // Settings icon click with fast rotation animation
        settingsIcon.setOnClickListener(v -> {
            animateSettingsClick(v);
            // TODO: Open settings/profile customization
            // startActivity(new Intent(this, SettingsActivity.class));
        });
    }

    private void showTab(int tabIndex) {
        // Update current tab
        currentTab = tabIndex;

        // Hide all content frames
        profileContent.setVisibility(View.GONE);
        leaderboardContent.setVisibility(View.GONE);
        chatContent.setVisibility(View.GONE);
        storageContent.setVisibility(View.GONE);

        // Reset all tab colors (only text colors, no icon tints)
        resetTabColors();

        // Show selected tab content and update colors
        switch (tabIndex) {
            case 0: // Leaderboard
                leaderboardContent.setVisibility(View.VISIBLE);
                setTabActive(navLeaderboardText);
                break;
            case 1: // Chat (this won't be used as we navigate to ChatActivity)
                chatContent.setVisibility(View.VISIBLE);
                setTabActive(navChatText);
                break;
            case 2: // Profile
                profileContent.setVisibility(View.VISIBLE);
                setTabActive(navProfileText);
                break;
            case 3: // Storage (updated from case 4)
                storageContent.setVisibility(View.VISIBLE);
                setTabActive(navStorageText);
                break;
        }
    }

    private void resetTabColors() {
        // Only reset text colors, no icon tinting
        int inactiveTextColor = ContextCompat.getColor(this, android.R.color.white);

        navLeaderboardText.setTextColor(inactiveTextColor);
        navChatText.setTextColor(inactiveTextColor);
        navProfileText.setTextColor(inactiveTextColor);
        navStorageText.setTextColor(inactiveTextColor);
    }

    private void setTabActive(TextView text) {
        // Only change text color for active tab, icons keep their original colors
        int activeTextColor = ContextCompat.getColor(this, android.R.color.holo_blue_bright);
        text.setTextColor(activeTextColor);
    }

    private void animateTabClick(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.9f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.9f, 1f);
        scaleX.setDuration(150);
        scaleY.setDuration(150);
        scaleX.start();
        scaleY.start();
    }

    private void animateSettingsClick(View view) {
        // Fast rotation animation for settings icon
        ObjectAnimator rotationAnimator = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f);
        rotationAnimator.setDuration(400); // Fast rotation duration
        rotationAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        rotationAnimator.start();

        // Optional: Add a slight scale effect along with rotation
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f);
        scaleX.setDuration(400);
        scaleY.setDuration(400);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleX.start();
        scaleY.start();
    }

    private void loadUserData() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            // Set display name with welcome message
            String displayName = user.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                userNameText.setText("Hello, " + displayName + "!");
            } else {
                userNameText.setText("Hello, User!");
            }

            // Load profile image with better error handling
            if (user.getPhotoUrl() != null) {
                Glide.with(this)
                        .load(user.getPhotoUrl())
                        .circleCrop()
                        .placeholder(R.drawable.whoi)
                        .error(R.drawable.whoi)
                        .into(userProfileImage);
            }

            // Load user stats from Firestore
            DocumentReference userDoc = firestore.collection("users").document(user.getUid());
            userDoc.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    long coins = documentSnapshot.getLong("coins") != null ? documentSnapshot.getLong("coins") : 100;
                    long xp = documentSnapshot.getLong("xp") != null ? documentSnapshot.getLong("xp") : 0;
                    long totalGames = documentSnapshot.getLong("totalGamesPlayed") != null ? documentSnapshot.getLong("totalGamesPlayed") : 0;
                    long winStreak = documentSnapshot.getLong("winStreak") != null ? documentSnapshot.getLong("winStreak") : 0;
                    long gameTokens = documentSnapshot.getLong("gameTokens") != null ? documentSnapshot.getLong("gameTokens") : 10;

                    long level = calculateLevel(xp);
                    int progressInLevel = calculateProgressInLevel(xp);

                    // Animate counter updates
                    animateCounterUpdate(userCoinsText, 0, (int) coins);
                    animateCounterUpdate(userXPText, 0, (int) xp);
                    animateCounterUpdate(gamesPlayedText, 0, (int) totalGames);
                    animateCounterUpdate(winStreakText, 0, (int) winStreak);
                    animateCounterUpdate(gameTokenText, 0, (int) gameTokens);

                    userLevelText.setText("Level: " + level);
                    animateProgressBar(progressInLevel);
                } else {
                    // Create new user document with default values
                    createNewUserDocument(user.getUid());
                }
            }).addOnFailureListener(e -> {
                // Handle error - set default values
                setDefaultValues();
            });
        } else {
            // User is not signed in, redirect to MainActivity
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    private void animateCounterUpdate(TextView textView, int startValue, int endValue) {
        ValueAnimator animator = ValueAnimator.ofInt(startValue, endValue);
        animator.setDuration(1000);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            textView.setText(String.valueOf(value));
        });
        animator.start();
    }

    private void animateProgressBar(int progress) {
        ObjectAnimator progressAnimator = ObjectAnimator.ofInt(levelProgressBar, "progress", 0, progress);
        progressAnimator.setDuration(1000);
        progressAnimator.setInterpolator(new DecelerateInterpolator());
        progressAnimator.start();
    }

    private long calculateLevel(long xp) {
        return (xp / 100) + 1; // 100 XP per level, starting at level 1
    }

    private int calculateProgressInLevel(long xp) {
        return (int) ((xp % 100)); // Progress within current level (0-100)
    }

    private void setDefaultValues() {
        animateCounterUpdate(userCoinsText, 0, 100);
        animateCounterUpdate(userXPText, 0, 0);
        animateCounterUpdate(gamesPlayedText, 0, 0);
        animateCounterUpdate(winStreakText, 0, 0);
        animateCounterUpdate(gameTokenText, 0, 10);
        userLevelText.setText("Level: 1");
        animateProgressBar(0);
    }

    private void createNewUserDocument(String userId) {
        // Create a new user document with default values
        User newUser = new User(100, 0, 1);

        firestore.collection("users").document(userId)
                .set(newUser)
                .addOnSuccessListener(aVoid -> {
                    // Document created successfully, update UI
                    setDefaultValues();
                })
                .addOnFailureListener(e -> {
                    // Handle error
                    setDefaultValues();
                });
    }

    // Enhanced User data class for Firestore
    public static class User {
        public long coins;
        public long xp;
        public long level;
        public long totalGamesPlayed;
        public long winStreak;
        public long gameTokens;
        public String lastLoginDate;

        public User() {} // Default constructor for Firestore

        public User(long coins, long xp, long level) {
            this.coins = coins;
            this.xp = xp;
            this.level = level;
            this.totalGamesPlayed = 0;
            this.winStreak = 0;
            this.gameTokens = 10; // Default game tokens
            this.lastLoginDate = "";
        }
    }

    @Override
    public void onBackPressed() {
        // Override back button to prevent going back to login screen
        moveTaskToBack(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh user data when returning to dashboard
        loadUserData();
        // Make sure profile tab is selected when returning from other activities
        showTab(2);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // Reset to profile tab when app is restarted
        showTab(2);
    }
}