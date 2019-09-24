/*
 * Copyright (c) 2019 BlackBerry.  All Rights Reserved.
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

// Usage: node ./BBMBot.js

// Try to load the configuration for the IDP.
const config = require('./config_mock');
const login = require('./MockLogin');

const bot = require('./botlibre')(config.botLibre.application,
                                  config.botLibre.instance);

// Load the module we use to setup the SDK.
const sdkSetup = require('./SdkSetup');

const SparkCommunications = require('bbm-enterprise');

// Create an SDK instance.
login.login(config)
.then((authManager) => sdkSetup.sdkSetup(config, authManager))
.then(sdk => {
  // Cache the messenger for simplicity.
  const messenger = sdk.messenger;

  // This example doesn't need any chat history to function, however we
  // restore at least 1 messages so that we can mark all messages up until the
  // last message in the chat as read as soon as we know about the chat.
  const restoreChat = async (chatId) => {
    let messages;
    try {
      messages = await messenger.fetchChatMessages(chatId,
                                                   { minFetchCount: 1 });

      // If any messages were restored, mark them all as read, but don't
      // reply to anything.
      if (messages.length > 0) {
        const message = messages[messages.length - 1];
        await messenger.chatMessageRead(chatId, message.messageId);
      }
    }
    catch(error) {
      console.error(
        messages === undefined
        ? `Cannot restore chat messages in chat=${chatId}: ${error}`
        : `Failed to mark last message in chat=${chatId} as read: ${error}`
      );
    }
  };

  // We will restore at least 1 message for all chats that we know about
  // on setup completion and for each new chat we learn about.
  messenger.getChats().map((chat) => restoreChat(chat.chatId));
  sdk.messenger.on('chatAdded', async (chatAddedEvent) => {
    if(chatAddedEvent.chat.state
       === SparkCommunications.Messenger.Chat.State.Waiting)
    {
      restoreChat(chatAddedEvent.chat.chatId);
    }
  });

  // When a new chat message arrives, we will mark the message as read and
  // respond if it's appropriate for us to do so.
  sdk.messenger.on('chatMessageAdded', async ({ chat, message }) => {
    // We only automatically respond when we have an incoming text message for
    // a chat that is in the Ready state.
    if (! message.isIncoming || message.tag !== 'Text' ||
        chat.state !== SparkCommunications.Messenger.Chat.State.Ready)
    {
      return;
    }

    // Mark the message as read.
    try {
      await messenger.chatMessageRead(message.chatId, message.messageId);
    }
    catch(error) {
      console.error(
        'Cannot send message read notification for messageId='
        + `${message.messagId} in chat=${message.chatId}: ${error}`
      );
    }

    // If the message is received in a multi-party chat, we will only reply
    // when the message is prefixed with '@bbmbot'.
    let content = message.content;
    if (! chat.isOneToOne) {
      const match = /@bbmbot (.*)/.exec(content);
      if (! match) {
        // We are in a multi-party chat but the message was not directed at
        // the bot, so there is nothing further for us to do.
        return;
      }

      // Just remember the part after the '@bbmbot' prefix as the message.
      content = match[1];
    }

    // Reply the message using the bot web service.  If we encounter an error,
    // we ill respond with the error.
    let response;
    try { response = await bot(content); }
    catch(error) { response = `Failed to generate reply; error=${error}`; }

    try {
      messenger.chatMessageSend(chat.chatId,
                                { tag: 'Text', content: response });
    }
    catch(error) {
      console.error(
        `Failed to reply to messageId=${message.messageId} in chat=`
        + `${chat.chatId}: ${error}`
      );
    }
  });
});
