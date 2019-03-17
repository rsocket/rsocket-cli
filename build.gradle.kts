import com.jfrog.bintray.gradle.BintrayExtension
import org.gradle.api.publish.maven.MavenPom
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
  kotlin("jvm") version Versions.kotlinVersion
  `maven-publish`
  application
  id("com.github.ben-manes.versions") version "0.21.0"
  id("org.jlleitschuh.gradle.ktlint") version "7.2.1"
  id("com.jfrog.bintray") version "1.8.4"
  id("org.jetbrains.dokka") version "0.9.17"
  id("net.nemerosa.versioning") version "2.8.2"
}

repositories {
  jcenter()
  mavenCentral()
  maven(url = "https://jitpack.io")
  maven(url = "http://repo.maven.apache.org/maven2")
  maven(url = "https://dl.bintray.com/kotlin/kotlin-eap/")
//  maven(url = "https://repo.spring.io/milestone")
//  maven(url = "https://oss.jfrog.org/libs-snapshot")
//  maven(url = "https://dl.bintray.com/reactivesocket/RSocket")
//  maven(url = "https://oss.sonatype.org/content/repositories/releases")
}

group = "com.baulsupp"
val artifactID = "rsocket-cli"
description = "RSocket CLI"
val projectVersion = versioning.info.display
//println("Version $projectVersion")
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
  implementation(Deps.activation)
  implementation(Deps.airline2)
  implementation(Deps.byteunits)
  implementation(Deps.coroutinesCore)
  implementation(Deps.coroutinesJdk8)
  implementation(Deps.coroutinesReactive)
  implementation(Deps.coroutinesReactor)
  implementation(Deps.guava)
  implementation(Deps.jacksonCbor)
  implementation(Deps.jacksonDatabind)
  implementation(Deps.jacksonJdk8)
  implementation(Deps.jacksonJsr310)
  implementation(Deps.jacksonKotlin)
  implementation(Deps.jacksonParams)
  implementation(Deps.jacksonYaml)
  implementation(Deps.jettyJttp2)
  implementation(Deps.kotlinReflect)
  implementation(Deps.kotlinStandardLibrary)
  implementation(Deps.okio)
  implementation(Deps.oksocialOutput)
  implementation(Deps.rsocketCore)
  implementation(Deps.rsocketLocal)
  implementation(Deps.rsocketNetty)
  implementation(Deps.slf4jApi)
  implementation(Deps.slf4jJdk14)
  implementation(Deps.ztExec)

  testImplementation(Deps.junitJupiterApi)
  testImplementation(Deps.kotlinTest)
  testImplementation(Deps.kotlinTestJunit)

  testRuntime(Deps.junitJupiterEngine)
  testRuntime(Deps.slf4jJdk14)
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
