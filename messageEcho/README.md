![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Message Echo for Linux

This sample application will demonstrate how to use the BlackBerry Spark
Communications Services SDK to send and receive messages.  It also shows how
messages can be extended to implement custom functionality such as hardware
control.

# Getting Started

This sample requires the SDK, which you can find along with related resources
at the location below.
    
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

Copy the resulting `messageEcho` application along with `libbbmcore.so` and
`libffmpeg.so` from the sdk directory to the directory where you run the
application from.

## Configuration

To get started, create a directory from which the application will read config
files and write any data or logs.  By default, this directory is
~/.messageEcho. The default can be overridden at runtime if needed. In this
directory, two files are required:

    .messageEcho/
    ├── domain
    └── identity OR token (see below)

    $ mkdir ~/.messageEcho

domain: This file contains a single line which is the ID of the Spark
        Communications domain you have configured. See the
        [Developer Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/)
        for instructions on how to create and configure a domain.
        
    $ echo "your_domain" > ~/.messageEcho/domain
        
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

To grant a user access to the red LED control file on a Raspberry Pi 3:

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

To use the default `.messageEcho` directory:

    $ ./messageEcho
    
or to specify a different directory containing the domain and token files:

    $ ./messageEcho directory_name

The application will output some basic status and identifiers as it starts
up that looks like:

    Configured for user domain 1a9a709e-1046-407a-9ace-33d9af30c788.
    Follow bbmcore's logs with this command in another terminal:
    tail --follow=name /home/pi/.messageEcho/logs/bbmcore.txt
    Asking bbmcore to start.
    EndpointId is RaMEa51SCciJ9K5Xrz3tPFDPgVQ4h9A02EH9kaxqelNRfsDXabjJIti79psKfyGG
    SyncPasscodeState is None
    SetupState is Success
    Profile regId: 787093443485642921 PIN: 1000f104

The `regId` value in the application's output is the unique identifier for the
Spark Communications identity that was created to represent the `userId` in
your `token` file. You will use this `regId` to refer this identity within
Spark Communications Services.

To run the application automatically as user `pi` each time the system is
started, add the following to `/etc/rc.local`:

    # Start the messageEcho sample application
    sudo -u pi ./home/pi/messageEcho > /home/pi/messageEcho.out &

# Supported Messages

The following are examples of messages that can be sent from another
application to exercise the messageEcho sample application.

To create a new chat with the messageEcho application's identity, send a chat
request message with the registration ID emitted by the messageEcho
application as an invitee:

    {
      "chatStart":
      {
        "invitees": [ {"regId": 787093443485642921} ],
        "cookie": "c",
        "subject": "Sample chat"
      }
    }
    
To have the application echo a message back, send a chat message using the
chatId of the chat created previously with the tag `Text`:

    {
      "chatMessageSend":
      {
        "chatId": "1",
        "tag": "Text",
        "content": "echo!"
      }
    }

If you are running `messageEcho` on a Raspberry Pi 3, you can control the power
LED by sending it a custom control message in any chat. Send a chat message
with the custom tag of `Blink` and a custom data object with the action
`start` or `stop`:

    {
      "chatMessageSend":
      {
        "chatId": "1",
        "tag": "Blink",
        "data": {"action": "start"}
      }
    }


# License

These examples are released as Open Source and licensed under the
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).
