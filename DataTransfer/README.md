![BlackBerry Spark Communications Platform](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Data Transfer for JavaScript

The DataTransfer application shows how to use the peer-to-peer data connection capability in the Spark SDK for JavaScript. The data connection API allows arbitrary data to be sent securely through a familiar stream interface.

### Features

The sample application allows the user to do the following:

- Start a data connection
- Send and receive files over the connection

<br>
<p align="center">
  <a href="screenShots/DataTransfer.png">
    <img src="screenShots/DataTransfer.png" width="50%" height="50%">
  </a>
</p>

## Getting Started

This sample requires the Spark SDK, which you can find along with related resources at the location below.

* Getting started with the [Spark SDK](https://developers.blackberry.com/us/en/products/blackberry-bbm-enterprise-sdk.html)
* [Development Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/index.html)
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/index.html)

<p align="center">
    <a href="https://www.youtube.com/watch?v=LAbxok2EQtI"
      target="_blank"><img src="../QuickStart/screenShots/bb-spark-web-sdk-getting-started.jpg"
      alt="YouTube Getting Started Video" width="486" height="" border="364"/></a>
</p>
<p align="center">
 <b>Getting started video</b>
</p>

### <a name="prereq"></a>Prerequisites

Run "yarn install" in the DataTransfer application directory to install the required packages.

Visit the [Getting Started with Web](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html) section to see the minimum requirements.

To use the DataTransfer example, you must set up the following elements in js/config.js:

- Oauth2 configuration (AUTH_CONFIGURATION)
- Your Spark user domain (ID_PROVIDER_DOMAIN)
- Firebase configuration (FIREBASE_CONFIG)
- User passcode (USER_SECRET)

## Walkthrough

Follow this guide for a walkthrough explaining how the Spark SDK for JavaScript is used to share data over a secure peer-to-peer data connection.
- [Initialize the Spark SDK for JavaScript](#init)
- [Perform setup](#setup)
- [Creating a connection](#create)
- [Receive a connection](#receive)
- [Receiving a file](#receiveFile)
- [Sending a file](#sendFile)

### <a name="init"></a>Initialize the Spark SDK for JavaScript

```javascript
  // Create new instance of BBMEnterprise.
  const bbmeSdk = new BBMEnterprise({
    domain: ID_PROVIDER_DOMAIN,
    environment: ID_PROVIDER_ENVIRONMENT,
    userId: authUserInfo.userId,
    getToken: authManager.getBbmSdkToken,
    description: navigator.userAgent,
    messageStorageFactory: BBMEnterprise.StorageFactory.SpliceWatcher,
    kmsArgonWasmUrl: KMS_ARGON_WASM_URL
  });
```

### <a name="setup"></a>Perform setup

```javascript
  // Handle changes of BBM Enterprise setup state.
  bbmeSdk.on('setupState', state => {
    console.log(`BBMEnterprise setup state: ${state.value}`);
    switch (state.value) {
      case BBMEnterprise.SetupState.Success:
        // Setup was successful. Application is ready to transfer data.
        resolve(bbmeSdk);
      return;
      case BBMEnterprise.SetupState.SyncRequired: {
        if (isSyncStarted) {
          reject(new Error('Failed to get user keys using provided USER_SECRET'));
          return;
        }
        const isNew = bbmeSdk.syncPasscodeState === BBMEnterprise.SyncPasscodeState.New;
        const syncAction = isNew ? BBMEnterprise.SyncStartAction.New : BBMEnterprise.SyncStartAction.Existing;
        bbmeSdk.syncStart(USER_SECRET, syncAction);
      }
      break;
      case BBMEnterprise.SetupState.SyncStarted:
        isSyncStarted = true;
      break;
    }
  });

  // Handle setup error.
  bbmeSdk.on('setupError', error => {
    reject(error.value);
  });

  bbmeSdk.setupStart();
```

### <a name="create"></a> Creating a connection

Start a new data connection using [createDataConnection](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.html#createDataConnection). When starting a data connection, the metadata object will be sent to the callee to describe the intent or content of the connection.

```javascript
  // Create connection to the specified Reg ID.
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

To monitor for incoming data connections, subscribe to the [incomingDataConnection](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.html#event:incomingDataConnection) event. This event is triggered each time a new incoming data connection request arrives. The event given to the event handler provides the application with the incoming [DataConnection](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html) instance which can be [accepted](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html#accept) or [declined](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html#end).

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

To receive data using the established [DataConnection](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html), the application must subscribe to the [dataReceived](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html#event:dataReceived) event. The event handler for this event will receive the [DataReceiver](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.DataReceiver.html) instance that will handle all incoming data over the [DataConnection](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html).

To receive a notification that the [DataConnection](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html) has been closed, subscribe to the [disconnected](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html#event:disconnected) event. The event handler will receive the [DataConnection](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html) that was closed and a status which indicates why the connection was closed.

```javascript
  // Subscribe to the new connection events.
  newConnection.on('dataReceived', onDataReceived);
  newConnection.on('disconnected', onDisconnected);
```

To specify the receiving mode, the [DataReceiver](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.DataReceiver.html) must be configured within the [dataReceived](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html#event:dataReceived) event handler. The DataTransfer application only operates on the complete data, so the DATA_FULL mode is used.

```javascript
  newReceiver.configure({
    mode: BBMEnterprise.Media.DataConnection.DataReceiver.Mode.DATA_FULL
  });
```

To receive notification that all of the incoming data has been received, subscribe to the [done](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.DataReceiver.html#event:done) event.

```javascript
  // Subscribe to 'done' event of the newReceiver
  newReceiver.on('done', onDone);
```

The [deplete](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.DataReceiver.html#deplete) method is used to consume the data that has been buffered within the [DataReceiver](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.DataReceiver.html). The DataTransfer example creates a blob from the received data which is used by the UI to show the received data.

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

To send a file, call the [sendFile](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html#sendFile) method on an established [DataConnection](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Media.DataConnection.html) instance. The DataTransfer example shows the progress of the file transfer by providing a progress callback.

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

These samples are released as Open Source and licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

This page includes icons from: https://material.io/icons/ used under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find a issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-javascript-samples/issues).
