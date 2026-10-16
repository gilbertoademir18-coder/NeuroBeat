import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/*
 * A assinatura do app, a mesma nas duas máquinas.
 *
 * O Android só atualiza um app com a mesma assinatura. A chave de debug é
 * por máquina (~/.android/debug.keystore), então um APK do overdrive e um do
 * notebook não se atualizavam: tinha que desinstalar, e isso apaga os dados
 * do app no celular. Por isso existe uma chave do NeuroBeat, fora do git, na
 * pasta ~/.android de cada máquina:
 *
 *   neurobeat.jks                     a chave
 *   neurobeat-assinatura.properties   arquivo=, alias=, senha=
 *
 * Sem esses arquivos (um clone novo), o build não quebra: cai na chave de
 * debug da máquina e avisa.
 */
val pastaAndroid = File(System.getProperty("user.home"), ".android")
val assinatura = File(pastaAndroid, "neurobeat-assinatura.properties").takeIf { it.exists() }
    ?.let { arquivo -> Properties().apply { arquivo.inputStream().use { load(it) } } }
if (assinatura == null) {
    logger.warn("NeuroBeat: sem ~/.android/neurobeat-assinatura.properties; usando a chave de debug desta máquina.")
}

android {
    namespace = "com.gills87.neurobeat"
    // O Compose 1.12 exige compilar contra a 37. Só afeta a compilação:
    // quem decide o comportamento no aparelho é o targetSdk.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gills87.neurobeat"
        // Android 13: a permissão READ_MEDIA_AUDIO nasce aqui.
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (assinatura != null) {
            create("neurobeat") {
                storeFile = File(pastaAndroid, assinatura.getProperty("arquivo"))
                storePassword = assinatura.getProperty("senha")
                keyAlias = assinatura.getProperty("alias")
                keyPassword = assinatura.getProperty("senha")
            }
        }
    }

    buildTypes {
        // Debug e release com a mesma chave: dá para trocar um pelo outro no
        // celular sem desinstalar.
        val chave = if (assinatura != null) signingConfigs.getByName("neurobeat") else null
        getByName("debug") {
            if (chave != null) signingConfig = chave
        }
        getByName("release") {
            signingConfig = chave ?: signingConfigs.getByName("debug")
            // Sem R8 por enquanto: o app é pequeno, e encolher o código é mais
            // uma coisa que pode quebrar (reflexão do Media3) sem ganho real.
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
}
