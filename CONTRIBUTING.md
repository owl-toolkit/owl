# Development Setup

A working Intelji IDEA development enviroment can be optained by typing:

`./gradlew idea`

And then importing the project to IDEA. 

Independenlty the code formatting rules can be imported from the `config` folder. 

# Checks

Before submitting code please run locally `./gradlew check` to run all jUnit tests and to check for the following errors.

## PMD

https://pmd.github.io/

## Findbugs

http://findbugs.sourceforge.net/

## Checkstyle 

The [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) is mandatory for submitting code and will be checked by checkstyle.
