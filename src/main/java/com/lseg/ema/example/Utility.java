/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.lseg.ema.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Predicate;

import com.refinitiv.ema.access.OmmConsumerConfig;

/** Parses command-line options, applies them to the EMA configuration, and stores the request defaults. */
final class Utility {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65_535;

    static String clientJwk;
    static String audience;
    static String serviceName = "ELEKTRON_DD";
    static String[] itemNames = {"/THB="};

    private static boolean hasJWK;

    /** Outcome of command-line parsing. */
    enum CommandLineResult {
        /** Options are valid; continue startup. */
        CONTINUE,
        /** Help was requested and printed; stop successfully. */
        HELP_SHOWN,
        /** An option was invalid; stop with a failure exit code. */
        INVALID
    }

    /** Prevents creation of this static helper class. */
    private Utility() {
    }

    /** Prints the supported command-line options. */
    static void printHelp() {
        System.out.println("""
                
                Options:
                  -?\tShows this usage
                  -serviceName service name to request (optional, default: ELEKTRON_DD).
                  -itemNames Comma-separated list of request item names (optional, default: /THB=, example: AUD=,HKD=).
                  -jwkFile file containing the private JWK encoded in JSON format (optional for V2 client credentials grant with JWT).
                  -audience Audience value for JWT (optional, for V2 oAuth client credentials with JWT).
                  -keyfile keystore file for encryption (optional).
                  -keypasswd keystore password for encryption (optional).
                  -securityProvider security provider for encrypted connection (SunJSSE and Conscrypt options are currently supported).
                  -spTLSv1.2 Enable TLS 1.2 security protocol. Default enables both TLS 1.2 and TLS 1.3 (optional).
                  -spTLSv1.3 Enable TLS 1.3 security protocol. Default enables both TLS 1.2 and TLS 1.3 (optional).
                
                Optional parameters for establishing a connection through a proxy server:
                  -ph Proxy host name (optional).
                  -pp Proxy port number (optional).
                  -plogin User name on proxy server (optional).
                  -ppasswd Password on proxy server (optional).
                  -pdomain Proxy Domain (optional).
                  -krbfile KRB File location and name. Needed for Negotiate/Kerberos and Kerberos authentications (optional).
                """);
    }

    /**
     * Reads command-line options and applies them to the EMA configuration.
     *
     * @param args   command-line arguments
     * @param config EMA consumer configuration to update
     * @return whether to continue, stop after help, or stop because an option is invalid
     */
    static CommandLineResult readCommandlineArgs(String[] args, OmmConsumerConfig config) {
        try {
            boolean tls12 = false;
            boolean tls13 = false;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-?" -> {
                        printHelp();
                        return CommandLineResult.HELP_SHOWN;
                    }
                    case "-serviceName" -> serviceName = nextArg(args, i++);
                    case "-itemNames" -> itemNames = parseItemNames(nextArg(args, i++));
                    case "-jwkFile" -> loadJwkFile(nextArg(args, i++), config);
                    case "-audience" -> config.audience(audience = nextArg(args, i++));
                    case "-keyfile" -> config.tunnelingKeyStoreFile(nextArg(args, i++));
                    case "-keypasswd" -> config.tunnelingKeyStorePasswd(nextArg(args, i++));
                    case "-securityProvider" -> config.tunnelingSecurityProvider(nextArg(args, i++));
                    case "-ph" -> config.tunnelingProxyHostName(nextArg(args, i++));
                    case "-pp" -> config.tunnelingProxyPort(validatePort(nextArg(args, i++)));
                    case "-plogin" -> config.tunnelingCredentialUserName(nextArg(args, i++));
                    case "-ppasswd" -> config.tunnelingCredentialPasswd(nextArg(args, i++));
                    case "-pdomain" -> config.tunnelingCredentialDomain(nextArg(args, i++));
                    case "-krbfile" -> config.tunnelingCredentialKRB5ConfigFile(nextArg(args, i++));
                    case "-spTLSv1.2" -> tls12 = true;
                    case "-spTLSv1.3" -> tls13 = true;
                    default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
            }

            // Set security protocol versions of TLS based on configured values, with default having TLS 1.2 and 1.3 enabled
            config.tunnelingSecurityProtocol("TLS");
            String[] tlsVersions = (tls12 == tls13)
                    ? new String[]{"1.2", "1.3"}
                    : tls12 ? new String[]{"1.2"} : new String[]{"1.3"};
            config.tunnelingSecurityProtocolVersions(tlsVersions);
        } catch (IllegalArgumentException exception) {
            System.err.println("Invalid command line: " + exception.getMessage());
            printHelp();
            return CommandLineResult.INVALID;
        }

        return CommandLineResult.CONTINUE;
    }

    /** Loads a private JWK file and applies it to the consumer configuration. */
    private static void loadJwkFile(String jwkFile, OmmConsumerConfig config) {
        try {
            clientJwk = Files.readString(Path.of(jwkFile));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read JWK file: " + jwkFile, exception);
        }

        System.out.println("Loaded JWK file: " + jwkFile);
        config.clientJWK(clientJwk);
        hasJWK = true;
    }

    /** Returns the nonblank value that follows the option at {@code index}. */
    private static String nextArg(String[] args, int index) {
        if (index + 1 >= args.length || args[index + 1].isBlank())
            throw new IllegalArgumentException("Missing value for option " + args[index]);
        return args[index + 1];
    }

    /** Returns the port unchanged after checking that it is a valid TCP port number. */
    private static String validatePort(String port) {
        String portRangeMessage = "Proxy port must be a number between %d and %d.".formatted(MIN_PORT, MAX_PORT);
        int portNumber;
        try {
            portNumber = Integer.parseInt(port.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(portRangeMessage, exception);
        }

        if (portNumber < MIN_PORT || portNumber > MAX_PORT)
            throw new IllegalArgumentException(portRangeMessage);
        return port.strip();
    }

    /** Splits a comma-separated item list such as {@code "EUR=,THB="} into trimmed, nonblank names. */
    private static String[] parseItemNames(String rawItemNames) {
        String[] parsedItemNames = Arrays.stream(rawItemNames.split(","))
                .map(String::strip)
                .filter(Predicate.not(String::isBlank))
                .toArray(String[]::new);

        if (parsedItemNames.length == 0)
            throw new IllegalArgumentException("At least one item name is required.");
        return parsedItemNames;
    }

    /** Returns whether a private JWK was loaded from {@code -jwkFile}. */
    static boolean hasJWK() {
        return hasJWK;
    }
}
