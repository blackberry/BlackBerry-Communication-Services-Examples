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

// Declare local variables used by the HTML functions below.
let title;
let chatInput;
let chatMessageList;
let chatListDiv;
let leaveButton;

/**
 * A simple chat program.
 *
 * @class SimpleChat
 * @memberof Examples
 */

HTMLImports.whenReady(function() {
  // Find the necessary HTMLElements and cache them.
  title = document.getElementById('title');
  const status = document.getElementById('status');
  chatInput = document.getElementById('chatInput');
  chatMessageList = document.getElementById('chatMessageList');
  const chatList = document.getElementById('chatList');
  chatListDiv = document.getElementById('chatListDiv');
  leaveButton = document.getElementById('leaveButton');

  window.onload = () => {
    bbmeInit();
  };

  const bbmeInit = () => {
    // Perform authentication.
    try {
      let bbmeSdk;
      let isSyncStarted = false;
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

      authManager.authenticate()
      .then(authUserInfo => {
        if (!authUserInfo) {
          console.warn('Application will be redirected the the authentication' +
            ' page');
          return;
        }
  
        try {
          // Create a BBMEnterprise instance.
          bbmeSdk = new BBMEnterprise({
            domain: ID_PROVIDER_DOMAIN,
            environment: ID_PROVIDER_ENVIRONMENT,
            userId: authUserInfo.userId,
            getToken: authManager.getBbmSdkToken,
            description: navigator.userAgent,
            messageStorageFactory: BBMEnterprise.StorageFactory.SpliceWatcher,
            kmsArgonWasmUrl: KMS_ARGON_WASM_URL
          });
  
          // Notify the user that we are working on signing in.
          status.innerHTML = 'Signing in';
        } catch (error) {
          showError(`Failed to create BBMEnterprise: ${error}`);
          return;
        }
  
        // Handle changes of BBMEnterprise setup state.
        bbmeSdk.on('setupState', state => {
          console.log(`SimpleChat: BBMEnterprise setup state: ${state.value}`);
          switch (state.value) {
            case BBMEnterprise.SetupState.Success:
            {
              // Setup was successful. Create user manager and initiate call.
              const registrationId = bbmeSdk.getRegistrationInfo().regId;
              const messenger = bbmeSdk.messenger;
  
              // Initialize the chat input.
              window.customElements.whenDefined(chatInput.localName)
              .then(() => {
                chatInput.setBbmMessenger(messenger);
              });
      
              // Initialize the message list.
              window.customElements.whenDefined(chatMessageList.localName)
              .then(() => {
                chatMessageList.setBbmMessenger(messenger);
                chatMessageList.setContext({
                  /**
                   * A function to retrieve the status indicator to use for a
                   * message.
                   * @param {BBMEnterprise.ChatMessage} message
                   * The message to retrieve status for.
                   * @returns {string} 
                   * (R) for read messages, (D) for delivered messages,
                   * nothing otherwise.
                   */
  
                  getMessageStatus: message => {
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
                   * @returns {string}
                   *   The content for a Text message, and other appropriate
                   *   values for other types of messages.
                   */
                  getMessageContent: message => message.tag === 'Text'
                    ? message.content : message.tag,
  
                  /**
                   * A function to retrieve the alignment to use for a message.
                   *
                   * @param {BBMEnterprise.ChatMessage} message
                   *   The message to retrieve alignment for.
                   * @returns {string}
                   *   The alignment for the message.
                   */
                  getMessageAlignment: message => message.isIncoming
                    ? 'right' : 'left'
                });
              });
  
              // Initialize the chat list.
              window.customElements.whenDefined(chatList.localName)
              .then(() => {
                chatList.setBbmMessenger(messenger);
                chatList.setContext({
                  // Get the name to use for the chat. This is the other
                  // participant's registration ID for a 1:1 chat, otherwise it
                  // is the chat's subject.
                  getChatName: chat => {
                    if(chat.isOneToOne) {
                      return (chat.participants[0].regId === registrationId)
                        ? chat.participants[1].regId.toString()
                        : chat.participants[0].regId.toString();
                    } else {
                      return chat.subject;
                    }
                  }
                });
              });
  
              // The message list needs to know about changes to the message
              // store.
  
              // Report the status to the user.
              status.innerHTML = `Registration Id: ${registrationId}`;
            }
            break;
            case BBMEnterprise.SetupState.SyncRequired: {
              if (isSyncStarted) {
                showError('Failed to get user keys using provided USER_SECRET');
                return;
              }
              const isNew =
                bbmeSdk.syncPasscodeState ===
                  BBMEnterprise.SyncPasscodeState.New;
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
        });
  
        // Start BBM Enterprise setup.
        bbmeSdk.setupStart();
      }).catch(error => {
        showError(`Failed to complete setup. Error: ${error}`);
      });
    } catch(error) {
      showError(`Failed to authenticate and start BBM SDK. Error: ${error}`);
    }
  };
});

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
  chatMessageList.style.display = 'block';
  chatInput.style.display = 'block';
  leaveButton.style.display = 'block';

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
