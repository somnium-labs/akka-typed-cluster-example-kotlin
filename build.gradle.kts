import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    val akkaVersion = "2.6.15"
    val scalaVersion = "2.13"
    val akkaHttpVersion = "10.2.5"
    val akkaCassandraVersion = "1.0.5"
    val akkaManagementVersion = "1.1.1"
    val logbackVersion = "1.2.7"

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.5.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")

    implementation("com.typesafe.akka:akka-remote_$scalaVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-actor-typed_$scalaVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-http_$scalaVersion:$akkaHttpVersion")
    implementation("com.typesafe.akka:akka-http-jackson_$scalaVersion:$akkaHttpVersion")
    implementation("com.typesafe.akka:akka-http-spray-json_$scalaVersion:$akkaHttpVersion")
    implementation("com.typesafe.akka:akka-http-xml_$scalaVersion:$akkaHttpVersion")
    implementation("com.typesafe.akka:akka-stream_$scalaVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-cluster-typed_$scalaVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-cluster-sharding-typed_$scalaVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-persistence-typed_$scalaVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-persistence-cassandra_$scalaVersion:$akkaCassandraVersion")
    implementation("com.typesafe.akka:akka-persistence-query_$scalaVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-serialization-jackson_$scalaVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-discovery_$scalaVersion:$akkaVersion")

    implementation("com.lightbend.akka.discovery:akka-discovery-kubernetes-api_$scalaVersion:$akkaManagementVersion")
    implementation("com.lightbend.akka.management:akka-management-cluster-bootstrap_$scalaVersion:$akkaManagementVersion")
    implementation("com.lightbend.akka.management:akka-management-cluster-http_$scalaVersion:$akkaManagementVersion")

    // Logback
    implementation("com.typesafe.akka:akka-slf4j_$scalaVersion:$akkaVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation("com.typesafe.akka:akka-multi-node-testkit_$scalaVersion:$akkaVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<ShadowJar> {
    val newTransformer = AppendingTransformer()
    newTransformer.resource = "reference.conf"
    transformers.add(newTransformer)

    manifest {
        attributes(mapOf("Main-Class" to "com.labs.somnium.app.StartNodeKt"))
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}

/* run
    java -Dconfig.resource=${configname}.conf -jar build/libs/${jarname}.jar
 */
