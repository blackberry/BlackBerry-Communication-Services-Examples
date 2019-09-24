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

/**
 * @class DataTransferElement
 * @memberof Examples
 */

import { PolymerElement, html } from '../node_modules/@polymer/polymer/polymer-element.js';
import { } from '../node_modules/@polymer/polymer/lib/elements/dom-repeat.js';

const STATE_CONNECTED = 'CONNECTED';
const STATE_DISCONNECTED = 'DISCONNECTED';
const STATE_INITIATED = 'INITIATED';
const MAX_LIST_ITEMS = 100;

/**
 * data-transfer-element element class implementation.
 *
 * Demonstrates how to implement file transfer functionality using the
 * Spark Communications SDK for JavaScript.
 */
class DataTransferElement extends PolymerElement {

  static get template() {
    return html`
      <style>
        :host {
          position: absolute;
          background: white;
          width: 480px;
          height: 480px;
          left: calc(50% - 240px);
          left: -webkit-calc(50% - 240px);
          top: calc(50% - 320px);
          top: -webkit-calc(50% - 320px);
          border-radius: 5px;
          box-shadow: 0px 8px 26px 0px black;
        }

        button {
          background: #087099;
          color: antiquewhite;
          border: none;
          padding: 5px;
          font-size: 14px;
          margin: 5px;
          height: 40px;
          cursor: pointer;
        }

        label {
          color: #087099;
          margin: 10px;
        }

        .container {
          width: calc(100% - 20px);
          width: -webkit-calc(100% - 20px);
          height: calc(100% - 20px);
          height: -webkit-calc(100% - 20px);
          margin: 10px;
        }

        .container .content {
          width: 100%;
          height: 100%;
          display: flex;
          flex-direction: column;
        }

        .container .content input {
          border: none;
          height: 40px;
          font-size: 14px;
          color: #087099;
          margin-right: 10px;
          padding-left: 10px;
          padding-right: 10px;
          font-family: sans-serif;
        }

        .container .content .file-list {
          height:100px;
          width: 100%;
          flex: auto;
          overflow: auto;
        }

        .file-progress {
          color: #087099;
          background: white;
          height: auto;
          display: flex;
          flex-direction: row;
          margin-left: 10px;
          margin-right: 10px;
          align-items: center;
          margin-bottom: 10px;
        }

        .file-progress img {
          height: 20px;
          width: 20px;
        }

      </style>
      <div class="container">
        <div class="content">
          <div style="display: flex; align-items: center;">
            <label><b>DATA TRANSFER</b></label>
          </div>

          <div style="display: flex; align-items: center;">
              <label>MY REG ID:</label>
              <label>[[myRegId]]</label>
          </div>

          <div style="display: flex; align-items: center;">
            <label>CONNECTION STATE:</label>
            <label>[[connectionState]]</label>
          </div>

          <div style="display: flex; align-items: center;">
            <label hidden$="[[hasConnection]]">CONNECT TO: </label>
            <input id="contact-reg-id-input"
                  type="number"
                  hidden$="[[hasConnection]]"
                  style="flex:1 auto; margin-right:10px;"
                  placeholder="ENTER CONTACT'S REG ID"
                  value="{{contactRegId::input}}">
          </div>

          <div style="display: flex; align-items: center;">
            <label hidden$="[[!hasConnection]]">OTHER PARTY REG ID:</label>
            <label hidden$="[[!hasConnection]]">[[contactRegId]]</label>
          </div>

          <div class="file-list">
            <dom-repeat items="[[fileTransfers]]">
              <template>
                <div class="file-progress">
                  <img src="img/ic_file_download_black_24px.svg"
                      hidden$=[[!item.isDownload]]>
                  <img src="img/ic_file_upload_black_24px.svg"
                      hidden$=[[item.isDownload]]>
                  <div style="display: flex; flex-direction: column; flex: auto; margin:5px; text-overflow: ellipsis;">
                    [[item.name]] ([[bytesToSize(item.size)]])
                    <div style$="height: 5px; margin-top:5px; width:[[item.percentage]]; background-color: #087099"></div>
                  </div>
                  <div style="height: 20px; width: 20px;">
                    <a hidden$=[[!item.fileUrl]] href$="[[item.fileUrl]]" download$="[[item.fileName]]">
                      <img src="img/ic_save_black_24px.svg"
                          alt="SAVE"
                          title="Click to save downloaded file.">
                    </a>
                    <img src="img/ic_check_black_24px.svg"
                        hidden$="[[!item.isSent]]"
                        alt="OK"
                        title="File is sent.">
                  </div>
                </div>
              </template>
            </dom-repeat>
          </div>
          <div style="text-align: right;">
            <button id="send-file-button"
                    style="width: 120px;"
                    on-click="sendFileButtonClicked"
                    hidden$="[[!hasConnection]]">SEND FILE</button>
            <button id="connect-button"
                    style="width: 120px;"
                    hidden$="[[hasConnection]]"
                    on-click="onConnectClicked">CONNECT</button>
            <button id="disconnect-button"
                    style="width: 120px;"
                    hidden$="[[!hasConnection]]"
                    on-click="onDisconnectClicked">DISCONNECT</button>
          </div>
        </div>
      </div>
      <input id="input-file" type="file" hidden on-change="onInputFileChange"/>
    `;
  }

  // Called after property values are set and local DOM is initialized
  ready() {
    super.ready();
    this.regIdInput = this.shadowRoot.querySelector('#contact-reg-id-input');
    this.inputFile = this.shadowRoot.querySelector('#input-file');
    this.regIdInput.focus();
  }

  static get properties() {
    return {
      connection: {
        type: Object,
        readOnly: false,
        notify: true,
        observer: 'onConnectionChanged',
        value: null
      },
      receiver: {
        type: Object,
        readOnly: false,
        notify: true,
        observer: 'onReceiverChanged',
        value: null
      },
      contactRegId: {
        type: String,
        readOnly: false,
        notify: true,
        value: ''
      },
      myRegId: {
        type: String,
        readOnly: false,
        notify: true,
        value: ''
      },
      connectionState: {
        type: String,
        readOnly: false,
        notify: true,
        value: STATE_DISCONNECTED
      },
      hasConnection: {
        type: Boolean,
        readOnly: false,
        notify: true,
        computed: 'getHasConnection(connectionState)'
      },
      fileTransfers: {
        type: Array,
        readOnly: false,
        notify: true,
        value: []
      }
    };
  }

  /**
   * Sets an instance of SparkCommunications.
   * @param {SparkCommunications} sdk
   *   An instance of SparkCommunications.
   */
  setBbmSdk(sdk) {
    this.bbmSdk = sdk;
    this.myRegId = this.bbmSdk.getRegistrationInfo().regId;
    this.bbmSdk.media.on('incomingDataConnection', connection => {
      const contactRegId = connection.callParty.regId;
      const messageString = `${contactRegId} wants to establish a connection.`
        + ' Do you want to accept?';
      if (confirm(messageString)) {
        connection.accept()
        .then(() => {
          this.connection = connection;
          this.connectionState = STATE_CONNECTED;
          this.contactRegId = contactRegId;
        })
        .catch(error => {
          alert('Failed to accept connection');
          console.error(`Failed to accept connection: ${error}`);
        });
      }
      else {
        connection.end(SparkCommunications.Media.CallEndReason.REJECT_CALL);
      }
    });
  }

  // Function is invoked each time new connection is set.
  onConnectionChanged(newConnection, oldConnection) {
    const onDataReceived = (connection, receiver) => {
      if (this.connection === connection) {
        // Set new file receiver.
        this.receiver = receiver;
      }
      else {
        console.warn('Data is received for the unknown connection.');
      }
    };

    const onDisconnected = () => {
      this.receiver = null;
      this.connectionState = STATE_DISCONNECTED;
    };

    if (oldConnection) {
      // Unsubscribe from the old connection events.
      oldConnection.end();
      oldConnection.removeListener('dataReceived', onDataReceived);
      oldConnection.removeListener('onDisconnected', onDisconnected);
      this.receiver = null;
    }

    if (newConnection) {
      // Subscribe to the new connection events.
      newConnection.on('dataReceived', onDataReceived);
      newConnection.on('disconnected', onDisconnected);
    }
  }

  // Function is invoked each time new file receiver is set.
  onReceiverChanged(newReceiver, oldReceiver) {
    const onDone = receiver => {
      // File is received.
      const data = receiver.deplete();
      const blob = new Blob([data], {
        type: receiver.header.type
      });
      const url = window.URL.createObjectURL(blob);
      // Set percentage to 100%. Set file name and download URL.
      this.set(`fileTransfers.0.percentage`, `100%`);
      this.set(`fileTransfers.0.fileUrl`, url);
      this.set(`fileTransfers.0.fileName`, receiver.header.name);
    };

    const onProgress = (receiver, percentage) => {
      this.set(`fileTransfers.0.percentage`, `${percentage}%`);
    };

    const onAborted = () => {
      alert('Failed to download file.');
    };

    if (oldReceiver) {
      // Unsubscribe from the old receiver events.
      oldReceiver.removeListener('done', onDone);
      oldReceiver.removeListener('progress', onProgress);
      oldReceiver.removeListener('aborted', onAborted);
    }

    if (newReceiver) {
      // Create new file transfer model and put it to the 'fileTransfers'
      // collection.
      const fileTransferItem = {};
      fileTransferItem.size = newReceiver.header.size;
      fileTransferItem.name = newReceiver.header.name;
      fileTransferItem.percentage = '0%';
      fileTransferItem.isDownload = true;
      // Make sure list doesn't exceed maximum allowed number of list items.
      while (this.fileTransfers.length >= MAX_LIST_ITEMS) {
        this.fileTransfers.pop();
      }
      this.unshift('fileTransfers', fileTransferItem);
      this.notifyPath('fileTransfers');
      newReceiver.configure({
        mode: SparkCommunications.Media.DataConnection.DataReceiver.Mode.DATA_FULL
      });
      // Subscribe to the new receiver events.
      newReceiver.on('done', onDone);
      newReceiver.on('progress', onProgress);
      newReceiver.on('aborted', onAborted);
    }
  }

  // Binding function. Binds click event handler to 'send-file-button'.
  sendFileButtonClicked() {
    this.inputFile.click();
  }

  // Binding function. Binds change event handler to 'input-file' element.
  // Event handler is invoked each time user selects new file. Event handler
  // initiates file sending.
  async onInputFileChange() {
    if (this.inputFile.files.count === 0) {
      return;
    }
    // Check if any file in collection of the selected file exceeds the
    // maximum allowed size.
    const file = this.inputFile.files[0];
    this.inputFile.value = '';
    // Create new file transfer model and put it to the 'fileTransfers'
    // collection.
    const fileTransferItem = {};
    fileTransferItem.size = file.size;
    fileTransferItem.name = file.name;
    fileTransferItem.percentage = 0;
    fileTransferItem.isDownload = false;
    this.unshift('fileTransfers', fileTransferItem);
    try {
      // Send user selected file.
      await this.connection.sendFile(file, percentage => {
        this.set(`fileTransfers.0.percentage`, `${percentage}%`);
      });
      // File is sent. Set percentage to 100%.
      this.set(`fileTransfers.0.percentage`, `100%`);
      this.set(`fileTransfers.0.isSent`, true);
    }
    catch(error) {
      alert(error);
    }
  }

  // Binding function. Binds click event handler to 'connect-button'.
  async onConnectClicked() {
    if (this.connectionState !== STATE_DISCONNECTED) {
      console.log('Connection is in progress.');
      return;
    }

    this.set('connectionState', STATE_INITIATED);
    // Make sure the other party regId is specified.
    if (!this.contactRegId) {
      alert('Contact regId is not specified.');
      return;
    }

    if (!this.bbmSdk) {
      alert('The SDK is not initialized.');
      return;
    }

    // Create connection to the specified regId.
    const connection = await this.bbmSdk.media.createDataConnection(
      new SparkCommunications.Media.Callee(this.contactRegId), null);

    connection.on('connected', () => {
      this.connection = connection;
      this.connectionState = STATE_CONNECTED;
    });
  }

  // Binding function. Binds click event handler to 'disconnect-button'.
  onDisconnectClicked() {
    if (this.connection) {
      this.connection.end(SparkCommunications.Media.CallEndReason.USER_HANGUP);
    }
  }

  // Computing function. Returns true if current connection state is 
  // 'CONNECTED'
  getHasConnection(connectionState) {
    return connectionState === STATE_CONNECTED;
  }

  // Function converts number of bytes into human readable format.
  bytesToSize(bytes) {
    if (bytes == 0) return '0 Bytes';
    var kb = 1024,
        decimals = 2,
        units = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'],
        i = Math.floor(Math.log(bytes) / Math.log(kb));
        if (i >= units.length) {
          i = units.length - 1;
        }
    return parseFloat((bytes / Math.pow(kb, i)).toFixed(decimals)) 
      + ' ' + units[i];
  }

  static get is() { return 'data-transfer-element'; }
}

customElements.define(DataTransferElement.is, DataTransferElement);
