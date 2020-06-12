import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.3.72"
  `maven-publish`
  application
  id("com.github.ben-manes.versions") version "0.28.0"
  id("net.nemerosa.versioning") version "2.13.1"
  id("com.diffplug.gradle.spotless") version "3.30.0"
}

repositories {
  jcenter()
  mavenCentral()
  maven(url = "https://jitpack.io")
  maven(url = "https://repo.maven.apache.org/maven2")
  maven(url = "https://repo.spring.io/milestone")
  maven(url = "https://repo.spring.io/release")
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

dependencies {
  implementation("com.baulsupp:oksocial-output:4.34.0")
  implementation("org.jfree:jfreesvg:3.4")
  implementation("com.kitfox.svg:svg-salamander:1.0")
  implementation("commons-io:commons-io:2.6")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.11.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.11.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.0")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.11.0")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.11.0")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.0")
  implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.11.0")
  implementation("com.google.guava:guava:29.0-jre")
  implementation("com.jakewharton.byteunits:byteunits:0.9.1")
  implementation("com.squareup.okio:okio:2.6.0")
  implementation("info.picocli:picocli:4.3.2")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.0.2.RELEASE")
  implementation("io.rsocket:rsocket-core:1.0.1")
  implementation("io.rsocket:rsocket-transport-local:1.0.1")
  implementation("io.rsocket:rsocket-transport-netty:1.0.1")
  implementation("javax.activation:activation:1.1.1")
  implementation("org.eclipse.jetty.http2:http2-http-client-transport:9.4.19.v20190610")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.72")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.72")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.6")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.6")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.3.6")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.3.6")
  implementation("org.slf4j:slf4j-api:1.8.0-beta4")
  implementation("org.slf4j:slf4j-jdk14:1.8.0-beta4")
  implementation("org.springframework.boot:spring-boot-starter-rsocket:2.3.0.RELEASE")
  implementation("org.zeroturnaround:zt-exec:1.11")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.3.72")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.3.72")

  testRuntime("org.junit.jupiter:junit-jupiter-engine:5.5.2")
  testRuntime("org.slf4j:slf4j-jdk14:1.8.0-beta4")
}

spotless {
  kotlinGradle {
    ktlint("0.31.0").userData(mutableMapOf("indent_size" to "2", "continuation_indent_size" to "2"))
    trimTrailingWhitespace()
    endWithNewline()
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
