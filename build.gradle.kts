plugins {
    // Fabric Loom is applied in both the ":common" and ":fabric" projects,
    // so it is declared at the root to ensure the plugin is resolved only once
    // and shared consistently across Gradle projects.
    alias(libs.plugins.fabric.loom) apply false

    // "multiloader-platform" bundles the "com.vanniktech.maven.publish" plugin, whose shared
    // build service requires a single classloader copy across the Gradle projects.
    id("multiloader-platform") apply false
}
