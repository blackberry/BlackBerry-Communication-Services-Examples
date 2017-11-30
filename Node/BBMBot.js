/*
 * Copyright (c) 2017 BlackBerry.  All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

//
// An example of a chatbot. This user will log in, and then respond to any
// message which starts with '@bbmbot'.
//

const login = require('./GoogleLogin');
const config = require('./config');
const bot = require('./botlibre')(config.botLibre.application,
                                  config.botLibre.instance);
const firebase = require('firebase');

// Create an SDK instance.
login.login()
.then(function(bbmsdk) {
  var messenger;
  function reply(chatId, message) {
    try {
      // Then send the message to a chatbot service.
      bot(message)
      .then(function(response) {
        // And post a message with the response.
        messenger.chatMessageSend(chatId, {
          tag: 'Text',
          content: response
        });
      })
      .catch(function(error) {
        messenger.chatMessageSend(chatId, {
          tag: 'Text',
          content: error.toString()
        });
      });
    } catch(error) {
      messenger.chatMessageSend(chatId, {
        tag: 'Text',
        content: error.toString()
      });
    }
  }

  // Look at messages.
  bbmsdk.messenger.on('chatMessageAdded', function(addedEvent) {
    const message = addedEvent.message;

    // Only respond if the message is incoming.
    if(message.isIncoming) {
      function sendResponse() {
        // Mark the message as read.
        messenger.chatMessageRead(message.chatId, message.messageId);

        // If the conversation is 1:1, always reply.
        if(messenger.getChat(message.chatId).isOneToOne) {
          reply(message.chatId, message.content);
        } else {
          // Or the message is prefixed with '@bbmbot' in a multiparty chat,
          // then reply.
          var match = /@bbmbot (.*)/.exec(message.content);
          if(match) {
            reply(message.chatId, match[1]);
          }
        }
      }
      // If the chat is not Ready, then fetch no messages with NoSync, to lie
      // to the recipient and say we have read all of their messages even
      // though we may not have. The bot only responds to online messages.
      if(addedEvent.chat.state === BBMEnterprise.Messenger.Chat.State.Waiting) {
        // The chat is not ready. Get it ready first, then reply.
        messenger.fetchChatMessages(
          message.chatId,
          {
            minFetchCount: 0,
            minStatus: BBMEnterprise.Messenger.NoSync
          })
        .then(sendResponse);
      } else {
        // The chat is ready, just reply.
        sendResponse();
      }
    }
  });

  // Set up the sdk.
  bbmsdk.setup()
  .then(function(chatInterfaces) {
    messenger = chatInterfaces.messenger;
  });
});
