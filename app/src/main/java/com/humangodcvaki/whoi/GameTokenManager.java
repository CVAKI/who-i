package com.humangodcvaki.whoi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

public class GameTokenManager {

    public static final long GAME_TOKEN_COST_INITIATOR = 2;
    public static final long GAME_TOKEN_COST_ACCEPTOR = 5;
    public static final long WIN_REWARD = 10;

    private final Context context;
    private final FirebaseFirestore firestore;
    private final String userId;

    public GameTokenManager(Context context, FirebaseFirestore firestore, String userId) {
        this.context = context;
        this.firestore = firestore;
        this.userId = userId;
    }

    public interface GameTokenCheckListener {
        void onSufficientTokens(long currentTokens);
        void onInsufficientTokens(long currentTokens);
        void onError(String message);
    }

    public interface GameTokenDeductionListener {
        void onDeductionSuccess(long newTokenBalance);
        void onDeductionFailure(String message);
    }

    /** Check if user has enough game tokens */
    public void checkGameTokens(long requiredTokens, final GameTokenCheckListener listener) {
        if (context == null || userId == null) {
            listener.onError("Invalid context or user ID");
            return;
        }

        firestore.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Long tokens = documentSnapshot.getLong("gameTokens");
                        if (tokens != null) {
                            if (tokens >= requiredTokens) {
                                listener.onSufficientTokens(tokens);
                            } else {
                                listener.onInsufficientTokens(tokens);
                            }
                        } else {
                            // Initialize tokens to 0 if not found
                            listener.onInsufficientTokens(0L);
                        }
                    } else {
                        listener.onError("User data not found.");
                    }
                })
                .addOnFailureListener(e -> {
                    if (listener != null) {
                        listener.onError("Failed to fetch game tokens: " + e.getMessage());
                    }
                });
    }

    /** Deduct game tokens using transaction */
    public void deductGameTokens(long tokenCost, final GameTokenDeductionListener listener) {
        if (userId == null || listener == null) {
            if (listener != null) {
                listener.onDeductionFailure("Invalid user ID");
            }
            return;
        }

        DocumentReference userRef = firestore.collection("users").document(userId);

        firestore.runTransaction((Transaction.Function<Long>) transaction -> {
                    Long tokens = transaction.get(userRef).getLong("gameTokens");
                    if (tokens == null || tokens < tokenCost) {
                        throw new FirebaseFirestoreException(
                                "Insufficient game tokens to play.",
                                FirebaseFirestoreException.Code.ABORTED
                        );
                    }
                    long newBalance = tokens - tokenCost;
                    transaction.update(userRef, "gameTokens", newBalance);
                    return newBalance;
                })
                .addOnSuccessListener(listener::onDeductionSuccess)
                .addOnFailureListener(e -> listener.onDeductionFailure(e.getMessage()));
    }

    /** Award game tokens using transaction */
    public void awardGameTokens(long tokenReward, final GameTokenDeductionListener listener) {
        if (userId == null || listener == null) {
            if (listener != null) {
                listener.onDeductionFailure("Invalid user ID");
            }
            return;
        }

        DocumentReference userRef = firestore.collection("users").document(userId);

        firestore.runTransaction((Transaction.Function<Long>) transaction -> {
                    Long tokens = transaction.get(userRef).getLong("gameTokens");
                    if (tokens == null) tokens = 0L;

                    long newBalance = tokens + tokenReward;
                    transaction.update(userRef, "gameTokens", newBalance);
                    return newBalance;
                })
                .addOnSuccessListener(listener::onDeductionSuccess)
                .addOnFailureListener(e -> listener.onDeductionFailure(e.getMessage()));
    }

    /** Show game invitation confirmation dialog for initiator */
    public void showGameInviteConfirmationDialog(long currentTokens, Runnable onConfirm, Runnable onCancel) {
        if (context == null || (context instanceof Activity && ((Activity) context).isFinishing())) {
            return;
        }

        try {
            new AlertDialog.Builder(context)
                    .setTitle("Invite to Game?")
                    .setMessage("Inviting your partner to play will cost " + GAME_TOKEN_COST_INITIATOR + " game tokens.\nYour balance: " + currentTokens)
                    .setPositiveButton("Send Invite", (dialog, which) -> {
                        if (onConfirm != null) {
                            onConfirm.run();
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
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

    /** Show game acceptance confirmation dialog for acceptor */
    public void showGameAcceptConfirmationDialog(long currentTokens, String partnerName, Runnable onAccept, Runnable onDecline) {
        if (context == null || (context instanceof Activity && ((Activity) context).isFinishing())) {
            return;
        }

        try {
            new AlertDialog.Builder(context)
                    .setTitle("Game Invitation")
                    .setMessage(partnerName + " invited you to play Stone Paper Scissors!\nAccepting will cost " + GAME_TOKEN_COST_ACCEPTOR + " game tokens.\nYour balance: " + currentTokens)
                    .setPositiveButton("Accept", (dialog, which) -> {
                        if (onAccept != null) {
                            onAccept.run();
                        }
                    })
                    .setNegativeButton("Decline", (dialog, which) -> {
                        if (onDecline != null) {
                            onDecline.run();
                        }
                    })
                    .setCancelable(false)
                    .show();
        } catch (Exception e) {
            Toast.makeText(context, "Error showing dialog", Toast.LENGTH_SHORT).show();
        }
    }

    /** Show insufficient game tokens dialog */
    public void showInsufficientGameTokensDialog(long currentTokens, long requiredTokens, Runnable onWatchAd, Runnable onBuyTokens, Runnable onCancel) {
        if (context == null || (context instanceof Activity && ((Activity) context).isFinishing())) {
            return;
        }

        try {
            new AlertDialog.Builder(context)
                    .setTitle("Not Enough Game Tokens")
                    .setMessage("You have only " + currentTokens + " game tokens.\nYou need at least " + requiredTokens + " game tokens to play.")
                    .setPositiveButton("Watch Ad", (dialog, which) -> {
                        if (onWatchAd != null) {
                            onWatchAd.run();
                        }
                    })
                    .setNegativeButton("Buy Tokens", (dialog, which) -> {
                        if (onBuyTokens != null) {
                            onBuyTokens.run();
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

    /** Navigate to AdsActivity for game tokens */
    public void navigateToAdsActivity() {
        if (context == null) {
            return;
        }

        try {
            Intent intent = new Intent(context, AdsActivity.class);
            intent.putExtra("rewardType", "gameTokens");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Error opening ads", Toast.LENGTH_SHORT).show();
        }
    }

    /** Navigate to BuyActivity for game tokens */
    public void navigateToBuyActivity() {
        if (context == null) {
            return;
        }

        try {
            Intent intent = new Intent(context, BuyActivity.class);
            intent.putExtra("purchaseType", "gameTokens");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Error opening buy tokens", Toast.LENGTH_SHORT).show();
        }
    }
}