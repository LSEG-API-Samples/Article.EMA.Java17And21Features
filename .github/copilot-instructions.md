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

### AppClient callback and test behavior

- Preserve direct login handling in `AppClient`: decode Login Refresh and Login Status messages with EMA domain objects and send their text to `outputConsumer`.
- Append `"\n"` to directly emitted Login Refresh, Login Status, and non-login Status text.
- Continue cloning non-login Refresh and Update messages immediately and submitting them to `MessageProcessingPipeline` for asynchronous JSON conversion.
- Keep all Status messages outside `MessageProcessingPipeline` and `EmaJsonConverter`.
- For non-login Status callbacks, emit `statusMsg.toString()` only when `statusMsg.hasState()` is `true`; ignore non-login Status messages without state.
- Keep `AppClientTest` synchronized with callback behavior. Test domain-decoded Login Refresh and Login Status output, direct state-bearing non-login Status output, and no output for a non-login Status without state.
- Use try-with-resources for `AppClient` tests so the virtual-thread pipeline is always closed.

### MessageProcessingPipeline behavior

- Keep `MessageProcessingPipeline` focused on bounded queue admission, batching, asynchronous JSON conversion, overload reporting, and graceful shutdown.
- Preserve the private sealed `QueuedMessage` hierarchy with the `QueuedRefresh` and `QueuedUpdate` record implementations. It intentionally demonstrates Java 17 sealed interfaces and records.
- Preserve the exhaustive Java 21 pattern-matching `switch` in `messageType()` without adding a redundant `default` branch.
- Preserve the shared bounded `LinkedBlockingQueue`, drop-newest behavior, monotonic callback sequence, and captured immutable `EventContext`.
- Preserve batch processing through `BATCH_SIZE` and `drainTo`; do not replace batching with one-message-at-a-time processing unless intentionally changing throughput behavior.
- Preserve long-running virtual-thread workers managed by `Executors.newVirtualThreadPerTaskExecutor()`.
- Keep queue admission synchronized with shutdown so a message cannot be accepted after draining has begun.
- Continue processing accepted messages during normal shutdown until the queue is empty.
- Keep per-message conversion failures isolated so one failure does not terminate a worker.
- After `InterruptedException`, restore interruption status with `Thread.currentThread().interrupt()`.
- Keep worker count, queue capacity, batch size, polling interval, and shutdown timeout as named tuning constants rather than embedding unexplained numeric values.
- Keep `README.md` synchronized when changing pipeline concurrency, admission, batching, ordering, overload, shutdown, or Java-feature demonstration behavior.

### ConsumerRTO request model

- Preserve the private sealed `ConsumerRTO.ItemRequest` hierarchy with `SingleItemRequest` and `BatchItemRequest` record implementations unless a requested design change requires additional request types.
- Construct requests through `ItemRequest.create()`, which rejects null or empty item collections, validates nonblank service and item names, strips surrounding whitespace, and selects the single-item or batch record from the item count.
- Keep `SingleItemRequest` limited to exactly one item and `BatchItemRequest` limited to at least two items; retain an immutable defensive copy of batch item names.
- Keep `ConsumerRTO.registerItemRequest()` exhaustive by using a Java 21 record-pattern `switch` over every permitted `ItemRequest` implementation without a redundant `default` branch.
- Keep the hierarchy nested and private while it is used only by `ConsumerRTO`; extract it only when another component needs the request model or direct package-level testing is required.

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

## Java coding conventions and style

### Naming
- Class names use `UpperCamelCase` (e.g., `ConsumerRTO`, `AppClient`).
- Method and variable names use `lowerCamelCase` (e.g., `applyEnvironmentCredentials`, `itemNames`).
- Constants use `UPPER_SNAKE_CASE` and are declared `private static final` (e.g., `CONSUMER_NAME`, `RUN_DURATION`).
- Package names are all lowercase with dot separators: `com.lseg.ema.example`.

### Code structure
- Keep classes small and focused on a single responsibility.
- Use `private static` for methods that are utility helpers within the class and do not need instance state.
- Prefer early-return guard clauses over deeply nested conditionals.
- Use `switch` expressions with arrow labels (`->`) rather than fall-through `switch` statements.
- Use `var` only when the inferred type is immediately obvious at the point of declaration; prefer explicit types in method signatures, fields, and catch clauses.

### Formatting
- Use 4-space indentation; do not use tabs.
- Place opening braces on the same line as the declaration or control statement.
- Omit braces only for single-statement blocks when the body fits on one line and remains readable; be consistent with surrounding code.
- Limit lines to a readable length; wrap chained method calls by aligning continuation at the opening parenthesis or by one extra indent level.
- Add a blank line between methods and between logically distinct blocks within a method.

### Javadoc and comments
- Write `/** ... */` Javadoc on `public` and package-private classes and methods.
- Use inline `//` comments only to explain non-obvious intent, not to restate what the code already says.
- Place field-level inline comments after the value on the same line when annotating constants (e.g., `22, // BID`).

### Imports
- Group imports: standard library first, then third-party, then internal; separate groups with a blank line.
- Do not use wildcard imports.
- Remove unused imports.

### Exception handling
- Catch the most specific exception type available; avoid catching `Exception` or `Throwable` unless required.
- After catching `InterruptedException`, restore the interrupted status with `Thread.currentThread().interrupt()` before returning or re-throwing.
- Do not swallow exceptions silently; log or re-throw with context.
- Use `finally` blocks or try-with-resources to guarantee resource cleanup.

### Collections and streams
- Prefer the Stream API and method references for filtering, mapping, and collecting when the result is clearer than an equivalent loop.
- Use `Arrays.stream`, `List.of`, and `Map.of` factory methods for immutable collections.
- Avoid raw types; always supply type parameters.

### Modern Java features
- Use Java 17 records for immutable data carriers.
- Use Java 17 sealed interfaces with Java 21 pattern-matching `switch` expressions when modeling closed type hierarchies.
- Use `String.isBlank()` instead of `isEmpty()` to guard against whitespace-only values.
- Use text blocks for multi-line string literals (e.g., `printHelp()` output).
- Use Java 21 virtual threads for lightweight concurrent tasks where appropriate.

## Documentation, licensing, and validation

- Add Apache-2.0 headers to new Java files, matching the existing copyright style, and keep licensing references consistent with `LICENSE.md`.
- Update `README.md` whenever runtime requirements, credentials, configuration behavior, build commands, artifact names, defaults, examples, or command-line options change.
- After Java or Maven changes, run `mvn test`.
- After packaging, manifest, or runtime startup changes, run `mvn clean package` and smoke-test from the project root with `java -jar .\target\ema-consumer-java21-1.0.jar "-?"`.
- Review Maven dependency changes for known vulnerabilities and compatibility with the selected EMA release. Do not suppress security findings without documenting the reason.
