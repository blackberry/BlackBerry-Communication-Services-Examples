/*
 * Copyright (c) 2018 BlackBerry.  All Rights Reserved.
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

// Usage: node ./BBMBot.js [idp]
//   Where idp is the identity provider of choice.   Valid values are:
//
//     * google:  Google will be used as the identity provide.  Only valid
//                Google identities can be used.
//
//     * mock:    No identity provider is to be used.  Only stubbed tokens and
//                identity will be provided.
//
//   If no idp value is supplied, the default idp is 'mock'.
//
const idp = process.argv.length === 3 ? process.argv[2] : 'mock';

// Try to load the configuration for the IDP.
let config;
let login;
switch(idp) {
case 'google':
  console.log('Loading configuration from: ./config_google.js');
  config = require('./config_google');
  login = require('./GoogleLogin');
  break;

case 'mock':
  console.log('Loading configuration from: ./config_mock.js');
  config = require('./config_mock');
  login = require('./MockLogin');
  break;

default:
  throw new Error(
    `Invalid idp argument: ${idp}; supported IDP: google, mock`);
}

const bot = require('./botlibre')(config.botLibre.application,
                                  config.botLibre.instance);

// Load the module we use to setup the SDK.
const sdkSetup = require('./SdkSetup');

const BBMEnterprise = require('bbm-enterprise');

// Create an SDK instance.
login.login(config)
.then((authManager) => sdkSetup.sdkSetup(config, authManager))
.then(sdk => {
  // Cache the messenger for simplicity.
  const messenger = sdk.messenger;

  // A helper function to say what to do with a message. We will send it off
  // to the bot web service, and then post a message in reply with either the
  // answer, or the error if we couldn't get an answer.
  const reply = (chatId, message) => {
    try {
      // Then send the message to a chatbot service.
      bot(message)
      .then(response => {
        // And post a message with the response.
        messenger.chatMessageSend(chatId, {
          tag: 'Text',
          content: response
        });
      })
      .catch(error => {
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
  };

  // Look at messages.
  sdk.messenger.on('chatMessageAdded', addedEvent => {
    const message = addedEvent.message;

    // Only respond if the message is incoming.
    if(message.isIncoming) {
      // A function to respond to an incoming message. Mark the message as read,
      // then reply to the message if necessary. Reply to all messages in a 1:1
      // chat, and to those prefixed with @bbmbot in a multiparty chat.
      const sendResponse = () => {
        // Mark the message as read.
        messenger.chatMessageRead(message.chatId, message.messageId)
        .catch(error => console.error(
          'bbm-chat-message-list: Cannot send message read notification for '
          + 'messageId=' + message.messageId + ' in chat=' + message.chatId
          + ': ' + error
        ));

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
      };

      // If the chat is not Ready, then fetch no messages with NoSync, to lie
      // to the recipient and say we have read all of their messages even
      // though we may not have. The bot only responds to online messages.
      if(addedEvent.chat.state === BBMEnterprise.Messenger.Chat.State.Waiting) {
        // The chat is not ready. Get it ready first, then reply.
        messenger.fetchChatMessages(
          message.chatId,
          {
            minFetchCount: 0,
            syncMode: BBMEnterprise.Messenger.NoSync
          })
        .then(sendResponse);
      } else {
        // The chat is ready, just reply.
        sendResponse();
      }
    }
  });
});
