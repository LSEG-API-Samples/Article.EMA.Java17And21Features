# EMA Java Project Instructions

## Project and runtime context

- Treat this repository as a Java 21 Maven example using the LSEG Enterprise Message API (EMA) to connect to the Real-Time Optimized (RTO) service.
- Compile with `maven.compiler.release` set to `21`. Do not introduce preview features unless explicitly requested and configured.
- Check the LSEG RTSDK Java API Compatibility Matrix before changing the JDK vendor, JDK version, or EMA release compatibility documented in `README.md`.
- Assume that running the consumer requires RTO credentials, appropriate service entitlements, and network access to RTO endpoints.
- Preserve the package namespace `com.lseg.ema.example`, the existing Java formatting and naming style, and small, focused changes over unrelated refactoring.
- Use the EMA version from the `rtsdk.version` Maven property; do not duplicate that dependency version elsewhere.
- Preserve `com.lseg.ema.example.ConsumerRTO` as the executable main class unless a requested change explicitly replaces the entry point.

## EMA behavior and configuration

- Keep `EmaConfig.xml` external at the project root. Do not copy it into `src/main/resources`, `target/classes`, or the executable JAR.
- Run and smoke-test the application from the project root because EMA discovers `EmaConfig.xml` from the process working directory.
- Preserve the `Consumer_RTO` configuration selected by the application unless a requested change intentionally replaces it.
- Preserve encrypted RTO session management, single-item and batch requests, field ID views, and login, refresh, update, status, generic, and acknowledgment callback handling.
- Maintain correct EMA lifecycle cleanup: always uninitialize an `OmmConsumer` that was successfully created, restore interruption status after `InterruptedException`, and do not expose sensitive values in errors or logs.

## Credentials and security

- Prefer the `RTO_CLIENTID_V2_1` and `RTO_CLIENTSECRET_1` environment variables for OAuth client credentials.
- Direct credential assignments in `ConsumerRTO.applyEnvironmentCredentials()` are temporary local demonstrations only. Always warn that hard-coded credentials can be recovered from source control or a decompiled JAR and must never be committed.
- Preserve JWK authentication through `-jwkFile` and `-audience` unless a requested change intentionally removes it.
- Never add real client IDs, client secrets, JWK contents, keystore passwords, or proxy passwords to source, documentation, tests, logs, or committed configuration.
- Keep credentials and environment-specific values out of `EmaConfig.xml` where practical, and recommend external secret management for production deployments.

## Command-line behavior

- Preserve the default service `ELEKTRON_DD` and default item `/THB=` unless the requested change also updates the code, help output, and `README.md`.
- Preserve support for `-?`, `-serviceName`, `-itemNames`, `-jwkFile`, `-audience`, `-keyfile`, `-keypasswd`, `-securityProvider`, `-spTLSv1.2`, `-spTLSv1.3`, `-ph`, `-pp`, `-plogin`, `-ppasswd`, `-pdomain`, and `-krbfile` unless intentionally changed.
- Validate option values before applying them to EMA configuration. Keep `Utility.printHelp()`, argument parsing, examples, and the `README.md` option table synchronized.
- Preserve the default behavior that enables both TLS 1.2 and TLS 1.3 when neither TLS-specific option is supplied.

## Build and packaging

- Preserve Maven Shade packaging so `target/ema-consumer-java21-1.0.jar` remains the primary executable JAR containing the application, EMA, and required runtime dependencies.
- Treat `target/original-ema-consumer-java21-1.0.jar` as an unnecessary thin-JAR build byproduct, not a deployment artifact.
- Preserve service-descriptor merging, dependency signature exclusions, and the `ConsumerRTO` manifest entry in the Shade configuration.
- Do not enable JAR minimization because EMA or its dependencies may load classes reflectively.
- Do not edit or commit generated content under `target/`.

## Documentation, licensing, and validation

- Add Apache-2.0 headers to new Java files, matching the existing copyright style, and keep licensing references consistent with `LICENSE.md`.
- Update `README.md` whenever runtime requirements, credentials, configuration behavior, build commands, artifact names, defaults, examples, or command-line options change.
- After Java or Maven changes, run `mvn test`.
- After packaging, manifest, or runtime startup changes, run `mvn clean package` and smoke-test from the project root with `java -jar .\target\ema-consumer-java21-1.0.jar "-?"`.
- Review Maven dependency changes for known vulnerabilities and compatibility with the selected EMA release. Do not suppress security findings without documenting the reason.
