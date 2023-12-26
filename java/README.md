# LSD

This is a Java implementation of LSD (Less Syntax Data) configuration/data transfer format.

## Installation

Because I am not able to provide this library for you via Maven Central, I cannot provide a perfect
official immutable way for distributing versions. However, folks at [Jitpack](<https://jitpack.io/>)
have solved this for all of us by providing a free distribution service for JVM libraries for
open-source projects. Give them a visit and learn how to install this library. Here is a possible setup:

### Gradle

```kotlin
// better follow jitpack and gradle guides! this may not work

repositories {
    mavenCentral()
    maven {
        name = "jitpack.io"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    implementation("com.github.kirillsemyonkin:lsd:master-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// optionally do not cache snapshot versions (always update)
import java.util.concurrent.TimeUnit.SECONDS
configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, SECONDS)
}
```

### Maven

```xml
<!-- better follow jitpack and maven guides! this may not work -->
<properties>
    <maven.compiler.release>21</maven.compiler.release>
</properties>
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
    <!-- optionally always update snapshot versions -->
    <snapshots>
        <enabled>true</enabled>
        <updatePolicy>always</updatePolicy>
    </snapshots>
</repositories>
<dependencies>
    <dependency>
        <groupId>com.github.kirillsemyonkin</groupId>
        <artifactId>lsd</artifactId>
        <version>master-SNAPSHOT</version>
    </dependency>
</dependencies>
```

## Usage

Once you got LSD into your Java project, `import` it in your code:

```java
import kirillsemyonkin.lsdata.LSD; // Just LSD class itself
import static kirillsemyonkin.lsdata.LSD.*; // Import everything directly into your scope
```

There is one `parse` method available for you:

```java
var fileLSD = LSD.parse(new File("example.lsd"));
var stringLSD = LSD.parse(new ByteArrayInputStream("example Hello world!".getBytes()));
```

To access values, you may list parts of your path which will automatically be converted to strings:

```java
var langKey = "java";
var langName = lsd
    .value(
        LanguageNameIsNotAValueException::new,
        "languages", langKey, "name"
    )
    .orElseThrow(CouldNotFindLanguageNameException::new);
```

## Planned

- [ ] Saving to a file
