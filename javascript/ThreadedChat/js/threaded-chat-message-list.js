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

'use strict';

import { PolymerElement, html } from '../node_modules/@polymer/polymer/polymer-element.js';
import { } from '../node_modules/@polymer/polymer/lib/elements/dom-repeat.js';
import { } from '../node_modules/@polymer/polymer/lib/elements/dom-if.js';

// The tag that will be used when sending messages that reference a message
// so that they are identified as threaded messages.
const REFERENCE_TAG_THREADED = 'Threaded';

// The string used to annotate a message that is being referenced while
// editing the message to be sent.
const REFERENCE_COMMENT_FIELD_TEXT = 'Commenting on:';

// The string used to annotate the chat to indicate which user commented on
// a message.
const REFERENCE_MESSAGE_STRING_COMMENT = ' commented on: ';

// The image that will appear next to a message bubble to indicate that a
// menu is available for the message.  The image path is given relative to
// the example application.
import IMG_CHEVRON from '../img/bubble_menu.png';

// A helper object for observing changes to a chat message.
class MessageObservable {
  constructor(messenger, chatId, messageId) {
    this.then = callback => {
      this._callback = callback;
      messenger.chatMessageWatch(chatId, messageId, this._callback);
    };

    this.unwatch = () => {
      messenger.chatMessageUnwatch(chatId, messageId, this._callback);
    };
  }
}

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
 */
class ThreadedChatMessageList extends PolymerElement {

  static get template() {
    return html`
      <style>
        .chevronPlaceholder {
          min-height: 18px;
          min-width: 18px;
          max-height: 18px;
          max-width: 18px;
          width: 18px;
          height: 18px;
        }

        .chevron {
          display: none;
          width: 18px;
          height: 18px;
        }

        .chevronDropdown {
          display: none;
          overflow: hidden;
          background: white;
          position: absolute;
          border-radius: 5px;
          border: 1px solid #96c5d8;
          box-shadow: 0px 8px 24px 0px black;
          flex-direction: column;
          z-index: 1;
        }

        .bbmChatMenuButton {
          color: black;
          padding: 12px 16px;
          text-decoration: none;
          display: block;
          cursor: pointer;
        }

        .messageRow {
          display: flex;
          flex-direction: column;
          margin-right: 100px;
        }

        .rowWithChevron {
          display: flex;
          flex-direction: row;
          margin-right: 100px;
          height: 30px;
        }

        .refMessageRow {
          border-left: 1px solid #dddddd;
          font-size: 12px;
        }

        .status {
          padding: 10px 10px 10px 10px;
          height: 20px;
        }

        .bubble-wrapper {
          flex-grow: 1;
          margin-top: 15px;
          margin-left: 5px;
          margin-right: 5px;

          display: flex;
          flex-direction: row;
        }

        .bubble-wrapper .bubble-avatar {
          min-width: 40px;
          min-height: 40px;
          max-width: 40px;
          max-height: 40px;
          margin: 5px;
          text-align: center;
          display: flex;
          border-radius: 50%;
          flex-direction: column;
          justify-content: center;
          font-size: large;
          color: white;
          background-size: contain;
        }

        .bubble-avatar {
          margin: 5px 5px 5px 5px;
        }

        .bubble-wrapper .bubble {
          flex-grow: 1;
        }

        .bubble-wrapper .bubble .firstRow {
          display: flex;
          margin-left: 10px;
          justify-content: space-between;
        }

        .bubble-wrapper .bubble .secondRow {
          position: relative;
          border-radius: 10px;
          display: inline-flex;
          margin-left: 10px;
          margin-right: 10px;
        }

        .secondRowWithChevron {
          display: flex;
          flex-direction: row;
          margin-right: 100px;
        }

        .bubble-content {
          margin: 10px 5px 10px 5px;
        }

        .child-avatar {
          width: 28px;
          height: 28px;
          vertical-align: top;
          border-radius: 50%;
          display: flex;
          flex-direction: column;
          justify-content: center;
          font-size: large;
          color: white;
          background-size: contain;
          text-align: center;
          font-size: small;
        }

        .child-bubble-wrapper {
          flex-grow: 1;
          margin-top: 10px;
          margin-left: 5px;
          margin-right: 5px;
          margin-bottom: 5px;

          display: flex;
          flex-direction: row;
        }

        .child-bubble {
          flex-grow: 1;
        }

        .child-bubble-content {
          margin: 5px 5px 5px 5px;
        }

        .child-firstRow {
          display: flex;
          margin-left: 10px;
          justify-content: space-between;
        }

        .child-secondRow {
          position: relative;
          border-radius: 10px;
          display: inline-flex;
          margin-left: 5px;
          margin-right: 10px;
        }
      </style>

      <!--
        This defines the contents of the menu that appears when clicking on the
        chevron associated with any given top-level chat message.
      -->
      <div class="chevronDropdown" id="chevronDropdown"
          style="margin-left:9px;margin-top:9px;">
        <div class="bbmChatMenuButton" on-click="commentMessage">Comment</div>
      </div>

      <!--
        Extend the bbmChatMessageList component so that we can display a
        top-level message in a chat with any messages that reference it nested
        beneath it.
      -->
      <bbm-chat-message-list id="list" items="[]" as="message">
        <template id="bubbleTemplate">
          <!-- Visible if this is a status message. -->
          <div class="messageRow">
            <div class="status" style$="display:[[message.isStatus]]">[[message.content]]</div>
            <div class="messageRow" style$="display:[[message.isText]]">
              <!--
                The main bubble wrapper for a top-level message that does not
                reference another message.
              -->
              <div class="bubble-wrapper" style$="display:[[message.isBubble]]">
                <!-- Avatar -->
                <template is="dom-if" if="[[message.avatar]]">
                  <div class="bubble-avatar" style$="display: [[message.isIncoming]]; background-image: url('[[message.avatar]]');"></div>
                </template>
                <template is="dom-if" if="[[!message.avatar]]">
                  <div class="bubble-avatar" style$="display: [[message.isIncoming]]; background-color: [[message.avatarColor]];">
                    [[getUserInitials(message.username)]]
                  </div>
                </template>
                <!-- Bubble content with header -->
                <div class="bubble">
                  <!-- First row -->
                  <div class="firstRow">
                    <div>[[message.username]]</div>
                    <div>[[message.timestamp.formattedTime]]</div>
                  </div>
                  <!-- Second row -->
                  <div class="secondRowWithChevron" on-mouseenter="showChevron"
                      on-mouseleave="hideChevron">
                    <div class="secondRow"
                        style$="margin-left: [[message.indent]]; background-color: [[message.backgroundColor]]">
                      <!-- Text content -->
                      <div style="flex-direction: column; margin: 0px 5px 0px 5px;">
                        <div style$="display:[[message.isText]];">
                          <div class="bubble-content">
                            [[message.content]]
                          </div>
                        </div>
                      </div>
                    </div>
                    <!--
                      The chevron menu icon that will appear when the user's
                      mouse hovers over the message.  When clicked, the
                      chevronDropDown menu will be shown.
                    -->
                    <div class="chevronPlaceholder">
                      <div class="chevron"
                        style$="background-image: url('[[getChevronImage()]]')"
                        on-click="showChevronDropdown">
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <!--
                The container for the messages that reference the above
                top-level message.
              -->
              <template is="dom-repeat" items="{{message.refBys}}">
                <div class="refMessageRow"
                    style$="margin-left: [[message.childIndent]]; background-color: [[message.childBackgroundColor]]">
                  <div class="child-bubble-wrapper">
                    <!-- Avatar -->
                    <template is="dom-if" if="[[item.childMessageAvatar]]">
                      <div class="child-avatar" style$="background-image: url('[[item.childMessageAvatar]]');"/>
                    </template>
                    <!-- Bubble content with header -->
                    <div class="child-bubble">
                      <!-- First row -->
                      <div class="child-firstRow">
                        <div>[[item.childMessageUsername]]</div>
                        <div>[[item.timestamp.formattedTime]]</div>
                      </div>
                      <!-- Second row -->
                      <div class="child-secondRow"
                          style$="background-color: [[item.bubbleBackgroundColor]]">
                        <!-- Text content -->
                        <div style="flex-direction: column; margin: 0px 5px 0px 5px;">
                          <div class="child-bubble-content">
                            [[item.childMessageContent]]
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </template>
      </bbm-chat-message-list>
    `;
  }

  static get is() {
    return 'bbm-threaded-message-list';
  }

  async ready() {
    super.ready();

    await window.customElements.whenDefined('bbm-chat-message-list');
    this.$.list.setMappingFunction(this.render.bind(this),
      this.clear.bind(this));
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
   * Sets an instance of the SparkCommunications.Messenger.
   * @param {SparkCommunications.Messenger} messenger
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
      this.contactManager.removeEventListener('user_changed', onUserChanged);
    }

    this.contactManager = value;

    if (value) {
      this.contactManager.addEventListener('user_changed', onUserChanged);
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
    const element = this.shadowRoot.querySelector('.chevronDropdown');

    this.selectedMessage = event.model.message;

    // Sets the position to display the menu button dropdown list
    element.setAttribute(
      "style","display: flex; position: fixed; z-index: 1; top: " 
      + event.y + "px; left: " + event.x + "px;");

    event.stopPropagation();

    const windowClickHandler = () => {
      this.closeDropdowns();
      window.removeEventListener('click', windowClickHandler);
    };

    window.addEventListener('click', windowClickHandler);
  }

  /**
   * Close all dropdown menus associated with the message list.
   */
  closeDropdowns() {
    const dropdowns = this.shadowRoot.querySelectorAll('.chevronDropdown');
    dropdowns.forEach(item => {
      if (item.style.display !== 'none') {
        item.style.display = 'none';
      }
    });

    // Now that the dropdown is closed, hide chevrons too, if required.
    const chevrons = this.shadowRoot.querySelectorAll('.chevron');
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
   * @param {SparkCommunications.Messenger.ChatMessage} selectedMessage 
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
    const chatInput = document.querySelector('#chatInput');
    if (chatInput) {
      chatInput.focus();
    }
  }

  // Get the username for a registration ID. If there is a name registered
  // for the user, it will be used. Otherwise, the registration ID in string
  // form will be used.
  getUserName(regId) {
    const contactName = this.contactManager.getDisplayName(regId);
    return contactName ? contactName : regId.toString();
  }

  /**
   * Returns the first character of the provided string
   */
  getUserInitials(username) {
    if (username) {
      return username.charAt(0).toUpperCase();
    }
    return '*';
  }

  // Get the timestamp for a message in string form, using the time range
  // formatter registered with this widget.
  getTimestamp(message) {
    let ret = { formattedTime: '' };

    if (this.timeRangeFormatter) {
      const now = new Date();
      const formattedMessage = this.timeRangeFormatter.format(
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
    if (message.timestamp.expiryTimer) {
      clearTimeout(message.timestamp.expiryTimer);
    }
  }

  // Cause a message to refresh. This should be invoked when something
  // outside of message data causes a bubble to need to be refreshed.
  refresh(message) {
    // Upon timeout, find the message, re-render, and splice it back
    // in. First find the message and make sure it's still in the
    // list.
    const index = this.$.list.$.list.items.findIndex(element =>
      element.message === message
    );

    if (index >= 0) {
      const formattedMessage = this.$.list.$.list.items[index];

      // Rerender based on the appData, which is assumed to have been
      // updated already by whatever triggered the refresh.
      const reRendered = this.format(message, formattedMessage.appData);

      // And splice it in.
      this.$.list.$.list.splice('items', index, 1, reRendered);
    }
  }

  render(message) {
    // Make a spot for data that belongs to the app, rather than the sdk.
    const appData = {
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
    const isStatus = this.messageFormatter.getIsStatusMessage(message) 
    || (message.ref && message.ref[0] 
      && message.ref[0].tag === REFERENCE_TAG_THREADED);

    const ret = {
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
      avatarColor: this.messageFormatter.getUserAvatarColor(message),
      isIncoming: message.isIncoming ? 'flex' : 'none',
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

    if (message.ref) {
      // The message being formatted references a top-level message.  This
      // example only references a single message at a time.
      //
      // We format this message as a 'system' message to be injected into
      // the chat message list.
      if (message.ref && message.ref[0]
          && message.ref[0].tag === REFERENCE_TAG_THREADED) {
        this.formatCommentSystemMessage(message.ref[0].messageId, ret);
      }
    }

    if (message.refBy) {
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

    if (ret.appData.commentSystemMessageTracker) {
      ret.appData.commentSystemMessageTracker.clear();
    }

    ret.appData.commentSystemMessageTracker 
      = Observer.getObserverContext(getter => {
      const observable = new MessageObservable(this.$.list.messenger,
          this.chatId, messageId);
      // Observe the message.
      getter.observe(observable, (getter, message) => {
          ret.content = ret.username + REFERENCE_MESSAGE_STRING_COMMENT
          + '"' + message.content + '"';
      });
    },
    () => {
      // Callback will be called after getting the message 
      // asynchronously. Refresh the UI to render the data
      this.refresh(targetMessage);
    });
  }

  // Construct the data needed to display a comment message.
  // @param {Object} messageId 
  //  The MessageId object of the comment message
  // @param {Object} ret
  //  The wrapper of the data to display the comment message
  formatCommentMessage(messageId, ret) {

    if (ret.appData.commentMessageTracker) {
      ret.appData.commentMessageTracker.clear();
    }

    let refMessage;
    ret.appData.commentMessageTracker = Observer.getObserverContext(getter => {
      const messageObservable = new MessageObservable(this.$.list.messenger,
          this.chatId, messageId);

      //observe the message.
      getter.observe(messageObservable, (getter, message) => {
        refMessage = message;
        const refMessageId = refMessage.messageId.toString();
        let isMessageFound = false;
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
    },
    () => {
      // Callback will be called after getting the message 
      // asynchronously. Refresh the UI to render the data
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
    const element = event.target.querySelector('.chevron');
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
    const chevron = event.target.querySelector('.chevron');
    const dropdown = this.shadowRoot.querySelector('.chevronDropdown');
    if (dropdown.style.display !== 'flex') {
      // Hide the chevron.
      chevron.style.display='none';
      chevron.needsToHide = false;
    }
    else {
      // We have to mark that the chevron should be hidden, but not actually
      // hide it.
      chevron.needsToHide = true;
    }
  }
}

window.customElements.define(ThreadedChatMessageList.is, ThreadedChatMessageList);
