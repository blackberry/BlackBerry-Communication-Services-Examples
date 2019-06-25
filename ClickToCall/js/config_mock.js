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

// This configuration will setup the ClickToCall example application to run in
// the sandbox environment against a domain with disabled user authentication.
//
// This configuration uses the BlackBerry Key Management Service (KMS), which
// BlackBerry recommends for most applications. See
// https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/security.html

// The ID of the domain assigned to this application.  Refer to the Developer
// Guide for more information on setting up your domain:
// https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html#domain
const DOMAIN_ID = 'your_domain_id';

// The identity of the user to call, as assigned by your identity provider.
const AGENT_USER_ID = 'user_id_to_call';

// This passcode is used to protect user keys.  This is configurable only to
// simplify the setup flow for the example application.  This is not a
// recommended practice.  Please refer to the RichChat application, which will
// prompt the logged in user for their passcode.
const KEY_PASSCODE = 'passcode';

// ===========================================================================
// The default values provided below configure ClickToCall to work as
// described in the Developer Guide.

// This configuration will only work in the sandbox environment.
const ENVIRONMENT = 'Sandbox';

// Authentication is disabled for this configuration.
const AUTH_CONFIGURATION = {};

// The URL or relative path of the Argon2 WASM file.
const KMS_ARGON_WASM_URL = '../../sdk/argon2.wasm';

// The function ClickToCall will use to create its user manager.  This
// configuration uses the MockUserManager.
const createUserManager = (userRegId, authManager, getIdentities) =>
  Promise.resolve(
    new MockUserManager(userRegId, authManager, getIdentities, DOMAIN_ID)
  );
