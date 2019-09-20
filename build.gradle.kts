import com.jfrog.bintray.gradle.BintrayExtension
import org.gradle.api.publish.maven.MavenPom
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTask
import org.gradle.internal.os.OperatingSystem

plugins {
  kotlin("jvm") version "1.3.41"
  `maven-publish`
  application
  id("com.github.ben-manes.versions") version "0.21.0"
  id("com.jfrog.bintray") version "1.8.4"
  id("org.jetbrains.dokka") version "0.9.18"
  id("net.nemerosa.versioning") version "2.8.2"
  id("com.palantir.graal") version "0.3.0-37-g77aa98f"
  id("com.hpe.kraal") version "0.0.15"
  id("com.palantir.consistent-versions") version "1.9.2"
  id("com.diffplug.gradle.spotless") version "3.24.0"
}

repositories {
  jcenter()
  mavenCentral()
  maven(url = "https://jitpack.io")
  maven(url = "http://repo.maven.apache.org/maven2")
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
  implementation("com.baulsupp:oksocial-output")
  implementation("com.fasterxml.jackson.core:jackson-databind")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("com.fasterxml.jackson.module:jackson-module-parameter-names")
  implementation("com.google.guava:guava")
  implementation("com.jakewharton.byteunits:byteunits")
  implementation("com.squareup.okio:okio")
  implementation("info.picocli:picocli")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("io.rsocket:rsocket-core")
  implementation("io.rsocket:rsocket-transport-local")
  implementation("io.rsocket:rsocket-transport-netty")
  implementation("javax.activation:activation")
  implementation("org.eclipse.jetty.http2:http2-http-client-transport")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.slf4j:slf4j-api")
  implementation("org.slf4j:slf4j-jdk14")
  implementation("org.springframework.boot:spring-boot-starter-rsocket")
  implementation("org.zeroturnaround:zt-exec")

  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.jetbrains.kotlin:kotlin-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

  testRuntime("org.junit.jupiter:junit-jupiter-engine:")
  testRuntime("org.slf4j:slf4j-jdk14")
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
  graalVersion("19.1.1")

  // https://github.com/palantir/gradle-graal/issues/105
  downloadBaseUrl("https://github.com/oracle/graal/releases/download/vm-19.1.1/graalvm-ce-$os-amd64-19.1.1.tar.gz?a=")

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
