# Setup

A working Intelji IDEA development environment can be obtained by typing:

`./gradlew idea`

and then importing the generated project (`owl.ipr`) to IDEA.

If you instead want to work with the IDEA gradle plugin, import the project as usual ("Open Project" > `build.gradle`) and perform the following configuration:
* "Annotation Processors":
  * Enable annotation processing
  * Store generated sources relative to module content root
  * Production sources directory: `build/generated-src/annot/main`
  * Test sources directory: `build/generated-src/annot/test`
* "Code Style": Import from `config/idea-codestyle.xml`
* "Inspections": Import from `config/idea-inspection-profile.xml`

# Checks

Before submitting code please executed `./gradlew check` locally to run all code checks.
Apart from jUnit tests, static code analysis is performed by [PMD](https://pmd.github.io/) and [FindBugs](http://findbugs.sourceforge.net/) (rules are located in the `config` folder).
Further, [checkstyle](http://checkstyle.sourceforge.net/) is used to check compliance with the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html). 
Passing all these tests is mandatory for submitted code.


