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
 *
 */

// Instantiate an SDK instance and returns a promise of the SDK instance that
// has already been setup.

const BBMEnterprise = require('bbm-enterprise');

module.exports = {
  sdkSetup: (config, authManager) => {
    return new Promise((resolve, reject) => {
      let isSyncStarted = false;
      console.log('Setting up the Spark Communications SDK');

      // Instantiate the SDK.
      //
      // We use the 'sdkConfig' imported from the example's configuration file
      // to override some of the options used to configure the SDK.
      //
      // This example might not work if your 'sdkConfig' specifies any of the
      // parameters assigned below.
      const sdk = new BBMEnterprise(Object.assign(
        {
          // You must specify your domain in the SDK_CONFIG.

          // This example requires user authentication to be disabled, which
          // is not supported in production.
          sandbox: true,

          // The user ID to use when connecting to the BlackBerry
          // Infrastructure.  We use the value returned by our identity
          // provider.
          userId: authManager.getLocalUserInfo().userId,

          // The access token to use when connecting to the BlackBerry
          // Infrastructure.  We use the value returned by our identity
          // provider.
          getToken: () => authManager.getBbmSdkToken(),

          // We just use the node version string to describe this endpoint.
          description: `node ${process.version}`
        },
        config.sdkConfig
      ));

      // Handle changes of BBM Enterprise setup state.
      sdk.on('setupState', state => {
        console.log(`BBMEnterprise setup state: ${state.value}`);
        switch (state.value) {
          case BBMEnterprise.SetupState.Success: {
            // Setup was successful.
            const userRegId = sdk.getRegistrationInfo().regId;
            console.log(`SDK setup complete; regId= ${userRegId}`);
            resolve(sdk);
          }
          return;

          case BBMEnterprise.SetupState.SyncRequired: {
            if (isSyncStarted) {
              // We have already tried to sync the user's keys using the
              // given passcode.  For simplicity in this example, we don't
              // try to recover when the configured passcode cannot be
              // used.
              reject(new Error(
                'Failed to get user keys using configured key_passcode'));
              return;
            }

            // We need to provide the SDK with the user's key passcode.
            sdk.syncStart(
              // Use the configured passcode.
              config.key_passcode,

              // Does the user have existing keys?
              sdk.syncPasscodeState === BBMEnterprise.SyncPasscodeState.New
              // No, we must create new keys.  The key passcode will be
              // used to protect the new keys.
              ? BBMEnterprise.SyncStartAction.New
              // Yes, we have existing keys.  The key passcode will be
              // used to unprotect the keys.
              : BBMEnterprise.SyncStartAction.Existing
            );
            break;
          }
          case BBMEnterprise.SetupState.SyncStarted:
            // Syncing of the user's keys has started.  Remember this so
            // that we can tell if the setup state regresses.
            isSyncStarted = true;
          break;
        }
      });

      // Handle setup error.
      sdk.on('setupError', error => {
        console.error(`SDK setup failed; error=${error}`);
        reject(error.value);
      });

      sdk.setupStart();
    });
  }
};
