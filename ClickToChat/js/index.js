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

// When the user clicks the button to start a chat with the configured user,
// this function will be called.  It will be defined only when the user has
// successfully logged in.
let startChat;

window.onload = async () => {
  // There are several asynchronous actions that must occur before we can use
  // the bbmChat component.  These are completed below.
  //
  // On successful completion, this promise will resolve with an object
  // containing the setup SDK and the identity of the configured user ID.  On
  // failure, this promise will be rejected with an Error.
  const bbmChatIsReady = new (function() {
    this.promise = new Promise((resolve, reject) => {
      this.resolve = resolve;
      this.reject = reject;
    });
  })();

  // Define the startChat function so that it is available right away.  This
  // function will only do something if the user is not yet chatting with
  // the configured user.
  let isChatting = false;
  startChat = async () => {
    // If we are already chatting, don't do anything.
    if (isChatting) {
      console.log('ClickToChat: chat is already in progress');
      return;
    }

    // Remember that we've tried to start a chat.
    isChatting = true;

    try {
      // Before we can start a chat, we must wait for all of the chat
      // creation dependencies to have completed.  This includes the SDK
      // setup and the identity lookup for the user that we will be starting
      // a chat with.
      const { sdk, identity } = await bbmChatIsReady.promise;

      // Begin a 1:1 chat with the configured user.
      const newChat = await sdk.messenger.chatStart({
        // This is a one-to-one chat with the configured user.
        isOneToOne: true,
        invitees: identity.regId
      });

      // Let the bbmChat component handle the chat interactions.
      const bbmChat = Polymer.dom(document.body).querySelector('#bbm-chat');
      bbmChat.setChatId(newChat.chat.chatId);

      // Show the chat window.
      document.querySelector('#chat-pane').style.display = 'block';

      // Listen for the chatDefunct event which indicates that the chat is
      // no longer active.  We use this event hide the bbmChat component
      // and indicate that the user is no longer chatting.
      bbmChat.addEventListener('chatDefunct', () => {
        document.querySelector('#chat-pane').style.display = 'none';
        isChatting = false;
      });
    }
    catch(error) {
      alert(`ClickToChat failed to start chat; error=${error}`);
      isChatting = false;
    }
  };

  // Do all of the asynchronous actions that are needed to prepare the bbmChat
  // component for use.
  try {
    // Make sure that the browser supports all of the necessary functionality,
    // including support for interacting with the BlackBerry Key Management
    // Service (KMS).
    await BBMEnterprise.validateBrowser({
      kms: { argonWasmUrl: KMS_ARGON_WASM_URL }
    });

    // Setup the authentication manager for the application.
    const authManager = new AuthenticationManager(AUTH_CONFIGURATION);
    if (AuthenticationManager.name === 'MockAuthManager') {
      // We are using the MockAuthmanager, so we need to override how it
      // acquires the local user's user ID.
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
    }

    // Authenticate the user.  Configurations that use a real identity
    // provider (IDP) will redirect the browser to the IDP's authentication
    // page.
    const authUserInfo = await authManager.authenticate();
    if (!authUserInfo) {
      console.warn('Redirecting for authentication.');
      return;
    }

    // Instantiate the SDK.
    const sdk = new BBMEnterprise({
      domain: DOMAIN_ID,
      environment: ENVIRONMENT,
      userId: authUserInfo.userId,
      getToken: () => authManager.getBbmSdkToken(),
      description: navigator.userAgent,
      kmsArgonWasmUrl: KMS_ARGON_WASM_URL,

      // This example uses the bbm-chat-message-list web component to manage
      // the message list.  It is a Polymer component that directly watches
      // for changes to the message storage array in order to efficiently
      // update the display.  To allow the bbm-chat-message-list to monitor
      // changes in the SDK's stored messages, we configure the SDK to build
      // its message storage array using the SpliceWatcher message storage
      // factory.
      messageStorageFactory: BBMEnterprise.StorageFactory.SpliceWatcher
    });

    // Setup is asynchronous.  Create a promise we can use to wait on
    // until the SDK setup has completed.
    const sdkSetup = new Promise((resolve, reject) => {
      // Handle changes to the SDK's setup state.
      let isSyncStarted = false;
      sdk.on('setupState', (state) => {
        console.log(
          `ClickToChat: BBMEnterprise setup state: ${state.value}`);
        switch (state.value) {
          case BBMEnterprise.SetupState.Success: {
            // Setup was successful.
            resolve();
            break;
          }
          case BBMEnterprise.SetupState.SyncRequired: {
            if (isSyncStarted) {
              // We have already tried to sync the user's keys using the
              // given passcode.  For simplicity in this example, we don't
              // try to recover when the configured passcode cannot be
              // used.
              reject(new Error(
                'Failed to get user keys using provided KEY_PASSCODE.'));
              return;
            }

            // We need to provide the SDK with the user's key passcode.
            sdk.syncStart(
              // For simplicity in this example, we always use the
              // configured passcode.
              KEY_PASSCODE,

              // Does the user have existing keys?
              sdk.syncPasscodeState === BBMEnterprise.SyncPasscodeState.New
              // No, we must create new keys.  The key passcode will be
              // used to protect the new keys.
              ? BBMEnterprise.SyncStartAction.New
              // Yes, we have existing keys.  The key passcode will be
              // used to unprotect the keys.
              : BBMEnterprise.SyncStartAction.Existing
            );
            break;
          }
          case BBMEnterprise.SetupState.SyncStarted: {
            // Syncing of the user's keys has started.  Remember this so
            // that we can tell if the setup state regresses.
            isSyncStarted = true;
            break;
          }
        }
      });

      // Any setup error received will fail the SDK setup promise.
      sdk.on('setupError', error => {
       reject(new Error(
         `Endpoint setup failed: ${error.value}`));
      });

      // Start the SDK setup.
      sdk.setupStart();
    });

    // Wait for the SDK setup to complete.
    await sdkSetup;

    // This example doesn't remove the event listeners on the setupState
    // or setupErrors events that were used to monitor the setup progress.
    // It also doesn't setup new listeners to monitor these events going
    // forward to act on any issue that causes the SDK's state to regress.

    // Create and initialize the user manager.
    const userManager = await createUserManager(
      sdk.getRegistrationInfo().regId,
      authManager,
      (...args) => sdk.getIdentitiesFromAppUserIds(...args)
    );
    await userManager.initialize();

    // Setup the bbmChat component to use the SDK and contact manager that
    // we've created for it to use.  We also disable the media
    // capabilities of the component.
    const bbmChat = Polymer.dom(document.body).querySelector('#bbm-chat');
    bbmChat.setBbmSdk(sdk);
    bbmChat.setContactManager(userManager);
    bbmChat.getChatHeader().set('isMediaEnabled', false);

    // Customize the look and feel of the messages displayed in the chat.
    bbmChat.setTimeRangeFormatter(new TimeRangeFormatter());
    bbmChat.setMessageFormatter(new MessageFormatter(userManager));

    // We need to lookup the regId of the configured user that we will be
    // starting a chat with.
    const identity = await sdk.getIdentitiesFromAppUserId(AGENT_USER_ID);

    // The bbmChat component is now ready for use.
    bbmChatIsReady.resolve({ sdk, identity });
  }
  catch (error) {
    console.error(`ClickToChat encountered an error; error=${error}`);
    console.error(error);
    bbmChatIsReady.reject(error);
  }
};

