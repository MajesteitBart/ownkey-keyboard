import java.security.MessageDigest
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { alias(libs.plugins.agp.library) }

val sherpaVersion = "1.13.4-asr1"
val sherpaDigest = "db5e489fb948e98a3c5cba14b1a5fe4e32aff68e238e971c4f27cb2db968677e"
val nativeOutput = layout.buildDirectory.dir("generated/sherpa")
val prepareSherpa by tasks.registering {
    inputs.property("version", sherpaVersion)
    inputs.property("sha256", sherpaDigest)
    outputs.dir(nativeOutput)
    doLast {
        val cache = File(gradle.gradleUserHomeDir, "caches/ownkey/ownkey-sherpa-onnx-$sherpaVersion.aar")
        fun digest(file: File): String = file.inputStream().use { source ->
            val sha = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536)
            while (true) { val n = source.read(buffer); if (n < 0) break; sha.update(buffer, 0, n) }
            sha.digest().joinToString("") { "%02x".format(it) }
        }
        if (!cache.isFile || digest(cache) != sherpaDigest) {
            cache.parentFile.mkdirs()
            val partial = File(cache.path + ".partial")
            val connection = uri("https://github.com/MajesteitBart/ownkey-keyboard/releases/download/orukeet-runtime-sherpa-$sherpaVersion/ownkey-sherpa-onnx-$sherpaVersion.aar").toURL().openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.getInputStream().use { source -> partial.outputStream().use { source.copyTo(it) } }
            check(digest(partial) == sherpaDigest) { "sherpa-onnx checksum mismatch" }
            try {
                Files.move(partial.toPath(), cache.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        val output = nativeOutput.get().asFile
        output.deleteRecursively(); output.mkdirs()
        ZipFile(cache).use { zip ->
            zip.entries().asSequence().filter { entry ->
                entry.name == "classes.jar" || entry.name.matches(Regex("jni/(arm64-v8a|x86_64)/[A-Za-z0-9_.-]+\\.so"))
            }.forEach { entry ->
                val target = File(output, entry.name)
                target.parentFile.mkdirs()
                zip.getInputStream(entry).use { source -> target.outputStream().use { source.copyTo(it) } }
            }
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
android {
    namespace = "org.ownkey.offline"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets.getByName("main").jniLibs.srcDir(nativeOutput.get().dir("jni").asFile)
    testOptions.unitTests.all { it.useJUnitPlatform() }
}
tasks.named("preBuild").configure { dependsOn(prepareSherpa) }
dependencies {
    implementation(files(nativeOutput.map { it.file("classes.jar") }).builtBy(prepareSherpa))
    implementation(libs.kotlinx.coroutines)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}
