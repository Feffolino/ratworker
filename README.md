# Rat Worker

Commandable worker NPCs for Minecraft 1.20.1 (Forge). A worker is recruited with a contract, follows orders from a compact command screen, and pays for its work in hunger and thirst. Workers learn new tasks from training manuals, earn XP while they work, and spend it on upgrades. Tasks are extensible from KubeJS and from a Java addon API.

Documentation: see the [wiki](https://github.com/Feffolino/ratworker/wiki).

## Build

Java 17 (the Gradle daemon looks for an installed JDK 17, see `gradle/gradle-daemon-jvm.properties`). Put the compile-only jars listed in `libs/README.md` into `libs/`, then:

```
gradlew build
```

The jar is in `build/libs/`.

## License

MIT. Author: Stefano Manca.
