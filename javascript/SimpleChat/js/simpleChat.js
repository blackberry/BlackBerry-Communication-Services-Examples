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

// Declare local variables used by the HTML functions below.
let title;
let chatInput;
let chatMessageList;
let chatListDiv;
let leaveButton;
let status;
let chatList;

/**
 * A simple chat program.
 *
 * @class SimpleChat
 * @memberof Examples
 */

window.onload = async () => {
  // Find the necessary HTMLElements and cache them
  title = document.getElementById('title');
  status = document.getElementById('status');
  chatInput = document.getElementById('chatInput');
  chatMessageList = document.getElementById('chatMessageList');
  chatList = document.getElementById('chatList');
  chatListDiv = document.getElementById('chatListDiv');
  leaveButton = document.getElementById('leaveButton');

  try {
    // Wait for the custom web components to load.
    await new Promise((resolve) => { HTMLImports.whenReady(resolve); });

    // Set the Argon2 WASM file location if it has not already been set.
    // If you have put the argon2.wasm file in a custom location, you can
    // override this option in the imported SDK_CONFIG.
    const kmsArgonWasmUrl =
      SDK_CONFIG.kmsArgonWasmUrl || '../../sdk/argon2.wasm';

    // Make sure that the browser supports all of the necessary functionality,
    // including support for interacting with the BlackBerry Key Management
    // Service (KMS).
    await BBMEnterprise.validateBrowser({
      kms: { argonWasmUrl: kmsArgonWasmUrl }
    });

    // Notify the user that we are authenticating.
    status.innerHTML = 'Authenticating';

    // Setup the authentication manager for the application.
    const authManager = new MockAuthManager();
    // We are using the MockAuthManager, so we need to override how it
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

    // Authenticate the user.  Configurations that use a real identity
    // provider (IDP) will redirect the browser to the IDP's authentication
    // page.
    const authUserInfo = await authManager.authenticate();
    if (!authUserInfo) {
      console.warn('Redirecting for authentication.');
      return;
    }

    // Notify the user that we are setting up the SDK.
    status.innerHTML = 'Setting up the SDK';

    // Instantiate the SDK.
    //
    // We use the SDK_CONFIG imported from the example's configuration file to
    // override some of the options used to configure the SDK.
    //
    // This example might not work if your SDK_CONFIG specifies any of the
    // parameters assigned below.
    const sdk = new BBMEnterprise(Object.assign(
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

        // Use the same kmsArgonWasmUrl that was used to to validate our
        // browser environment above.
        kmsArgonWasmUrl,

        // This example uses the bbm-chat-message-list web component to manage
        // the message list.  It is a Polymer component that directly watches
        // for changes to the message storage array in order to efficiently
        // update the display.  To allow the bbm-chat-message-list to monitor
        // changes in the SDK's stored messages, we configure the SDK to build
        // its message storage array using the SpliceWatcher message storage
        // factory.
        messageStorageFactory: BBMEnterprise.StorageFactory.SpliceWatcher
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
          `SimpleChat: BBMEnterprise setup state: ${state.value}`);
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

    // The SDK is now setup.  Remember the local user's regId.
    const regId = sdk.getRegistrationInfo().regId;

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
       * @param {BBMEnterprise.Messenger.Chat} chat
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
    // SDK's messenger object.  We also setup a context for the element that
    // defines how it will behave.
    chatMessageList.setBbmMessenger(sdk.messenger);
    chatMessageList.setContext({
      /**
       * A function to retrieve the status indicator to use for an outgoing
       * message.
       *
       * @param {BBMEnterprise.ChatMessage} message
       *   The message to retrieve status for.
       *
       * @returns {string}
       *   The empty string is used for all incoming messages.  Otherwise, the
       *   following status indicators are used:
       *   - (...) => Sending
       *   - (S)   => Sent
       *   - (D)   => Delivered
       *   - (R)   => Read
       *   - (F)   => Failed
       *   - (?)   => Any unknown status value.
       */
      getMessageStatus: (message) => {
        if (message.isIncoming) {
          return '';
        }
        switch (message.state.value) {
          case 'Sending': return '(...)';
          case 'Sent': return '(S)';
          case 'Delivered': return '(D)';
          case 'Read': return '(R)';
          case 'Failed': return '(F)';
          default: return '(?)';
        }
      },

      /**
       * A function to retrieve the content to use for a message.
       *
       * @param {BBMEnterprise.Messenger.ChatMessage} message
       *   The message to retrieve content for.
       *
       * @returns {string}
       *   The content for a Text message, and other appropriate
       *   values for other types of messages.
       */
      getMessageContent: (message) =>
        message.tag === 'Text' ? message.content : message.tag,

      /**
       * A function to retrieve the alignment to use for a message.
       *
       * @param {BBMEnterprise.ChatMessage} message
       *   The message to retrieve alignment for.
       *
       * @returns {string}
       *   The alignment for the message.
       */
      getMessageAlignment: (message) =>
        message.isIncoming ? 'right' : 'left'
    });

    // Configure the chatInput component.  It needs a handle to the SDK's
    // messenger object.
    chatInput.setBbmMessenger(sdk.messenger);

    // Everything is setup.  Report our regId as the status.
    status.innerHTML = `regId: ${regId}`;
  }
  catch(error) {
    showError(`SimpleChat encountered an error: ${error}`);
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
function enterChat(element) {
  var chatId = element.id;

  // Initialize the component.
  chatMessageList.chatId = chatId;
  chatInput.setChatId(chatId);
  chatInput.set('isPriorityEnabled', false);

  // Make the right things visible.
  chatListDiv.style.display = 'none';
  chatMessageList.style.display = 'flex';
  chatInput.style.display = 'block';
  leaveButton.style.display = 'flex';

  // Set the title
  title.innerHTML = element.innerHTML;
}

/**
 * Leave the active chat. This takes us back to the chat list.
 */
function leaveChat() {
  // Uninitialize the components.
  chatMessageList.chatId = undefined;

  // Make the right things visible.
  chatListDiv.style.display = 'block';
  chatMessageList.style.display = 'none';
  chatInput.style.display = 'none';
  leaveButton.style.display = 'none';

  // Set the title
  title.innerHTML = 'Simple Chat';
}

/**
 * Display an error message in the status area.
 *
 * @param {string} message
 *   The error message to display.
 */
function showError(message) {
  console.log(message);
  // GOTCHA: This renders unsanitized text as html. In a real application, use
  // your framework's method, or some other method, to sanitize the text prior
  // to displaying it.
  document.getElementById('status').innerHTML = message;
}
