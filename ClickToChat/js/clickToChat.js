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
var bbmeSdk;
var isChatting = false;
var isSyncStarted = false;

var CHAT_DETAILS = {
  invitees: [CONTACT_REG_ID],
  isOneToOne: true,
  subject: ''
};

window.onload = () => {
  try {
    BBMEnterprise.validateBrowser().then(() => {
      bbmChat = document.querySelector('#bbm-chat');
      window.customElements.whenDefined(bbmChat.localName).then(() => {
        bbmChat.addEventListener('chatDefunct', () => {
          const chatPane = document.querySelector('#chat-pane');
          chatPane.style.display = 'none';
          isChatting = false;
        });
      });
    }).catch(() => {
      alert('Failed to validate browser.');
    });
  } catch (error) {
    console.error('Failed to validate browser : ' + error);
  }
};

// Function starts chat with with the Chat Bot. Initializes bbmeSDK if not yet
// initialized.
function startChat () {
  if (isChatting) {
    console.log('User is already in chat.');
    return;
  }
  // If messenger is defined, then start chat. Otherwise call initBbme to
  // initialize BBM Enterprise SDK for JavaScript, and define messenger.
  if (bbmMessenger) {
    bbmMessenger.chatStart(CHAT_DETAILS).then(pendingChat => {
      bbmChat.setChatId(pendingChat.chat.chatId);
      const chatPane = document.querySelector('#chat-pane');
      chatPane.style.display = 'block';
      isChatting = true;
    });
  }
  else {
    initBbme()
    .then(userManager => {
      bbmMessenger = bbmeSdk.messenger;
      bbmChat.setBbmSdk(bbmeSdk);
      bbmChat.setContactManager(userManager);
      bbmChat.setTimeRangeFormatter(new TimeRangeFormatter());
      bbmChat.setMessageFormatter(new MessageFormatter(userManager));
      bbmChat.getChatHeader().set('isMediaEnabled', false);
      userManager.initialize()
      .then(() => {
        startChat();
      });
    }).catch(error => {
      console.warn('Failed to initialize BBM Enterprise SDK for JavaScript. '
        + error);
      alert('Failed to initialize BBM Enterprise SDK for JavaScript. '
        + error );
    });
  }
}

// Function initializes BBM Enterprise SDK for JavaScript.
function initBbme() {
  return new Promise((resolve, reject) => {
    const authManager = createAuthManager();
    authManager.authenticate()
    .then(authUserInfo => {
      bbmeSdk = new BBMEnterprise({
        domain: ID_PROVIDER_DOMAIN,
        environment: ID_PROVIDER_ENVIRONMENT,
        userId: authUserInfo.userId,
        getToken: authManager.getBbmSdkToken,
        description: navigator.userAgent,
        messageStorageFactory: BBMEnterprise.StorageFactory.SpliceWatcher,
        kmsArgonWasmUrl: KMS_ARGON_WASM_URL
      });

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
              resolve(userManager);
            });
            break;
          }
          case BBMEnterprise.SetupState.SyncRequired: {
            if (isSyncStarted) {
              reject(new Error('Failed to get user keys using provided USER_SECRET'));
              return;
            }
            const isNew =
              bbmeSdk.syncPasscodeState === BBMEnterprise.SyncPasscodeState.New;
            const syncAction = isNew
              ? BBMEnterprise.SyncStartAction.New
              : BBMEnterprise.SyncStartAction.Existing;
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
        alert(`BBM Enterprise registration failed: ${error.value}`);
        reject(error.value);
      });
  
      // Start BBM Enterprise setup.
      bbmeSdk.setupStart();
    });
  });
}
