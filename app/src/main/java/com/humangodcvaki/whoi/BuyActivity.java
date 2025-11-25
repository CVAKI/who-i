package com.humangodcvaki.whoi;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BuyActivity extends AppCompatActivity implements PurchasesUpdatedListener {

    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private String uid;

    private TextView currentCoinsText;
    private Button backToChatBtn;
    private ImageView btnBack;
    private ProgressBar loadingProgress;
    private LinearLayout packagesContainer;

    private BillingClient billingClient;
    private List<ProductDetails> productDetailsList = new ArrayList<>();
    private long currentCoins = 0;
    private static final int CHAT_COST = 50;

    // Must exactly match Play Console product IDs
    private static final List<String> PRODUCT_IDS = Arrays.asList(
            "coins_100",
            "coins_250",
            "coins_500",
            "coins_1000",
            "coins_2500"
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buy);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        uid = user.getUid();

        initViews();
        setupBilling();
        loadUserCoins();
        setupClickListeners();
    }

    private void initViews() {
        currentCoinsText = findViewById(R.id.currentCoinsText);
        backToChatBtn = findViewById(R.id.backToChatBtn);
        btnBack = findViewById(R.id.btnBack);
        loadingProgress = findViewById(R.id.loadingProgress);
        packagesContainer = findViewById(R.id.packagesContainer);

        updateBackToChatButton();
    }

    private void setupBilling() {
        billingClient = BillingClient.newBuilder(this)
                .setListener(this)
                .enablePendingPurchases()
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    loadProductDetails();
                } else {
                    loadingProgress.setVisibility(View.GONE);
                    Toast.makeText(BuyActivity.this, "Failed to connect to billing service", Toast.LENGTH_SHORT).show();
                    showOfflinePackages();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Toast.makeText(BuyActivity.this, "Billing service disconnected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadProductDetails() {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        for (String id : PRODUCT_IDS) {
            products.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build());
        }

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                this.productDetailsList = productDetailsList;
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    createPackageViews();
                });
            } else {
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    showOfflinePackages();
                    Toast.makeText(BuyActivity.this, "Failed to load packages", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void createPackageViews() {
        packagesContainer.removeAllViews();
        for (ProductDetails productDetails : productDetailsList) {
            View packageView = createPackageCard(productDetails);
            packagesContainer.addView(packageView);
        }
    }

    private void showOfflinePackages() {
        packagesContainer.removeAllViews();
        String[] offlinePackages = {
                "1000 Coins|$0.99",
                "2500 Coins|$1.99",
                "5000 Coins|$3.99",
                "10000 Coins|$6.99",
                "25000 Coins|$14.99"
        };
        for (String packageInfo : offlinePackages) {
            String[] parts = packageInfo.split("\\|");
            View packageView = createOfflinePackageCard(parts[0], parts[1]);
            packagesContainer.addView(packageView);
        }
    }

    private View createPackageCard(ProductDetails productDetails) {
        CardView card = new CardView(this);
        card.setCardElevation(8);
        card.setRadius(12);
        card.setUseCompatPadding(true);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        TextView titleText = new TextView(this);
        int coins = getCoinsFromId(productDetails.getProductId());
        titleText.setText(coins + " Coins");
        titleText.setTextSize(20);
        titleText.setTextColor(getResources().getColor(android.R.color.black));
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);

        ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
        String price = offerDetails != null ? offerDetails.getFormattedPrice() : "N/A";
        TextView priceText = new TextView(this);
        priceText.setText(price);
        priceText.setTextSize(16);
        priceText.setTextColor(getResources().getColor(android.R.color.darker_gray));

        Button buyButton = new Button(this);
        buyButton.setText("Purchase");
        buyButton.setOnClickListener(v -> launchPurchaseFlow(productDetails));

        if (coins >= 500) {
            TextView bonusText = new TextView(this);
            bonusText.setText("Best Value!");
            bonusText.setTextSize(12);
            bonusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            bonusText.setTypeface(null, android.graphics.Typeface.BOLD);
            layout.addView(bonusText);
        }

        layout.addView(titleText);
        layout.addView(priceText);
        layout.addView(buyButton);
        card.addView(layout);

        return card;
    }

    private View createOfflinePackageCard(String title, String price) {
        CardView card = new CardView(this);
        card.setCardElevation(8);
        card.setRadius(12);
        card.setUseCompatPadding(true);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        TextView titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextSize(20);
        titleText.setTextColor(getResources().getColor(android.R.color.black));
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView priceText = new TextView(this);
        priceText.setText(price);
        priceText.setTextSize(16);
        priceText.setTextColor(getResources().getColor(android.R.color.darker_gray));

        Button buyButton = new Button(this);
        buyButton.setText("Coming Soon");
        buyButton.setEnabled(false);

        layout.addView(titleText);
        layout.addView(priceText);
        layout.addView(buyButton);
        card.addView(layout);

        return card;
    }

    private int getCoinsFromId(String productId) {
        switch (productId) {
            case "coins_100": return 1000;
            case "coins_250": return 2500;
            case "coins_500": return 5000;
            case "coins_1000": return 10000;
            case "coins_2500": return 25000;
            default: return 0;
        }
    }

    private void launchPurchaseFlow(ProductDetails productDetails) {
        BillingFlowParams.ProductDetailsParams productParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build();

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Arrays.asList(productParams))
                .build();

        BillingResult billingResult = billingClient.launchBillingFlow(this, billingFlowParams);
        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            Toast.makeText(this, "Failed to launch purchase flow", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, "Purchase cancelled", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Purchase failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
            return;
        }

        // Acknowledge purchase if not acknowledged
        if (!purchase.isAcknowledged()) {
            // For consumable products, consume instead of acknowledge
            ConsumeParams consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();

            billingClient.consumeAsync(consumeParams, (billingResult, purchaseToken) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // Get product ID safely
                    List<String> products = purchase.getProducts();
                    if (products != null && !products.isEmpty()) {
                        String productId = products.get(0);
                        int coins = getCoinsFromId(productId);

                        // Update coins in Firestore
                        firestore.collection("users").document(uid)
                                .update("coins", FieldValue.increment(coins))
                                .addOnSuccessListener(aVoid -> {
                                    currentCoins += coins;
                                    runOnUiThread(() -> {
                                        updateUI();
                                        Toast.makeText(this, "+" + coins + " coins added!", Toast.LENGTH_LONG).show();
                                    });
                                })
                                .addOnFailureListener(e -> {
                                    runOnUiThread(() -> {
                                        Toast.makeText(this, "Failed to add coins: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                                });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to consume purchase", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    private void setupClickListeners() {
        // This button should start chatting ONLY when user has enough coins
        backToChatBtn.setOnClickListener(v -> {
            if (currentCoins >= CHAT_COST) {
                startActivity(new Intent(this, ChatActivity.class));
                finish();
            } else {
                Toast.makeText(this, "You need " + (CHAT_COST - currentCoins) + " more coins to start chatting", Toast.LENGTH_SHORT).show();
            }
        });

        // This should go back to previous screen, NOT to chat
        btnBack.setOnClickListener(v -> {
            finish(); // Just close this activity and return to previous screen
        });
    }

    // Override phone's back button to prevent going to chat
    @Override
    public void onBackPressed() {
        finish(); // Just close this activity and return to previous screen
    }

    private void loadUserCoins() {
        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Long coinsLong = doc.getLong("coins");
                        currentCoins = coinsLong != null ? coinsLong : 0;
                        runOnUiThread(this::updateUI);
                    }
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to load coins", Toast.LENGTH_SHORT).show();
                    });
                });
    }

    private void updateUI() {
        currentCoinsText.setText("Current Balance: " + currentCoins + " coins");
        updateBackToChatButton();
    }

    private void updateBackToChatButton() {
        if (currentCoins >= CHAT_COST) {
            backToChatBtn.setText("Start Chatting (" + CHAT_COST + " coins)");
            backToChatBtn.setEnabled(true);
        } else {
            int needed = (int) (CHAT_COST - currentCoins);
            backToChatBtn.setText("Need " + needed + " more coins");
            backToChatBtn.setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (billingClient != null) {
            billingClient.endConnection();
        }
        super.onDestroy();
    }
}