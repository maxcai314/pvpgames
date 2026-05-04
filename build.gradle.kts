plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
//    id("com.gradleup.shadow") version "9.3.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.onarandombox.com/content/groups/public/")
}

dependencies {
    compileOnly("net.luckperms:api:5.4")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.2")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13") {
        // These transitives clash with newer versions pulled by
        // worldedit-bukkit:7.4.2; we get them from there at compile time
        // and the runtime server provides them anyway.
        exclude(group = "com.google.guava")
        exclude(group = "com.google.code.gson")
        exclude(group = "it.unimi.dsi")
        exclude(group = "com.sk89q.worldedit")
    }
    compileOnly("org.mvplugins.multiverse.core:multiverse-core:5.6.1")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx8G", "-Dcom.mojang.eula.agree=true")

        // Auto-install third-party plugins into the test server's plugins/ folder.
        // Versions match the compileOnly deps above.
        downloadPlugins {
            hangar("WorldEdit", "7.4.2")
            // WorldGuard is not on Hangar; pull from Modrinth instead.
            modrinth("worldguard", "7.0.13")
            hangar("Multiverse-Core", "5.6.1")
        }
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
