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
 * This is the example application, which displays a very basic implementation
 * of how to implement generic Click To Chat functionality using the bbm-chat UI
 * widget.
 *
 * When the user clicks "Start Secure Chat" button, the application will start a
 * chat with the hard coded user RegId (CONTACT_REG_ID).
 *
 * @class ClickToChat
 * @memberof Examples
 */

let bbmChat;
let isChatting = false;
const CHAT_DETAILS = {
  invitees: [CONTACT_REG_ID],
  isOneToOne: true,
  subject: ''
};

const authManager = new AuthenticationManager(AUTH_CONFIGURATION);
// Override getUserId() used by the MockAuthManager.
authManager.getUserId = () => new Promise((resolve, reject) => {
  const userEmailDialog = document.createElement('bbm-user-email-dialog');
  document.body.appendChild(userEmailDialog);
  userEmailDialog.addEventListener('Ok', e => {
    userEmailDialog.parentNode.removeChild(userEmailDialog);
    resolve(e.detail.userEmail);
  });
  userEmailDialog.addEventListener('Cancel', () => {
    userEmailDialog.parentNode.removeChild(userEmailDialog);
    reject('Failed to get user email.');
  });
});

window.onload = async () => {
  try {
    bbmChat = document.querySelector('#bbm-chat');
    await window.customElements.whenDefined(bbmChat.localName);
    await BBMEnterprise.validateBrowser();
    bbmChat.addEventListener('chatDefunct', () => {
      const chatPane = document.querySelector('#chat-pane');
      chatPane.style.display = 'none';
      bbmChat.setContactManager(undefined);
      bbmChat.setBbmSdk(undefined);
      bbmChat.setTimeRangeFormatter(undefined);
      bbmChat.setMessageFormatter(undefined);
      isChatting = false;
    });

    // The user is already authenticated. Start chat.
    if (authManager.isAuthenticated()) {
      startChat();
    }
  }
  catch (error) {
    console.error(`Failed to start application. Error: ${error}`);
  }
};

/**
 * This function starts a chat with using the configured CHAT_DETAILS
 * {@link BBMEnterprise.Messenger.ChatStartOptions} object. This will also
 * initialize the SDK instance if necessary.
 */
async function startChat() {
  if (isChatting) {
    console.log('User is already in chat.');
    return;
  }
  else {
    try {
      isChatting = true;
      const bbmeSdk = await initBbmeSdk();
      if (!bbmeSdk) {
        console.warn('Redirecting app to the authentication page...');
        return;
      }
      const userRegId = bbmeSdk.getRegistrationInfo().regId;
      const userManager = await createUserManager(userRegId, authManager,
        bbmeSdk.getIdentitiesFromAppUserIds);
      await userManager.initialize();
      bbmChat.setBbmSdk(bbmeSdk);
      bbmChat.setContactManager(userManager);
      bbmChat.setTimeRangeFormatter(new TimeRangeFormatter());
      bbmChat.setMessageFormatter(new MessageFormatter(userManager));
      bbmChat.getChatHeader().set('isMediaEnabled', false);
      const pendingChat = await bbmeSdk.messenger.chatStart(CHAT_DETAILS);
      const chatPane = document.querySelector('#chat-pane');
      bbmChat.setChatId(pendingChat.chat.chatId);
      chatPane.style.display = 'block';
    }
    catch (error) {
      const errorMessage = 
        `Failed to initialize the SDK. Error: ${error}`;
      alert(errorMessage);
      isChatting = false;
    }
  }
}

/**
 * This function initializes the SDK.
 */
function initBbmeSdk() {
  return new Promise(async (resolve, reject) => {
    try {
      let isSyncStarted = false;
      const authUserInfo = await authManager.authenticate();
      if (!authUserInfo) {
        console.log('Application will be redirected to authentication page.');
        resolve();
        return;
      }

      const sdk = new BBMEnterprise({
        domain: ID_PROVIDER_DOMAIN,
        environment: ID_PROVIDER_ENVIRONMENT,
        userId: authUserInfo.userId,
        getToken: authManager.getBbmSdkToken,
        description: navigator.userAgent,
        messageStorageFactory: BBMEnterprise.StorageFactory.SpliceWatcher,
        kmsArgonWasmUrl: KMS_ARGON_WASM_URL
      });

      // Handle changes of BBM Enterprise setup state.
      sdk.on('setupState', state => {
        console.log(`BBMEnterprise setup state: ${state.value}`);
        switch (state.value) {
          case BBMEnterprise.SetupState.Success: {
            resolve(sdk);
            break;
          }
          case BBMEnterprise.SetupState.SyncRequired: {
            if (isSyncStarted) {
              reject(new Error('Failed to get user keys using provided USER_SECRET'));
              return;
            }
            const isNew =
              sdk.syncPasscodeState === BBMEnterprise.SyncPasscodeState.New;
            const syncAction = isNew
              ? BBMEnterprise.SyncStartAction.New
              : BBMEnterprise.SyncStartAction.Existing;
            sdk.syncStart(USER_SECRET, syncAction);
          }
          break;
          case BBMEnterprise.SetupState.SyncStarted:
            isSyncStarted = true;
          break;
        }
      });

      // Handle setup error.
      sdk.on('setupError', error => {
        alert(`BBM Enterprise registration failed: ${error.value}`);
        reject(error.value);
      });

      // Start BBM Enterprise setup.
      sdk.setupStart();
    }
    catch(error) {
      reject(error);
    }
  });
}
