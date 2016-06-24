# Releasing the Project

This project's build system uses the Gradle plugin `com.bmuschko.nexus` to
configure both signing and uploading release archives to maven central. This
can be performed via `./gradlew --no-daemon clean uploadArchives`. (Note that
`--no-daemon` is added to support interactive input from the console.)

There are a few sensitive parameters to the build configuration which should
not be committed to the repository, namely, signing and upload credentials. In
particular, the `com.bmuschko.nexus` plugin expects the following to be
available as project properties:

- `signing.keyId`               (required)
- `signing.secretKeyRingFile`   (required)
- `signing.password`            (optional)
- `nexusUsername`               (optional)
- `nexusPassword`               (optional)

If an "optional" property is not set on the project, then the plugin will
interactively prompt the user for this information during the build.

These properties need to be to be configured on the developer's system if
he/she wants to release the project to Maven Central. This project expect these
project properties to be set in an auxiliary build script, `releasing.gradle`.

Some notes on the `releasing.gradle` script:

- If this script exists, it will be applied to this project. If it does not
exist, then attempts to upload archives will fail.
- Because this script may contain sensitive information, it should never be
committed to the Git repository.
- The script should set the desired project properties using the project's
`ext` configuration block, that is, the project's `ExtraPropertiesExtension`.
