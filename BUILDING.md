# Build Instructions

Download 'GraalVM 22.1: JDK 17' from the [GraalVM website](https://www.graalvm.org/) and install it
with the **native-image** component. Do not forget to add the installed tools to the search path:

```
PATH=/<path>/graalvm-ce-java17-22.1.0/bin/:$PATH
JAVA_HOME=/<path>/graalvm-ce-java17-22.1.0/
```

Then execute the following command to obtain the native-image distribution:

```
./gradlew nativeImageDistZip -Pdisable-pandoc
```

The resulting `.zip` is located in `build/distributions`. If GraalVM cannot be installed, the
JRE-distribution can be built with:

```
./gradlew distZip -Pdisable-pandoc
```

The resulting `.zip` is located in `build/distributions`.

## Docker

In case you want to build and run tests using docker (recommended on Windows), first build the
docker image with

```
  cd docker-build-environment && docker build -t owl .
```

Then, run the build process via

```
  docker run --rm -it -v <path>/owl:/dir -w /dir owl ./gradlew build
```

or similar. Optionally, you can pass `GRADLE_HOME=./.gradle` and `GRADLE_USER_HOME=./.gradle` to
speed up subsequent builds.