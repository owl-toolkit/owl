# Building

## Prerequisites

Building the project from source requires an installed [JDK 12](http://jdk.java.net/12/) and an installed C++17 compiler. Furthermore to generate HTML documentation [pandoc](https://pandoc.org/) is needed.

Owl is built with [Gradle](http://gradle.org/), which is automatically bootstrapped. You can view the available tasks with:

```
$ ./gradlew tasks
```

## Standard Distribution

The standard distribution can be obtained with:

```
$ ./gradlew distZip
```

The resulting `.zip` is located in `build/distributions`. In order to run all tests and checks please use:

```
$ ./gradlew check
```

Lastly you can install the distribution to the default location using:

```
$ ./gradlew installDist
```

## Minimized Distribution

In order to save space and reduce load time of the application a minimized `jar` can be produced with:

```
$ ./gradlew minimizedDistZip
```

The minimization removes unused `class`-files from the jar and produces a single jar.

## Documentation

The corresponding javadoc can be obtained with:

```
$ ./gradlew javadoc
```
