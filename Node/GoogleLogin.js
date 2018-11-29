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
// The bbm sdk requires an oauth2 provider.
//
// This module implements it, using Google for the oauth2 provider.
//

// Include some needed modules.
const FirebaseUserManager = require(
  'bbm-enterprise/examples/support/identity/FirebaseUserManager.js');
const google = require('googleapis');
const oauth2 = google.oauth2('v2');
const {JWT} = require('google-auth-library');

// These two modules are a little bit special - the FirebaseKeyProvider expects
// some symbols to be available globally, so import these two as global
// variables.
global.BBMEnterprise = require('bbm-enterprise');
global.firebase = require('firebase');

module.exports = {
  login: function(config) {
    function GoogleAuthManager(config) {
      // Construct an authentication client to do token retrieval.
      this.client = new JWT(
        config.googleConfig.client_email, null, config.googleConfig.private_key,
        [
          'https://www.googleapis.com/auth/userinfo.profile',
          'https://www.googleapis.com/auth/userinfo.email',
          'https://www.googleapis.com/auth/firebase'
        ]);

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
                  this.picture = result.picture;
                  this.userId = result.id;
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
          avatarUrl: this.picture,
          userId: this.userId
        };
      };

      this.getBbmSdkToken = () => {
        return this.client.authorize()
        .then(result => result.access_token);
      };

      this.getUserManagerToken = () => {
        return this.getBbmSdkToken();
      };
    }

    const authManager = new GoogleAuthManager(config);

    return new Promise((resolve, reject) => {
      let isSyncStarted = false;
      authManager.authenticate()
      .then(authUserInfo => {
        console.log('Completed google authentication. Performing BBM login');

        // Create an SDK instance.
        const bbmeSdk = new BBMEnterprise({
          domain: config.id_provider_domain,
          environment: config.id_provider_environment,
          userId: authUserInfo.id,
          getToken: authManager.getBbmSdkToken,
          description: `node ${process.version}`
        });

        // Handle changes of BBM Enterprise setup state.
        bbmeSdk.on('setupState', state => {
          console.log(`BBMEnterprise setup state: ${state.value}`);
          switch (state.value) {
            case BBMEnterprise.SetupState.Success: {
              const userRegId = bbmeSdk.getRegistrationInfo().regId;
              console.log(`BBM Login complete with RegId: ${userRegId}`);

              // Use FirebaseUserManager to register BbmBot in the database.
              FirebaseUserManager.factory.createInstance(
                config.firebaseConfig, userRegId, authManager,
                require('bbm-enterprise/examples/support/identity/GenericUserInfo.js'),
                bbmeSdk.getIdentitiesFromAppUserId,
                bbmeSdk.getIdentitiesFromAppUserIds,
                config.appName);

              resolve(bbmeSdk);
            }
            return;

            case BBMEnterprise.SetupState.SyncRequired: {
              if (isSyncStarted) {
                reject(new Error('Failed to get user keys using provided password'));
                return;
              }
              const isNew =
                bbmeSdk.syncPasscodeState === BBMEnterprise.SyncPasscodeState.New;
              const syncAction = isNew
                ? BBMEnterprise.SyncStartAction.New
                : BBMEnterprise.SyncStartAction.Existing;
              bbmeSdk.syncStart(config.password, syncAction);
              break;
            }
            case BBMEnterprise.SetupState.SyncStarted:
              isSyncStarted = true;
            break;
          }
        });

        // Handle setup error.
        bbmeSdk.on('setupError', error => {
          console.error('BBM Login failed' + error);
          reject(error.value);
        });

        bbmeSdk.setupStart();
      })
      .catch(error => {
        // Log and propagate error.
        console.log(`Failed to authenticate: ${error}`);
        throw(error);
      });
    });
  }
};
