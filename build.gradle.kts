import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
  kotlin("jvm") version "1.4.0"
  kotlin("kapt") version "1.4.0"
  `maven-publish`
  application
  id("net.nemerosa.versioning") version "2.13.1"
  id("com.diffplug.spotless") version "5.1.0"
  id("com.palantir.graal") version "0.7.1-15-g62b5090"
}

repositories {
  jcenter()
  mavenCentral()
  maven(url = "https://jitpack.io")
  maven(url = "https://oss.jfrog.org/oss-snapshot-local")
}

group = "com.github.yschimke"
description = "RSocket CLI"
version = versioning.info.display

base {
  archivesBaseName = "rsocket-cli"
}

application {
  mainClassName = "io.rsocket.cli.Main"
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
  withType(KotlinCompile::class) {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.allWarningsAsErrors = false
    kotlinOptions.freeCompilerArgs = listOf("-Xjsr305=strict", "-Xjvm-default=enable")
  }
}

tasks {
  withType(Tar::class) {
    compression = Compression.NONE
  }
}

val sourcesJar by tasks.creating(Jar::class) {
  classifier = "sources"
  from(kotlin.sourceSets["main"].kotlin)
}

val javadocJar by tasks.creating(Jar::class) {
  classifier = "javadoc"
  from("$buildDir/javadoc")
}

val jar = tasks["jar"] as org.gradle.jvm.tasks.Jar

distributions {
  getByName("main") {
    contents {
      from("${rootProject.projectDir}") {
        include("README.md", "LICENSE")
      }
      from("${rootProject.projectDir}/zsh") {
        into("zsh")
      }
      into("lib") {
        from(jar)
      }
      into("lib") {
        from(configurations.runtimeClasspath)
      }
    }
  }
}

publishing {
  repositories {
    maven(url = "build/repository")
  }
  publications {
    register("mavenJava", MavenPublication::class) {
      from(components["java"])
      artifact(sourcesJar)
      artifact(tasks.distTar.get())
    }
  }
}

graal {
  mainClass("io.rsocket.cli.Main")
  outputName("rsocketcli")
  graalVersion("20.2.0")
  javaVersion("11")

  option("--enable-https")
  option("--no-fallback")
  option("--allow-incomplete-classpath")

  if (Os.isFamily(Os.FAMILY_WINDOWS)) {
    // May be possible without, but autodetection is problematic on Windows 10
    // see https://github.com/palantir/gradle-graal
    // see https://www.graalvm.org/docs/reference-manual/native-image/#prerequisites
    windowsVsVarsPath("C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools\\VC\\Auxiliary\\Build\\vcvars64.bat")
  }
}

dependencies {
//  implementation(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar"))))
  implementation("io.rsocket.kotlin:rsocket-core-jvm:0.10.0-task/interactions-api-SNAPSHOT")
  implementation("io.rsocket.kotlin:rsocket-transport-ktor-client:0.10.0-task/interactions-api-SNAPSHOT")
  implementation("io.ktor:ktor-network-tls:1.4.0")
  implementation("io.ktor:ktor-client-okhttp:1.4.0")
  implementation("io.ktor:ktor-client-core-jvm:1.4.0")

  implementation("io.rsocket:rsocket-core:1.0.2")

  implementation("com.github.yschimke:oksocial-output:5.7")
  implementation("com.squareup.okhttp3:okhttp:4.8.1")
  implementation("com.squareup.okio:okio:2.8.0")
  implementation("info.picocli:picocli:4.5.0")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.0")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.9")
  implementation("com.squareup.moshi:moshi:1.9.3")
  implementation("com.squareup.moshi:moshi-adapters:1.9.3")
  implementation("com.squareup.moshi:moshi-kotlin:1.9.3")
  implementation("org.slf4j:slf4j-jdk14:2.0.0-alpha1")

  kapt("info.picocli:picocli-codegen:4.5.0")
  compileOnly("org.graalvm.nativeimage:svm:20.2.0") {
    // https://youtrack.jetbrains.com/issue/KT-29513
    exclude(group= "org.graalvm.nativeimage")
    exclude(group= "org.graalvm.truffle")
    exclude(group= "org.graalvm.sdk")
    exclude(group= "org.graalvm.compiler")
  }

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.4.0")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.4.0")

  testRuntime("org.junit.jupiter:junit-jupiter-engine:5.5.2")
}
