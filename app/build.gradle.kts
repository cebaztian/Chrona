import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

abstract class CheckNoInternetPermission : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun check() {
        val manifest = mergedManifest.get().asFile
        logger.lifecycle("checkNoInternetPermission: reading ${manifest.path}")
        if ("android.permission.INTERNET" in manifest.readText()) {
            throw GradleException("The release manifest declares android.permission.INTERNET (ADR-002).")
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        tasks.register<CheckNoInternetPermission>("checkNoInternetPermission") {
            group = "verification"
            description = "Fails if the merged release manifest declares android.permission.INTERNET."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
    }
}

tasks.named("check") {
    dependsOn("checkNoInternetPermission")
}

android {
    namespace = "com.nutabe.chrona"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nutabe.chrona"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
