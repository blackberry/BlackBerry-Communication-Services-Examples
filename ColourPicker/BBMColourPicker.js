//
//  Copyright (c) 2018 BlackBerry.  All Rights Reserved.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
// 
//  http://www.apache.org/licenses/LICENSE-2.0
// 
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

//
// An example of interfacing with a Node.js script on a Raspberry Pi
// using the BBM Enterprise Node SDK
//

const login = require('./GoogleLogin');
const config = require('./config');
const firebase = require('firebase');
const hexParse = require('./hexParser');

//  Create an SDK instance.
login.login()
.then(function(bbmsdk) {
  var messenger;
  
  //  Look at messages.
  bbmsdk.messenger.on('chatMessageAdded', function(addedEvent) {
    const message = addedEvent.message;
    console.log(message);
	 
    function interpretMessage() {
      //  Mark the message as read.
      messenger.chatMessageRead(message.chatId, message.messageId);

      //  Parse incoming message and display write to Pi PWM controllers.
      hexParse(message.content);
    }
    
    //  If the chat is not Ready, then fetch no messages with NoSync, to lie
    //  to the recipient and say we have read all of their messages even
    //  though we may not have. The bot only responds to online messages.
    if(addedEvent.chat.state === BBMEnterprise.Messenger.Chat.State.Waiting) {
      //  The chat is not ready. Get it ready first, then read message.
      messenger.fetchChatMessages(
        message.chatId,
        {
          minFetchCount: 0,
          minStatus: BBMEnterprise.Messenger.NoSync
        })
      .then(interpretMessage);
    } else {
      //  The chat is ready, just read.
      interpretMessage();
    }
    
  });

  //  Set up the sdk.
  bbmsdk.setup()
  .then(function(chatInterfaces) {
    messenger = chatInterfaces.messenger;
  });
});

