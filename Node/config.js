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

module.exports = {
  // This domain is a string known to the BBM Enterprise server, which is
  // generally a GUID.
  id_provider_domain: 'your_idp_domain',

  // The environment of your BBM Enterprise server. Must be either 'Sandbox' or
  // 'Production'.
  id_provider_environment: 'Sandbox',

  // Configuration information for firebase, to use for key storage.
  firebaseConfig: {
    apiKey: "your_api_key",
    authDomain: "your_auth_domain",
    databaseURL: "your_database_url",
    projectId: "your_project_id",
    storageBucket: "your_storage_bucket",
    messagingSenderId: "your_messaging_sender_id"
  },

  // Configuration information for a botlibre account. botlibre provides chatbot
  // services.
  botLibre: {
    application: 'your_botlibre_application',
    instance: 'your_botlibre_instance'
  },

  // Configuration information for login to a google service account.
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

  // A password to use to protect keys before storing them on firebase.
  password: 'Protect my keys!'
};
