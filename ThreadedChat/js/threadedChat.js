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
 * A threaded chat program.
 *
 * @class ThreadedChat
 * @memberof Examples
 */

(function (window, document) {
  //String that identifies what type of reference it is
  const REFERENCE_TAG_THREADED = "Threaded";

  //String is displayed on the field of message reference
  //when a message is being edited
  const REFERENCE_COMMENT_FIELD_TEXT = "Commenting on: ";
  const REFERENCE_MESSAGE_STRING_COMMENT = " commented on: ";

  const IMG_CHEVRON = "img/bubble_menu.png";

  var widgetURI = (document._currentScript || document.currentScript).src;
  var m_basePath = widgetURI.substring(0, widgetURI.lastIndexOf("/js") + 1);

  // The observable object used by Observer class from SDK to monitor a message
  var MessageObservable = function Observable(messenger, chatId, messageId) {
    return {
      then: (callback) => {
        this.callback = callback;
        messenger.chatMessageWatch(chatId, messageId, callback);
      },
      unwatch: () => {
        messenger.chatMessageUnwatch(chatId, messageId, this.callback);
      }
    };
  };

  const waitPageLoaded = () => new Promise(resolve => {
    window.onload = () => {
      resolve();
    };
  });

  HTMLImports.whenReady(async () => {
    await waitPageLoaded();
    // Find the necessary HTMLElements and cache them.
    title = document.getElementById('title');
    const status = document.getElementById('status');
    chatInput = document.getElementById('chatInput');
    chatMessageList = document.getElementById('chatMessageList');
    const chatList = document.getElementById('chatList');
    chatListDiv = document.getElementById('chatListDiv');
    leaveButton = document.getElementById('leaveButton');

    let bbmeSdk;

    // Show the information field for message reference.
    chatMessageList.addEventListener('messageReference', e => {
      chatInput.showRefField(e);
    });

    // Perform authentication.
    try {
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
          console.warn('Application will be redirected to the authentication page');
          return;
        }
        try {
          // Construct BBMEnterprise.Messenger which provides higher level
          // functionality used to manipulate and annotate chats.
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
              case BBMEnterprise.SetupState.Success:
              {
                // Setup was successful. Create user manager and initiate call.
                const userRegId = bbmeSdk.getRegistrationInfo().regId;
                const messenger = bbmeSdk.messenger;

                // Initialize the chat input.
                window.customElements.whenDefined(chatInput.localName)
                .then(() => {
                  chatInput.setBbmMessenger(messenger);
                });

                // Initialize the message list.
                window.customElements.whenDefined(chatMessageList.localName)
                .then(() => 
                  createUserManager(userRegId, authManager,
                    bbmeSdk.getIdentitiesFromAppUserIds)
                  .then(userManager =>
                    userManager.initialize()
                    .then(() => {
                      chatMessageList.setBbmMessenger(messenger);
                      const messageFormatter = new MessageFormatter(userManager);
                      chatMessageList.setMessageFormatter(messageFormatter);
                      chatMessageList.setTimeRangeFormatter(new TimeRangeFormatter());
                    })
                  )
                );

                // Initialize the chat list.
                window.customElements.whenDefined(chatList.localName)
                .then(() => {
                  chatList.setBbmMessenger(messenger);
                  chatList.setContext({
                    // Get the name to use for the chat. This is the other
                    // participant's registration ID for a 1:1 chat, otherwise
                    // it is the chat's subject.
                    getChatName: chat => {
                      if(chat.isOneToOne) {
                        return (chat.participants[0].regId === userRegId)
                          ? chat.participants[1].regId.toString()
                          : chat.participants[0].regId.toString();
                      } else {
                        return chat.subject;
                      }
                    }
                  });
                });

                // Report the status to the user.
                status.innerHTML = `Registration Id: ${userRegId}`;
              }
              break;

              case BBMEnterprise.SetupState.SyncRequired: {
                if (isSyncStarted) {
                  showError('Failed to get user keys using provided USER_SECRET');
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
            showError(`Failed to create BBMEnterprise: ${error.value}`);
          });

          // Start BBM Enterprise setup.
          bbmeSdk.setupStart();

          // Notify the user that we are working on signing in.
          status.innerHTML = 'Signing in';
        } catch (error) {
          showError(`Failed to create BBMEnterprise: ${error}`);
          return;
        }
      }).catch(error => {
        showError(`Failed to complete setup. Error: ${error}`);
      });
    } catch(error) {
      showError(`Failed to authenticate and start BBM SDK. Error: ${error}`);
    }

    /**
     * The implementation for the message list component. Note: this function
     * takes an html template as a child, which will be used to display each
     * message. The template has some important properties:
     * - A string enclosed in [[]] may be used to insert javascript code into
     *   the block, either as text or attributes.
     * - The types of javascript allowed include:
     *   - message.XXX where XXX is a property of a ChatMessage (and will be
     *   resolved to the value of that property for the ChatMessage being
     *   displayed).
     *   - The name of a function where the function is in the object passed to
     *     `context'.
     *   - ! to negate a value.
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
        return m_basePath + IMG_CHEVRON;
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
       * Dispatch an event of quoting the selected message.
       */
      commentMessage() {
        this.dispatchEvent(new CustomEvent('messageReference', 
          {'detail': { targetMessageId:  this.selectedMessage.message.messageId, 
            refTag: REFERENCE_TAG_THREADED,
        content: REFERENCE_COMMENT_FIELD_TEXT + '"' + this.selectedMessage.content + '"',
        textMessage: ""}}));
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

        //For message references
        if(message.ref) {
          //For quoted message
          if(message.ref && message.ref[0] 
              && message.ref[0].tag === REFERENCE_TAG_THREADED) {
           this.formatCommentSystemMessage(message.ref[0].messageId, ret);
          }
        }

        if(message.refBy) {
          ret.refBys = [];
          for(var i=0; i < message.refBy.length; i++) {
            if(message.refBy[i].tag === REFERENCE_TAG_THREADED
              && message.refBy[i].messageIds.length > 0) {
              for(var j=0; j < message.refBy[i].messageIds.length; j++) {
                this.formatCommentMessage(message.refBy[i].messageIds[j], ret);
              }
              break;
            }
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
})(window, document);

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
