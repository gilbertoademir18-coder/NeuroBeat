// O AGP 9 já traz o Kotlin embutido: não aplicar `org.jetbrains.kotlin.android`.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
