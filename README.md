# Demonstrating Java 17 and Java 21 using the EMA Consumer Application 

- Version: 1.0
- Last updated: Sep 2026
- Prerequisite: Java SDK version 21 and RTO access

## Overview

Java has evolved significantly from Java 11 LTS to Java 17 LTS and Java 21 LTS. Each release introduces bug fixes, performance improvements, and new language features that enhance developer productivity, code readability, and application maintainability. 

This proof-of-concept Maven project uses the LSEG Enterprise Message API (EMA) to connect to Real-Time Optimized (RTO). It demonstrates practical Java 17 and Java 21 features in a small consumer application based on the [EMA Java ex113_MP_SessionMgmt](https://github.com/Refinitiv/Real-Time-SDK/tree/master/Java/Ema/Examples/src/main/java/com/refinitiv/ema/examples/training/consumer/series100/ex113_MP_SessionMgmt) example.

### Note

This POC is intended for demonstration and educational purposes only and is **not designed for production use**. The source code prioritizes clarity and ease of understanding over performance, scalability, and production-grade practices. Do not use this project as-is in a production environment. The author assumes no responsibility for any issues arising from its use.

## Features

- Encrypted RTO connections with EMA session management
- Single-item and batch field-view requests
- Java 17 records and sealed interfaces for closed request and message models
- Java 21 pattern-matching `switch` expressions and virtual threads
- Bounded, asynchronous conversion of Refresh and Update messages to JSON
- Direct handling of Login and Status messages
- One executable shaded JAR containing the application and runtime dependencies

## Prerequisites

- JDK 21 on an operating system supported by the [RTSDK Java API Compatibility Matrix](https://developers.lseg.com/en/api-catalog/real-time-opnsrc/rt-sdk-java/documentation#api-compatibility-matrix)
- Apache Maven
- LSEG RTO Service ID, client secret or private JWK, and required service entitlements
- Network access to the RTO endpoints

Contact your LSEG representative or account manager for RTO access. This example does not support Machine ID accounts; see the [Account authorization V1 to V2 migration cheat sheet](https://developers.lseg.com/en/article-catalog/article/account-authorization-v1-to-v2-migration-cheat-sheet).

Confirm the installed tools:

```bash
java -version
mvn -version
```

## Configuration

The external `EmaConfig.xml` file defines the EMA consumer, RTO channel, and dictionaries. The application selects `Consumer_RTO`, which uses an encrypted connection with session management enabled.

Keep `EmaConfig.xml` in the project root and run all commands from that directory. The default RTO location is `ap-southeast-1`; change the channel's `Location` value as needed. See the [Real-Time Optimized Install and Config Guide](https://developers.lseg.com/en/api-catalog/real-time-opnsrc/rt-sdk-java/documentation#refinitiv-real-time-optimized-install-and-config-guide) for supported regions.

## Credentials

The application supports these RTO Version 2 authentication combinations:

- Client ID (Service ID) and client secret
- Client ID and private JWK JSON file

Set the client ID and client secret for the current terminal session.

**PowerShell**

```powershell
$env:RTO_CLIENTID_V2_1 = "your-client-id"
$env:RTO_CLIENTSECRET_1 = "your-client-secret"
```

**Bash**

```bash
export RTO_CLIENTID_V2_1="your-client-id"
export RTO_CLIENTSECRET_1="your-client-secret"
```

For JWK authentication, set `RTO_CLIENTID_V2_1` and pass the private JWK file with `-jwkFile <file>`. When a JWK file is supplied, the application does not apply the client secret.

Startup stops before opening an RTO session if the client ID is blank or if neither a client secret nor a JWK file is provided.

> **Security:** Private JWK files and client secrets contain sensitive material. Never commit them to source control. Keep them outside the repository or use an external secret manager.

### Manual Credentials

For temporary local demonstrations, credentials can be assigned in the `ConsumerRTO.applyEnvironmentCredentials()` method:

```java
String clientId = "your-client-id";
String clientSecret = "your-client-secret";
```

> **Security:** Hard-coded credentials can be recovered from source control or a decompiled JAR. Never commit real credentials.

## Build and Test

Run the tests, then build the executable shaded JAR:

```bash
mvn test
mvn clean package
```

The executable artifact is `target/ema-consumer-java21-1.0.jar`.

## Run

Run the application from the project root:

```bash
java -jar target/ema-consumer-java21-1.0.jar
```

Show command-line help without opening an RTO session:

```bash
java -jar target/ema-consumer-java21-1.0.jar "-?"
```

Request one item:

```bash
java -jar target/ema-consumer-java21-1.0.jar -serviceName ELEKTRON_DD -itemNames "/THB="
```

Request a batch of items:

```bash
java -jar target/ema-consumer-java21-1.0.jar -serviceName ELEKTRON_DD -itemNames "AUD=,HKD=,JPY="
```

### Command-Line Options

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

## Output and Processing

EMA Refresh and Update callbacks clone messages and submit them to a bounded queue for batched JSON conversion on virtual-thread workers. Login Refresh and Login Status messages are printed directly in their domain-decoded form. State-bearing non-login Status messages are also printed directly; Status messages without state are ignored.

Concurrent workers may emit JSON lines in a different order from callback arrival. Each JSON object includes a monotonic `sequence` value. If the queue reaches capacity, the newest incoming message is dropped. During normal shutdown, accepted messages are drained before the worker executor closes.

| Setting | Default |
|---|---:|
| Worker count | `5` |
| Queue capacity | `1,000` |
| Batch size | `50` |

## Project Structure

- `EmaConfig.xml`: external EMA consumer, channel, and dictionary configuration
- `pom.xml`: Java 21 build, EMA dependency, tests, and shaded-JAR packaging
- `src/main/java/com/lseg/ema/example/ConsumerRTO.java`: application entry point, credentials, requests, and lifecycle
- `src/main/java/com/lseg/ema/example/AppClient.java`: EMA callback handling
- `src/main/java/com/lseg/ema/example/MessageProcessingPipeline.java`: bounded asynchronous processing
- `src/main/java/com/lseg/ema/example/EmaJsonConverter.java`: JSON conversion
- `src/main/java/com/lseg/ema/example/Utility.java`: command-line parsing and JWK loading

## License

Copyright 2020–2026 LSEG.

Licensed under the [Apache License 2.0](LICENSE.md).

