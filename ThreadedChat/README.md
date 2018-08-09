![BlackBerry Spark Communications Platform](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Threaded Chat Sample for JavaScript

The Threaded Chat sample app demonstrates how a user can post comments on other messages in a chat to create threaded 
conversations using the [Chat Message References](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/references.html) feature of the Spark SDK for JavaScript.

This example utilizes the [Support](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/javascript/support/README.html) library to quickly create a basic chat application.

This directory contains sample code for a Threaded Chat app that
demonstrates how to use message references to build a threaded
conversation using the Spark SDK.  For a more rich chat app experience please see the
[Rich Chat](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/javascript/RichChat/README.html)
app provided with the SDK.

### Features

It allows the user to do the following:

- Post a comment on a message in a chat
- View comments on a message as a threaded conversation
- View inline notification when someone makes a comment on a message

<br>

<p align="center">
<a href="screenShots/ThreadedChat.png"><img src="screenShots/ThreadedChat.png" width="50%" height="50%"></a>
</p>


## Getting Started

These samples require the Spark SDK which you can find along with related resources at the location below.
    
* Getting stated with the [Spark SDK](https://developers.blackberry.com/us/en/products/blackberry-bbm-enterprise-sdk.html)
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

Run "yarn install" in the ThreadedChat application directory to install the required packages.

Visit the [Getting Started with Web](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html) section to see the minimum requirements.

To use the ThreadedChat example, you must set up the following elements in js/config.js:

- Oauth2 configuration (AUTH_CONFIGURATION)
- Your Spark user domain (ID_PROVIDER_DOMAIN)
- Firebase configuration (FIREBASE_CONFIG)
- User passcode (USER_SECRET)

## Walkthrough

Follow this guide for a walkthrough of how to make a comment on another message in a chat.

- [Import the bbmChatList UI widget into your web application](#importChatList)
- [Import the bbmChatMessageList UI widget into your web application](#importChatMessageList)
- [Initialize the Spark SDK for JavaScript](#init)
- [Display a threaded conversation](#displayThreadedConversatoin)
- [Make a comment on a message](#makeComment)

### <a name="importChatList"></a>Import the bbmChatList UI widget into your web application

Your web application needs to import the bbmChatList widget in order to display a list of chats.

```html
  <link rel="import" href="node_modules/bbmChatList/bbmChatList.html">
```

The element is initialized with a template to give the appearance for each chat.

```html
<bbm-chat-list id="chatList" style="height:100%">
  <template>
    <button id="[[chat.chatId]]" onclick="enterChat(this)">[[getChatName(chat)]]</button>
  </template>
</bbm-chat-list>
```

The bbmChatList widget only needs the messenger object and it will do the rest. It optionally takes a context object as well, which can be used to resolve the names of functions, as in the above getChatName.

```javascript
chatList.setBbmMessenger(messenger);
chatList.setContext({
  // Get the name to use for the chat. This is the other participant's
  // registration ID for a 1:1 chat, otherwise it is the chat's
  // subject.
  getChatName: function(chat) {
    if(chat.isOneToOne) {
      if(chat.participants[0].regId === bbmsdk.getRegistrationInfo().regId) {
        return chat.participants[1].regId.toString();
      } else {
        return chat.participants[0].regId.toString();
      }
    } else {
      return chat.subject;
    }
  }
});
```

### <a name="importChatMessageList"></a>Import the bbmChatMessageList UI widget into your web application

Your web application needs to import the bbmChatMessageList widget in order to display the messages in a chat.

```html
  <link rel="import" href="node_modules/bbmChatMessageList/bbmChatMessageList.html">
```
To display different types of message bubbles, or to display these types differently, a bbmChatMessageList can be used. The bbmChatMessageList widget only needs the messenger object and it will do the rest. It optionally takes a context object as well, which can be used to resolve the names of functions, as in the above getChatName.

A bbmChatMessageList requires an html template to describe a bubble and will instantiate this template for each message rather than using a default bubble. In this example, the threadedChatMessageList implements this template for bbmChatMessageList to display message bubbles for both of target messages(comments) and source messages(parent messages).

```html
<bbm-threaded-message-list id="chatMessageList" class="messageList" style="display: none; margin: 10px 10px 10px 10px; height: 100%"></bbm-threaded-message-list>

<dom-module id="bbm-threaded-message-list">
  <template>
    <bbm-chat-message-list id="list" items="[]" as="message">
        <template id="bubbleTemplate">
        ...
        </template>
    </bbm-chat-message-list>
  </template>
</dom-module>
```

### <a name="init"></a>Initialize the Spark SDK for JavaScript

```javascript
  // Create a BBMEnterprise instance.
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

### <a name="displayThreadedConversatoin"></a>Display a threaded conversation
With the [Chat Message References](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/references.html) of the Spark SDK, a message contains an list of outgoing references and a list of incoming references. 
Each incoming reference is identified by a "refBy" object that has:
- a tag that identifies what type of reference it is, for example, a tag of "Threaded"
- a list of message IDs identifying the source messages which refers to this message

<p align="center">
<a href="screenShots/ThreadedMessageList.png"><img src="screenShots/ThreadedMessageList.png" width="50%" height="50%"></a>
</p>

The following code is to check if a target message has a list of source messages that refers to it by a tag of "Threaded", and then displays them:

```javascript
  if(message.refBy) {
    ret.refBys = [];
    for(var i=0; i < message.refBy.length; i++) {
      if(message.refBy[i].tag === 'Threaded'
        && message.refBy[i].messageIds.length > 0) {
        for(var j=0; j < message.refBy[i].messageIds.length; j++) {
          // Display the source messages
          ...
        }
        break;
      }
    }
  }
```

Each outgoing reference is identified by a "ref" object that has:
- a tag that identifies what type of reference it is, for example, a tag of "Threaded"
- a list of message IDs identifying the target messages to which this message refers


<p align="center">
<a href="screenShots/ThreadedMessage.png"><img src="screenShots/ThreadedMessage.png" width="50%" height="50%"></a>
</p>

The following code is to check if the source message refers to a target message by a tag of "Threaded", and then displays the source message as an inline message to notify users that a comment has been made:

```javascript
  if(message.ref) {
    if(message.ref && message.ref[0]
        && message.ref[0].tag === 'Threaded') {
      //Display the source messages
      ...
    }
  }

```

### <a name="makeComment"></a>Make a commment on a message
The function for sending a source message that refers to a target message is integrated into the bbmChatInput widget. Your web application just needs to import the bbmChatInput widget, and then dispatches a custom event that contains a reference tag, a message id of the target message, and an information string to display above the input field. The widget will do the rest.

```html
  <bbm-chat-input id="chatInput" style="display: none"></bbm-chat-input>
```

```javascript
  this.dispatchEvent(new CustomEvent('messageReference',
    {'detail': { targetMessageId:  this.selectedMessage.message.messageId, 
      refTag: 'Threaded',
  content: 'Commenting on "' + this.selectedMessage.content + '"',
  textMessage: ""}}));
```

<p align="center">
<a href="screenShots/Input.png"><img src="screenShots/Input.png" width="50%" height="50%"></a>
</p>

## License

These samples are released as Open Source and licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find a issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-javascript-samples/issues).
