# Build and release

## Requirements

- JDK 17 or later
- Maven 3.6 or later

## Commands

```bash
mvn test                 # run the full suite (38 tests)
mvn package              # build the jar
mvn install              # install to your local repository
```

## Publishing

Releases go to Maven Central under `io.github.13liyunfei`:

```bash
mvn -Prelease clean verify    # sign and check the artifacts
mvn -Prelease deploy          # publish
```

The `release` profile adds the sources jar, the javadoc jar, GPG signing, and the Central Portal publishing plugin. It is inactive during normal builds, so `mvn test` never requires a signing key.

Publishing requires three things configured outside the repository:

1. A Central Portal user token in `~/.m2/settings.xml` under server id `central`
2. A GPG key, with its id and passphrase in the `release` profile
3. The `io.github.13liyunfei` namespace claimed and verified on central.sonatype.com

## Versioning

Semantic versioning. Published artifacts are immutable — a released version cannot be replaced or removed, so verify before deploying.
