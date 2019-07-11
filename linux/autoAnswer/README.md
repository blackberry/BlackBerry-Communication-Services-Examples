![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Auto Answer for Linux

This example application will demonstrate how to use the BlackBerry Spark
Communications Services SDK to answer an incoming voice or video call.

# Getting Started

This example requires the Spark Communications Services SDK, which you can find
along with related resources at the locations below.

* Instructions to [Download and Configure](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html) the SDK.
* [Linux Getting Started](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-linux.html) instructions in the Developer Guide.
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/bbmds.html)

Visit the
[Getting Started with Linux](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-linux.html)
section to see the minimum requirements.

## Compiling

This example application uses libjsoncpp to construct and parse JSON
messages. To install the jsoncpp development package:

    $ sudo apt-get install libjsoncpp-dev

Compile the application using the included artefacts. See the `Makefile` for
important variables such as `SPARK_SDK` and support for cross compiling.  To
compile for the native architecture from the example's default location in the
SDK archive:

    $ make

Copy the resulting `autoAnswer` application along with `libbbmcore.so` and
`libffmpeg.so` from the `sdk/` directory to the directory where you run the
application from.

## Configuration

To get started, create a directory from which the application will read config
files and write any data or logs.  By default, this directory is
~/.autoAnswer. The default can be overridden at runtime if needed. In this
directory, two files are required:

    .autoAnswer/
    ├── domain
    └── identity OR token (see below)

    $ mkdir ~/.autoAnswer

domain: This file contains a single line which is the ID of the Spark
        Communications domain you have configured. See the
        [Developer Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/)
        for instructions on how to create and configure a domain.
        
    $ echo "your_domain" > ~/.autoAnswer/domain
        
identity: The presence of this file indicates that your domain is configured
          to use no authentication.  This file includes a single line
          containing a single string that will be used to specify the identity
          (i.e. the "account") that the application will use to connect to the
          BlackBerry Infrastructure.  See the README for the `idp` example
          application for more information about configuring a Spark
          Communications sandbox domain for no authentication.

token: The presence of this file indicates that your domain is configured to
       use a "micro IDP".  This file includes the identity to use when
       authenticating with the BlackBerry Infrastructure as well as the JWT
       "auth token". See the README for the `idp` example application for
       details on how to generate a token file.

When running on a Raspberry Pi, this application will attempt to flash the red
LED light during a call. To grant a user access to the LED control file on a
Raspberry Pi 3:

    # Add "leds" group and add the user to that group.
    sudo addgroup --system leds
    sudo adduser pi leds

    # Add udev rule for led.
    cat <<EOF |sudo tee /etc/udev/rules.d/99-leds.rules >/dev/null
    SUBSYSTEM=="leds", RUN+="/bin/sh -c 'chgrp leds /sys/class/leds/led1/trigger && chmod g+rw /sys/class/leds/led1/trigger'"
    EOF

    # Reboot
    sudo reboot

# Running the application

To use the default `.autoAnswer` directory:

    $ ./autoAnswer
    
or to specify a different directory containing the domain and token files:

    $ ./autoAnswer directory_name

The application will output some basic status and identifiers as it starts
up that looks like:

    Configured for user domain 1a9a709e-1046-407a-9ace-33d9af30c788.
    Follow bbmcore's logs with this command in another terminal:
    tail --follow=name /home/pi/.autoAnswer/logs/bbmcore.txt
    Asking bbmcore to start.
    EndpointId is RaMEa51SCciJ9K5Xrz3tPFDPgVQ4h9A02EH9kaxqelNRfsDXabjJIti79psKfyGG
    SyncPasscodeState is None
    SetupState is Success
    Profile regId: 787093443485642921 PIN: 1000f104

The `regId` value in the application's output is the unique identifier for the
Spark Communications identity that was created to represent the `userId` in
your `token` file. You will use this `regId` to refer to this identity within
Spark Communications Services when placing a call.

To run the application automatically as user `pi` each time the system is
started, add the following to `/etc/rc.local`:

    # Start the autoAnswer example application
    sudo -u pi ./home/pi/autoAnswer > /home/pi/autoAnswer.out &


# License

These examples are released as Open Source and licensed under the
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).
