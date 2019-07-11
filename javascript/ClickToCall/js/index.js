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
 * of how to implement generic Click To Call functionality using bbm-call UI
 * widget.
 *
 * When the user clicks the "Start Secure Call" button, the application will
 * start a call with the hard coded user RegId (CONTACT_REG_ID).
 *
 * @class ClickToCall
 * @memberof Examples
 */

// Function makes call to specified contact.
function makeCall() {
  const left = window.innerWidth / 2 - 200;
  const popup = window.open('callPopup.html', 'Call Window',
    `height=300,width=400,left=${left},top=200`);
  if (!popup) {
    alert('Failed to create popup window. Please check that your browser' +
      ' allows popups on this page.');
  }
}
