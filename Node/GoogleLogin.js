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

//
// The bbm sdk requires an oauth2 provider, and a key storage service.
//
// This module implements both, using Google for the oauth2 provider, and
// implementing key storage using Firebase.
//

// Include some needed modules.
const FirebaseUserManager = require(
  'bbm-enterprise/examples/support/identity/FirebaseUserManager.js');
const FirebaseKeyProvider = require(
  'bbm-enterprise/examples/support/protect/firebase/FirebaseKeyProvider.js');
const KeyProtect = require(
  'bbm-enterprise/examples/support/protect/encryption/KeyProtect.js');
const google = require('googleapis');
const oauth2 = google.oauth2('v2');
const {JWT} = require('google-auth-library');

// These two modules are a little bit special - the FirebaseKeyProvider expects
// some symbols to be available globally, so import these two as global
// variables.
global.BBMEnterprise = require('bbm-enterprise')

global.firebase = require('firebase');

module.exports = {
  login: function(config) {
    function GoogleAuthManager(config) {
      // Construct an authentication client to do token retrieval.
      this.client = new JWT(
        config.googleConfig.client_email, null, config.googleConfig.private_key,
        ['https://www.googleapis.com/auth/userinfo.profile',
        'https://www.googleapis.com/auth/userinfo.email',
        'https://www.googleapis.com/auth/firebase']);

      this.authenticate = function() {
        return this.client.authorize()
        .then(result => {
          // Retrieve the User ID.
          return new Promise((resolve, reject) => {
            oauth2.userinfo.get({access_token: result.access_token},
                                (error, result) => {
              if(error) {
                reject(error);
              } else {
                // Cache the picture, to return later.
                this.picture = result.picture;
                resolve(result);
              }
            });
          });
        });
      };

      this.getLocalUserInfo = () => {
        return {
          displayName: 'BBMBot',
          email: '',
          avatarUrl: this.picture
        };
      }

      this.getBbmSdkToken = () => {
        return this.client.authorize()
        .then(result => result.access_token);
      };

      this.getUserManagerToken = () => {
        return this.getBbmSdkToken();
      };
    };

    const authManager = new GoogleAuthManager(config);

    return authManager.authenticate()
    .then(result => {
      // Create an SDK instance.
      const bbmsdk = new BBMEnterprise({
        domain: config.id_provider_domain,
        environment: config.id_provider_environment,
        userId: result.id,
        getToken: () => authManager.getBbmSdkToken(),
        getKeyProvider: (regId, accessToken) =>
          FirebaseUserManager.factory.createInstance(
            config.firebaseConfig, regId, authManager,
            require('bbm-enterprise/examples/support/identity/GenericUserInfo.js'))
          .then(contactsManager =>
            KeyProtect.factory.createInstance(
              () => Promise.resolve(config.password),
              regId,
              'BBME SDK Pre-KMS DRK')
            .then(keyProtect =>
              FirebaseKeyProvider.factory.createInstance(
                config.firebaseConfig,
                accessToken,
                contactsManager.getUid,
                () => Promise.resolve('GenerateNewKeys'),
                keyProtect))),
        description: 'node ' + process.version
      });
      bbmsdk.on('registrationChanged', registrationInfo => {
        if(registrationInfo.state === "Failure") {
          console.error('BBM Login failed');
          return;
        } else if (registrationInfo.state === "Success") {
          console.log('BBM Login complete with regid: ' + registrationInfo.regId);
        }
      });
      return bbmsdk;
    });
  }
}
