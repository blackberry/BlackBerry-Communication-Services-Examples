![BlackBerry Spark Communications Platform](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Simple Chat for iOS

The Simple Chat sample demonstrates how you can build a simple chat
application using the Spark SDK and how easily messaging can be integrated
into your application.  For a more rich chat app experience please see the
[Rich Chat](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/ios/RichChat/README.html)
app provided with the SDK.
This example builds on the [Quick Start](../QuickStart/README.md) example that
demonstrates how you can authenticate with the SDK using the [Identity Provider](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/identityManagement.html) 
of your application.

### Features

This app can be used with the other sample apps but will only send and render messages with
the "Text" tag.  It will allow you to do the following:

* Authenticate a user
* Public key management
* Create a chat
* View a list of chats
* Send text messages
* Mark messages as read and view delivery status

This sample can interact with the
[Rich Chat](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/ios/RichChat/README.html)
and [Quick Start](../QuickStart/README.md) samples so it may utilize the same
configuration.  SimpleChat will only render and send messages with the "Text" 
tag to the [Rich Chat](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/ios/RichChat/README.html) app.


## Getting Started

This sample requires the Spark SDK, which you can find along with related resources at the location below.
    
* Getting started with the [Spark SDK](https://developers.blackberry.com/us/en/products/blackberry-bbm-enterprise-sdk.html)
* [Development Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/index.html)
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/ios/index.html)

<p align="center">
    <a href="http://www.youtube.com/watch?feature=player_embedded&v=H1yiHSGsAIg"
      target="_blank"><img src="../QuickStart/screenShots/bbme-sdk-ios-getting-started.jpg" 
      alt="YouTube Getting Started Video" width="486" height="" border="364"/></a>
</p>
<p align="center">
 <b>Getting started video</b>
</p>

### Configuration

* [Sample application configuration using Google Sign-In and Firebase](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/googleSignInForiOSExamples.html)
* [Sample application configuration using Azure Active Directory](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/azureForiOSExamples.html)


## Walkthrough

- [Support Library](#supportLib)
- [SimpleChatApp](#simpleChatApp)
- [StartChatViewController](#startChatViewController)
- [ChatsListViewController](#chatsListViewController)
- [ChatViewController](#chatViewController)

### <a name="supportLib"></a>Support Library

Common code for authenticating users, synchronizing keys and performing common tasks can be found in /examples/Support/Source.  Much of the heavy lifting can be found in these classes and they should be referenced before implementing a custom key management or authentication scheme.

*BBMAuthController* and *BBMKeyManager* can be instantiated with classes/instances that allow you to substitute your own user authentication or key management scheme while handling all of the SDK related functionality.

*BBMAccess* provides wrappers around common outgoing messages and the SDK data model.

You may use, extend, or modify this code as needed for your own application.

### <a name="simpleChatApp"></a>SimpleChatApp

The *SimpleChatApp* class is the primary entry point for the application and owns and instance of a *BBMAuthController* and a *BBMKeyManager*.  These classes provide authentication via GoogleSignIn and key management via Firebase.  *SimpleChatApp* uses ObservableMonitors to monitor the credential and service state on the BBMAuthController instance and syncronizes keys and/or configures services when the appropriate conditions are met.


For *BBMAuthController*, you can substitute an implementation of *id&lt;BBMTokenManager&gt;* that interacts with your oAuth provider of choice.  Configuration is handled via the ConfigSettings.plist file:
```swift
private let _authController : BBMAuthController! = {
    let controller = BBMAuthController.fromConfigFile()
    return controller
}()
```

Sample Token Managers are provided for both Azure AD and Google SignIn.  To use Google SignIn with the BlackBerry Key Management Service (for example), the ConfigSettings.plist should be configured as follows:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>authProvider</key>
    <string>googleSignIn</string>
    <key>useBlackBerryKMS</key>
    <true/>

    <!-- This key must match the authProvider value above -->
    <key>googleSignIn</key>
    <dict>
    <key>domain</key>
    <string>blackberry_service_domain (ie :abde-ed6d-11e6-a754-f8cab45257283)</string>
    <key>clientId</key>
    <string>google_client_id (ie: 485684fd-643d-4612-83c6-71a075f4357f)</string>
    <key>environment</key>
    <string>sandbox</string>
</dict>
</dict>
</plist>
```

### <a name="startChatViewController"></a>StartChatViewController

*StartChatViewController* shows how to start a chat.   The details for starting a chat are found in *BBMChatCreator.m* which submits the request to start a chat to the service and waits for the response before calling back to the view controller.

```objective-c
[self.chatCreator startConferenceWithRegIds:@[regId]
                                    subject:subject
                                   callback:^(NSString *chatId, BBMChatStartFailedMessageReason failReason) {
    if(chatId) {
        //Chat with chatId was created
    }else{
        //Check failReason.. Something went wrong
    }
}];
```

The implementation of startConferenceWithRegIds:subject:callback: in *BBMChatCreator* looks something like this:
```objective-c
NSMutableArray *invitees = [[NSMutableArray alloc] init];
for (NSNumber *regId in regIds) {
    BBMChatStartMessage_Invitees *invitee = [[BBMChatStartMessage_Invitees alloc] init];
    invitee.regId = regId;
    [invitees addObject:invitee];
}

BBMChatStartMessage *chatStart = [[BBMChatStartMessage alloc] initWithInvitees:invitees
                                                                       subject:subject];
chatStart.isOneToOne = isOneToOne;
[[BBMEnterpriseService service] sendMessageToService:chatStart];

//Wait for the BBMChatStartFailedMessage or a ListAdd to the chat List
```

### <a name="chatsListViewController"></a>ChatsListViewController

*ChatsListViewController* will render a list of the available chats.  An ObservableMonitor is used to monitor the list of chats and update the tableView as chats are added or removed.

*ChatsListViewController* monitors the chat list and updates a table view for any changes via:
```objective-c
//Observe the list of chats.  Note the use of the "observableArray" property on the chat
//LiveList which will trigger this monitor any time there is a change in the chat LiveList.
//We only need to be running this monitor while the view is visible.
typeof(self) __weak weakSelf = self;
self.chatsMonitor = [ObservableMonitor monitorActivatedWithName:@"chatsMonitor" block:^{
    BBMLiveList *chatsList = [[[BBMEnterpriseService service] model] chat];
    NSMutableArray *validChats = [NSMutableArray array];
    for(BBMChat *chat in chatsList.observableArray) {
        //Do not render hidden or defunct chats
        if(!chat.isHiddenFlagSet && !(chat.state == kBBMChat_StateDefunct)) {
            [validChats addObject:chat];
        }
    }

    //Reload the table if the list of chats has changed
    if(![weakSelf.chats isEqualToArray:validChats]) {
        weakSelf.chats = validChats;
        [weakSelf.tableView reloadData];
    }
}];
```

### <a name="chatViewController"></a>ChatViewController

*ChatViewController* will render a list of the messages in a specific chat.  An ObservableMonitor is used to monitor the message count and last message identifier on the chat and request messages and update the tableView when either change.

*ChatViewController* will monitor the chat state and load new messages and update the tableView via:
```
//This monitor will lazy-load all of the messages in a given chat and add them to an array
//that we can use to drive our tableView.  chat.lastMessage and chat.numMessages are both
//observable properties so this block will run whenever these change - which will happen whenever
//a message is added or removed.
typeof(self) __weak weakSelf = self;
self.chatMonitor = [ObservableMonitor monitorActivatedWithName:@"chatMonitor" block:^{
    unsigned long long lastMsg = chat.lastMessage;
    unsigned long long firstMsg = lastMsg - chat.numMessages + 1;   //Message ids are 1-indexed
    NSMutableArray *messages = [NSMutableArray array];

    BBMLiveMap *msgMap = [[BBMEnterpriseService service] model].chatMessage;

    for(unsigned long long msgId = firstMsg; msgId <= lastMsg; msgId++) {
        BBMChatMessageKey *key = [BBMChatMessageKey keyWithChatId:chat.chatId messageId:msgId];
        BBMChatMessage *msg = msgMap[key];

        //In practice, you may not wish to render all chatMessages.  Here, we render only
        //messages with the @"Text" tag.  Alternatively, you can use chatMessageWithCritera
        //to get customized chatMessage maps that include only messages for specific chats that
        //have a specific message tag.  Here we use the primary chatMessage map that will load
        //any message for any chat on demand.  Chats may also be very long, in which case you
        //may wish to load messages only as the user scrolls to them (see RichChat)

        //Ignore everything except messages with a "Text" tag.
        if([msg.tag isEqualToString:@"Text"]) {
            //Message ids should be in chronological order.  You can also sort by timestamp
            //if needed.
            [messages addObject:msg];
        }
    }

    if(![weakSelf.messages isEqualToArray:messages]) {
        //If the list of messages has changed, reload the table.
        weakSelf.messages = messages;
        [weakSelf.tableView reloadData];
    }
}];
```

When messages are loaded, we will find the last unread incoming message and mark it as read via:
```objective-c
//Messages will be ordered from oldest to newest.  Marking the newest unread incoming message
//as read will mark all previous incoming messages as read, likewise if the newest incoming
//message is read, then all other incoming messages have already been marked as read.
for(BBMChatMessage *message in self.messages.reverseObjectEnumerator) {
    if(message.isIncomingFlagSet && message.state != kBBMChatMessage_StateRead) {
        [BBMAccess markMessagesRead:@[message]];
        break;
    }else if(message.isIncomingFlagSet && message.state == kBBMChatMessage_StateRead) {
        //All of the messages are already marked as read
        break;
    }
}
```

## License

These samples are released as Open Source and licensed under the
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html). 

These samples were created using SDKs from Apple Inc. and may contain code
licensed for use only with Apple products. Please review your Apple SDK
Agreement for additional details.

This page includes icons from: https://material.io/icons/ used under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find a issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-ios-samples/issues).
