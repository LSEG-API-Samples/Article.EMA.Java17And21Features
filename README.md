# Demonstrating Java 17 and Java 21 using the EMA Consumer Application 

- Version: 1.0
- Last updated: Sep 2026
- Prerequisite: Java SDK version 21 and RTO access

## Overview

Java has evolved significantly from Java 11 LTS to Java 17 LTS and Java 21 LTS. Each release introduces bug fixes, performance improvements, and new language features that enhance developer productivity, code readability, and application maintainability. 

This Proof of Concept (POC) project demonstrates selected features introduced in Java 17 and Java 21 using an Enterprise Message API (EMA) Consumer application. Its objective is to help developers understand the benefits of newer Java versions and support their adoption through practical examples that improve code readability and maintainability.

### Note

This POC is intended for demonstration and educational purposes only and is **not designed for production use**. The source code prioritizes clarity and ease of understanding over performance, scalability, and production-grade practices. Do not use this project as-is in a production environment. The author assumes no responsibility for any issues arising from its use.

## What Does the Consumer Application Demonstrate?

The consumer application is based on the [EMA Java ex113_MP_SessionMgmt](https://github.com/Refinitiv/Real-Time-SDK/tree/master/Java/Ema/Examples/src/main/java/com/refinitiv/ema/examples/training/consumer/series100/ex113_MP_SessionMgmt) example. It demonstrates the following workflow:

- Authenticates and connects to Real-Time Optimized (RTO) through an encrypted EMA session.
- Models single-item and batch field-View requests using Java 17 sealed interfaces and records, then dispatches them using a Java 21 record-pattern switch.
- Clones Refresh and Update messages, processes them in bounded batches on virtual-thread workers, and converts them asynchronously to JSON.
- Prints domain-decoded Login Refresh and Login Status messages directly, prints state-bearing non-login Status messages as EMA text, and ignores non-login Status messages without state.
- Packages the application and its runtime dependencies as one executable shaded JAR.

## Prerequisites

- JDK 21 (Check the [API Compatibility Matrix](https://developers.lseg.com/en/api-catalog/real-time-opnsrc/rt-sdk-java/documentation#api-compatibility-matrix) document for supported vendors and versions)
- Apache Maven
- LSEG Real-Time Optimized Service-ID/JWK credentials and the required service entitlements
- Network access to the RTO endpoints
- Support OS Platforms (Check the [API Compatibility Matrix](https://developers.lseg.com/en/api-catalog/real-time-opnsrc/rt-sdk-java/documentation#api-compatibility-matrix) document)

Please contact your LSEG Representative or Account Manager to help you with RTO access.

Confirm the installed tools:

```bash
java -version
mvn -version
```

### Prerequisites Note 

This project code does not cover the Machine-ID type account (see [Account authorization V1 to V2 migration cheat sheet](https://developers.lseg.com/en/article-catalog/article/account-authorization-v1-to-v2-migration-cheat-sheet) article). 

## Configuration

`EmaConfig.xml` defines the EMA consumers, channels, and dictionaries. The application selects the `Consumer_RTO` consumer. You can set the RTO endpoint region via the `Location` property. 

```xml
<Channel>
	<Name value="Channel_RTO"/>
	<ChannelType value="ChannelType::RSSL_ENCRYPTED"/>
	<CompressionType value="CompressionType::None"/>
	<GuaranteedOutputBuffers value="5000"/>
	<!-- EMA discovers a host and a port from LDP service discovery for the specified
                   location when both of them are not set and the session management is enable. -->
	<Location value="ap-southeast-1"/>
	<EnableSessionManagement value="1"/>
	<ObjectName value=""/>
</Channel>
```

Please check the [Real-Time - Optimized Install and Config Guide](https://developers.lseg.com/en/api-catalog/real-time-opnsrc/rt-sdk-java/documentation#refinitiv-real-time-optimized-install-and-config-guide) for the list of supported regions.

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

## Credentials

### Recommended: environment variables

The POC application supports the following combination of RTO Version 2 Authentication:

- `Client ID` (aka Service-ID) + `Client Secret`
- `Client ID` + `JWK encoded public key`


The example code reads the client ID from these environment variables:

- `RTO_CLIENTID_V2_1`
- `RTO_CLIENTSECRET_1`

The JWK file is supported through the following application argument

- `-jwkFile file containing the private JWK encoded in JSON format`

> **Security warning:** A private JWK JSON file contains sensitive private-key material. Never commit it to source control. Store it securely outside the repository or use an external secret manager.

Startup ends before opening an RTO session when the Client ID is absent or blank, or when neither a Client Secret nor a JWK file is provided. When a JWK file is provided, the application does not apply the Client Secret.

Set them for the current terminal session:

**Powershell**

```powershell
$env:RTO_CLIENTID_V2_1 = "your-client-id"
$env:RTO_CLIENTSECRET_1 = "your-client-secret"
```

**Linux**

```bash
export RTO_CLIENTID_V2_1="your-client-id"
export RTO_CLIENTSECRET_1="your-client-secret"
```

### Manual source-code configuration

For local demonstration purposes, the client ID and client secret can instead be assigned directly in `ConsumerRTO.java`. In `applyEnvironmentCredentials()` method, replace the environment-variable lookups with your values:

```java
String clientId = "your-client-id";
String clientSecret = "your-client-secret";
```

Line numbers can change as the source evolves; locate the `applyEnvironmentCredentials()` method rather than relying only on the numbers.

> **Security warning:** Hard-coded credentials can be exposed through source control, build logs, or decompiled JAR files. Use this approach only for temporary local testing. Never commit real credentials. Environment variables or an external secret manager are recommended.

## Build

The Maven Shade Plugin packages the application, EMA, and required runtime libraries into the primary executable JAR:

```powershell
mvn clean package
```

The executable artifact is **target/ema-consumer-java21-1.0.jar** file.

## Run

Run the executable JAR from the project root so EMA can find `EmaConfig.xml`:

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

### Command-line options

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

## Java feature demonstration

`MessageProcessingPipeline` demonstrates Java 17 records and sealed interfaces through its closed
Refresh/Update queue model. It also uses a Java 21 exhaustive pattern-matching `switch` and Java 21
virtual threads for asynchronous batch processing.

### Virtual-thread JSON processing

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

`MessageProcessingPipeline` also demonstrates modern Java language features. Its private sealed
`QueuedMessage` interface permits only the `QueuedRefresh` and `QueuedUpdate` record
implementations. Records provide immutable carriers for the message sequence, captured callback
context, and cloned EMA message. An exhaustive pattern-matching `switch` maps each permitted
message type to its JSON name without requiring a `default` branch. Sealed interfaces and records
are Java 17 features; the pattern-matching `switch` used by this project is a Java 21 feature.

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
- `MessageProcessingPipeline.java` owns bounded FIFO queue admission, batch processing, virtual
  workers, overload counting, shutdown draining, and the sealed queued-message hierarchy.
- `EmaJsonConverter.java` performs stateless, worker-safe EMA item-message conversion.

## License

Copyright 2020–2026 LSEG.

Licensed under the [Apache License 2.0](LICENSE.md).

