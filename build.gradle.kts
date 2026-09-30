plugins {
    id("com.android.application") version "9.1.1" apply false
    kotlin("jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
// Rebuildable files stay outside synchronized source trees when DEV_TEMP_BASE is set.
allprojects {
    System.getenv("DEV_TEMP_BASE")?.let { base ->
        layout.buildDirectory.set(file("$base/build/projects/open-mobifitness/outputs/${project.name}"))
    }
}
