package com.humangodcvaki.whoi;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GoogleSignIn";
    private static final int RC_SIGN_IN = 1001;

    private GoogleSignInClient googleSignInClient;
    private FirebaseAuth firebaseAuth;

    private Button signInButton, signOutButton;
    private TextView userNameText, userEmailText;
    private ImageView userProfileImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        configureGoogleSignIn();
        firebaseAuth = FirebaseAuth.getInstance();

        signInButton.setOnClickListener(v -> signIn());
        signOutButton.setOnClickListener(v -> signOut());

        checkCurrentUser();
    }

    private void initViews() {
        signInButton = findViewById(R.id.sign_in_button);
        signOutButton = findViewById(R.id.sign_out_button);
        userNameText = findViewById(R.id.user_name);
        userEmailText = findViewById(R.id.user_email);
        userProfileImage = findViewById(R.id.user_profile_image);
    }

    private void configureGoogleSignIn() {
        try {
            String clientId = getString(R.string.default_web_client_id);
            Log.d(TAG, "Client ID: " + clientId);

            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(clientId)
                    .requestEmail()
                    .build();

            googleSignInClient = GoogleSignIn.getClient(this, gso);
            Log.d(TAG, "Google Sign-In configured successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error configuring Google Sign-In", e);
        }
    }

    private void checkCurrentUser() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        updateUI(user);
    }

    private void signIn() {
        Log.d(TAG, "Starting sign-in process");

        // Force showing Gmail account chooser every time
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    private void signOut() {
        firebaseAuth.signOut();
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Toast.makeText(MainActivity.this, "Signed out", Toast.LENGTH_SHORT).show();
            updateUI(null);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Log.d(TAG, "onActivityResult: RC_SIGN_IN");
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.d(TAG, "Sign-in successful, account: " + account.getEmail());
            firebaseAuthWithGoogle(account.getIdToken());
        } catch (ApiException e) {
            Log.e(TAG, "Google sign in failed with error code: " + e.getStatusCode(), e);

            String errorMessage;
            switch (e.getStatusCode()) {
                case 10:
                    errorMessage = "Developer Error: Check SHA-1 fingerprint in Firebase Console";
                    break;
                case 12500:
                    errorMessage = "Sign In Currently Disabled For This App";
                    break;
                case 7:
                    errorMessage = "Network Error: Check internet connection";
                    break;
                default:
                    errorMessage = "Google Sign-In failed: " + e.getStatusCode();
            }

            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
            updateUI(null);
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        Log.d(TAG, "firebaseAuthWithGoogle: " + (idToken != null));
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firebase sign-in success");
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        Toast.makeText(this, "Welcome " + user.getDisplayName(), Toast.LENGTH_SHORT).show();
                        updateUI(user);
                    } else {
                        Log.e(TAG, "Firebase sign-in failed", task.getException());
                        Toast.makeText(this, "Firebase authentication failed", Toast.LENGTH_SHORT).show();
                        updateUI(null);
                    }
                });
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            // Go to DashboardActivity
            Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish(); // Prevent going back to sign-in screen
        } else {
            // Show sign-in UI
            signInButton.setVisibility(View.VISIBLE);
            signOutButton.setVisibility(View.GONE);
            userNameText.setVisibility(View.GONE);
            userEmailText.setVisibility(View.GONE);
            userProfileImage.setVisibility(View.GONE);
        }
    }
}
