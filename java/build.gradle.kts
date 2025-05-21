plugins {
    id("java-library")
    id("net.thebugmc.gradle.sonatype-central-portal-publisher") version "1.2.4"
}

description = "LSD (Less Syntax Data) configuration/data transfer format"
group = "ru.kirillsemyonkin"
version = "0.2.2"

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

centralPortal {
    pom {
        url = "https://github.com/kirillsemyonkin/lsd"
        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
            license {
                name = "Apache 2.0"
                url = "https://opensource.org/licenses/Apache-2.0"
            }
        }
        developers {
            developer {
                name = "Kirill Semyonkin"
                email = "burnytc@gmail.com"
            }
        }
        scm {
            connection = "scm:git:git://github.com/kirillsemyonkin/lsd.git"
            developerConnection = "scm:git:ssh://git@github.com:kirillsemyonkin/lsd.git"
            url = "https://github.com/kirillsemyonkin/lsd/tree/master"
        }
    }
}

signing {
    useGpgCmd()
}

tasks.compileJava {
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all",
            "-Xlint:-serial",
        )
    )
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }
}

tasks.build {
    dependsOn(tasks.javadoc)
}