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

// This configuration will setup the BBM Bot example application to run in
// the sandbox environment against a domain with disabled user authentication.
//
// This configuration uses the BlackBerry Key Management Service (KMS), which
// BlackBerry recommends for most applications. See
// https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/security.html

module.exports = {
  // The ID of the domain assigned to this application.  Refer to the
  // Developer Guide for more information on setting up your domain:
  // https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html#domain
  domain_id: 'your_domain_id',

  // The environment in which the domain has been defined.  This configuration
  // will only work in the sandbox environment.
  environment: 'Sandbox',

  // Configure the Mock IDP client.
  mockConfig: {
    // Set the user ID by which the bot will be known within the domain.
    userId: 'SparkCommunicationsBot'
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
