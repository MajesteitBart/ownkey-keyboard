/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotest)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// The offline benchmark simulator lives in its own source set so the library itself stays dependency-free.
val sim: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
sourceSets.test {
    compileClasspath += sim.output
    runtimeClasspath += sim.output
}
configurations[sim.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations.testImplementation.get().extendsFrom(configurations[sim.implementationConfigurationName])

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

tasks.withType<Test> {
    testLogging {
        events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
        showStandardStreams = true
    }
    useJUnitPlatform()
    maxHeapSize = "1536m"
    systemProperty("ownkey.repoRoot", rootProject.projectDir.absolutePath)
}

tasks.register<JavaExec>("simulate") {
    group = "verification"
    description = "Replays the kbbench typists against the prediction engine (see tools/kbbench/sim/README.md)."
    classpath = sim.runtimeClasspath
    mainClass = "org.florisboard.lib.prediction.sim.SimulatorMainKt"
    workingDir = rootProject.projectDir
    maxHeapSize = "2g"
}

dependencies {
    "simImplementation"(libs.kotlinx.serialization.json)

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)
}
