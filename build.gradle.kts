// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false // If you have library modules
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    kotlin("android") version "1.9.22" apply false // Add a version and apply false
    id("com.google.gms.google-services") version "4.4.3" apply false
}
