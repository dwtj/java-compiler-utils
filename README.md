# Java Compiler Utilities

This project implements some simple utilities for simplifying the usage of
various APIs related to Java compilation and annotation processing. The focus
is on standard Java APIs, but there is also some functionality which is
specific to Oracle's `javac`.


## Build and Install

The provided Gradle wrapper is the preferred way to build and install this
project. From the root of the project,

```
$ ./gradlew clean install
```


## Relevant Standards and APIs

- Pluggable Annotation Processing API, `javax.annotation.processing` (JSR 269, JSR 308)
- The Java Compiler API, `javax.tools.*` (JSR 199)
- Language Model API, `javax.lang.model.*`
- Compiler Tree API, `com.sun.source.*`
- JCTree API, `com.sun.tools.javac.tree.*`


## References

- [JLS 8](http://docs.oracle.com/javase/specs/jls/se8/html/index.html)
- [JVMS 8](http://docs.oracle.com/javase/specs/jvms/se8/html/index.html)
- [The Hitchhiker's Guide to `javac`](http://openjdk.java.net/groups/compiler/doc/hhgtjavac/index.html)
