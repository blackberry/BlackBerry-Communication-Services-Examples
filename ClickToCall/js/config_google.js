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

// This configuration will configure the ClickToCall example application to
// run against a domain configured to use Google for authentication and user
// management.
//
// Refer to the Developer Guide for more details on how to setup your
// application to use Google:
// https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/googleSignInIdentityManagement.html
//
// This configuration uses the BlackBerry Key Management Service (KMS), which
// BlackBerry recommends for most applications. See
// https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/security.html

// The ID of the domain assigned to this application.  Refer to the Developer
// Guide for more information on setting up your domain:
// https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html#domain
const DOMAIN_ID = 'your_domain_id';

// The client ID of the Google OAuth 2.0 service.
const CLIENT_ID = 'your_google_oauth_client_id';

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

// The environment in which your domain was created.  This must be either
// 'Sandbox' or 'Production'.
const ENVIRONMENT = 'Sandbox';

// The OAuth 2.0 configuration for authenticating users and managing contacts.
const AUTH_CONFIGURATION = {
  // The Google OAuth 2.0 service endpoint.
  authService : 'https://accounts.google.com/o/oauth2/v2/auth',

  // Scopes of OAuth 2.0 access token (which resources it can access)
  scope : 'https://www.googleapis.com/auth/contacts',

   // The client ID of the Google OAuth 2.0 service.
  clientId: CLIENT_ID
};

// The URL or relative path of the Argon2 WASM file.
const KMS_ARGON_WASM_URL = '../../sdk/argon2.wasm';

// The function ClickToCall will use to create its user manager.  This
// configuration uses the GooglePeopleUserManager.
const createUserManager = (userRegId, authManager, getIdentities) =>
  Promise.resolve(
    new GooglePeopleUserManager(userRegId, authManager, getIdentities,
                                AUTH_CONFIGURATION)
  );

