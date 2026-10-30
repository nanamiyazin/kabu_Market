plugins {
    java
}

group = "dev.kabumarket"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    // Paper 26.1.2 の API（ビルド番号は最新の stable に合わせて変更可）
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    // Vault API（経済プラグイン連携）。古い Bukkit を引き込まないよう依存は無効化
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        isTransitive = false
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}
