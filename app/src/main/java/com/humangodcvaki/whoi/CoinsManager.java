package com.humangodcvaki.whoi;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

public class CoinsManager {

    public static final long CHAT_COST = 50; // Cost per chat
    private final Context context;
    private final FirebaseFirestore firestore;
    private final String userId;

    public CoinsManager(Context context, FirebaseFirestore firestore, String userId) {
        this.context = context;
        this.firestore = firestore;
        this.userId = userId;
    }

    public interface CoinCheckListener {
        void onSufficientCoins(long currentCoins);
        void onInsufficientCoins(long currentCoins);
        void onError(String message);
    }

    public interface CoinDeductionListener {
        void onDeductionSuccess(long newCoinBalance);
        void onDeductionFailure(String message);
    }

    /** Check if the user has enough coins */
    public void checkUserCoins(final CoinCheckListener listener) {
        if (context == null || userId == null) {
            listener.onError("Invalid context or user ID");
            return;
        }

        firestore.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Long coins = documentSnapshot.getLong("coins");
                        if (coins != null) {
                            if (coins >= CHAT_COST) {
                                listener.onSufficientCoins(coins);
                            } else {
                                listener.onInsufficientCoins(coins);
                            }
                        } else {
                            // Initialize coins to 0 if not found
                            listener.onInsufficientCoins(0L);
                        }
                    } else {
                        listener.onError("User data not found.");
                    }
                })
                .addOnFailureListener(e -> {
                    if (listener != null) {
                        listener.onError("Failed to fetch coins: " + e.getMessage());
                    }
                });
    }

    /** Deduct coins using a transaction */
    public void deductCoinsForChat(final CoinDeductionListener listener) {
        if (userId == null || listener == null) {
            if (listener != null) {
                listener.onDeductionFailure("Invalid user ID");
            }
            return;
        }

        DocumentReference userRef = firestore.collection("users").document(userId);

        firestore.runTransaction((Transaction.Function<Long>) transaction -> {
                    Long coins = transaction.get(userRef).getLong("coins");
                    if (coins == null || coins < CHAT_COST) {
                        throw new FirebaseFirestoreException(
                                "Insufficient coins to start chat.",
                                FirebaseFirestoreException.Code.ABORTED
                        );
                    }
                    long newBalance = coins - CHAT_COST;
                    transaction.update(userRef, "coins", newBalance);
                    return newBalance;
                })
                .addOnSuccessListener(listener::onDeductionSuccess)
                .addOnFailureListener(e -> listener.onDeductionFailure(e.getMessage()));
    }

    /** Show confirmation dialog before deducting coins */
    public void showCoinConfirmationDialog(long currentCoins, Runnable onConfirm, Runnable onCancel) {
        if (context == null || (context instanceof Activity && ((Activity) context).isFinishing())) {
            return;
        }

        try {
            new AlertDialog.Builder(context)
                    .setTitle("Start Chat?")
                    .setMessage("Starting a chat will cost " + CHAT_COST + " coins.\nYour balance: " + currentCoins)
                    .setPositiveButton("Yes", (dialog, which) -> {
                        if (onConfirm != null) {
                            onConfirm.run();
                        }
                    })
                    .setNegativeButton("No", (dialog, which) -> {
                        if (onCancel != null) {
                            onCancel.run();
                        }
                    })
                    .setCancelable(false)
                    .show();
        } catch (Exception e) {
            Toast.makeText(context, "Error showing dialog", Toast.LENGTH_SHORT).show();
        }
    }

    /** Show insufficient coins dialog */
    public void showInsufficientCoinsDialog(long currentCoins, Runnable onWatchAd, Runnable onBuyCoins, Runnable onCancel) {
        if (context == null || (context instanceof Activity && ((Activity) context).isFinishing())) {
            return;
        }

        try {
            new AlertDialog.Builder(context)
                    .setTitle("Not Enough Coins")
                    .setMessage("You have only " + currentCoins + " coins.\nYou need at least " + CHAT_COST + " coins to start a chat.")
                    .setPositiveButton("Watch Ad", (dialog, which) -> {
                        if (onWatchAd != null) {
                            onWatchAd.run();
                        }
                    })
                    .setNegativeButton("Buy Coins", (dialog, which) -> {
                        if (onBuyCoins != null) {
                            onBuyCoins.run();
                        }
                    })
                    .setNeutralButton("Cancel", (dialog, which) -> {
                        if (onCancel != null) {
                            onCancel.run();
                        }
                    })
                    .setCancelable(false)
                    .show();
        } catch (Exception e) {
            Toast.makeText(context, "Error showing dialog", Toast.LENGTH_SHORT).show();
        }
    }

    /** Navigate to AdsActivity */
    public void navigateToAdsActivity() {
        if (context == null) {
            return;
        }

        try {
            Intent intent = new Intent(context, AdsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            // Don't finish the activity immediately - let the user navigate back if needed
            // Only finish if explicitly needed for your app flow
        } catch (Exception e) {
            Toast.makeText(context, "Error opening ads", Toast.LENGTH_SHORT).show();
        }
    }

    /** Navigate to BuyActivity */
    public void navigateToBuyActivity() {
        if (context == null) {
            return;
        }

        try {
            Intent intent = new Intent(context, BuyActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            // Don't finish the activity immediately - let the user navigate back if needed
            // Only finish if explicitly needed for your app flow
        } catch (Exception e) {
            Toast.makeText(context, "Error opening buy coins", Toast.LENGTH_SHORT).show();
        }
    }

    /** Navigate to BuyActivity and finish current activity (use this if you want the old behavior) */
    public void navigateToBuyActivityAndFinish() {
        if (context == null) {
            return;
        }

        try {
            Intent intent = new Intent(context, BuyActivity.class);
            context.startActivity(intent);

            // Add a small delay before finishing to ensure the new activity starts properly
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                if (!activity.isFinishing()) {
                    activity.finish();
                }
            }
        } catch (Exception e) {
            Toast.makeText(context, "Error opening buy coins", Toast.LENGTH_SHORT).show();
        }
    }

    /** Navigate to AdsActivity and finish current activity (use this if you want the old behavior) */
    public void navigateToAdsActivityAndFinish() {
        if (context == null) {
            return;
        }

        try {
            Intent intent = new Intent(context, AdsActivity.class);
            context.startActivity(intent);

            // Add a small delay before finishing to ensure the new activity starts properly
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                if (!activity.isFinishing()) {
                    activity.finish();
                }
            }
        } catch (Exception e) {
            Toast.makeText(context, "Error opening ads", Toast.LENGTH_SHORT).show();
        }
    }
}