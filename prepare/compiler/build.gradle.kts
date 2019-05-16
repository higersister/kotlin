import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import proguard.gradle.ProGuardTask

description = "Kotlin Compiler"

plugins {
    // HACK: java plugin makes idea import dependencies on this project as source (with empty sources however),
    // this prevents reindexing of kotlin-compiler.jar after build on every change in compiler modules
    java
}

// You can run Gradle with "-Pkotlin.build.proguard=true" to enable ProGuard run on kotlin-compiler.jar (on TeamCity, ProGuard always runs)
val shrink = findProperty("kotlin.build.proguard")?.toString()?.toBoolean() ?: hasProperty("teamcity")

val fatJarContents by configurations.creating
val fatJarContentsStripMetadata by configurations.creating
val fatJarContentsStripServices by configurations.creating

// JPS build assumes fat jar is built from embedded configuration,
// but we can't use it in gradle build since slightly more complex processing is required like stripping metadata & services from some jars
if (kotlinBuildProperties.isInJpsBuildIdeaSync) {
    val embedded by configurations
    embedded.apply {
        extendsFrom(fatJarContents)
        extendsFrom(fatJarContentsStripMetadata)
        extendsFrom(fatJarContentsStripServices)
    }
}

val runtimeJar by configurations.creating
val compile by configurations  // maven plugin writes pom compile scope from compile configuration by default
val proguardLibraries by configurations.creating {
    extendsFrom(compile)
}

// Libraries to copy to the lib directory
val libraries by configurations.creating

val default by configurations
default.extendsFrom(runtimeJar)

val compilerBaseName = name

val outputJar = fileFrom(buildDir, "libs", "$compilerBaseName.jar")

val compilerModules: Array<String> by rootProject.extra

dependencies {
    compile(kotlinStdlib())
    compile(project(":kotlin-script-runtime"))
    compile(project(":kotlin-reflect"))
    compile(commonDep("org.jetbrains.intellij.deps", "trove4j"))

    proguardLibraries(project(":kotlin-annotations-jvm"))
    proguardLibraries(
        files(
            firstFromJavaHomeThatExists("jre/lib/rt.jar", "../Classes/classes.jar"),
            firstFromJavaHomeThatExists("jre/lib/jsse.jar", "../Classes/jsse.jar"),
            toolsJar()
        )
    )

    compilerModules.forEach {
        fatJarContents(project(it)) { isTransitive = false }
    }

    libraries(intellijDep()) { includeIntellijCoreJarDependencies(project) { it.startsWith("trove4j") } }

    fatJarContents(kotlinBuiltins())
    fatJarContents(commonDep("javax.inject"))
    fatJarContents(commonDep("org.jline", "jline"))
    fatJarContents(commonDep("org.fusesource.jansi", "jansi"))
    fatJarContents(protobufFull())
    fatJarContents(commonDep("com.google.code.findbugs", "jsr305"))
    fatJarContents(commonDep("io.javaslang", "javaslang"))
    fatJarContents(commonDep("org.jetbrains.kotlinx", "kotlinx-coroutines-core")) { isTransitive = false }

    fatJarContents(intellijCoreDep()) { includeJars("intellij-core", "java-compatibility-1.0.1") }
    fatJarContents(intellijDep()) {
        includeIntellijCoreJarDependencies(project) {
            !(it.startsWith("jdom") || it.startsWith("log4j") || it.startsWith("trove4j"))
        }
    }
    fatJarContents(intellijDep()) { includeJars("jna-platform", "lz4-1.3.0") }

    fatJarContentsStripServices(jpsStandalone()) { includeJars("jps-model") }

    fatJarContentsStripMetadata(intellijDep()) { includeJars("oro-2.0.8", "jdom", "log4j" ) }
}

publish()

noDefaultJar()

val packCompiler by task<ShadowJar> {
    configurations = listOf(fatJarContents)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDir = File(buildDir, "libs")

    setupPublicJar(compilerBaseName, "before-proguard")

    dependsOn(fatJarContentsStripServices)
    from {
        fatJarContentsStripServices.files.map {
            zipTree(it).matching { exclude("META-INF/services/**") }
        }
    }

    dependsOn(fatJarContentsStripMetadata)
    from {
        fatJarContentsStripMetadata.files.map {
            zipTree(it).matching { exclude("META-INF/jb/**", "META-INF/LICENSE") }
        }
    }

    manifest.attributes["Class-Path"] = compilerManifestClassPath
    manifest.attributes["Main-Class"] = "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler"
}

val proguard by task<ProGuardTask> {
    dependsOn(packCompiler)
    configuration("$rootDir/compiler/compiler.pro")

    val outputJar = fileFrom(buildDir, "libs", "$compilerBaseName-after-proguard.jar")

    inputs.files(packCompiler.outputs.files.singleFile)
    outputs.file(outputJar)

    libraryjars(mapOf("filter" to "!META-INF/versions/**"), proguardLibraries)

    printconfiguration("$buildDir/compiler.pro.dump")
}

val pack = if (shrink) proguard else packCompiler

dist(targetName = "$compilerBaseName.jar", fromTask = pack) {
    from(libraries)
}

runtimeJarArtifactBy(pack, pack.outputs.files.singleFile) {
    name = compilerBaseName
    classifier = ""
}

sourcesJar {
    from {
        compilerModules.map {
            project(it).mainSourceSet.allSource
        }
    }
}

javadocJar()
