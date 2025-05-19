# LSD

This is a Java implementation of LSD (Less Syntax Data) configuration/data transfer format.

## Installation

### Gradle

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("ru.kirillsemyonkin:lsd:0.1.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
```

### Maven

```xml
<properties>
    <maven.compiler.release>21</maven.compiler.release>
</properties>
<dependencies>
    <dependency>
        <groupId>ru.kirillsemyonkin</groupId>
        <artifactId>lsd</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

## Usage

Once you got LSD into your Java project, `import` it in your code:

```java
import ru.kirillsemyonkin.lsdata.LSD; // Just LSD class itself
```

There is one `parse` method available for you:

```java
var fileLSD = LSD.parse(new File("example.lsd"));
var stringLSD = LSD.parse("example Hello world!");
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
