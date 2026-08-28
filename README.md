# Demonstrating Java 17 and Java 21 using the EMA Consumer Application 

This project demonstrate the Java 17 and Java 21 new features via the Enterprise Message API (EMA) Consumer Application. 

The project goal is to show a simple demonstration of the new features in Java 17 and Java 21 that help developers to adopt new version of Java for the code readability and maintainability.

The consumer application performs the following features:
- OAuth client authentication
- An encrypted RTO connection with session management
- Single-item and batch requests
- Field ID views
- Login, refresh, update, status, generic, and acknowledgment callbacks
  - Asynchronous Refresh and Update conversion to JSON with Java 21 virtual threads
  - Direct login and state-bearing non-login Status output
- Packaging the application and its runtime dependencies as one executable JAR

## Prerequisites

- JDK 21 (Check the [API Compatibility Matrix](https://developers.lseg.com/en/api-catalog/real-time-opnsrc/rt-sdk-java/documentation#api-compatibility-matrix) document for supported vendors and versions)
- Apache Maven
- LSEG Real-Time Optimized credentials and the required service entitlements
- Network access to the RTO endpoints

Confirm the installed tools:

```powershell
java -version
mvn -version
```

## Configuration

`EmaConfig.xml` defines the EMA consumers, channels, and dictionaries. The application selects the `Consumer_RTO` consumer.

Keep `EmaConfig.xml` in the process working directory. When using the commands in this README, run them from the project root:

```text
Java_17_21_EMA/
├── .github/copilot-instructions.md
├── EmaConfig.xml
├── pom.xml
├── README.md
├── LICENSE.md
├── src/
└── target/
```

`EmaConfig.xml` is intentionally external and is not embedded in the executable JAR.

## Credentials

### Recommended: environment variables

`ConsumerRTO` reads the client ID and client secret from these environment variables:

- `RTO_CLIENTID_V2_1`
- `RTO_CLIENTSECRET_1`

Set them for the current PowerShell session:

```powershell
$env:RTO_CLIENTID_V2_1 = "your-client-id"
$env:RTO_CLIENTSECRET_1 = "your-client-secret"
```

### Manual source-code configuration

For local demonstration purposes, the client ID and client secret can instead be assigned directly in `ConsumerRTO.java`. In `applyEnvironmentCredentials()`, replace the environment-variable lookups (currently lines 49 and 50) with your values:

```java
String clientId = "your-client-id";
String clientSecret = "your-client-secret";
```

Line numbers can change as the source evolves; locate the `applyEnvironmentCredentials()` method rather than relying only on the numbers.

> **Security warning:** Hard-coded credentials can be exposed through source control, build logs, or decompiled JAR files. Use this approach only for temporary local testing. Never commit real credentials. Environment variables or an external secret manager are recommended.

The application also supports OAuth client authentication with a JWK file through the `-jwkFile` and `-audience` options.

## Build

The Maven Shade Plugin packages the application, EMA, and required runtime libraries into the primary executable JAR:

```powershell
mvn clean package
```

The executable artifact is:

```text
target/ema-consumer-java21-1.0.jar
```

Maven Shade also retains the original thin application JAR as a build byproduct:

```text
target/original-ema-consumer-java21-1.0.jar
```

The `original-` JAR is not required to run or distribute the application.

## Run

Run the executable JAR from the project root so EMA can find `EmaConfig.xml`:

```powershell
java -jar .\target\ema-consumer-java21-1.0.jar
```

Show command-line help without opening an RTO session:

```powershell
java -jar .\target\ema-consumer-java21-1.0.jar "-?"
```

Request one item:

```powershell
java -jar .\target\ema-consumer-java21-1.0.jar -serviceName ELEKTRON_DD -itemNames "/THB="
```

Request a batch of items:

```powershell
java -jar .\target\ema-consumer-java21-1.0.jar -serviceName ELEKTRON_DD -itemNames "AUD=,HKD=,JPY="
```

## Virtual-thread JSON processing

EMA invokes the Refresh, Update, and Status callbacks on its dispatch thread. Refresh and Update
callbacks clone the message immediately and submit it to a bounded in-memory queue; five
long-running virtual-thread workers drain the queue in batches and write one JSON object per message
to standard output. The worker executor manages the virtual threads rather than creating a new
thread for every message. Login Refresh and Login Status messages bypass this pipeline and are
printed in their EMA domain-decoded text representation. Non-login Status messages with state are
printed directly in their original EMA text representation; non-login Status messages without state
are ignored. Status messages are never routed through data processing.

The example defaults in `MessageProcessingPipeline.java` are deliberately easy to find and tune:

| Constant | Default | Purpose |
|---|---:|---|
| `WORKER_COUNT` | `5` | Number of concurrent JSON workers. |
| `QUEUE_CAPACITY` | `1_000` | Maximum number of cloned messages waiting for processing. |
| `BATCH_SIZE` | `50` | Maximum messages claimed by a worker per queue operation. |

Refresh and Update messages share the full queue capacity and are admitted in arrival order. When
the queue is full, the incoming message is dropped regardless of its type. Shutdown statistics report
the total number of dropped messages without logging every overload event.

Because five workers process concurrently, JSON lines can be emitted in a different order from
callback arrival. Every line contains a monotonic `sequence` value that identifies arrival order.
The `itemName` property contains the EMA message name, or `<not set>` when no name is present.
Field-list attributes and payloads are emitted as structured fields; other EMA container types are
identified by `dataType`. Each field entry uses `FID` for `fieldEntry.fieldId()` and `FieldName` for
`fieldEntry.name()`.

During shutdown, `ConsumerRTO` first uninitializes the EMA consumer to stop callbacks, then drains
the accepted messages and closes the virtual-thread executor.

The implementation separates callback, concurrency, and conversion responsibilities:

- `AppClient.java` handles EMA callbacks, direct login Refresh and Status output, and immediate
  Refresh/Update cloning.
- `MessageProcessingPipeline.java` owns FIFO queue admission, batching, virtual workers, overload
  counting, and shutdown.
- `EmaJsonConverter.java` performs stateless, worker-safe EMA item-message conversion.

## Command-line options

| Option | Description |
|---|---|
| `-?` | Display usage information. |
| `-serviceName <name>` | Service to request; defaults to `ELEKTRON_DD`. |
| `-itemNames <items>` | Comma-separated item names; defaults to `/THB=`. |
| `-jwkFile <file>` | Private JWK file for OAuth client authentication with JWT. |
| `-audience <value>` | JWT audience. |
| `-keyfile <file>` | Keystore used by the encrypted connection. |
| `-keypasswd <password>` | Keystore password. |
| `-securityProvider <name>` | TLS provider, such as SunJSSE or Conscrypt. |
| `-spTLSv1.2` | Enable only TLS 1.2 unless TLS 1.3 is also supplied. |
| `-spTLSv1.3` | Enable only TLS 1.3 unless TLS 1.2 is also supplied. |
| `-ph <host>` | Proxy host. |
| `-pp <port>` | Proxy port. |
| `-plogin <user>` | Proxy username. |
| `-ppasswd <password>` | Proxy password. |
| `-pdomain <domain>` | Proxy domain. |
| `-krbfile <file>` | Kerberos configuration file. |

When neither TLS version option is supplied, both TLS 1.2 and TLS 1.3 are enabled.

## Project structure

- `.github/copilot-instructions.md` — repository-wide Copilot guidance for this project
- `ConsumerRTO.java` — application entry point, credentials, requests, and lifecycle
- `AppClient.java` — EMA callbacks, direct Status output, login Refresh decoding, and data-message cloning
- `MessageProcessingPipeline.java` — bounded queue and virtual-thread processing
- `EmaJsonConverter.java` — stateless EMA item-message JSON conversion
- `Utility.java` — command-line parsing and optional JWK loading
- `EmaConfig.xml` — external EMA consumer, channel, and dictionary configuration
- `pom.xml` — Java 21 compilation, EMA dependency, and executable JAR packaging

## Things To Do Next

- Update code to demonstrate Java 17 sealed classes, records, and pattern matching

## License

Copyright 2020–2026 LSEG.

Licensed under the [Apache License 2.0](LICENSE.md).

