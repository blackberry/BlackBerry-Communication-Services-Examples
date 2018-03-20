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
 */

'use strict';

/**
 * This is the example application, which displays very basic implementation
 * of how to implement generic Click To Chat functionality using bbm-chat UI
 * widget.
 *
 * When user clicks "Start Secure Chat" button, application will start BBME
 * chat with the hard coded user RegId (CONTACT_REG_ID).
 *
 * @class ClickToChat
 * @memberof Examples
 */

var bbmMessenger;
var bbmChat;
var bbmSdk;
var contactsManager = null;

var CHAT_DETAILS = {
  invitees: [CONTACT_REG_ID],
  isOneToOne: true,
  subject: ""
};

window.onload = e => {
  try {
    BBMEnterprise.validateBrowser().then(() => {
      bbmChat = document.querySelector("#bbm-chat");
      window.customElements.whenDefined(bbmChat.localName).then(() => {
        enableStartChatButton(true);
        bbmChat.addEventListener("chatDefunct", () => {
          var chatPane = document.querySelector("#chat-pane");
          chatPane.style.display = "none";
          enableStartChatButton(true);
        });
      });
    }).catch( error => {
      console.error("Failed to validate browser : " + error);
      alert("Failed to validate browser.");
    });
  } catch (error) {
    console.error("Failed to validate browser : " + error);
  }
}

// Function starts chat with with the Chat Bot. Initializes bbmSDK if not yet
// initialized.
function startChat () {
  enableStartChatButton(false);
  // If messenger is defined, then start chat. Otherwise call initBbme to
  // initialize BBM Enterprise SDK for JavaScript, and define messenger.
  if (bbmMessenger) {
    bbmMessenger.chatStart(CHAT_DETAILS).then(pendingChat => {
      bbmChat.setChatId(pendingChat.chat.chatId);
      var chatPane = document.querySelector("#chat-pane");
      chatPane.style.display = "block";
    });
  }
  else {
    initBbme().then(data => {
      bbmMessenger = data.chatInterfaces.messenger;
      bbmChat.setBbmSdk(bbmSdk);
      bbmChat.setContactManager(data.contactsManager);
      bbmChat.setTimeRangeFormatter(data.timeRangeFormatter);
      bbmChat.setMessageFormatter(data.messageFormatter);
      bbmChat.getChatHeader().set('mediaEnabled', false);
      startChat();
    }).catch(error => {
      console.warn("Failed to initialize BBM Enterprise SDK for JavaScript: "
        + error);
      alert("Failed to initialize BBM Enterprise SDK for JavaScript");
      enableStartChatButton(true);
    })
  }
}

// Function initializes BBM Enterprise SDK for JavaScript.
// Promise of Object which has properties:
// - chatInterfaces: BBM Enterprise SDK for JavaScript registration information
// - contactsManager: instance of FirebaseUserManager
function initBbme() {
  return new Promise((resolve, reject) => {
    var authManager = createAuthManager();
    authManager.authenticate()
    .then(userInfo => {
      try {
        bbmSdk = new BBMEnterprise({
          domain: ID_PROVIDER_DOMAIN,
          environment: 'Sandbox',
          userId: userInfo.userId,
          getToken: () => authManager.getBbmSdkToken(),
          getKeyProvider: (regId, accessToken) =>
            createUserManager(regId, authManager).then(userManager => {
              contactsManager = userManager;
              return Promise.all([
                createKeyProtect(regId),
                userManager.initialize().then(() => userManager.getUid(regId))
              ]).then(results =>
                  createKeyProvider(results[1],
                    accessToken,
                    authManager,
                    userManager.getUid,
                    () => Promise.resolve('GenerateNewKeys'),
                    results[0]));
            }),
          description: navigator.userAgent,
          messageStorageFactory: BBMEnterprise.StorageFactory.SpliceWatcher
        });
      } catch (error) {
        reject(error);
      }

      // Initialize BBM Enterprise SDK for JavaScript and start the setup
      bbmSdk.setup().then(chatInterfaces => {
        var registrationInfo = bbmSdk.getRegistrationInfo();
        resolve({
          chatInterfaces: chatInterfaces,
          contactsManager: contactsManager,
          timeRangeFormatter: new TimeRangeFormatter(),
          messageFormatter: new MessageFormatter(contactsManager)
        });
      }).catch(error => reject(error));
    });
  });
}

// Function enables / disables "Start secure chat" button
function enableStartChatButton(value) {
  var startChat = document.querySelector("#start-chat");
  startChat.disabled = !value;
}
