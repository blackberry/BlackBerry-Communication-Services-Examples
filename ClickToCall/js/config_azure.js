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

// Reg Id of the contact to start call with.
const CONTACT_REG_ID = 'contact_reg_id';

// This secret is used to protect user keys. Must be individual for each user.
const USER_SECRET = 'user_secret';

// Refer to the guide to set up your application to use Azure:
// https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/azureForWebExamples.html

// This domain is a string known to the BBM Enterprise server, which is
// generally a GUID.
// To create a new domain follow the link below
// https://account.good.com/#/a/organization/applications/add
const ID_PROVIDER_DOMAIN = 'your_idp_domain';

// The client ID of application registered on OAuth 2.0 server.
// To set up your application refer to
// https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/azureIdentityManagement.html
const CLIENT_ID = 'your_client_id';

// The tenant ID of your organization.
// To get your tenant ID refer to
// https://docs.microsoft.com/en-us/azure/active-directory/develop/active-directory-howto-tenant
const TENANT_ID = 'your_tenant_id';

// =============================================================================
// Configuration below does not require modifications. Change it only if you
// want to change existing application behavior.

// The environment of your BBM Enterprise server. Must be either 'Sandbox' or
// 'Production'.
const ID_PROVIDER_ENVIRONMENT = 'Sandbox';

// The URL or relative path of the Argon2 WASM file.
const KMS_ARGON_WASM_URL = '../../sdk/argon2.wasm';

// This configuration contains service endpoints and information for OAuth2
// authentication.
const AUTH_CONFIGURATION = {
  // OAuth 2.0 endpoint for requesting an access token.
  authService: `https://login.microsoftonline.com/${TENANT_ID}/oauth2/v2.0/authorize`,

  // The client ID of application registered on OAuth 2.0 server
  clientId: CLIENT_ID,

  // Scopes of OAuth 2.0 access token (which resources it can access)
  scope: `api://${CLIENT_ID}/Messaging.All https://graph.microsoft.com/User.ReadWrite https://graph.microsoft.com/User.ReadBasic.All`
};

// Create the user manager for the Click To Call app.
const createUserManager = (userRegId, authManager, getIdentities) =>
  Promise.resolve(new AzureUserManager(userRegId, authManager, getIdentities));
