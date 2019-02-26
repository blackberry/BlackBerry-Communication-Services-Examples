![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Data Transfer for JavaScript

The Data Transfer example application demonstrates how to use the peer-to-peer
data connection feature of BlackBerry Spark Communications Services. The data
connection API allows arbitrary data to be sent securely through a familiar
stream interface.

This example builds on the [Quick Start](../QuickStart/README.md) example that
demonstrates setting up the SDK in a domain with user authentication disabled
and the BlackBerry Key Management Service enabled.

### Features

The Data Transfer example allows the user to do the following:

* Start a data connection
* Send and receive files over the connection

<br/>
<p align="center">
  <a href="screenShots/DataTransfer.png">
    <img src="screenShots/DataTransfer.png" width="50%" height="50%">
  </a>
</p>

## Getting Started

This example requires the Spark Communications SDK, which you can find along
with related resources at the locations below.

* Instructions to
[Download and Configure](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html)
the SDK.
* [Getting Started with Web](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html)
instructions in the Developer Guide.
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/index.html)

<p align="center">
    <a href="https://youtu.be/CSXZT2perqE"
      target="_blank"><img src="../QuickStart/screenShots/bb-spark-web-sdk-getting-started.jpg"
      alt="YouTube Getting Started Video" width="486" height="" border="364"/></a>
</p>
<p align="center">
  <b>Getting started video</b>
</p>

By default, this example application is configured to work in a domain with
user authentication disabled and the BlackBerry Key Management Service
enabled.  See the [Download & Configure](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html)
section of the Developer Guide to get started configuring a
[domain](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/faq.html#domain)
in the [sandbox](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/faq.html#sandbox).

When you have a domain in the sandbox, edit Data Transfer's `config_mock.js`
file to configure the example with your domain ID and a key passcode.

Set the `DOMAIN_ID` parameter to your sandbox domain ID.

```javascript
const DOMAIN_ID = 'your_domain_id';
```

Set the `KEY_PASSCODE` parameter to the string used to protect the logged in
user's keys stored in the [BlackBerry Key Management Service](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/security.html).
Real applications should not use the same passcode for all users.   However,
it allows this example application to be smaller and focus on demonstrating
its call functionality instead of passcode management.

```javascript
const KEY_PASSCODE = 'passcode';
```

Run `yarn install` in the Data Transfer application directory to install the
required packages.

When you run the Data Transfer application, it will prompt you for a user ID.
Because you've configured your domain to have user authentication disabled, you
can enter any string you like for the user ID and an SDK identity will be
created for it.  Other applications that you run in the same domain will be
able to find this identity by this user ID.

## Walkthrough

Before a chat with a configured user can be initiated, the user must be
[authenticated](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html#authentication) and the [SDK started](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html#start-sdk).

Follow this guide for a walkthrough of how to share data over a secure
peer-to-peer data connection.  This walkthrough describes the functionality
provided by the `data-transfer-element` component, which can be found in
`js/dataTransferElement.js`.

- [Creating a connection](#create)
- [Receive a connection](#receive)
- [Receiving a file](#receiveFile)
- [Sending a file](#sendFile)

### <a name="create"></a> Creating a connection

The `onConnectClicked()` event handler starts a new data connection using
[`BBMEnterprise.Media.createDataConnection()`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.html#createDataConnection).
When starting a data connection, the metadata object will be sent to the
callee to describe the intent or content of the connection.

```javascript
  // Create connection to the specified regId.
  this.bbmSdk.media.createDataConnection(
    new BBMEnterprise.Media.Callee(this.contactRegId), metaData)
  .then(connection => {
    connection.on('connected', () => {
      this.connection = connection;
      this.connectionState = STATE_CONNECTED;
    });
  });
```

### <a name="receive"></a> Receive a connection

When the `setBbmSdk()` function is called, the component will use the SDK
instance to monitor for incoming data connections by subscribing to the
[`incomingDataConnection`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.html#event:incomingDataConnection)
event. This event is triggered each time a new incoming data connection
request arrives. The event given to the event handler provides the application
with the incoming
[`DataConnection`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html)
instance which can be
[`accepted`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html#accept)
or
[`declined`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html#end).

```javascript
  this.bbmSdk.media.on('incomingDataConnection', connection => {
    const contactRegId = connection.callParty.regId;
    const messageString = `${contactRegId} wants to establish a connection.  Do you want to accept?`
      if (confirm(messageString)) {
      connection.accept()
      .then(() => {
        this.connection = connection;
        this.connectionState = STATE_CONNECTED;
        this.contactRegId = contactRegId;
      })
      .catch(error => {
        alert('Failed to accept connection');
        console.error(`Failed to accept connection: ${error}`);
      });
    }
    else {
      connection.end(BBMEnterprise.Media.CallEndReason.REJECT_CALL);
    }
  });
```

### <a name="receiveFile"></a> Receiving a file

To receive data using the established
[`DataConnection`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html),
the application must subscribe to the
[`dataReceived`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html#event:dataReceived)
event. The event handler for this event will receive the
[`DataReceiver`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.DataReceiver.html)
instance that will handle all incoming data over the
[`DataConnection`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html).

To receive a notification that the
[`DataConnection`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html)
has been closed, subscribe to the
[`disconnected`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html#event:disconnected)
event. The event handler will receive the
[`DataConnection`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html)
that was closed and a status that indicates why the connection was closed.

The context for this snippet can be found in the `onConnectionChanged()`
callback.

```javascript
  // Subscribe to the new connection events.
  newConnection.on('dataReceived', onDataReceived);
  newConnection.on('disconnected', onDisconnected);
```

To specify the receiving mode, the
[`DataReceiver`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.DataReceiver.html)
must be configured within the
[`dataReceived`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html#event:dataReceived)
event handler. The Data Transfer application only operates on the complete
data, so the `DATA_FULL` mode is used.

The context for this snippet can be found in the `onReceiverChanged()`
callback.

```javascript
  newReceiver.configure({
    mode: BBMEnterprise.Media.DataConnection.DataReceiver.Mode.DATA_FULL
  });
```

To receive notification that all of the incoming data has been received,
subscribe to the
[`done`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.DataReceiver.html#event:done)
event.

The context for this snippet can be found in the `onReceiverChanged()` callback.

```javascript
  // Subscribe to 'done' event of the newReceiver
  newReceiver.on('done', onDone);
```

The
[`deplete`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.DataReceiver.html#deplete)
method is used to consume the data that has been buffered within the
[`DataReceiver`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.DataReceiver.html).
The Data Transfer example creates a blob from the received data, which is used
by the UI to show the received data.

The context for this snippet can be found in the `onReceiverChanged()`
callback.

```javascript
  const onDone = receiver => {
    // File is received.
    const data = receiver.deplete();
    const blob = new Blob([data], {
      type: receiver.header.type
    });
    const url = window.URL.createObjectURL(blob);
    // Set percentage to 100%. Set file name and download URL.
    this.set(`fileTransfers.0.percentage`, `100%`);
    this.set(`fileTransfers.0.fileUrl`, url);
    this.set(`fileTransfers.0.fileName`, receiver.header.name);
  };
```

### <a name="sendFile"></a> Sending a file

To send a file, call the
[`sendFile`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html#sendFile)
method on an established
[`DataConnection`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html)
instance. The Data Transfer example shows the progress of the file transfer by
providing a progress callback.


The context for this snippet can be found in the `onInputFileChange()`
callback.

```javascript
  // Send user selected file.
  this.connection.sendFile(file, percentage => {
    this.set(`fileTransfers.0.percentage`, `${percentage}%`);
  })
  .then(() => {
    // File is sent. Set percentage to 100%.
    this.set(`fileTransfers.0.percentage`, `100%`);
    this.set(`fileTransfers.0.isSent`, true);
  }).catch(error => {
    alert(error);
  });
```

## License

These examples are released as Open Source and licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).  

This page includes icons from: https://material.io/icons/ used under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find a issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-javascript-samples/issues).
