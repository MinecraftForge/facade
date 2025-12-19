# Facade

A simple tool that injects interfaces into a jar file.
This is meant to be a build time tool to transform dependencies adding new interfaces.
Allowing your code to compile against the injected code as if it was a normal dependency.

This uses the ClassFile API and so needs java 25+

## Usage

```shell
java -jar facade.jar --input dependency.jar --output patched.jar --config interfaces.cfg
```

## Gradle
Currently there is no gradle demo/plugin for this, as the intended use is through [ForgeGradle][ForgeGradle] and [Mavenzier][Mavenizer].

> [!WARNING]
> **There is no public API for this tool!** This is designed to solely be a CLI tool, which means that all of the implementations are internal. We reserve the right to change the internal implementation at any time.


[ForgeGradle]: https://github.com/MinecraftForge/ForgeGradle
[Mavenizer]: https://github.com/MinecraftForge/MinecraftMavenizer