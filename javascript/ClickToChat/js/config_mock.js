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

// This configuration will setup the ClickToChat example application to run in
// the sandbox environment against a domain with disabled user authentication.
//
// This configuration uses the BlackBerry Key Management Service (KMS), which
// BlackBerry recommends for most applications. See
// https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/security.html

// This object will be used to override parts of SDK configuration used by the
// example application.
const SDK_CONFIG = {
  // The ID of the domain assigned to this application.  Refer to the Developer
  // Guide for more information on setting up your domain:
  // https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html#domain
  domain: 'your_domain_id'
};

// The identity of the user to chat with, as assigned by your identity
// provider.
const AGENT_USER_ID = 'agent_user_id';

// This passcode is used to protect user keys.  This is configurable only to
// simplify the setup flow for the example application.  This is not a
// recommended practice.  Please refer to the RichChat application, which will
// prompt the logged in user for their passcode.
const KEY_PASSCODE = 'passcode';
