import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.3.72"
  `maven-publish`
  application
  id("net.nemerosa.versioning") version "2.13.1"
  id("com.diffplug.gradle.spotless") version "3.30.0"
  id("com.palantir.graal") version "0.7.1"
  kotlin("kapt") version "1.3.72"
}

repositories {
  jcenter()
  mavenCentral()
  maven(url = "https://jitpack.io")
  maven(url = "https://repo.maven.apache.org/maven2")
  maven(url = "https://repo.spring.io/milestone")
  maven(url = "https://repo.spring.io/release")
  maven(url = "https://dl.bintray.com/whyoleg/rsocket-kotlin")
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
    kotlinOptions.apiVersion = "1.3"
    kotlinOptions.languageVersion = "1.3"
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

spotless {
  kotlinGradle {
    ktlint("0.31.0").userData(mutableMapOf("indent_size" to "2", "continuation_indent_size" to "2"))
    trimTrailingWhitespace()
    endWithNewline()
  }
}

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
  outputName("rsocket-cli")
  graalVersion("20.1.0")
  javaVersion("11")

  option("--enable-https")
  option("--no-fallback")
  option("--allow-incomplete-classpath")

//  if (Os.isFamily(Os.FAMILY_WINDOWS)) {
//    // May be possible without, but autodetection is problematic on Windows 10
//    // see https://github.com/palantir/gradle-graal
//    // see https://www.graalvm.org/docs/reference-manual/native-image/#prerequisites
//  windowsVsVarsPath("C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools\\VC\\Auxiliary\\Build\\vcvars64.bat")
//  }
}

dependencies {
  implementation("io.rsocket.kotlin:rsocket-core-jvm:0.1.0.beta.2")
  implementation("io.rsocket.kotlin:rsocket-transport-websocket-client-jvm:0.1.0.beta.2")
  implementation("io.ktor:ktor-network-tls:1.3.2")
//  implementation("io.ktor:ktor-http-cio:1.3.2")
  implementation("io.ktor:ktor-client-okhttp:1.3.2")
  implementation("io.ktor:ktor-client-core-jvm:1.3.2")

  implementation("io.rsocket:rsocket-core:1.0.2")

  implementation("com.github.yschimke:oksocial-output:5.6")
  implementation("com.squareup.okhttp3:okhttp:4.8.1")
  implementation("com.squareup.okio:okio:2.7.0")
  implementation("info.picocli:picocli:4.5.0")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.72")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.72")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.8")
  implementation("com.squareup.moshi:moshi:1.9.3")
  implementation("com.squareup.moshi:moshi-adapters:1.9.3")
  implementation("com.squareup.moshi:moshi-kotlin:1.9.3")
  implementation("org.slf4j:slf4j-jdk14:2.0.0-alpha1")

  kapt("info.picocli:picocli-codegen:4.5.0")
  compileOnly("org.graalvm.nativeimage:svm:20.1.0")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.3.72")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.3.72")

  testRuntime("org.junit.jupiter:junit-jupiter-engine:5.5.2")
}
