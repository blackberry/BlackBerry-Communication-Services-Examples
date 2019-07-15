![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Simple Chat for JavaScript

The Simple Chat application demonstrates how you can build a simple chat
application using the BlackBerry Spark Communications Services.  This
application demonstrates how easily messaging can be integrated into your
application.

This example utilizes the
[Support](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/javascript/support/README.html)
library to quickly create a basic chat application.  For a more rich chat
application experience, please see the [Rich
Chat](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/javascript/RichChat/README.html)
app provided with the SDK.

This example builds on the [Quick Start](../QuickStart/README.md) example that
demonstrates setting up the SDK in a domain with user authentication disabled
and the BlackBerry Key Management Service enabled.

### Features

This example demonstrates how easy it is to integrate the `bbmChatList`,
`bbmChatMessageList`, and `bbmChatInput` components into your webpage so that
you can:

* Create a chat
* View the chat list
* View all sent and received messages in a chat
* Mark incoming messages as `Read`
* Send text-based messages

<br/>
<p align="center">
  <a href="screenShots/SimpleChat.png"><img src="screenShots/SimpleChat.png" width="50%" height="50%"></a>
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

This example application works in a sandbox domain with user authentication
disabled and the BlackBerry Key Management Service enabled.  See the
[Download & Configure](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html)
section of the Developer Guide to get started configuring a
[domain](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/faq.html#domain)
in the [sandbox](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/faq.html#sandbox).

When you have a domain in the sandbox, edit Simple Chat's `config_mock.js`
file to configure the example with your domain ID and a key passcode.

Set the `SDK_CONFIG.domain` parameter to your sandbox domain ID.

```javascript
const SDK_CONFIG = {
  domain: 'your_domain_id'
};
```

Set the `KEY_PASSCODE` parameter to the string used to protect the logged-in
user's keys stored in the [BlackBerry Key Management Service](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/security.html).
Real applications should not use the same passcode for all users.   However,
it allows this example application to be smaller and focus on demonstrating
its call functionality instead of passcode management.

```javascript
const KEY_PASSCODE = 'passcode';
```

Run `yarn install` in the Simple Chat application directory to install the
required packages.

When you run the Simple Chat application, it will prompt you for a user ID.
Because you've configured your domain to have user authentication disabled, you
can enter any string you like for the user ID and an SDK identity will be
created for it.  Other applications that you run in the same domain will be
able to find this identity by this user ID.

The Simple Chat example application cannot initiate a chat.  Configure the
[Rich
Chat](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/javascript/RichChat/README.html)
example application to use your domain and initiate a chat with the user
logged into Simple Chat.

## Walkthrough

Before interaction with the user's chats can begin, the user must be
[authenticated](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html#authentication)
and the [SDK
started](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html#start-sdk).

Follow this guide for a walkthrough of how to display a list of chats and a
list of messages in one chat.

- [Import and configure the bbmChatList component](#importChatList)
- [Import and configure the bbmChatMessageList component](#importChatMessageList)
- [Import and configure the bbmChatInput component](#importChatInput)

### <a name="importChatList"></a>Import the and configure bbmChatList component

Import the `bbmChatList` component to display the list of chats the logged-in
user is participating in.

```html
  <link rel="import" href="node_modules/bbmChatList/bbmChatList.html">
```

The `bbmChatList` component has a template that allows you to control its
look, feel, and behavior.  In this example, your chat list will be composed of
a button for each chat.  When clicked, the button will call the `enterChat`
function for the chat whose button was clicked to display the name and
messages in the chat.

```html
<bbm-chat-list id="chatList" style="height:100%">
  <template>
    <button id="[[chat.chatId]]" onclick="enterChat(this)">[[getChatName(chat)]]</button>
  </template>
</bbm-chat-list>
```

The `bbmChatList` component only requires a setup instance
[`BBMEnterprise.Messenger`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Messenger.html)
it will do the rest.  In this example, the function used for displaying the
chat name in the template are defined as part of the component's context.

```javascript
  // Configure the chatList component.  It needs a handle to the SDK's
  // messenger object.  We also setup a context for the element that defines
  // how it will behave.
  chatList.setBbmMessenger(sdk.messenger);
  chatList.setContext({
    /**
     * Get the name to use for the chat.
     *
     * @param {BBMEnterprise.Messenger.Chat} chat
     *   The chat whose name is to be returned.
     *
     * @returns {string}
     *   The name to be used for the chat.
     */
    getChatName: (chat) => {
      if (chat.isOneToOne) {
        // We have a 1:1 chat.  We will be returning the regId of the other
        // participant as the chat name.
        return (chat.participants[0].regId === regId)
          ? chat.participants[1].regId : chat.participants[0].regId;
      }
      // Otherwise, return the chat's subject.
      return chat.subject;
    }
  });
```

### <a name="importChatMessageList"></a>Import and configure the bbmChatMessageList component

The `bbmChatMessageList` component is used to display the messages of a chat.
When focus is given to the window displaying the `bbmChatMessageList`, it will
automatically mark the most recently received message as `Read`.

```html
  <link rel="import" href="node_modules/bbmChatMessageList/bbmChatMessageList.html">
```

The `bbmChatMessageList` component has a template that allows you to control
its look, feel, and behavior.  In this example, your chat message list will
display incoming messages as left aligned text and outgoing messages as right
aligned text.  All outgoing messages will also be decorated with a string that
represents its current status.

```html
<bbm-chat-message-list id="chatMessageList" style="display: none; height:100%">
  <template id="bubbleTemplate">
    <div style="text-align:[[getMessageAlignment(message)]]">[[getMessageStatus(message)]][[getMessageContent(message)]]</div>
  </template>
</bbm-chat-message-list>
```

The `bbmChatMessageList` component only requires a setup instance
[`BBMEnterprise.Messenger`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Messenger.html)
it will do the rest.  In this example, the functions used for display in the
template are defined as part the component's context.

```javascript
  // Configure the chatMessageList component.  It needs a handle to the
  // SDK's messenger object.  We also setup a context for the element that
  // defines how it will behave.
  chatMessageList.setBbmMessenger(sdk.messenger);
  chatMessageList.setContext({
    /**
     * A function to retrieve the status indicator to use for an outgoing
     * message.
     *
     * @param {BBMEnterprise.ChatMessage} message
     *   The message to retrieve status for.
     *
     * @returns {string}
     *   The empty string is used for all incoming messages.  Otherwise, the
     *   following status indicators are used:
     *   - (...) => Sending
     *   - (S)   => Sent
     *   - (D)   => Delivered
     *   - (R)   => Read
     *   - (F)   => Failed
     *   - (?)   => Any unknown status value.
     */
    getMessageStatus: (message) => {
      if (message.isIncoming) {
        return '';
      }
      switch (message.state.value) {
        case 'Sending': return '(...)';
        case 'Sent': return '(S)';
        case 'Delivered': return '(D)';
        case 'Read': return '(R)';
        case 'Failed': return '(F)';
        default: return '(?)';
      }
    },

    /**
     * A function to retrieve the content to use for a message.
     *
     * @param {BBMEnterprise.Messenger.ChatMessage} message
     *   The message to retrieve content for.
     *
     * @returns {string}
     *   The content for a Text message, and other appropriate
     *   values for other types of messages.
     */
    getMessageContent: (message) =>
      message.tag === 'Text' ? message.content : message.tag,

    /**
     * A function to retrieve the alignment to use for a message.
     *
     * @param {BBMEnterprise.ChatMessage} message
     *   The message to retrieve alignment for.
     *
     * @returns {string}
     *   The alignment for the message.
     */
    getMessageAlignment: (message) =>
      message.isIncoming ? 'right' : 'left'
  });
```

### <a name="importChatInput"></a>Import and configure the bbmChatInput component

The `bbmChatInput` component is used to send text messages to the chat
currently being displayed.

```html
  <link rel="import" href="node_modules/bbmChatInput/bbmChatInput.html">
```

The `bbmChatInput` component only requires a setup instance
[`BBMEnterprise.Messenger`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/BBMEnterprise.Messenger.html)
it will do the rest.

```javascript
  // Configure the chatInput component.  It needs a handle to the SDK's
  // messenger object.
  chatInput.setBbmMessenger(sdk.messenger);
```

You can send text messages to the chat being displayed by typing in the
`bbmChatInput` component.  You can press `Enter` or click the Send button to
send your message to the chat.

## License

These examples are released as Open Source and licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find a issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-javascript-samples/issues).
