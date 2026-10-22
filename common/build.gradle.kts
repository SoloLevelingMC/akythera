plugins {
    id("multiloader-base")
    // SoloLevelingMC fork: the common sources compile against the same Minecraft jar Fabric Loom gives
    // ":fabric", with the same access widener. ModDevGradle and NeoForm (which decompiled and recompiled
    // Minecraft on every fresh build) went away with the NeoForge module (see README_FORK.md).
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.mcSafeResources)
}

configureBaseArchive("Common")

loom {
    accessWidenerPath = file("src/main/resources/akythera.accesswidener")
}

dependencies {
    minecraft(libs.fabric.minecraft)
    // Only for the @Environment annotations the Minecraft jar carries; the common code calls no Fabric API.
    compileOnly(libs.fabric.loader)

    compileOnly(libs.mixin)
    compileOnly(libs.asm.tree) // Manual import for implementations of IMixinConfigPlugin
    compileOnly(libs.forgeconfigapiport.common)

    api(libs.playerAnimationLibrary)
    api(libs.bendableCuboids)
}

mcSafeResources {
    namespace.set(modId)
    outputPackage.set("com.yesman.${modId}.generated")
}

// TODO: temporarily disabled old code, remove when the job finished
sourceSets.main.get().java {
    exclude("yesman/epicfight/**")
    exclude("ongoing/yesman/epicfight/**")
}

java.sourceSets.main.get().java.srcDirs(
    tasks.generateLangKeys.map { it.outputs.files.singleFile },
    tasks.generateSoundKeys.map { it.outputs.files.singleFile }
)

tasks.compileJava { dependsOn(tasks.generateLangKeys, tasks.generateSoundKeys) }
