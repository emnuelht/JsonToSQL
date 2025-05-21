plugins {
    application
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.0")
    implementation("com.github.emnuelht:compare-json:v1.0.3")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "create.ddl.Main"
}
