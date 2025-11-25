package com.humangodcvaki.whoi;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AdsActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private String userId;

    private TextView titleText, instructionText, currentCoinsText, earnCoinsText, statusText;
    private TextView dailyBonusStatusText, adsWatchedToday, coinsEarnedToday, totalAdsWatched;
    private Button watchAdBtn, backToDashboardBtn, btnDailyBonus, btnBuyCoins;
    private ImageView backBtn;
    private CardView adCard, coinsCard;
    private ProgressBar adProgressBar;

    // AdMob variables
    private RewardedAd rewardedAd;
    private boolean isLoadingAd = false;

    // Daily bonus variables
    private boolean hasClaimed = false;
    private String lastClaimDate = "";
    private Handler dailyBonusHandler;
    private Runnable dailyBonusRunnable;

    private static final int COINS_PER_AD = 250;
    private static final int DAILY_BONUS_COINS = 100;
    private static final String REWARDED_AD_UNIT_ID = "ca-app-pub-7730916065550976/REPLACE_WITH_YOUR_REWARDED_AD_ID"; // Replace with your actual rewarded ad unit ID
    private long currentCoins = 0;

    // Stats variables
    private int todayAdsWatched = 0;
    private int todayCoinsEarned = 0;
    private int totalAdsCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ads);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userId = user.getUid();
        initViews();
        setupClickListeners();
        setupAnimations();
        initializeAdMob();
        loadCurrentCoins();
        loadDailyBonusStatus();
        loadUserStats();
        setupDailyBonusTimer();
    }

    private void initViews() {
        titleText = findViewById(R.id.titleText);
        instructionText = findViewById(R.id.instructionText);
        currentCoinsText = findViewById(R.id.currentCoinsText);
        earnCoinsText = findViewById(R.id.earnCoinsText);
        watchAdBtn = findViewById(R.id.watchAdBtn);
        backToDashboardBtn = findViewById(R.id.backToDashboardBtn);
        backBtn = findViewById(R.id.backBtn);
        adCard = findViewById(R.id.adCard);
        coinsCard = findViewById(R.id.coinsCard);
        adProgressBar = findViewById(R.id.adProgressBar);
        statusText = findViewById(R.id.statusText);

        // Daily bonus elements
        btnDailyBonus = findViewById(R.id.btnDailyBonus);
        dailyBonusStatusText = findViewById(R.id.dailyBonusStatusText);

        // Stats elements
        adsWatchedToday = findViewById(R.id.adsWatchedToday);
        coinsEarnedToday = findViewById(R.id.coinsEarnedToday);
        totalAdsWatched = findViewById(R.id.totalAdsWatched);

        // Buy coins button
        btnBuyCoins = findViewById(R.id.btnBuyCoins);

        // Set initial text
        earnCoinsText.setText("Earn " + COINS_PER_AD + " coins per ad!");
        instructionText.setText("Watch rewarded ads to earn coins and unlock chat features!");

        // Initially disable the watch ad button until ad is loaded
        watchAdBtn.setEnabled(false);
        watchAdBtn.setText("Loading Ad...");

        // Initialize daily bonus button
        btnDailyBonus.setEnabled(false);
        dailyBonusStatusText.setText("Checking status...");
    }

    private void setupClickListeners() {
        watchAdBtn.setOnClickListener(v -> {
            if (rewardedAd != null && !isLoadingAd) {
                showRewardedAd();
            } else if (!isLoadingAd) {
                loadRewardedAd();
                Toast.makeText(this, "Loading ad, please wait...", Toast.LENGTH_SHORT).show();
            }
        });

        backToDashboardBtn.setOnClickListener(v -> finish());
        backBtn.setOnClickListener(v -> finish());

        // Daily bonus click listener
        btnDailyBonus.setOnClickListener(v -> claimDailyBonus());

        // Buy coins click listener
        btnBuyCoins.setOnClickListener(v -> {
            Toast.makeText(this, "Buy coins feature coming soon!", Toast.LENGTH_SHORT).show();
            // TODO: Implement in-app purchases
        });
    }

    private void setupAnimations() {
        // Initial setup - make views invisible
        View[] views = {adCard, coinsCard, watchAdBtn, backToDashboardBtn, btnDailyBonus};

        for (View view : views) {
            if (view != null) {
                view.setAlpha(0f);
                view.setTranslationY(50f);
            }
        }

        // Animate views in with stagger effect
        new Handler().postDelayed(() -> animateViewsIn(), 200);
    }

    private void animateViewsIn() {
        View[] views = {adCard, coinsCard, watchAdBtn, backToDashboardBtn, btnDailyBonus};

        for (int i = 0; i < views.length; i++) {
            final View view = views[i];
            if (view != null) {
                new Handler().postDelayed(() -> {
                    ObjectAnimator fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
                    ObjectAnimator slideIn = ObjectAnimator.ofFloat(view, "translationY", 50f, 0f);

                    fadeIn.setDuration(300);
                    slideIn.setDuration(300);
                    fadeIn.setInterpolator(new DecelerateInterpolator());
                    slideIn.setInterpolator(new DecelerateInterpolator());

                    fadeIn.start();
                    slideIn.start();
                }, i * 100);
            }
        }
    }

    private void initializeAdMob() {
        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
                updateStatusText("AdMob initialized successfully");
                loadRewardedAd();
            }
        });
    }

    private void loadRewardedAd() {
        if (isLoadingAd) return;

        isLoadingAd = true;
        watchAdBtn.setEnabled(false);
        watchAdBtn.setText("Loading Ad...");
        updateStatusText("Loading rewarded ad...");

        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(this, REWARDED_AD_UNIT_ID, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(LoadAdError loadAdError) {
                rewardedAd = null;
                isLoadingAd = false;
                watchAdBtn.setEnabled(true);
                watchAdBtn.setText("Retry Loading Ad");
                updateStatusText("Ad failed to load: " + loadAdError.getMessage());
                Toast.makeText(AdsActivity.this, "Failed to load ad. Tap to retry.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdLoaded(RewardedAd ad) {
                rewardedAd = ad;
                isLoadingAd = false;
                watchAdBtn.setEnabled(true);
                watchAdBtn.setText("Watch Ad & Earn " + COINS_PER_AD + " Coins");
                updateStatusText("Ad loaded! Ready to watch.");

                // Set up fullscreen content callback
                setupAdCallbacks();

                Toast.makeText(AdsActivity.this, "Ad loaded successfully!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupAdCallbacks() {
        if (rewardedAd != null) {
            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdClicked() {
                    updateStatusText("Ad clicked");
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    updateStatusText("Ad dismissed");
                    rewardedAd = null;
                    watchAdBtn.setText("Loading Ad...");
                    loadRewardedAd(); // Load next ad
                }

                @Override
                public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                    updateStatusText("Ad failed to show: " + adError.getMessage());
                    rewardedAd = null;
                    loadRewardedAd(); // Try to load again
                }

                @Override
                public void onAdImpression() {
                    updateStatusText("Ad impression recorded");
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    updateStatusText("Ad showed fullscreen content");
                }
            });
        }
    }

    private void showRewardedAd() {
        if (rewardedAd != null) {
            // Show loading indicator
            adProgressBar.setVisibility(View.VISIBLE);
            watchAdBtn.setEnabled(false);

            rewardedAd.show(this, new OnUserEarnedRewardListener() {
                @Override
                public void onUserEarnedReward(RewardItem rewardItem) {
                    // User earned reward, add coins
                    int rewardAmount = rewardItem.getAmount();
                    if (rewardAmount == 0) {
                        rewardAmount = COINS_PER_AD; // Use default if AdMob doesn't specify amount
                    }

                    addCoinsToUser(rewardAmount, true); // true indicates this is from an ad
                }
            });
        } else {
            updateStatusText("Ad not ready. Loading...");
            loadRewardedAd();
        }
    }

    // Daily Bonus Methods
    private void loadDailyBonusStatus() {
        DocumentReference userDoc = firestore.collection("users").document(userId);
        userDoc.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                lastClaimDate = documentSnapshot.getString("lastDailyBonus");
                updateDailyBonusUI();
            } else {
                // User document doesn't exist, create it with default values
                createUserDocument();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load daily bonus status", Toast.LENGTH_SHORT).show();
            dailyBonusStatusText.setText("Error loading status");
        });
    }

    private void createUserDocument() {
        Map<String, Object> userData = new HashMap<>();
        userData.put("coins", 0);
        userData.put("lastDailyBonus", "");
        userData.put("adsWatchedToday", 0);
        userData.put("coinsEarnedToday", 0);
        userData.put("totalAdsWatched", 0);
        userData.put("lastStatsUpdate", getCurrentDateString());

        firestore.collection("users").document(userId)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    lastClaimDate = "";
                    updateDailyBonusUI();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to create user profile", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateDailyBonusUI() {
        String today = getCurrentDateString();

        if (lastClaimDate != null && lastClaimDate.equals(today)) {
            // Already claimed today
            hasClaimed = true;
            btnDailyBonus.setEnabled(false);
            btnDailyBonus.setText("✓ Claimed Today");

            // Calculate time until next claim
            Calendar nextMidnight = Calendar.getInstance();
            nextMidnight.add(Calendar.DAY_OF_YEAR, 1);
            nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
            nextMidnight.set(Calendar.MINUTE, 0);
            nextMidnight.set(Calendar.SECOND, 0);
            nextMidnight.set(Calendar.MILLISECOND, 0);

            long timeUntilNext = nextMidnight.getTimeInMillis() - System.currentTimeMillis();
            String timeLeft = formatTimeLeft(timeUntilNext);
            dailyBonusStatusText.setText("Next bonus in " + timeLeft);

        } else {
            // Can claim today
            hasClaimed = false;
            btnDailyBonus.setEnabled(true);
            btnDailyBonus.setText("🎁 Claim " + DAILY_BONUS_COINS + " Coins");
            dailyBonusStatusText.setText("Available now!");
        }
    }

    private void claimDailyBonus() {
        if (hasClaimed) {
            Toast.makeText(this, "You've already claimed your daily bonus today!", Toast.LENGTH_SHORT).show();
            return;
        }

        String today = getCurrentDateString();

        // Disable button to prevent multiple claims
        btnDailyBonus.setEnabled(false);
        btnDailyBonus.setText("Claiming...");

        // Update user document with new coins and claim date
        DocumentReference userDoc = firestore.collection("users").document(userId);

        firestore.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(userDoc);

            // Get current coins
            Long currentCoinsLong = snapshot.getLong("coins");
            long coins = currentCoinsLong != null ? currentCoinsLong : 0;

            // Add daily bonus
            long newCoins = coins + DAILY_BONUS_COINS;

            // Update document
            Map<String, Object> updates = new HashMap<>();
            updates.put("coins", newCoins);
            updates.put("lastDailyBonus", today);

            transaction.update(userDoc, updates);
            return newCoins;

        }).addOnSuccessListener(newCoins -> {
            // Update local variables
            currentCoins = newCoins;
            lastClaimDate = today;
            hasClaimed = true;

            // Update UI
            animateCoinsUpdate((int) currentCoins);
            updateDailyBonusUI();

            // Show success message
            Toast.makeText(this, "Daily bonus claimed! +" + DAILY_BONUS_COINS + " coins earned!",
                    Toast.LENGTH_LONG).show();

            // Celebration animation
            celebrateBonusEarned();

        }).addOnFailureListener(e -> {
            // Re-enable button on failure
            btnDailyBonus.setEnabled(true);
            btnDailyBonus.setText("🎁 Claim " + DAILY_BONUS_COINS + " Coins");
            Toast.makeText(this, "Failed to claim daily bonus. Please try again.", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupDailyBonusTimer() {
        dailyBonusHandler = new Handler();
        dailyBonusRunnable = new Runnable() {
            @Override
            public void run() {
                if (hasClaimed) {
                    // Update countdown timer
                    Calendar nextMidnight = Calendar.getInstance();
                    nextMidnight.add(Calendar.DAY_OF_YEAR, 1);
                    nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
                    nextMidnight.set(Calendar.MINUTE, 0);
                    nextMidnight.set(Calendar.SECOND, 0);
                    nextMidnight.set(Calendar.MILLISECOND, 0);

                    long timeUntilNext = nextMidnight.getTimeInMillis() - System.currentTimeMillis();

                    if (timeUntilNext <= 0) {
                        // New day started, allow claiming again
                        hasClaimed = false;
                        updateDailyBonusUI();
                    } else {
                        String timeLeft = formatTimeLeft(timeUntilNext);
                        dailyBonusStatusText.setText("Next bonus in " + timeLeft);
                    }
                }

                // Schedule next update
                dailyBonusHandler.postDelayed(this, 1000); // Update every second
            }
        };

        // Start the timer
        dailyBonusHandler.post(dailyBonusRunnable);
    }

    private String formatTimeLeft(long milliseconds) {
        long hours = TimeUnit.MILLISECONDS.toHours(milliseconds);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format(Locale.getDefault(), "%dm %ds", minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%ds", seconds);
        }
    }

    private String getCurrentDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    private void celebrateBonusEarned() {
        // Bounce animation for daily bonus button
        ObjectAnimator bounceX = ObjectAnimator.ofFloat(btnDailyBonus, "scaleX", 1f, 1.3f, 1f);
        ObjectAnimator bounceY = ObjectAnimator.ofFloat(btnDailyBonus, "scaleY", 1f, 1.3f, 1f);

        bounceX.setDuration(500);
        bounceY.setDuration(500);

        bounceX.start();
        bounceY.start();
    }

    // Stats Methods
    private void loadUserStats() {
        DocumentReference userDoc = firestore.collection("users").document(userId);
        userDoc.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String lastUpdate = documentSnapshot.getString("lastStatsUpdate");
                String today = getCurrentDateString();

                if (!today.equals(lastUpdate)) {
                    // New day, reset daily stats
                    resetDailyStats();
                } else {
                    // Same day, load existing stats
                    Long adsWatchedLong = documentSnapshot.getLong("adsWatchedToday");
                    Long coinsEarnedLong = documentSnapshot.getLong("coinsEarnedToday");
                    Long totalAdsLong = documentSnapshot.getLong("totalAdsWatched");

                    todayAdsWatched = adsWatchedLong != null ? adsWatchedLong.intValue() : 0;
                    todayCoinsEarned = coinsEarnedLong != null ? coinsEarnedLong.intValue() : 0;
                    totalAdsCount = totalAdsLong != null ? totalAdsLong.intValue() : 0;

                    updateStatsDisplay();
                }
            }
        }).addOnFailureListener(e -> {
            // Error loading stats, use default values
            updateStatsDisplay();
        });
    }

    private void resetDailyStats() {
        String today = getCurrentDateString();

        Map<String, Object> updates = new HashMap<>();
        updates.put("adsWatchedToday", 0);
        updates.put("coinsEarnedToday", 0);
        updates.put("lastStatsUpdate", today);

        firestore.collection("users").document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    todayAdsWatched = 0;
                    todayCoinsEarned = 0;
                    updateStatsDisplay();
                });
    }

    private void updateStatsDisplay() {
        if (adsWatchedToday != null) {
            adsWatchedToday.setText(String.valueOf(todayAdsWatched));
        }
        if (coinsEarnedToday != null) {
            coinsEarnedToday.setText(String.valueOf(todayCoinsEarned));
        }
        if (totalAdsWatched != null) {
            totalAdsWatched.setText(String.valueOf(totalAdsCount));
        }
    }

    private void updateUserStats(int coinsEarned, boolean isFromAd) {
        Map<String, Object> updates = new HashMap<>();

        if (isFromAd) {
            todayAdsWatched++;
            totalAdsCount++;
            updates.put("adsWatchedToday", todayAdsWatched);
            updates.put("totalAdsWatched", totalAdsCount);
        }

        todayCoinsEarned += coinsEarned;
        updates.put("coinsEarnedToday", todayCoinsEarned);
        updates.put("lastStatsUpdate", getCurrentDateString());

        firestore.collection("users").document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> updateStatsDisplay());
    }

    private void addCoinsToUser(int coinsToAdd, boolean isFromAd) {
        DocumentReference userDoc = firestore.collection("users").document(userId);

        firestore.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(userDoc);
            Long currentCoinsLong = snapshot.getLong("coins");
            long coins = currentCoinsLong != null ? currentCoinsLong : 0;

            long newCoins = coins + coinsToAdd;
            transaction.update(userDoc, "coins", newCoins);
            return newCoins;

        }).addOnSuccessListener(newCoins -> {
            currentCoins = newCoins;

            // Hide progress bar
            adProgressBar.setVisibility(View.GONE);

            // Update UI
            animateCoinsUpdate((int) currentCoins);
            updateStatusText("Reward earned! +" + coinsToAdd + " coins");

            // Update stats
            updateUserStats(coinsToAdd, isFromAd);

            // Show success message
            Toast.makeText(this, "Congratulations! +" + coinsToAdd + " coins earned! Total: " + currentCoins,
                    Toast.LENGTH_LONG).show();

            // Celebration animation
            celebrateCoinsEarned();

        }).addOnFailureListener(e -> {
            // Hide progress bar on failure
            adProgressBar.setVisibility(View.GONE);
            updateStatusText("Failed to add coins. Please try again.");
            Toast.makeText(this, "Failed to add coins. Please try again.", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadCurrentCoins() {
        DocumentReference userDoc = firestore.collection("users").document(userId);
        userDoc.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Long coinsLong = documentSnapshot.getLong("coins");
                currentCoins = coinsLong != null ? coinsLong : 0;
                animateCoinsUpdate((int) currentCoins);
            } else {
                currentCoins = 0;
                animateCoinsUpdate(0);
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load coins", Toast.LENGTH_SHORT).show();
            updateStatusText("Failed to load coins from database");
        });
    }

    private void animateCoinsUpdate(int coins) {
        ValueAnimator animator = ValueAnimator.ofInt(0, coins);
        animator.setDuration(1000);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            currentCoinsText.setText("Current Coins: " + value);
        });
        animator.start();
    }

    private void celebrateCoinsEarned() {
        // Bounce animation for coins card
        ObjectAnimator bounceX = ObjectAnimator.ofFloat(coinsCard, "scaleX", 1f, 1.2f, 1f);
        ObjectAnimator bounceY = ObjectAnimator.ofFloat(coinsCard, "scaleY", 1f, 1.2f, 1f);

        bounceX.setDuration(600);
        bounceY.setDuration(600);

        bounceX.start();
        bounceY.start();

        // Optional: Add particle effect or color animation here
    }

    private void updateStatusText(String message) {
        if (statusText != null) {
            statusText.setText(message);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh coins and daily bonus status when returning to activity
        loadCurrentCoins();
        loadDailyBonusStatus();
        loadUserStats();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up timer
        if (dailyBonusHandler != null && dailyBonusRunnable != null) {
            dailyBonusHandler.removeCallbacks(dailyBonusRunnable);
        }
    }

    @Override
    public void onBackPressed() {
        // Allow back press since we're not simulating ads anymore
        super.onBackPressed();
    }
}