import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileOutputStream
import java.security.MessageDigest

plugins {
  id("com.android.application") version "8.13.2"
}

dependencies {
  // Following versions of androidx.window require sdk version 23
  implementation("androidx.window:window-java:1.4.0")
  implementation("androidx.core:core:1.16.0") // Version 1.17.0 available with sdk 36
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20240303")
  testImplementation("org.robolectric:robolectric:4.15.1")
}

android {
  buildFeatures {
    buildConfig = true
  }

  namespace = "juloo.keyboard2"
  compileSdkVersion = "android-36"

  defaultConfig {
    applicationId = "dev.frankenkey.keyboard"
    minSdk = 21
    targetSdk { version = release(36) }
    versionCode = 91
    versionName = "2.0.40"
  }

  sourceSets {
    named("main") {
      manifest.srcFile("AndroidManifest.xml")
      java.srcDirs("srcs/juloo.keyboard2", "vendor/cdict/java/juloo.cdict")
      res.srcDirs("res", "build/generated-resources")
      assets.srcDirs("assets")
    }

    named("test") {
      java.srcDirs("test")
    }
  }


  externalNativeBuild {
    ndkBuild {
      path = file("vendor/Android.mk")
    }
  }

  signingConfigs {
    // Debug builds will always be signed. If no environment variables are set, a default
    // keystore will be initialized by the task initDebugKeystore and used. This keystore
    // can be uploaded to GitHub secrets by following instructions in CONTRIBUTING.md
    // in order to always receive correctly signed debug APKs from the CI.
    named("debug") {
      storeFile = file(System.getenv("DEBUG_KEYSTORE") ?: "debug.keystore")
      storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "debug0"
      keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "debug"
      keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "debug0"
    }

    create("release") {
      val ks = System.getenv("RELEASE_KEYSTORE")
      if (ks != null) {
        storeFile = file(ks)
        storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("RELEASE_KEY_ALIAS")
        keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    named("release") {
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro")
      isShrinkResources = true
      isDebuggable = false
      resValue("string", "app_name", "FrankenKey")
      signingConfig = signingConfigs["release"]
    }

    named("debug") {
      isMinifyEnabled = false
      isShrinkResources = false
      isDebuggable = true
      applicationIdSuffix = ".debug"
      resValue("string", "app_name", "FrankenKey (Debug)")
      resValue("bool", "debug_logs", "true")
      signingConfig = signingConfigs["debug"]
    }
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}


// This raises an error with an informative message instead of the confusing
// ndk-build errors that occur when submodules are not initialized.
gradle.projectsEvaluated {
  if (!file("vendor/cdict/java").exists())
    throw GradleException("Git submodules not initialized. Run 'git submodule update --init'")
}

val buildKeyboardFont by tasks.registering(Exec::class) {
  val `in` = projectDir.resolve("srcs/special_font")
  val out = layout.projectDirectory.file("assets/special_font.ttf")
  inputs.dir(`in`)
  outputs.file(out)
  doFirst { println("\nBuilding assets/special_font.ttf") }
  workingDir = `in`
  val svgFiles = `in`.listFiles()!!.filter {
    it.isFile && it.name.endsWith(".svg")
  }.toTypedArray()
  commandLine("fontforge", "-lang=ff", "-script", "build.pe", out.asFile.absolutePath, *svgFiles)
}

val genEmojis by tasks.registering(Exec::class) {
  doFirst { println("\nGenerating res/raw/emojis.txt") }
  workingDir = projectDir
  commandLine("python", "gen_emoji.py")
}

val genLayoutsList by tasks.registering(Exec::class) {
  inputs.dir(projectDir.resolve("srcs/layouts"))
  outputs.file(projectDir.resolve("res/values/layouts.xml"))
  doFirst { println("\nGenerating res/values/layouts.xml") }
  workingDir = projectDir
  commandLine("python", "gen_layouts.py")
}

val genMethodXml by tasks.registering(Exec::class) {
  val out = projectDir.resolve("res/xml/method.xml")
  inputs.file(projectDir.resolve("gen_method_xml.py"))
  inputs.file(projectDir.resolve("res/values/dictionaries.xml"))
  outputs.file(out)
  doFirst { println("\nGenerating res/xml/method.xml") }
  doFirst { standardOutput = FileOutputStream(out) }
  workingDir = projectDir
  commandLine("python", "gen_method_xml.py")
}

val checkKeyboardLayouts by tasks.registering(Exec::class) {
  inputs.dir(projectDir.resolve("srcs/layouts"))
  inputs.file(projectDir.resolve("srcs/juloo.keyboard2/KeyValue.java"))
  outputs.file(projectDir.resolve("check_layout.output"))
  doFirst { println("\nChecking layouts") }
  workingDir = projectDir
  commandLine("python", "check_layout.py")
}

val compileComposeSequences by tasks.registering(Exec::class) {
  val `in` = projectDir.resolve("srcs/compose")
  val out = projectDir.resolve("srcs/juloo.keyboard2/ComposeKeyData.java")
  inputs.dir(`in`)
  outputs.file(out)
  doFirst { println("\nGenerating $out") }
  val sequences = `in`.listFiles { it: File ->
    !it.name.endsWith(".py") && !it.name.endsWith(".md")
  }!!.map { it.absolutePath }.toTypedArray()
  workingDir = projectDir
  commandLine("python", `in`.resolve("compile.py").absolutePath, *sequences)
  doFirst { standardOutput = FileOutputStream(out) }
}

tasks.withType(Test::class).configureEach {
  dependsOn(genLayoutsList, checkKeyboardLayouts, compileComposeSequences, genMethodXml)
}

val initDebugKeystore by tasks.registering(Exec::class) {
  doFirst { println("Initializing default debug keystore") }
  isEnabled = !file("debug.keystore").exists()
  // A shell script might be needed if this line requires input from the user
  commandLine("keytool", "-genkeypair", "-dname", "cn=d, ou=e, o=b, c=ug", "-alias", "debug", "-keypass", "debug0", "-keystore", "debug.keystore", "-keyalg", "rsa", "-storepass", "debug0", "-validity", "10000")
}

// latn_qwerty_us is used as a raw resource by the custom layout option.
val copyRawQwertyUS by tasks.registering(Copy::class) {
  from("srcs/layouts/latn_qwerty_us.xml")
  into("build/generated-resources/raw")
}

val copyLayoutDefinitions by tasks.registering(Copy::class) {
  from("srcs/layouts")
  include("*.xml")
  into("build/generated-resources/xml")
}

tasks.named("preBuild") {
  dependsOn(initDebugKeystore, copyRawQwertyUS, copyLayoutDefinitions)
  // 'mustRunAfter' defines ordering between tasks (which is required by
  // Gradle) but doesn't create a dependency. These rules update files that are
  // checked in the repository that don't need to be updated during regular
  // builds.
  mustRunAfter(genEmojis, genLayoutsList, compileComposeSequences, genMethodXml)
}

val productionIconHashes = mapOf(
  "res/mipmap-mdpi/ic_launcher.png" to
    "00a0c38113a17e7fcc04d9414f9a14f58c62220125db364526365821166593db",
  "res/mipmap-mdpi/ic_launcher_foreground.png" to
    "26b1c594103d708789ba3c859e27a5b4ea1a66291e4d46ee6e9b015f2cfa3dcd",
  "res/mipmap-hdpi/ic_launcher.png" to
    "2a66f5bf89bded3bc233d84e359e8e04e77c6653a26239e819bb40887e310e21",
  "res/mipmap-hdpi/ic_launcher_foreground.png" to
    "e6d5f8cd1b46a4ec6aada52c3c3b67b3c5d3dc1704fcac6701d043f5515d5526",
  "res/mipmap-xhdpi/ic_launcher.png" to
    "c88eedc85104cf40787a1608b9b08c937fae1c877af8156d0273890c7211be10",
  "res/mipmap-xhdpi/ic_launcher_foreground.png" to
    "27ff40b4940a9a1b657f70eb18c75a7ea8bdc4d648c8dcb2070ae6b99d4900a3",
  "res/mipmap-xxhdpi/ic_launcher.png" to
    "605d70c6c5ab1cb27bec0a84440eaafc967a7586a3883fe954ce060421a690b2",
  "res/mipmap-xxhdpi/ic_launcher_foreground.png" to
    "ee3b89b071b24f891816ce71da11bf31fe9290812ac2cfc669ec75264c69a625",
  "res/mipmap-xxxhdpi/ic_launcher.png" to
    "afeed6d8e2acf6e295b00c2852d2cd0f97809d248321e7f2a944cd57df50fa92",
  "res/mipmap-xxxhdpi/ic_launcher_foreground.png" to
    "c124589f6c3514cd9c24568d55bc56eaf1ef8e8bd41aac713f00767dcb5ca8db")

fun sha256(file: File): String =
  MessageDigest.getInstance("SHA-256").digest(file.readBytes())
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

val verifyReleaseIdentity by tasks.registering {
  group = "verification"
  description = "Rejects debug or incorrectly branded APKs from release delivery."
  doLast {
    val overrideApk = providers.gradleProperty("releaseApk").orNull
    val apk = if (overrideApk == null)
      layout.buildDirectory.file(
          "outputs/apk/release/FrankenKey-release.apk").get().asFile
    else
      file(overrideApk)
    if (!apk.isFile)
      throw GradleException("Release APK not found: $apk")
    val aapt2 = android.sdkDirectory.resolve(
        "build-tools/${android.buildToolsVersion}/aapt2")
    if (!aapt2.isFile)
      throw GradleException("aapt2 not found: $aapt2")
    val badging = providers.exec {
      commandLine(aapt2, "dump", "badging", apk)
    }.standardOutput.asText.get()
    if (!badging.contains("package: name='dev.frankenkey.keyboard'") ||
        !badging.contains("application-label:'FrankenKey'") ||
        !Regex("application-icon-\\d+:'[^']+'").containsMatchIn(badging) ||
        badging.contains("application-debuggable") ||
        badging.contains("FrankenKey (Debug)") ||
        badging.contains("name='dev.frankenkey.keyboard.debug'"))
      throw GradleException(
          "Release identity check failed; refusing a debug or incorrectly branded APK.")
    for ((path, expectedHash) in productionIconHashes)
    {
      val icon = file(path)
      if (!icon.isFile || sha256(icon) != expectedHash)
        throw GradleException(
            "Production launcher icon check failed: $path")
    }
  }
}

tasks.configureEach {
  if (name == "assembleRelease")
    finalizedBy(verifyReleaseIdentity)
}
