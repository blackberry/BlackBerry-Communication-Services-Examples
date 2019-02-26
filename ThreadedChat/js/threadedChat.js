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
    // Wait for the custom web components to load.
    await new Promise((resolve) => { HTMLImports.whenReady(resolve); });

    // Notify the user that we are authenticating.
    status.innerHTML = 'Authenticating';

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

    // Notify the user that we are working the SDK setup.
    status.innerHTML = 'Setting up the SDK';

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
          `ThreadedChat: BBMEnterprise setup state: ${state.value}`);
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

    // Create and initialize the user manager.  This will be used by the
    // bbmChatMessageList component for displaying user information.
    const userManager = await createUserManager(
      sdk.getRegistrationInfo().regId,
      authManager,
      (...args) => sdk.getIdentitiesFromAppUserIds(...args)
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
}

/**
 * Leave the active chat. This takes us back to the chat list.
 */
function leaveChat() {
  // Uninitialize the components.
  chatMessageList.setChatId(undefined);

  // Make the right things visible.
  chatListDiv.style.display = "block";
  chatMessageList.style.display = "none";
  chatInput.style.display = "none";
  leaveButton.style.display = "none";

  // Set the title
  title.innerHTML = 'Threaded Chat';
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

//============================================================================
// :: Web Components

HTMLImports.whenReady(() => {
  // The tag that will be used when sending messages that reference a message
  // so that they are identified as threaded messages.
  const REFERENCE_TAG_THREADED = "Threaded";

  // The string used to annotate a message that is being referenced while
  // editing the message to be sent.
  const REFERENCE_COMMENT_FIELD_TEXT = "Commenting on:";

  // The string used to annotate the chat to indicate which user commented on
  // a message.
  const REFERENCE_MESSAGE_STRING_COMMENT = " commented on: ";

  // The image that will appear next to a message bubble to indicate that a
  // menu is available for the message.  The image path is given relative to
  // the example application.
  const IMG_CHEVRON = "./img/bubble_menu.png";

  // A helper object for observing changes to a chat message.
  const MessageObservable = function(messenger, chatId, messageId) {
    return {
      // Set the callback to be called when the message changes.
      then: (callback) => {
        this._callback = callback;
        messenger.chatMessageWatch(chatId, messageId, this._callback);
      },
      // Stop watching the message and remove the callback.
      unwatch: () => {
        messenger.chatMessageUnwatch(chatId, messageId, this._callback);
      }
    };
  };

  /**
   * The implementation for the threaded message list component.
   *
   * Note: this component takes an HTML template as a child, which will be
   * used to display each message.  The template has some important
   * properties:
   *
   * A string enclosed in [[]] may be used to insert JavaScript code into the
   * block, either as text or attributes.  Some JavaScript values that may be
   * included are:
   *
   * - Any property of a ChatMessage may be referenced for display of the
   *   property value.  For example: '[[message.messageId]]'
   *
   * - You may call any function that is defined in the configured context of
   *   the component.  For example: '[[getMessageText()]]'
   *
   * - You can use '!' to negate a value.  For example:
   *   '[[! message.isIncoming]]'
   *
   * @memberof Support.Widgets
   */
  class ThreadedChatMessageList extends Polymer.Element {
    static get is() {
      return 'bbm-threaded-message-list';
    }

    ready() {
      super.ready();

      window.customElements.whenDefined('bbm-chat-message-list').then(() => {
        this.$.list.setMappingFunction(this.render.bind(this),
          this.clear.bind(this));
      });
    }

    // Defined list of properties of custom control
    static get properties() {
      return {
        // Holds the currently selected message
        selectedMessage: {
          type: Object,
          readOnly: false,
          notify: true
        }
      };
    }

    /**
     * Sets instance of bbmMessenger
     * @param {BBMEnterprise.Messenger} messenger
     *   The messenger to use to retrieve a chat's message list.
     */
    setBbmMessenger(messenger) {
      this.bbmMessenger = messenger;
      this.$.list.setBbmMessenger(messenger);
    }

    /**
     * Sets current chat id
     * @param {String} chatId - Current chat id
     */
    setChatId(chatId) {
      this.chatId = chatId;
      this.$.list.setChatId(chatId);
    }

    /**
     * Sets instance of contactManager. The contactManager is used for
     * rendering contact information in bubbles.
     *
     * @param {object} value
     *   The contact manager used to display information about contacts in
     *   bubbles.
     */
    setContactManager(value) {
      if(this.contactManager) {
        this.contactManager.removeEventListener("user_changed", onUserChanged);
      }

      this.contactManager = value;

      if (value) {
        this.contactManager.addEventListener("user_changed", onUserChanged);
      }
    }

    /**
     * Sets the time range formatter. The time range formatter is used for
     * displaying time stamps relative to the current time.
     *
     * @param {object} value
     *   The time range formatter used to display relative timestamps in
     *   bubbles.
     */
    setTimeRangeFormatter(value) {
      this.timeRangeFormatter = value;
    }

    /**
     * Sets the message formatter. The message formatter is used to format
     * the content of messages for display.
     *
     * @param {object} value
     *   The message formatter used to display information about the message.
     */
    setMessageFormatter(value) {
      this.messageFormatter = value;
    }

    /**
     * Get the image to display for a message chevron
     *
     * @returns {string} Path to the image.
     */
    getChevronImage() {
      return IMG_CHEVRON;
    }

    /**
     * Trigger a chevron's dropdown menu to be displayed.
     *
     * @param {Event} event
     *   The mouse press event which triggers the dropdown to be displayed.
     */
    showChevronDropdown(event) {
      var element = this.shadowRoot.querySelector('.chevronDropdown');

      this.selectedMessage =  event.model.message;

      // Sets the position to display the menu button dropdown list
      element.setAttribute(
        "style","display: flex; position: fixed; z-index: 1; top: " 
        + event.y + "px; left: " + event.x + "px;");

      event.stopPropagation();

      var windowClickHandler = () => {
        this.closeDropdowns();
        window.removeEventListener('click', windowClickHandler);
      };

      window.addEventListener('click', windowClickHandler);
    }

    /**
     * Close all dropdown menus associated with the message list.
     */
    closeDropdowns() {
      var dropdowns =
        this.shadowRoot.querySelectorAll('.chevronDropdown');
      dropdowns.forEach(item => {
        if (item.style.display !== 'none') {
          item.style.display = 'none';
        }
      });

      // Now that the dropdown is closed, hide chevrons too, if required.
      var chevrons =
        this.shadowRoot.querySelectorAll('.chevron');
      chevrons.forEach(item => {
        if (item.needsToHide) {
          item.style.display = 'none';
          item.needsToHide = false;
        }
      });
    }

    /**
     * check if a menu button needs to be displayed for a message
     *
     * @param {BBMEnterprise.Messenger.ChatMessage} selectedMessage 
     *  The selected ChatMessage that the menu button belongs to
     *
     * @returns {string} 'none' to display the button, or 'block' to
     *  hide the button
     */
    isMenuButtonDisplayed(selectedMessage) {
      return selectedMessage && selectedMessage.message.isIncoming
        ? 'none' : 'block';
    }

    /**
     * Dispatch an event to notify listeners that a message is being commented
     * on.
     */
    commentMessage() {
      // Dispatch the 'messageReference' event.
      this.dispatchEvent(new CustomEvent('messageReference', {
        'detail': {
          // The messageId of the message being referenced.
          targetMessageId: this.selectedMessage.message.messageId,
          // The reference tag to use when referencing the message.
          refTag: REFERENCE_TAG_THREADED,
          // What text to show when we are composing a comment on the
          // referenced message.
          content:
            `${REFERENCE_COMMENT_FIELD_TEXT} "${this.selectedMessage.content}"`,
          // What text to show as the comment.
          textMessage: ""
        }
      }));
      chatInput.focus();
    }

    // Get the username for a registration ID. If there is a name registered
    // for the user, it will be used. Otherwise, the registration ID in string
    // form will be used.
    getUserName(regId) {
      var contactName = this.contactManager.getDisplayName(regId);
      return contactName ? contactName : regId.toString();
    }

    // Get the timestamp for a message in string form, using the time range
    // formatter registered with this widget.
    getTimestamp(message) {
      let ret =
        {
          formattedTime: ''
        };

      if (this.timeRangeFormatter) {
        var now = new Date();
        var formattedMessage = this.timeRangeFormatter.format(
          this.timeRangeFormatter.chatBubbleFormatters,
          message.timestamp,
          now);

        // Construct a return value. It definitely has a time.
        ret.formattedTime = formattedMessage.formattedTime;

        // It may also have a timeout, after which the time should be updated.
        if (formattedMessage.expiresIn) {
          ret.expiryTimer = setTimeout(
            () => this.refresh(message),
            formattedMessage.expiresIn.getTime() - now.getTime()
          );
        }
      }

      return ret;
    }

    /**
     * Clear the data associated with a message. This should be called when
     * a message is about to be deleted, and cleans up temporary data created
     * by formatting the message.
     */
    clear(message) {
      if(message.timestamp.expiryTimer) {
        clearTimeout(message.timestamp.expiryTimer);
      }
    }

    // Cause a message to refresh. This should be invoked when something
    // outside of message data causes a bubble to need to be refreshed.
    refresh(message) {
      var formattedMessage;
      // Upon timeout, find the message, re-render, and splice it back
      // in. First find the message and make sure it's still in the
      // list.
      var index = this.$.list.$.list.items.findIndex((element) =>
        element.message === message
      );

      if (index >= 0) {
        formattedMessage = this.$.list.$.list.items[index];

        // Rerender based on the appData, which is assumed to have been
        // updated already by whatever triggered the refresh.
        var reRendered = this.format(message, formattedMessage.appData);

        // And splice it in.
        this.$.list.$.list.splice('items', index, 1, reRendered);
      }
    }

    render(message) {
      // Make a spot for data that belongs to the app, rather than the sdk.
      var appData = {
        commentSystemMessageTracker: undefined,
        commentMessageTracker: undefined
      };

      // Format the original message data for display.
      return this.format(message, appData);
    }

    // Construct the data needed to display a message.
    // @param {ChatMessage} message
    // The message from the BBM Enterprise for Javascript SDK.
    // @returns {object} The data needed by the template used for this message
    // list, defined in the html file.
    format(message, appData) {
      var isStatus = this.messageFormatter.getIsStatusMessage(message) 
      || (message.ref && message.ref[0] 
        && message.ref[0].tag === REFERENCE_TAG_THREADED);

      var ret = {
        // This is the original SDK data.
        message: message,
        // This is the original app data.
        appData: appData,
        // The rest is the formatted data. This is all build out of the first
        // two.
        isStatus: isStatus ? 'block' : 'none',
        isBubble: isStatus ? 'none' : 'flex',
        isText: !isStatus? 'flex' : 'none',
        avatar: this.messageFormatter.getMessageAvatar(message),
        isIncoming: message.isIncoming ? 'block' : 'none',
        indent: '10px',
        childIndent: '85px',
        username: message.isIncoming 
          ? this.messageFormatter.getUserName(message)
          : 'You',
        timestamp: this.getTimestamp(message),
        content: this.messageFormatter.getMessageText(message),
        backgroundColor: message.isIncoming ? '#efefef' : '#cee2eb',
        childBackgroundColor: message.isIncoming ? '#fafafa' : '#eff8fb',
        isHidden: message.isDeleted 
          || (!message.isIncoming && message.isRecalled)
          ? 'none': 'block',
        alignment: message.isIncoming ? 'left' : 'right'
      };

      if(message.ref) {
        // The message being formatted references a top-level message.  This
        // example only references a single message at a time.
        //
        // We format this message as a 'system' message to be injected into
        // the chat message list.
        if(message.ref && message.ref[0]
            && message.ref[0].tag === REFERENCE_TAG_THREADED) {
         this.formatCommentSystemMessage(message.ref[0].messageId, ret);
        }
      }

      if(message.refBy) {
        // The message being formatted is referenced by at least one other
        // message.  Format each of the messages that reference this messages
        // as 'comment' messages.
        ret.refBys = [];
        for (const ref of message.refBy) {
          if (ref.tag === REFERENCE_TAG_THREADED) {
            for (const messageId of ref.messageIds) {
              this.formatCommentMessage(messageId, ret);
            }
            // We've handled the only reference tag type we care about, so we
            // can exit the loop early.
            break;
          }
          // Ignore all other reference tag types.
        }
      }
      return ret;
    }

    // Construct the data needed to display a comment system message.
    // For example, 'XXX commented on "aaaaaaaaa"'
    // @param {Object} messageId 
    //  the MessageId object of the comment system message
    // @param {Object} ret
    //  the wrapper of the data to display the comment system message
    formatCommentSystemMessage(messageId, ret) {
      var targetMessage;

      if(ret.appData.commentSystemMessageTracker) {
        ret.appData.commentSystemMessageTracker.clear();
      }

      ret.appData.commentSystemMessageTracker 
        = Observer.getObserverContext((getter) => {
        var observable = new MessageObservable(this.$.list.messenger,
            this.chatId, messageId);
        //observe the message.
        getter.observe(observable, (getter, message) => {
            ret.content = ret.username + REFERENCE_MESSAGE_STRING_COMMENT
            + '"' + message.content + '"';
        });
      }, () => {
        //Callback will be called after getting the message 
        //asynchronously. Refresh the UI to render the data
        this.refresh(targetMessage);
      });
    }

    // Construct the data needed to display a comment message.
    // @param {Object} messageId 
    //  The MessageId object of the comment message
    // @param {Object} ret
    //  The wrapper of the data to display the comment message
    formatCommentMessage(messageId, ret) {

      if(ret.appData.commentMessageTracker) {
        ret.appData.commentMessageTracker.clear();
      }

      var refMessage;
      ret.appData.commentMessageTracker
        = Observer.getObserverContext((getter) => {
        var messageObservable
          = new MessageObservable(this.$.list.messenger,
            this.chatId, messageId);

        //observe the message.
        getter.observe(messageObservable, (getter, message) => {
          refMessage = message;
          var refMessageId = refMessage.messageId.toString();
          var isMessageFound = false;
          for(var i = 0; i < ret.refBys.length; i++) {
            if(ret.refBys[i].childMessageId === refMessageId) {
              //Update the existing child message
              ret.refBys[i].content = refMessage.content;
              isMessageFound = true;
              break;
            }
          }

          if(!isMessageFound) {
            //add the child message
            ret.refBys.push({
              childMessageId: refMessageId,
              childMessageUsername: refMessage.isIncoming 
                ? this.messageFormatter.getUserName(message.sender)
                : 'You',
              childMessageContent: refMessage.content,
              childMessageAvatar: 
                this.messageFormatter.getMessageAvatar(refMessage),
              bubbleBackgroundColor: refMessage.isIncoming 
                ? '#efefef' : '#cee2eb',
              timestamp: this.getTimestamp(refMessage)
            });
          }
        });
      }, () => {
        //Callback will be called after getting the message 
        //asynchronously. Refresh the UI to render the data
        this.refresh(refMessage);
      }); 
    }

    /**
     * Show the chevron when hovering over a message bubble.
     *
     * @param {Event} event
     *   The mouse enter event which triggers the show
     */
    showChevron(event) {
      var element = event.target.querySelector('.chevron');
      element.style.display='block';
    }

    /**
     * Hide the chevron when ending a hover over a message bubble. Don't hide
     * it if the menu is open.
     *
     * @param {Event} event
     *   The mouse leave event which triggers the hide
     */
    hideChevron(event) {
      var chevron = event.target.querySelector('.chevron');
      var dropdown = this.shadowRoot.querySelector('.chevronDropdown');
      if(dropdown.style.display !== 'flex') {
        // Hide the chevron.
        chevron.style.display='none';
        chevron.needsToHide = false;
      } else {
        // We have to mark that the chevron should be hidden, but not actually
        // hide it.
        chevron.needsToHide = true;
      }
    }
  }

  window.customElements.define(ThreadedChatMessageList.is,
                               ThreadedChatMessageList);
});

