# Building

Owl is built with [Gradle](http://gradle.org/).

To build all Java tools, use the following command.
All dependencies are downloaded automatically.

```
$ ./gradlew buildBin
```

The tools are located in `build/bin`.

To also build the included C and C++ tools, an appropriate compiler is required.
A full build can be executed by

```
$ ./gradlew assemble
```

All resulting artifacts are located in `build/distributions`.

By default, ProGuard is used to minimize the resulting jar.
Specify `-Pfull` to disable the optimizations.
