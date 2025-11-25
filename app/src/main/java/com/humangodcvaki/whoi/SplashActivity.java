package com.humangodcvaki.whoi;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private ImageView foxLogo;
    private TextView companyName;
    private TextView tagline;
    private TextView presents;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize views
        foxLogo = findViewById(R.id.fox_logo);
        companyName = findViewById(R.id.company_name);
        tagline = findViewById(R.id.tagline);
        presents = findViewById(R.id.presents);

        // Start the animation sequence
        startSplashAnimation();

        // Delay navigation until animation ends
        new Handler().postDelayed(() -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                // User already signed in → go to Dashboard
                startActivity(new Intent(SplashActivity.this, DashboardActivity.class));
            } else {
                // No user → go to Login (MainActivity)
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            }
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 5500); // Match your animation duration
    }

    private void startSplashAnimation() {
        // Phase 1: Logo appears and scales in
        ObjectAnimator logoFadeIn = ObjectAnimator.ofFloat(foxLogo, "alpha", 0f, 1f);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(foxLogo, "scaleX", 0.5f, 1f);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(foxLogo, "scaleY", 0.5f, 1f);
        ObjectAnimator logoRotation = ObjectAnimator.ofFloat(foxLogo, "rotation", -10f, 0f);

        AnimatorSet logoAppear = new AnimatorSet();
        logoAppear.playTogether(logoFadeIn, logoScaleX, logoScaleY, logoRotation);
        logoAppear.setDuration(1000);
        logoAppear.setStartDelay(500);
        logoAppear.setInterpolator(new AccelerateDecelerateInterpolator());

        // Phase 2: Fox "cry" animation
        AnimatorSet foxCry = createFoxCryAnimation();
        foxCry.setStartDelay(1500);

        // Phase 3: Company name appears
        ObjectAnimator nameTranslation = ObjectAnimator.ofFloat(companyName, "translationY", 100f, 0f);
        ObjectAnimator nameFadeIn = ObjectAnimator.ofFloat(companyName, "alpha", 0f, 1f);

        AnimatorSet nameAppear = new AnimatorSet();
        nameAppear.playTogether(nameTranslation, nameFadeIn);
        nameAppear.setDuration(800);
        nameAppear.setStartDelay(3500);
        nameAppear.setInterpolator(new AccelerateDecelerateInterpolator());

        // Phase 4: Tagline appears
        ObjectAnimator taglineTranslation = ObjectAnimator.ofFloat(tagline, "translationY", 80f, 0f);
        ObjectAnimator taglineFadeIn = ObjectAnimator.ofFloat(tagline, "alpha", 0f, 1f);

        AnimatorSet taglineAppear = new AnimatorSet();
        taglineAppear.playTogether(taglineTranslation, taglineFadeIn);
        taglineAppear.setDuration(800);
        taglineAppear.setStartDelay(4000);
        taglineAppear.setInterpolator(new AccelerateDecelerateInterpolator());

        // Phase 5: "Presents" appears
        ObjectAnimator presentsTranslation = ObjectAnimator.ofFloat(presents, "translationY", 60f, 0f);
        ObjectAnimator presentsFadeIn = ObjectAnimator.ofFloat(presents, "alpha", 0f, 1f);

        AnimatorSet presentsAppear = new AnimatorSet();
        presentsAppear.playTogether(presentsTranslation, presentsFadeIn);
        presentsAppear.setDuration(600);
        presentsAppear.setStartDelay(4500);
        presentsAppear.setInterpolator(new AccelerateDecelerateInterpolator());

        // Start all animations
        logoAppear.start();
        foxCry.start();
        nameAppear.start();
        taglineAppear.start();
        presentsAppear.start();
    }

    private AnimatorSet createFoxCryAnimation() {
        ObjectAnimator cryScale = ObjectAnimator.ofFloat(foxLogo, "scaleX", 1f, 1.05f, 0.98f, 1.02f, 1f);
        cryScale.setDuration(2000);
        cryScale.setInterpolator(new BounceInterpolator());

        ObjectAnimator cryRotate = ObjectAnimator.ofFloat(foxLogo, "rotation", 0f, 2f, -1f, 1f, 0f);
        cryRotate.setDuration(2000);
        cryRotate.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet crySet = new AnimatorSet();
        crySet.playTogether(cryScale, cryRotate);
        return crySet;
    }
}
