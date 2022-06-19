import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
  kotlin("jvm") version "1.6.21"
  kotlin("kapt") version "1.6.21"
  `maven-publish`
  application
  id("net.nemerosa.versioning") version "2.15.1"
  id("com.diffplug.spotless") version "6.7.2"
  id("com.palantir.graal") version "0.10.0"
}

repositories {
  mavenCentral()
  maven(url = "https://jitpack.io")
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
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks {
  withType(KotlinCompile::class) {
    kotlinOptions.jvmTarget = "17"
    kotlinOptions.allWarningsAsErrors = false
    kotlinOptions.freeCompilerArgs = listOf("-Xjsr305=strict", "-Xjvm-default=all", "-opt-in=kotlin.RequiresOptIn")
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
      duplicatesStrategy = DuplicatesStrategy.WARN

      from("${rootProject.projectDir}") {
        include("README.md", "LICENSE")
      }
      from("${rootProject.projectDir}/zsh") {
        into("zsh")
      }
      into("lib") {
        from(jar)
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
  graalVersion("22.1.0")
  javaVersion("17")

  option("--enable-https")
  option("--no-fallback")

  if (Os.isFamily(Os.FAMILY_WINDOWS)) {
    // May be possible without, but autodetection is problematic on Windows 10
    // see https://github.com/palantir/gradle-graal
    // see https://www.graalvm.org/docs/reference-manual/native-image/#prerequisites
    windowsVsVarsPath("C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools\\VC\\Auxiliary\\Build\\vcvars64.bat")
  }
}

dependencies {
  implementation("io.rsocket.kotlin:rsocket-core-jvm:0.15.4")
  implementation("io.rsocket.kotlin:rsocket-transport-ktor:0.15.4")
  implementation("io.rsocket.kotlin:rsocket-ktor-client:0.15.4")
  implementation("io.rsocket.kotlin:rsocket-transport-ktor-websocket-client:0.15.4")
  implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:0.15.4")
  implementation("io.ktor:ktor-network-tls:2.0.2")
  implementation("io.ktor:ktor-client-okhttp:2.0.2")
  implementation("io.ktor:ktor-client-core-jvm:2.0.2")

  // define a BOM and its version
  implementation(platform("com.squareup.okhttp3:okhttp-bom:5.0.0-alpha.8"))

  implementation("com.github.yschimke.schoutput:schoutput:0.9.2")
  implementation("com.squareup.okhttp3:okhttp")
  implementation("com.squareup.okio:okio:3.1.0")
  implementation("info.picocli:picocli:4.6.3")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.21")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21")
  implementation("com.squareup.moshi:moshi:1.13.0")
  implementation("com.squareup.moshi:moshi-adapters:1.13.0")
  implementation("com.squareup.moshi:moshi-kotlin:1.13.0")
  implementation("org.slf4j:slf4j-jdk14:2.0.0-alpha7")

  kapt("info.picocli:picocli-codegen:4.6.3")
  compileOnly("org.graalvm.nativeimage:svm:21.1.0")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.6.21")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.6.21")

  testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.2")
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
