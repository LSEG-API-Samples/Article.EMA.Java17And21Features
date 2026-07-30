/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.lseg.ema.example;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Predicate;

import com.refinitiv.ema.access.OmmConsumerConfig;
import com.refinitiv.eta.codec.CodecReturnCodes;

public class Utility {

    static String clientJwk;
    static String audience;
    static String serviceName = "ELEKTRON_DD";
    static String[] itemNames = {"/THB="};

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

    static boolean readCommandlineArgs(String[] args, OmmConsumerConfig config) {
        try {
            boolean tls12 = false;
            boolean tls13 = false;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-?" -> {
                        printHelp();
                        return false;
                    }
                    case "-serviceName" -> serviceName = nextArg(args, i++);
                    case "-itemNames" -> {
                        itemNames = parseItemNames(nextArg(args, i++));
                        if (itemNames.length == 0)
                            throw new IllegalArgumentException("At least one item name is required.");
                    }
                    case "-jwkFile" -> loadJwkFile(nextArg(args, i++), config);
                    case "-audience" -> config.audience(audience = nextArg(args, i++));
                    case "-keyfile" -> config.tunnelingKeyStoreFile(nextArg(args, i++));
                    case "-keypasswd" -> config.tunnelingKeyStorePasswd(nextArg(args, i++));
                    case "-securityProvider" -> config.tunnelingSecurityProvider(nextArg(args, i++));
                    case "-ph" -> config.tunnelingProxyHostName(nextArg(args, i++));
                    case "-pp" -> config.tunnelingProxyPort(nextArg(args, i++));
                    case "-plogin" -> config.tunnelingCredentialUserName(nextArg(args, i++));
                    case "-ppasswd" -> config.tunnelingCredentialPasswd(nextArg(args, i++));
                    case "-pdomain" -> config.tunnelingCredentialDomain(nextArg(args, i++));
                    case "-krbfile" -> config.tunnelingCredentialKRB5ConfigFile(nextArg(args, i++));
                    case "-spTLSv1.2" -> tls12 = true;
                    case "-spTLSv1.3" -> tls13 = true;
                    default -> { // unrecognized command line argument
                        printHelp();
                        return false;
                    }
                }
            }

            // Set security protocol versions of TLS based on configured values, with default having TLS 1.2 and 1.3 enabled
            config.tunnelingSecurityProtocol("TLS");
            String[] tlsVersions = (tls12 == tls13)
                    ? new String[]{"1.2", "1.3"}
                    : tls12 ? new String[]{"1.2"} : new String[]{"1.3"};
            config.tunnelingSecurityProtocolVersions(tlsVersions);
        } catch (Exception e) {
            printHelp();
            return false;
        }

        return true;
    }

    // Loads a JWK file and applies it to the consumer config; exits the process on read failure.
    private static void loadJwkFile(String jwkFile, OmmConsumerConfig config) {
        if (jwkFile == null)
            return;

        try {
            // Get the full contents of the JWK file.
            byte[] jwkBuffer = Files.readAllBytes(Paths.get(jwkFile));
            clientJwk = new String(jwkBuffer);
            config.clientJWK(clientJwk);
        } catch (Exception e) {
            System.err.println("Error loading JWK file: " + e.getMessage());
            System.err.println();
            System.out.println("Consumer exits...");
            System.exit(CodecReturnCodes.FAILURE);
        }
    }

    // Returns the value following the option at the given index, or null if none exists.
    private static String nextArg(String[] args, int index) {
        return index < (args.length - 1) ? args[index + 1] : null;
    }

    // Splits a comma-separated item list (e.g. "EUR=,THB=,JPY=") into a trimmed, non-blank array.
    private static String[] parseItemNames(String rawItemNames) {
        return rawItemNames == null
                ? new String[0]
                : Arrays.stream(rawItemNames.split(","))
                        .map(String::strip)
                        .filter(Predicate.not(String::isBlank))
                        .toArray(String[]::new);
    }
}
