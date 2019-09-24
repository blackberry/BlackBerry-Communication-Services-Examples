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

import '../../support/util/MessageFormatter.js';
import '../../support/util/TimeRangeFormatter.js';
import '../../support/util/Observer.js';

import "../node_modules/bbmChatInput/bbmChatInput.js";
import "../node_modules/bbmChatList/bbmChatList.js";
import "../node_modules/bbmChatMessageList/bbmChatMessageList.js";
import './threaded-chat-message-list.js';

// Declare local variables used by the HTML functions below.
let title;
let chatInput;
let chatMessageList;
let chatListDiv;
let leaveButton;

/**
 * A threaded chat program.
 *
 * @class ThreadedChat
 * @memberof Examples
 */

window.onload = async () => {
  // Find the necessary HTMLElements and cache them.
  title = document.getElementById('title');
  chatInput = document.getElementById('chatInput');
  chatMessageList = document.getElementById('chatMessageList');
  chatListDiv = document.getElementById('chatListDiv');
  leaveButton = document.getElementById('leaveButton');
  const status = document.getElementById('status');
  const chatList = document.getElementById('chatList');

  try {
    // Notify the user that we are authenticating.
    status.innerHTML = 'Authenticating';
    // Setup the authentication manager for the application.
    const authManager = new MockAuthManager();
    // We are using the MockAuthManager, so we need to override how it
    // acquires the local user's user ID.
    authManager.getUserId = () => new Promise((resolve, reject) => {
      const userId = prompt('Enter your user ID');
      if (userId && userId.trim()) {
        resolve(userId.trim());
      }
      else {
        reject('Failed to get user ID');
      }
    });

    // Authenticate the user.  Configurations that use a real identity
    // provider (IDP) will redirect the browser to the IDP's authentication
    // page.
    const authUserInfo = await authManager.authenticate();
    if (!authUserInfo) {
      console.warn('Redirecting for authentication.');
      return;
    }

    // Notify the user that we are working the SDK setup.
    status.innerHTML = 'Setting up your endpoint';

    // Instantiate the SDK.
    //
    // We use the SDK_CONFIG imported from the example's configuration file to
    // override some of the options used to configure the SDK.
    //
    // This example might not work if your SDK_CONFIG specifies any of the
    // parameters assigned below.
    const sdk = new SparkCommunications(Object.assign(
      {
        // You must specify your domain in the SDK_CONFIG.

        // This example requires user authentication to be disabled, which is
        // not supported in production.
        sandbox: true,

        // The user ID to use when connecting to the BlackBerry
        // Infrastructure.  We use the value returned by our identity
        // provider.
        userId: authUserInfo.userId,

        // The access token to use when connecting to the BlackBerry
        // Infrastructure.  We use the value returned by our identity
        // provider.
        getToken: () => authManager.getBbmSdkToken(),

        // We just use the browser's userAgent string to describe this
        // endpoint.
        description: navigator.userAgent,

        // Set the Argon2 WASM file location if it has not already been set.
        // If you have put the argon2.wasm file in a custom location, you can
        // override this option in the imported SDK_CONFIG.
        kmsArgonWasmUrl: SDK_CONFIG.kmsArgonWasmUrl || '../../sdk/argon2.wasm',

        // This example uses the bbm-chat-message-list web component to manage
        // the message list.  It is a Polymer component that directly watches
        // for changes to the message storage array in order to efficiently
        // update the display.  To allow the bbm-chat-message-list to monitor
        // changes in the SDK's stored messages, we configure the SDK to build
        // its message storage array using the SpliceWatcher message storage
        // factory.
        messageStorageFactory: SparkCommunications.StorageFactory.SpliceWatcher
      },
      SDK_CONFIG
    ));

    // Setup is asynchronous.  Create a promise we can use to wait on
    // until the SDK setup has completed.
    const sdkSetup = new Promise((resolve, reject) => {
      // Handle changes to the SDK's setup state.
      let isSyncStarted = false;
      sdk.on('setupState', (state) => {
        console.log(
          `ThreadedChat: Endpoint setup state: ${state.value}`);
        switch (state.value) {
          case SparkCommunications.SetupState.Success: {
            // Setup was successful.
            resolve();
            break;
          }
          case SparkCommunications.SetupState.SyncRequired: {
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
              sdk.syncPasscodeState === SparkCommunications.SyncPasscodeState.New
              // No, we must create new keys.  The key passcode will be
              // used to protect the new keys.
              ? SparkCommunications.SyncStartAction.New
              // Yes, we have existing keys.  The key passcode will be
              // used to unprotect the keys.
              : SparkCommunications.SyncStartAction.Existing
            );
            break;
          }
          case SparkCommunications.SetupState.SyncStarted: {
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

    // The SDK is now setup.  Remember the local user's regId.
    const regId = sdk.getRegistrationInfo().regId;

    // Create and initialize the user manager.  This will be used by the
    // bbmChatMessageList component for displaying user information.
    const userManager = new MockUserManager(
      sdk.getRegistrationInfo().regId,
      authManager,
      (...args) => sdk.getIdentitiesFromAppUserIds(...args),
      SDK_CONFIG.domain,
      (...args) => sdk.getIdentitiesFromRegId(...args)
    );
    await userManager.initialize();

    // Wait for the custom components to be upgraded before we configure them.
    await Promise.all([
      window.customElements.whenDefined(chatList.localName),
      window.customElements.whenDefined(chatMessageList.localName),
      window.customElements.whenDefined(chatInput.localName)
    ]);

    // Configure the chatList component.  It needs a handle to the SDK's
    // messenger object.  We also setup a context for the element that defines
    // how it will behave.
    chatList.setBbmMessenger(sdk.messenger);
    chatList.setContext({
      /**
       * Get the name to use for the chat.
       *
       * @param {SparkCommunications.Messenger.Chat} chat
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

    // Configure the chatMessageList component.  It needs a handle to the
    // SDK's messenger object.  We also setup formatters for the Message and
    // its timestamp.   The user manager is used to get information about
    // the message sender.
    chatMessageList.setBbmMessenger(sdk.messenger);
    chatMessageList.setMessageFormatter(new MessageFormatter(userManager));
    chatMessageList.setTimeRangeFormatter(new TimeRangeFormatter());

    // When a message is referenced, we will show the referenced message
    // information in the bbmChatInput component.
    chatMessageList.addEventListener('messageReference', e => {
      chatInput.showRefField(e);
    });

    // Configure the chatInput component.  It needs a handle to the SDK's
    // messenger object.
    chatInput.setBbmMessenger(sdk.messenger);

    // Everything is setup.  Report our regId as the status.
    status.innerHTML = `regId: ${regId}`;
  }
  catch (error) {
    const message = `ThreadedChat encountered an error: ${error}`;
    console.log(message);
    document.getElementById('status').innerHTML = message;
  }
};

//============================================================================
// :: HTML functions
//
// The remaining functions are called from the HTML code

/**
 * Enter the message list for a chat.
 *
 * @param {HTMLElement} element
 *   The list element of the chat to enter.
 */
window.enterChat = element => {
  var chatId = element.id;

  // Initialize the component.
  chatMessageList.setChatId(chatId);
  chatInput.setChatId(chatId);
  chatInput.set('isPriorityEnabled', false);

  // Make the right things visible.
  chatListDiv.style.display = "none";
  chatMessageList.style.display = "block";
  chatInput.style.display = "block";
  leaveButton.style.display = "block";

  // Set the title
  title.innerHTML = 'Threaded Chat: ' + element.innerHTML;
};

/**
 * Leave the active chat. This takes us back to the chat list.
 */
window.leaveChat = () => {
  // Uninitialize the components.
  chatMessageList.setChatId(undefined);

  // Make the right things visible.
  chatListDiv.style.display = "block";
  chatMessageList.style.display = "none";
  chatInput.style.display = "none";
  leaveButton.style.display = "none";

  // Set the title
  title.innerHTML = 'Threaded Chat';
};
