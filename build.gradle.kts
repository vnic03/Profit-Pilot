import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("com.diffplug.spotless") version "8.10.4"
    java
    id("org.springframework.boot") version "4.1.1"
}

group = "io.github.vnic03"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation(platform("tools.jackson:jackson-bom:3.1.7"))

    constraints {
        implementation("org.apache.tomcat.embed:tomcat-embed-core:11.0.27") {
            because("Sicherheitsupdates für eingebetteten Tomcat")
        }
        implementation("org.apache.tomcat.embed:tomcat-embed-el:11.0.27") {
            because("Einheitliche Tomcat-Version")
        }
        implementation("org.apache.tomcat.embed:tomcat-embed-websocket:11.0.27") {
            because("Einheitliche Tomcat-Version")
        }
    }

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    implementation("org.json:json:20260814")
    implementation("com.google.guava:guava:33.5.0-jre")
    implementation("io.github.cdimascio:dotenv-java:3.2.0")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("nz.ac.waikato.cms.weka:weka-stable:3.8.5")

    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()

    jvmArgs("-Xshare:off")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("profit-pilot.jar")

    // Production supplies its own configuration; never bundle local credentials.
    exclude("**/application.properties")

    from("web/build") {
        into("BOOT-INF/classes/static")
    }

    doFirst {
        check(file("web/build/index.html").isFile) {
            "Build the frontend before running bootJar."
        }
    }
}

springBoot {
    buildInfo {
        properties {
            additional.set(
                mapOf(
                    "revision" to providers
                        .environmentVariable("PROFITPILOT_BUILD_REVISION")
                        .getOrElse("local-unversioned")
                )
            )
        }
    }
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.28.0").aosp()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
