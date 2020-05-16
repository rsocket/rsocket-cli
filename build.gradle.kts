import com.jfrog.bintray.gradle.BintrayExtension
import org.gradle.api.publish.maven.MavenPom
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTask
import org.gradle.internal.os.OperatingSystem

plugins {
  kotlin("jvm") version "1.3.71"
  `maven-publish`
  application
  id("com.github.ben-manes.versions") version "0.21.0"
  id("com.jfrog.bintray") version "1.8.4"
  id("org.jetbrains.dokka") version "0.9.18"
  id("net.nemerosa.versioning") version "2.8.2"
  id("com.palantir.graal") version "0.6.0"
  id("com.hpe.kraal") version "0.0.15"
  id("com.diffplug.gradle.spotless") version "3.24.0"
}

repositories {
  jcenter()
  mavenCentral()
  maven(url = "https://jitpack.io")
  maven(url = "https://repo.maven.apache.org/maven2")
  maven(url = "https://dl.bintray.com/kotlin/kotlin-eap/")
  maven(url = "https://oss.jfrog.org/oss-snapshot-local")
  maven(url = "https://repo.spring.io/milestone")
  maven(url = "https://repo.spring.io/release/")
//  maven(url = "https://oss.jfrog.org/libs-snapshot")
//  maven(url = "https://dl.bintray.com/reactivesocket/RSocket")
//  maven(url = "https://oss.sonatype.org/content/repositories/releases")
}

group = "com.baulsupp"
val artifactID = "rsocket-cli"
description = "RSocket CLI"
val projectVersion = versioning.info.display
// println("Version $projectVersion")
version = projectVersion

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
    compression = Compression.GZIP
  }
}

tasks {
  "dokka"(DokkaTask::class) {
    outputFormat = "javadoc"
    outputDirectory = "$buildDir/javadoc"
  }
  withType<GenerateMavenPom> {
    destination = file("$buildDir/libs/${jar.get().baseName}.pom")
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
  implementation("com.baulsupp:oksocial-output:4.32.0")
  implementation("org.jfree:jfreesvg:3.4")
  implementation("com.kitfox.svg:svg-salamander:1.0")
  implementation("commons-io:commons-io:2.6")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.10.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.10.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.10.0")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.10.0")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.10.0")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.0")
  implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.10.0")
  implementation("com.google.guava:guava:28.1-jre")
  implementation("com.jakewharton.byteunits:byteunits:0.9.1")
  implementation("com.squareup.okio:okio:2.2.2")
  implementation("info.picocli:picocli:4.0.1")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.0.0.RELEASE")
  implementation("io.rsocket:rsocket-core:1.0.0")
  implementation("io.rsocket:rsocket-transport-local:1.0.0")
  implementation("io.rsocket:rsocket-transport-netty:1.0.0")
  implementation("javax.activation:activation:1.1.1")
  implementation("org.eclipse.jetty.http2:http2-http-client-transport:9.4.19.v20190610")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.71")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.71")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.6")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.6")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.3.6")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.3.6")
  implementation("org.slf4j:slf4j-api:1.8.0-beta4")
  implementation("org.slf4j:slf4j-jdk14:1.8.0-beta4")
  implementation("org.springframework.boot:spring-boot-starter-rsocket:2.3.0.RELEASE")
  implementation("org.zeroturnaround:zt-exec:1.11")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.3.70")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.3.70")

  testRuntime("org.junit.jupiter:junit-jupiter-engine:5.5.2")
  testRuntime("org.slf4j:slf4j-jdk14:1.8.0-beta4")
}

fun MavenPom.addDependencies() = withXml {
  asNode().appendNode("dependencies").let { depNode ->
    configurations.implementation.get().allDependencies.forEach {
      depNode.appendNode("dependency").apply {
        appendNode("groupId", it.group)
        appendNode("artifactId", it.name)
        appendNode("version", it.version)
      }
    }
  }
}

publishing {
  publications {
    create("mavenJava", MavenPublication::class) {
      artifactId = artifactID
      groupId = project.group.toString()
      version = project.version.toString()
      description = project.description
      artifact(jar)
      artifact(sourcesJar) {
        classifier = "sources"
      }
      artifact(javadocJar) {
        classifier = "javadoc"
      }
      pom.addDependencies()
      pom {
        packaging = "jar"
        developers {
          developer {
            email.set("yuri@schimke.ee")
            id.set("yschimke")
            name.set("Yuri Schimke")
          }
        }
        licenses {
          license {
            name.set("Apache License")
            url.set("http://opensource.org/licenses/apache-2.0")
            distribution.set("repo")
          }
        }
        scm {
          connection.set("scm:git:https://github.com/yschimke/okurl.git")
          developerConnection.set("scm:git:git@github.com:yschimke/okurl.git")
          url.set("https://github.com/yschimke/okurl.git")
        }
      }
    }
  }
}

fun findProperty(s: String) = project.findProperty(s) as String?
bintray {
  user = findProperty("baulsuppBintrayUser")
  key = findProperty("baulsuppBintrayKey")
  publish = true
  setPublications("mavenJava")
  pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
    repo = "baulsupp.com"
    name = "rsocket-cli"
    userOrg = user
    websiteUrl = "https://github.com/rsocket/rsocket-cli"
    githubRepo = "rsocket/rsocket-cli"
    vcsUrl = "https://github.com/rsocket/rsocket-cli.git"
    desc = project.description
    setLabels("kotlin")
    setLicenses("Apache-2.0")
    version(delegateClosureOf<BintrayExtension.VersionConfig> {
      name = project.version.toString()
    })
  })
}

val os = if (OperatingSystem.current().isMacOsX()) {
  "darwin"
} else {
  "linux"
}

graal {
  mainClass("io.rsocket.cli.Main")
  outputName("rsocket-cli")
  option("--allow-incomplete-classpath")
  option("--enable-all-security-services")
  option("--report-unsupported-elements-at-runtime")
  option("--auto-fallback")
  option("--enable-http")
  option("--enable-https")
  option("-H:+AddAllCharsets")
  option("-H:ReflectionConfigurationFiles=reflect.config")
  option("-H:+ReportExceptionStackTraces")
  option("--initialize-at-build-time=reactor,ch.qos.logback,com.fasterxml.jackson,fresh.graal,io.micronaut,io.netty,io.reactivex,org.reactivestreams,org.slf4j,org.yaml.snakeyaml,javax")

//  -H:InitialCollectionPolicy=com.oracle.svm.core.genscavenge.CollectionPolicy$BySpaceAndTime \
//  -J-Djava.util.concurrent.ForkJoinPool.common.parallelism=1 \
//  -Dio.netty.noUnsafe=true \
//  -Dio.netty.noJdkZlibDecoder=true \
  option("--delay-class-initialization-to-runtime=io.netty.handler.ssl.JdkNpnApplicationProtocolNegotiator,io.netty.handler.ssl.ReferenceCountedOpenSslEngine,io.netty.util.internal.ObjectCleaner,io.netty.handler.ssl.ReferenceCountedOpenSslContext,io.netty.channel.DefaultChannelConfig,io.netty.handler.codec.http.HttpObjectEncoder,io.netty.handler.codec.http.websocketx.WebSocket00FrameEncoder,javax.net.ssl.SSLContext")
}

spotless {
  kotlinGradle {
    ktlint("0.31.0").userData(mutableMapOf("indent_size" to "2", "continuation_indent_size" to "2"))
    trimTrailingWhitespace()
    endWithNewline()
  }
}
