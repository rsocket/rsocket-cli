import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
  kotlin("jvm") version "1.4.30"
  kotlin("kapt") version "1.4.30"
  `maven-publish`
  application
  id("net.nemerosa.versioning") version "2.13.1"
  id("com.diffplug.spotless") version "5.1.0"
  id("com.palantir.graal") version "0.7.2"
}

repositories {
  jcenter()
  mavenCentral()
  maven(url = "https://jitpack.io")
  maven(url = "https://dl.bintray.com/kotlinx/kotlinx")
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
    kotlinOptions.freeCompilerArgs = listOf("-Xjsr305=strict", "-Xjvm-default=enable", "-Xopt-in=kotlin.RequiresOptIn")
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
  outputName("rsocket-cli")
  graalVersion("21.0.0")
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
  implementation("io.rsocket.kotlin:rsocket-core-jvm:0.12.0")
  implementation("io.rsocket.kotlin:rsocket-transport-ktor-client:0.12.0")
  implementation("io.ktor:ktor-network-tls:1.5.0")
  implementation("io.ktor:ktor-client-okhttp:1.5.0")
  implementation("io.ktor:ktor-client-core-jvm:1.5.0")

  implementation("io.rsocket:rsocket-core:1.1.0")

  // define a BOM and its version
  implementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0-RC1"))

  implementation("com.github.yschimke:oksocial-output:5.10")
  implementation("com.squareup.okhttp3:okhttp")
  implementation("com.squareup.okio:okio:3.0.0-alpha.1")
  implementation("info.picocli:picocli:4.5.2")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.30")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.30")
  implementation("com.squareup.moshi:moshi:1.11.0")
  implementation("com.squareup.moshi:moshi-adapters:1.11.0")
  implementation("com.squareup.moshi:moshi-kotlin:1.11.0")
  implementation("org.slf4j:slf4j-jdk14:2.0.0-alpha1")

  kapt("info.picocli:picocli-codegen:4.5.2")
  compileOnly("org.graalvm.nativeimage:svm:21.0.0.2") {
    // https://youtrack.jetbrains.com/issue/KT-29513
    exclude(group= "org.graalvm.nativeimage")
    exclude(group= "org.graalvm.truffle")
    exclude(group= "org.graalvm.sdk")
    exclude(group= "org.graalvm.compiler")
  }

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.4.30")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.4.30")

  testRuntime("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

if (properties.containsKey("graalbuild")) {
  val nativeImage = tasks["nativeImage"]

  distributions {
    val graal = create("graal") {
      contents {
        from("${rootProject.projectDir}") {
          include("README.md", "LICENSE")
        }
        from("${rootProject.projectDir}/zsh") {
          into("zsh")
        }
        into("bin") {
          from(nativeImage)
        }
      }
    }
  }
}
