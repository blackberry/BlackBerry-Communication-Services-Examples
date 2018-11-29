![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Endpoint Manager for Linux

This sample application will demonstrate how to use the BlackBerry Spark
Communications Services SDK to
[manage an identity's registered endpoints](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/mpop.html).

As explained in the
[Developer Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/),
each running instance of an application is an _endpoint_ that connects to the
BlackBerry Infrastructure as a representative of an _identity_.

# Getting Started

This sample requires the SDK, which you can find along with related
resources at the location below.
    
* Getting started with [BlackBerry Spark Communications Services](https://developers.blackberry.com/us/en/products/blackberry-spark-communications-platform.html)
* [Developer Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/index.html)
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/bbmds.html)

## Compiling

This sample application uses libjsoncpp to construct and parse JSON
messages. To install the jsoncpp development package:

    $ sudo apt-get install libjsoncpp-dev

Compile the application using the included artefacts. See the `Makefile` for
important variables such as `SPARK_SDK` and support for cross compiling.  To
compile for the native architecture from the sample's default location in the
SDK archive:

    $ make

Copy the resulting `endpointManager` application along with `libbbmcore.so` and
`libffmpeg.so` from the sdk directory to the directory where you run the
application from.

# Configuration

To get started, create a directory from which the application will read config
files and write any data or logs.  By default, this directory is
~/.endpointManager. The default can be overridden at runtime if needed. In
this directory, two files are required:

    .endpointManager/
    ├── domain
    └── identity OR token (see below)

    $ mkdir ~/.endpointManager

domain: This file contains a single line which is the ID of the Spark
        Communications domain you have configured. See the
        [Developer Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/)
        for instructions on how to create and configure a domain.
        
    $ echo "your_domain" > ~/.endpointManager/domain
        
identity: The presence of this file indicates that your domain is configured
          to use no authentication.  This file includes a single line
          containing a single string that will be used to specify the identity
          (i.e. the "account") that the application will use to connect to the
          BlackBerry Infrastructure.  See the README for the `idp` sample
          application for more information about configuring a Spark
          Communications sandbox domain for no authentication.

token: The presence of this file indicates that your domain is configured to
       use a "micro IDP".  This file includes the identity to use when
       authenticating with the BlackBerry Infrastructure as well as the JWT
       "auth token". See the README for the `idp` sample application for
       details on how to generate a token file.


# Running the application

To use the default `.endpointManager` directory:

    $ ./endpointManager
    
or to specify a different directory containing the domain and token files:

    $ ./endpointManager directory_name

The application will output some basic status and identifiers as it starts
up that looks like:

    Configured for user domain 1a9a709e-1046-407a-9ace-33d9af30c788.
    Follow bbmcore's logs with this command in another terminal:
    tail --follow=name /home/pi/.endpointManager/logs/bbmcore.txt
    Asking bbmcore to start.
    EndpointId is RaMEa51SCciJ9K5Xrz3tPFDPgVQ4h9A02EH9kaxqelNRfsDXabjJIti79psKfyGG
    SyncPasscodeState is None
    SetupState is Success
    Profile regId: 787093443485642921 PIN: 1000f104

It will also emit the list of currently registered endpoints which may or may
not include the current endpoint:

    Endpoint lookup successful with 3 of 3 endpoints.
    1) Id: RaMEa51SCciJ9K5Xrz3tPFDPgVQ4h9A02EH9kaxqelNRfsDXabjJIti79psKfyGG
       Description: Mobile client
       Nickname: Phone
       IsCurrent: false
    2) Id: XIOvRQMYGKehJQhW6W17AgW2YVGcATjvSqQZdobLRvPLku0FA7gYBmR2zPiLXRVf
       Description: Sample application
       Nickname:
       IsCurrent: true
    3) Id: xDgwV2EJgkAJ3KD10bdQzKvghqYsG5VTdwrPTm51UnMPpIPLhf90WkUxNnqe4Jkg
       Description: Desktop client
       Nickname:
       IsCurrent: false
       
    Enter an endpoint ID to be deleted or <Enter> to exit:
    
You may enter any endpoint ID and the application will attempt to de-register
it. If you de-register the current endpoint, the application will exit after
completing the request.


# License

These examples are released as Open Source and licensed under the
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).
