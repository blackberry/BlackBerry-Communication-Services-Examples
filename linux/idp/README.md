![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# IDP for Linux

This example requires the Spark Communications Services SDK, which you can find
along with related resources at the locations below.

* Instructions to [Download and Configure](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html) the SDK.
* [Linux Getting Started](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-linux.html) instructions in the Developer Guide.
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/bbmds.html)

Visit the
[Getting Started with Linux](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-linux.html)
section to see the minimum requirements.

# Authentication

These SDK example applications use [JWT tokens](https://jwt.io/) to
authenticate with the BlackBerry Infrastructure.

For simplicity, these example applications do not support connecting to a real
Identity Provider (IDP).  Instead, they support two different ways of
authenticating:

## Option 1: No Authentication

If you run these examples in a Spark Communications sandbox domain, you can
configure that domain to not require authentication.  In this configuration,
you need to configure each example with an _identity_ identifier only, and the
applications will use self-generated, unsigned tokens when connecting to the
BlackBerry Infrastructure.

Be aware that this mode of operation allows _anybody_ to connect to your Spark
Communications sandbox domain since no endpoint authentication is performed.

This mode of operation is not supported in Spark Communications production
domains.

To configure applications to use this mode of operation, you create an
`identity` file in their persistent data directory as explained in the
applications' README files.  You do not have to follow the remaining steps
that are outlined in this document.

## Option 2: Micro Identity Provider

Normally, Spark Communications domains require endpoint authentication.  As a
domain administrator, you configure which IDP the domain uses.  In this
option, you use this `idp` tool to operate a micro IDP from the command-line
and configure your domain to verify the tokens that IDP generates.

The rest of this document explains how to set up such a configuration.


# Overview

You will learn how to do the following things:

 - Create a public and private key pair to use for ECDSA signing on the
   command-line.
 
 - Make the public key from that pair available in
   [JWK form](https://tools.ietf.org/html/rfc7517) to the your Spark
   Communications domain.
   
 - Configure your domain to trust that public key.
   
 - Issue JWT "auth token" values from the command-line for use with these
   example applications.
   
This is not how you would set up a production authentication system, but it is
enough to get started with these example applications.


# Roles and Responsibilities

As explained in the
[Developer Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/index.html),
each running instance of your application is an _endpoint_ that connects to the
BlackBerry Infrastructure as a representative of an _identity_.

Normally, each endpoint has its own personal copy of credentials that it can
use with an Identity Provider (IDP) service.  These credentials are checked by
the IDP, which then issues a signed auth token to that endpoint.  This auth
token is typically a JWT token.  The endpoint then gives the auth token to the
SDK, and the SDK uses that to prove to the BlackBerry Infrastructure that it
is allowed to act on behalf of the endpoint's identity within your
application's SDK domain.

These example applications do not have their own personal copy of credentials
and they do not reach out to an IDP service.  Instead, each example application
simply gets a pre-configured, long-lived auth token value that is granted by
the simulated IDP.  This keeps the example applications simple and focused, but
allows them to work within the sandbox domain you configure.

**Production applications should not follow the simulated IDP model described
  here.**


# Dependencies

The `idp` tool requires the following command-line utilities to be installed:

 - GNU make 
 - awk
 - base64
 - bash
 - cat
 - cut
 - date
 - egrep
 - expr
 - openssl
 - tr
 - xxd

On Ubuntu, Debian, or Raspbian, you can make sure these utilities are
available by running the following command as root:

    # apt-get install make awk bash coreutils grep openssl xxd 
    

# Setup

The `idp` tool found in this directory will let you create your own
command-line simulated identity provider for use with these example
applications.

The identity provider must remember a few things between invocations, so it
will create files inside the same directory that the `idp` script is run
from the first time you run it.

To configure `idp` for the first time, change to the directory containing it
and run it.

For example:

    $ ./idp
    [generate] private.pem
    [generate] public.pem
    [generate] public.jwk
    [generate] token
    {
      "authToken":
      {
        "userId": "n_Phx08I6pXoWWAiDKiVMZDL",
        "authToken": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImdIT1dubWJsUHpqenZ5ZThXWVd4d0FHZSJ9.eyJpc3MiOiJTaW11bGF0ZWQgSURQIiwiYXVkIjoiU2FtcGxlIEFwcGxpY2F0aW9uIiwianRpIjoiaUR2dE5ieVk3YWF3bVFOMFNOd09oZG9UIiwic3ViIjoibl9QaHgwOEk2cFhvV1dBaURLaVZNWkRMIiwiaWF0IjoxNTI5NTIyMzA4LCJleHAiOjE1NjEwNTgzMDh9.msuGtQaVedjqPN87g02SVQxegMcEEQMAUql98pq9S1KQ4vemyIkiCmeNvJ3nZNlPoweBiN3iA-thbA1B1zhrNg"
      }
    }

The first three `[generate]` lines show the tool generating a new key pair and
the JWK form of the public key.

The last `[generate]` line shows the tool generating a new auth token that can
be used by one of the example applications.

The remaining lines show the newly generated auth token for a randomly
generated identity's user ID.  Every time you run the tool, it tries to
generate a new auth token.  A later section will explain how to use the tool
to generate tokens.  For now, you can ignore this token.


# Publishing the JWK Set

The `public.jwk` file that the `idp` tool creates is the file that you need to
publish for the BlackBerry Infrastructure to use when verifying your auth
token values.

To publish this file:

 - Copy the `public.jwk` file to a public, Internet-facing web server that serves pages
   with a valid, verifiable HTTPS TLS certificate.  This file contains a [JWK
   Set](https://tools.ietf.org/html/rfc7517#section-5) with the public key
   that is needed to verify the tokens.
   
 - As described in the
   [Identity Management section of the Developer Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/identityManagement.html),
   create a domain for your application in the sandbox environment, if you
   haven't done so already.
   
 - Configure the domain's "JWKS URI" field with the `public.jwk` file that you
   published in the previous step.
   
 - Configure the domain's "Client IDs" field with the string
   `SampleApplication`.  This is the `aud` value used in the `idp` tool's JWT
   tokens.
   
 - After creating the domain, edit it and ensure that the "JWT Field Names"
   are configured as follows.  Other field names do not matter.
   
   - User ID:   `sub`
   - Expiry:    `exp`
   - Client ID: `aud`
   - Issuer:    `iss`
   
 - Do not configure any "Scopes".
 
 - Save any changes you made while editing the domain configuration.

Your application's domain is now configured to trust the tokens issued by
the simulated identity provider.


# Generating and using auth tokens with the example applications

After successfully completing the above steps, simply run the `idp` tool and
it will generate a new random user ID (known as `sub` in JWT) and a new token
to go with it.  For example:

    $ ./idp
    [generate] token
    {
      "authToken":
      {
        "userId": "kX9DLJzJDTINOD9LY7WbKnQA",
        "authToken": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImdIT1dubWJsUHpqenZ5ZThXWVd4d0FHZSJ9.eyJpc3MiOiJTaW11bGF0ZWQgSURQIiwiYXVkIjoiU2FtcGxlIEFwcGxpY2F0aW9uIiwianRpIjoiSzEzaExTX1pjOVlDWkswX18xZ3lhS0NYIiwic3ViIjoia1g5RExKekpEVElOT0Q5TFk3V2JLblFBIiwiaWF0IjoxNTI5NTIyMzI5LCJleHAiOjE1NjEwNTgzMjl9.Msbl06XJkZxQ4EVzhirtqyCBawViqH64-N6dsoP8jZr746BkEGfijicGLZ-XUA3jdUNLfQmyIgoeusRVAzhsDQ"
      }
    }

The auth token is stored in a file named `token` and also printed to the screen for
convenience.  The auth token is represented as a
[BBMDS `authToken` message](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/bbmds/bbmcore_to_ui.html#authToken)
that can be immediately used inside one of the example applications.

To use a specific user ID instead of a randomly generated one, pass it as the
`SUB` command-line argument:

    $ ./idp SUB=user@example.org
    [generate] token
    {
      "authToken":
      {
        "userId": "user@example.org",
        "authToken": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImdIT1dubWJsUHpqenZ5ZThXWVd4d0FHZSJ9.eyJpc3MiOiJTaW11bGF0ZWQgSURQIiwiYXVkIjoiU2FtcGxlIEFwcGxpY2F0aW9uIiwianRpIjoieFVhNU5KVFQ0YkNwY0NQZUkySkVxbl9fIiwic3ViIjoidXNlckBleGFtcGxlLm9yZyIsImlhdCI6MTUyOTUyMjM0MywiZXhwIjoxNTYxMDU4MzQzfQ.j0vy7NhdN61FSqHCFgLLErRV3Jzc0_wpK_SfkVGPT8wVlyjUCXp4SB5fWZAn3FbzRAEU658O6CxEqfOA3GNpvw"
      }
    }

You can also override the generated token's expiry duration and JWT `aud`
value with similar arguments.  Type `idp help` for more details, including
examples.


# License

These examples are released as Open Source and licensed under the
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).
