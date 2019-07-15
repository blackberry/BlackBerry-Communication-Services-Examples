![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# BlackBerry Spark Communications Services

BlackBerry Spark Communications Services is an IP-based cloud communications
platform that lets you easily create powerful new experiences between your
users, machines, and IoT devices.  Its enterprise-grade communication and data
sharing functionality can be integrated directly into your Linux, Android,
iOS, and web applications.  With end-to-end encryption, digitally signed
messages, and guaranteed data delivery, Spark Communications is a secure CPaaS
(communications platform as a service) solution that can enable you to build
powerful connections around the world, while keeping everything safe and
secure in a "private garden" communication system.

# Spark Communications Examples for Linux

| Example Application | Description |
|---------------------|-------------|
| [Auto Answer](autoAnswer/README.md) | Build an application that answers a secure incoming voice or video call. |
| [Message Echo](messageEcho/README.md) | Build an application that automatically echos back any received chat messages and controls LEDs. |
| [Endpoint Manager](endpointManager/README.md) | Build an application that can remove an identity's registered [endpoints](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/mpop.html). |
| [IDP](idp/README.md) | The `idp` application does not use the Spark Communications SDK. It is intended to be used to jump-start development and prototyping by providing a simulated [Identity Provider service](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/identityManagement.html). |

Some example applications depend on additional packages beyond those that the
Spark Communications SDK itself requires.  See each application's README for
instructions on how to build and use it.

When compiling the example applications, the `SPARK_SDK` environment variable
can be set to the absolute or relative path of the `sdk/` directory that came in
this archive.  By default, the examples will look for that directory in its
default location from the archive, but if you move the directories or use the
example build rules for your own programs, you might need to set this variable.

The example applications' build rules assume that you are using gcc-6.3 or a
compatible compiler.  See the Spark Communications SDK for Linux requirements
for more details on what tools and environments are supported.

# Getting Started

This example requires the Spark Communications Services SDK, which you can find
along with related resources at the locations below.

* Instructions to [Download and Configure](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html) the SDK.
* [Linux Getting Started](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-linux.html) instructions in the Developer Guide.
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/bbmds.html)

Visit the
[Getting Started with Linux](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-linux.html)
section to see the minimum requirements.

# License

These examples are released as Open Source and licensed under the
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).
