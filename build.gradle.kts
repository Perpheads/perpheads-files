import org.jooq.meta.jaxb.ForcedType

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.allopen") version "2.4.21"
    id("io.quarkus")
    id("nu.studer.jooq") version "10.2.1"
    id("io.kotest") version "6.2.6"
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId = providers.gradleProperty("quarkusPlatformGroupId").get()
val quarkusPlatformArtifactId = providers.gradleProperty("quarkusPlatformArtifactId").get()
val quarkusPlatformVersion = providers.gradleProperty("quarkusPlatformVersion").get()
val ktorVersion = providers.gradleProperty("ktorVersion").get()
val jooqVersion = providers.gradleProperty("jooqVersion").get()

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:quarkus-amazon-services-bom:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-liquibase")
    implementation("io.quarkus:quarkus-security")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("io.quarkus:quarkus-smallrye-context-propagation")
    implementation("io.quarkiverse.jooq:quarkus-jooq:2.2.0")
    implementation("io.quarkiverse.quinoa:quarkus-quinoa:2.9.3")
    implementation("io.smallrye.reactive:mutiny-kotlin")
    implementation("io.quarkiverse.amazonservices:quarkus-amazon-s3")
    implementation("software.amazon.awssdk:netty-nio-client")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.ktor:ktor-client-core:${ktorVersion}")
    implementation("io.ktor:ktor-client-okhttp:${ktorVersion}")
    implementation("io.ktor:ktor-client-content-negotiation:${ktorVersion}")
    implementation("io.ktor:ktor-serialization-jackson:${ktorVersion}")
    implementation("io.quarkus:quarkus-websockets-next")
    implementation("org.jooq:jooq-kotlin:${jooqVersion}")
    implementation("org.jooq:jooq-kotlin-coroutines:${jooqVersion}")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.kotest:kotest-framework-engine:6.2.6")
    testImplementation("io.kotest:kotest-assertions-core:6.2.6")
    testImplementation("io.kotest:kotest-runner-junit5:6.2.6")
    jooqGenerator("org.jooq:jooq-meta-extensions-liquibase:$jooqVersion")
    testImplementation("io.quarkus:quarkus-junit")
}

group = "com.perpheads"
version = "1.0-SNAPSHOT"


tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
        javaParameters = true
    }
}


jooq {
    version.set(jooqVersion)
    configurations {
        create("main") {
            jooqConfiguration.apply {
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.extensions.liquibase.LiquibaseDatabase"
                        isUnsignedTypes = false
                        properties.add(org.jooq.meta.jaxb.Property().withKey("rootPath").withValue("src/main/resources"))
                        properties.add(org.jooq.meta.jaxb.Property().withKey("scripts").withValue("db/changeLog.xml"))

                        forcedTypes.addAll(
                            listOf(
                                ForcedType()
                                    .withName("BOOLEAN")
                                    .withIncludeTypes("(?i:TINYINT\\(1\\))"),
                                ForcedType()
                                    .withUserType("java.time.Instant")
                                    .withIncludeTypes("(?i:DATETIME|TIMESTAMP)")
                                    .withBinding("com.perpheads.files.DateTimeToInstantConverter")
                            )
                        )
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = false
                        isFluentSetters = false
                        isDaos = false
                        isJavaTimeTypes = true
                    }
                    target.apply {
                        packageName = "com.perpheads.files.db"
                    }
                    strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
                }
            }
        }
    }
}

tasks.named<nu.studer.gradle.jooq.JooqGenerate>("generateJooq") {
    allInputsDeclared.set(true)

    inputs.files(fileTree("src/main/resources/db"))
        .withPropertyName("migrations")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}