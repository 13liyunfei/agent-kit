# Build & Release

## Requirements

| Dependency | Version | Notes |
| --- | --- | --- |
| JDK | 17+ | Compile target Java 17 |
| Maven | 3.9+ | Build / test / release |
| Network | optional | First build pulls jackson / slf4j / junit (offline with `-o`) |

## Build commands

```bash
# Unit tests (38 cases, offline-capable)
mvn test

# Package jar (target/agent-kit-0.1.0.jar)
mvn package

# Install to local Maven repo (~/.m2/repository/io/github/13liyunfei/agent-kit/0.1.0/)
mvn install

# Fast package without tests
mvn package -DskipTests
```

> Consumers must run `mvn install` first (or configure a private repository) before depending on `io.github.13liyunfei:agent-kit:0.1.0` in their `pom.xml`.

## Versioning

- Current version: `0.1.0` (first reusable milestone)
- Version is declared in `<version>` of `pom.xml`; when releasing, keep in sync:
  - The dependency snippets in `README.md` (English) and `README.zh-CN.md` (Chinese)
  - The component tables in both READMEs if the component list changes
- Semantic versioning: `MAJOR.MINOR.PATCH`
  - MAJOR: breaking API changes (e.g. `ChatModel` signature)
  - MINOR: backward-compatible new components / extension points
  - PATCH: bug fixes

## Release process

```bash
# 1. Full test suite passes
mvn test

# 2. Package verification
mvn package

# 3. Install locally (for other local projects)
mvn install

# 4. (Optional) Publish to a private Nexus / central repository
mvn deploy   # requires repository credentials in settings.xml
```

## Pre-commit checklist

- [ ] `mvn test` all green (38 cases)
- [ ] New components registered in the component tables of `README.md` (English) and `README.zh-CN.md` (Chinese)
- [ ] New SPI extension points registered in the extension-point tables of both READMEs
- [ ] Packages follow `com.codereview.kit.*`; only JDK / jackson / slf4j dependencies
- [ ] Public APIs carry Javadoc explaining intent and usage
