import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

/*
 * Copyright (C) 2016, 2022  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

plugins {
    `java-library`
    distribution
    application
    antlr

    idea

    // The following static analysis plugins are currently disabled, since they
    // take too much resources and have only limited benefit.
    //
    // 'checkstyle', 'errorprone', 'pmd'

    `maven-publish`
    signing
    // https://plugins.gradle.org/plugin/io.github.gradle-nexus.publish-plugin
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

group = "de.tum.in"
version = "22.0-development"
val owlMainClass = "owl.command.OwlCommand"

base {
    archivesName.set("owl")
}

java {
    toolchain {
        // JDK 17 is the latest release supported by GraalVM (native-image).
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    withJavadocJar()
    withSourcesJar()
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "owl",
            "Implementation-Version" to project.version,
            "Main-Class" to owlMainClass
        )
    }
}

application {
    mainClass.set(owlMainClass)
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

val os = DefaultNativePlatform.getCurrentOperatingSystem()!!
val buildMarkdown = !os.isWindows && !project.hasProperty("disable-pandoc")
val staticNativeExecutable = project.hasProperty("static-native-executable")
val enableNativeAssertions = project.hasProperty("enable-native-assertions")

repositories {
    mavenCentral()
}

dependencies {
    // https://github.com/google/guava
    implementation("com.google.guava", "guava", "31.1-jre")

    // https://github.com/incaseoftrouble/jbdd
    implementation("de.tum.in", "jbdd", "0.5.2")

    // http://www.antlr.org/
    // https://mvnrepository.com/artifact/org.antlr/antlr4-runtime
    implementation("org.antlr", "antlr4-runtime", "4.9.3")

    // https://www.graalvm.org/
    // https://mvnrepository.com/artifact/org.graalvm.sdk/graal-sdk
    implementation("org.graalvm.sdk", "graal-sdk", "22.1.0")

    // https://github.com/google/gson
    testImplementation("com.google.code.gson", "gson", "2.8.5")

    // https://github.com/junit-team/junit5/
    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.6.2")
    testImplementation("org.junit.jupiter", "junit-jupiter-params", "5.6.2")
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", "5.6.2")

    antlr("org.antlr", "antlr4", "4.9.3")

    // https://mvnrepository.com/artifact/com.google.auto.value/auto-value
    compileOnly("com.google.auto.value", "auto-value-annotations", "1.8.2")
    annotationProcessor("com.google.auto.value", "auto-value", "1.8.2")
}

// Remove jars that are not used during runtime from classpath.
listOf(configurations.runtimeClasspath, configurations.testRuntimeClasspath).forEach {
    it {
        exclude("com.google.guava", "listenablefuture")
        exclude("com.google.j2objc", "j2objc-annotations")
        exclude("org.antlr", "antlr4")
        exclude("org.antlr", "antlr-runtime")
        exclude("org.antlr", "ST4")
        exclude("org.immutables")
    }
}

// ---------------- Testing ----------------

tasks.test {
    useJUnitPlatform {
        excludeTags("size-regression-test", "size-regression-train", "size-report", "performance")
    }
}

tasks.register<Test>("sizeRegressionTest") {
    useJUnitPlatform {
        includeTags("size-regression-test")
    }
}

tasks.register<Test>("sizeRegressionTrain") {
    useJUnitPlatform {
        includeTags("size-regression-train")
    }
}

tasks.register<Test>("sizeReport") {
    useJUnitPlatform {
        includeTags("size-report")
    }
}
tasks.register<Test>("performanceTest") {
    useJUnitPlatform {
        includeTags("performance")
    }
}

tasks.withType<Test> {
    maxHeapSize = "6G"
}

// ---------------- Compilation ----------------

tasks.compileJava {
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:cast",
            "-Xlint:deprecation",
            "-Xlint:divzero",
            "-Xlint:empty",
            "-Xlint:finally",
            "-Xlint:overrides",
            // "-Xlint:processing",
            "-Xlint:try",
            // "-Xlint:unchecked",
            "-Xlint:varargs",
            "-Werror"
        )
    )
}

// ---------------- ANTLR ----------------

val antlrDir = "${project.buildDir}/generated/sources/antlr/"
tasks.generateGrammarSource {
    arguments.addAll(listOf("-visitor", "-long-messages", "-lib", "src/main/antlr"))
    outputDirectory = file("${antlrDir}/java/main/owl/grammar")
}
sourceSets["main"].java { srcDir("${antlrDir}/java/main/") }
tasks.getByPath(":sourcesJar").dependsOn(tasks.generateGrammarSource)

// ---------------- Static Analysis ----------------

//    pmd {
//        toolVersion = "6.45.0" // https://pmd.github.io/
//        reportsDir = file("${project.buildDir}/reports/pmd")
//        ruleSetFiles = files("${project.projectDir}/config/pmd-rules.xml")
//        ruleSets = listOf() // We specify all rules in rules.xml
//        isConsoleOutput = false
//        isIgnoreFailures = true // PMD is broken on github actions
//    }
//
//    tasks.withType<Pmd> {
//        group = "verification"
//
//        reports {
//            xml.required.set(false)
//            html.required.set(true)
//        }
//        excludes.addAll(
//            listOf(
//                "**/generated/**",
//                "**/thirdparty/**"
//            )
//        )
//    }

// ---------------- Native Compilation ----------------

val kissatDir = "${projectDir}/thirdparty/kissat"
val configureKissat = tasks.register<Exec>("configureKissat") {
    group = "native"
    description = "Configure Kissat"

    workingDir(kissatDir)
    commandLine("./configure")

    inputs.files("${kissatDir}/Makefile.in", "${kissatDir}/configure")
    outputs.file("${kissatDir}/build/Makefile")
}

val buildKissat = tasks.register<Exec>("buildKissat") {
    group = "native"
    description = "Build Kissat Binary"
    dependsOn(configureKissat)

    workingDir(kissatDir)
    commandLine("make")

    doLast {
        exec {
            commandLine("${kissatDir}/build/kissat", "--banner")
        }
    }

    outputs.dir("${kissatDir}/build/")
    // outputs.cacheIf { true }
}

tasks.test.configure { dependsOn(buildKissat) }

val buildNativeLibrary = tasks.register<Exec>("buildNativeLibrary") {
    group = "native"
    description = "Compile the native library"
    dependsOn(tasks.jar, buildKissat)

    mkdir("${buildDir}/native-library")
    workingDir("${buildDir}/native-library")

    val command = (System.getenv("GRAAL_HOME")?.let { "${it}/bin/" } ?: "") + "native-image"

    commandLine(
        command,
        "owl.cinterface.CInterface", "owl",
        "-cp", sourceSets["main"].runtimeClasspath.asPath,
        if (enableNativeAssertions) "-ea" else "-da",
        "-DowlInclude=${projectDir}/src/main/c/include",
        "--shared",
        "--initialize-at-build-time=com.google,org.antlr,de.tum,owl",
        "--link-at-build-time=com.google,org.antlr,de.tum,owl",
        "--no-fallback",
        // (uncomment for performance analysis)
        // "-H:+PrintAnalysisCallTree",
        // "-H:DashboardDump=reports/graalvm-dashboard.dump",
        // "-H:+DashboardAll",
        "-H:+ReportExceptionStackTraces",
        // "-H:-UseServiceLoaderFeature",
        "-Djava.lang.Integer.IntegerCache.high=1024" // Cache more boxed integers.
    )

    (System.getenv("CC"))?.let { args("-H:CCompilerPath=${it}") }

    outputs.files(
        file("${project.buildDir}/native-library/graal_isolate.h"),
        file("${project.buildDir}/native-library/graal_isolate_dynamic.h"),
        file("${project.buildDir}/native-library/owl.h"),
        file("${project.buildDir}/native-library/owl_dynamic.h"),
        file("${project.buildDir}/native-library/owl." + (if (os.isMacOsX) "dylib" else "so")),
    )
}

val buildNativeExecutable = tasks.register<Exec>("buildNativeExecutable") {
    group = "native"
    description = "Compile the native executable"
    dependsOn(tasks.jar, buildKissat)

    mkdir("${buildDir}/native-executable")
    workingDir("${buildDir}/native-executable")

    val graalHome = System.getenv("GRAAL_HOME")
    val command = (if (graalHome == null) "" else "$graalHome/bin/") + "native-image"

    // TODO: remove dependency on header files.
    commandLine(
        command,
        "owl",
        "-jar", tasks.jar.get().archiveFile.get(),
        "-cp", sourceSets["main"].runtimeClasspath.asPath,
        if (enableNativeAssertions) "-ea" else "-da",
        "-DowlInclude=${projectDir}/src/main/c/include",
        "--features=owl.command.OwlCommandRuntimeReflectionRegistrationFeature",
        "--initialize-at-build-time=com.google,org.antlr,de.tum,owl",
        "--link-at-build-time=com.google,org.antlr,de.tum,owl",
        "--no-fallback",
        // (uncomment for performance analysis)
        // "-H:+PrintAnalysisCallTree",
        // "-H:DashboardDump=reports/graalvm-dashboard.dump",
        // "-H:+DashboardAll",
        "-H:+ReportExceptionStackTraces",
        "-Djava.lang.Integer.IntegerCache.high=1024" // Cache more boxed integers.
    )

    if (staticNativeExecutable) {
        args("--static", "--libc=musl")
        (System.getenv("MUSL_CC"))?.let { args("-H:CCompilerPath=${it}") }
    } else {
        (System.getenv("CC"))?.let { args("-H:CCompilerPath=${it}") }
    }

    outputs.file("${buildDir}/native-executable/owl")
}

// ---------------- Documentation ----------------

// Compile the markdown files
val compileMarkdown = tasks.register<Exec>("compileMarkdown") {
    group = "documentation"

    executable("scripts/render-markdown.sh")
    outputs.dir("${project.buildDir}/docs/markdown")
    args("${project.buildDir}/docs/markdown")
    onlyIf {
        buildMarkdown
    }
}

tasks.javadoc.configure {
    options {
        this as StandardJavadocDocletOptions
        encoding = "UTF-8"
        links("https://docs.oracle.com/en/java/javase/17/docs/api")
        addBooleanOption("html5", true)
        addBooleanOption("Xdoclint:none", true) // Very lenient
        quiet()
    }
    exclude("**/thirdparty/**")
}

// ---------------- Distributions ----------------

val nativeDistributionOsIdentifier =
    if (os.isMacOsX) "macos"
    else if (os.isLinux) (if (staticNativeExecutable) "linux-musl" else "linux-glibc")
    else if (os.isWindows) "windows"
    else "unknown"
val nativeDistributionName = "owl-${nativeDistributionOsIdentifier}-amd64"

distributions {
    main {
        distributionBaseName.set("owl-jre")

        contents {
            from("AUTHORS")
            from("LICENSE")
            from(compileMarkdown)

            into("bin") {
                from(buildKissat) {
                    include("kissat")
                }
                from("scripts/rabinizer.sh")
            }
            into("jar") {
                from(tasks.jar)
                from(tasks.getByPath(":sourcesJar"))
                from(tasks.getByPath(":javadocJar"))
            }
        }
    }

    create("nativeImage") {
        distributionBaseName.set(nativeDistributionName)
        contents {
            from("AUTHORS")
            from("LICENSE")
            from(compileMarkdown)

            into("bin") {
                from(buildKissat) {
                    include("kissat")
                }
                from("scripts/rabinizer.sh")
                from(buildNativeExecutable)
            }
            into("lib") {
                from("${projectDir}/src/main/c/include")
                from(buildNativeLibrary)
            }
            into("jar") {
                from(tasks.jar)
                from(tasks.getByPath(":sourcesJar"))
                from(tasks.getByPath(":javadocJar"))
            }
        }
    }
}

// ---------------- Publishing ----------------

// Publishing - clean publishToSonatype closeAndReleaseSonatypeStagingRepository
// Authentication: sonatypeUsername+sonatypePassword in ~/.gradle/gradle.properties
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(project.components["java"])

            signing {
                useGpgCmd()
                sign(publishing.publications)
            }

            pom {
                name.set("owl")
                description.set("A tool collection and library for Omega-words, -automata and Linear Temporal Logic (LTL)")
                url.set("https://github.com/owl-toolkit/owl")

                licenses {
                    license {
                        name.set("The GNU General Public License, Version 3")
                        url.set("https://www.gnu.org/licenses/gpl.txt")
                    }
                }

                developers {
                    developer {
                        id.set("sickert")
                        name.set("Salomon Sickert-Zehnter")
                        email.set("salomon.sickert@mail.huji.ac.il")
                        url.set("https://github.com/sickert")
                        timezone.set("Asia/Jerusalem")
                    }
                    developer {
                        id.set("incaseoftrouble")
                        name.set("Tobias Meggendorfer")
                        email.set("tobias@meggendorfer.de")
                        url.set("https://github.com/incaseoftrouble")
                        timezone.set("Europe/Berlin")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/owl-toolkit/owl.git")
                    developerConnection.set("scm:git:git@github.com:owl-toolkit/owl.git")
                    url.set("https://github.com/owl-toolkit/owl")
                }
            }
        }
    }
}

nexusPublishing {
    repositories {
        sonatype()
    }
}
