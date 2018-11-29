![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Click To Chat Sample for JavaScript

The Click to Chat sample app demonstrates how to integrate a chat experience
into your website with the Spark Communications SDK. This app allows a user to
click a button on a webpage to start a secure chat with a predefined user or
agent. The bbmChat widget handles the rendering of messages within the chat,
and allows the user to send text, picture, and file messages.

<p align="center">
<br>
    <a href="http://www.youtube.com/watch?feature=player_embedded&v=-U3kKWkFeW0"
      target="_blank"><img src="screenShots/bbme-sdk-web-click-to-chat.jpg" 
      alt=Integrate Chat Bots into your Apps" width="486" height="" border="364"/></a>
</p>
<p align="center">
 <b>Demo video: Integrate a secure chat widget into your web page</b>
</p>

### Features

This app demonstrates how easy it is to integrate the bbmChat widget into your
webpage. It initializes the SDK, and starts a chat with a predefined user. The
app then launches the bbmChat widget which allows the user to:
* View all sent and received messages in a chat
* Send a text message, picture, or file attachment
* Send high priority messages
* Mark incoming messages as Read
* Show typing notifications
* Retract messages
* Delete messages
* Show delivered and read message status
* Show the chat participant

<br>

<p align="center">
<a href="screenShots/ClickToChat.png"><img src="screenShots/ClickToChat.png" width="50%" height="50%"></a>
</p>


## Getting Started

This sample requires the Spark Communications SDK for JavaScript, which you can find along with related resources at the location below.

* Getting started with the [Spark Communications SDK](https://developers.blackberry.com/us/en/products/blackberry-spark-communications-platform.html)
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

### Prerequisites

Run "yarn install" in the ClickToChat application directory to install the required packages.

Visit the [Getting Started with Web](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html) section to see the minimum requirements.

To use the ClickToChat example, you must set up the following elements in js/config.js:

- Oauth2 configuration (AUTH_CONFIGURATION)
- A hardcoded contact registration ID with whom anyone who views the page will chat (CONTACT_REG_ID)
- Your sandbox domain (ID_PROVIDER_DOMAIN)
- Firebase configuration (FIREBASE_CONFIG)
- User passcode (USER_SECRET)

## Walkthrough

Follow this guide for a walkthrough of how to integrate a rich chat experience into your webpage.

- [Import the bbmChat UI widget into your web application](#importChat)
- [Initialize the SDK](#init)
- [Perform setup](#setup)
- [Start a chat with a predefined user](#startChat)

### <a name="importChat"></a>Import the bbmChat UI widget into your web application

Your web application simply needs to import the bbmChat widget in order to bring a rich chat experience into your webpages.

```html
  <link rel="import" href="node_modules/bbmChat/bbmChat.html">
```

The bbmChat widget needs only the ID of the chat you've created and it will handle the rest.


### <a name="init"></a>Initialize the Spark SDK for JavaScript

Create new instance of BBMEnterprise.

```javascript
  bbmeSdk = new BBMEnterprise({
    domain: ID_PROVIDER_DOMAIN,
    environment: ID_PROVIDER_ENVIRONMENT,
    userId: authUserInfo.userId,
    getToken: authManager.getBbmSdkToken,
    description: navigator.userAgent,
    messageStorageFactory: BBMEnterprise.StorageFactory.SpliceWatcher,
    kmsArgonWasmUrl: KMS_ARGON_WASM_URL
  });
```

For more information about setting up the SDK, visit the [Getting Started with Web](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html) section of the guide.

### <a name="setup"></a>Perform setup

When the setup state changes, a 'setupState' event will be emitted. Listen for this to determine when the setup state changes.

```javascript
  // Handle changes of BBM Enterprise setup state.
  bbmeSdk.on('setupState', state => {
    console.log(`BBMEnterprise setup state: ${state.value}`);
    switch (state.value) {
      case BBMEnterprise.SetupState.Success: {
        const userRegId = bbmeSdk.getRegistrationInfo().regId;
        createUserManager(userRegId, authManager,
          bbmeSdk.getIdentitiesFromAppUserId,
            bbmeSdk.getIdentitiesFromAppUserIds)
        .then(userManager => {
          // User manager is created.
          // Application is able ot start chat now.
          // ... Start chat here.
        });
        break;
      }
      case BBMEnterprise.SetupState.SyncRequired: {
        if (isSyncStarted) {
          reject(new Error('Failed to get user keys using provided USER_SECRET'));
          return;
        }
        const isNew =  bbmeSdk.syncPasscodeState === BBMEnterprise.SyncPasscodeState.New;
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
    // Notify user about failure.
    alert(`BBM Enterprise setup failed: ${error.value}`);
    reject(error.value);
  });

  // Start BBM Enterprise setup.
  bbmeSdk.setupStart();
```

### <a name="startChat"></a>Start a chat with a predefined user
To start a chat with a predefined user and show the bbmChat widget, you need to invoke the [bbmMessenger.chatStart](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Messenger.html#chatStart) API and pass in the user's regId. Upon successfully creating the chat, launch the bbmChat widget to allow the user to view and send messages in the chat.

```javascript
  const bbmChat = document.querySelector('#bbm-chat');
  bbmMessenger.chatStart(CHAT_DETAILS).then(pendingChat => {
    bbmChat.setChatId(pendingChat.chat.chatId);
  });
```

## License

These samples are released as Open Source and licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

This page includes icons from: https://material.io/icons/ used under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find a issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-javascript-samples/issues).
