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
 *
 */

// This configuration will setup the BBMBot example application to run in
// the sandbox environment against a domain to use Google for authentication
// and user management.
//
// Refer to the Developer Guide for more details on how to setup your
// application to use Google:
// https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/googleSignInIdentityManagement.html
//
// This configuration uses the BlackBerry Key Management Service (KMS), which
// BlackBerry recommends for most applications. See
// https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/security.html

module.exports = {
  // The ID of the domain assigned to this application.  Refer to the
  // Developer Guide for more information on setting up your domain:
  // https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html#domain
  domain_id: 'your_domain_id',

  // The environment in which the domain has been defined. This must be either
  // 'Sandbox' or 'Production'.
  environment: 'Sandbox',

  // The OAuth 2.0 configuration for your Google service account.  A Google
  // service account can be created by logging into the Google API console at:
  // https://console.developers.google.com.
  //
  // * Choose credentials, then in the credentials tab, choose 'Manage service
  //   accounts'.
  // * Choose 'CREATE SERVICE ACCOUNT'.
  // * Make sure to check the box labelled 'Enable G Suite Domain-wide
  //   Delegation' when creating the account.
  googleConfig: {
    "type": "service_account",
    "project_id": "your_project_id",
    "private_key_id": "your_private_key_id",
    "private_key": "your_private_key",
    "client_email": "your_client_email",
    "client_id": "your_client_id",
    "auth_uri": "your_auth_uri",
    "token_uri": "your_token_uri",
    "auth_provider_x509_cert_url": "your_auth_provider_cert",
    "client_x509_cert_url": "your_client_cert"
  },

  // This passcode is used to protect the configured user's keys.
  key_passcode: 'passcode',

  // Configuration information for a Bot Libre account. Bot Libre provides
  // chatbot services.
  botLibre: {
    application: 'your_botlibre_application',
    instance: 'your_botlibre_instance'
  }
};
