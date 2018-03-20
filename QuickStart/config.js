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


// This domain is a string known to the BBM Enterprise server, which is 
// generally a GUID.
var ID_PROVIDER_DOMAIN = 'your_idp_domain';

// The environment of your BBM Enterprise server. Must be either 'Sandbox' or
// 'Production'.
var ID_PROVIDER_ENVIRONMENT = 'Sandbox';

// This configuration contains service endpoints and information for OAuth2
// authentication.
var AUTH_CONFIGURATION = {
  // The type of authentication. Must be either 'OAuth' or 'JWT'.
  type: 'OAuth',

  // OAuth 2.0 endpoint for requesting an access token
  // To use google OAuth service, put:
  // 'https://accounts.google.com/o/oauth2/v2/auth'
  authService : 'your_auth_service_endpoint',

  // OAuth 2.0 endpoint for token validation
  // To use google token info service, put :
  // 'https://www.googleapis.com/oauth2/v3/tokeninfo'
  tokenInfoService : 'your_oauth_token_info_endpoint',

  // OAuth 2.0 endpoint for obtaining user information (name, email, avatar URL)
  // To use google user info service, put:
  // 'https://www.googleapis.com/plus/v1/people/me'
  userInfoService : 'your_oauth_user_info_endpoint',

  // Scopes of OAuth 2.0 access token (which resources it can access)
  // If google OAuth service is used, put following scopes:
  // 'profile https://www.googleapis.com/auth/firebase https://www.googleapis.com/auth/userinfo.email'
  scope : 'your_scope_oauth',

  // The client ID of application registered on OAuth 2.0 server
  clientId: 'your_client_id',

  // Redirect URL same as registered on OAuth 2.0 server. Required by OAuth 2.0
  // server to redirect application after issuing an access token.
  redirectUri : 'your_redirect_url'
};

var createAuthManager = function() {
  return new GoogleAuthManager(AUTH_CONFIGURATION);
};
