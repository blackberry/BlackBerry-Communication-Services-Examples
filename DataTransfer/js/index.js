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

/**
 * This is the example application, which displays basic implementation of file
 * transfer functionality using BBMEnterprise SDK for JavaScript.
 * 
 * @class DataTransfer
 * @memberof Examples
 */

window.onload = async () => {
  try {
    // Make sure that the browser supports all of the necessary functionality,
    // including support for interacting with the BlackBerry Key Management
    // Service (KMS).
    await BBMEnterprise.validateBrowser({
      kms: { argonWasmUrl: KMS_ARGON_WASM_URL }
    });

    // Setup the authentication manager for the application.
    const authManager = new AuthenticationManager(AUTH_CONFIGURATION);
    if (AuthenticationManager.name === 'MockAuthManager') {
      // We are using the MockAuthmanager, so we need to override how it
      // acquires the local user's user ID.
      authManager.getUserId = () => new Promise((resolve, reject) => {
        const userEmailDialog = document.createElement('bbm-user-email-dialog');
        document.body.appendChild(userEmailDialog);
        userEmailDialog.addEventListener('Ok', e => {
          userEmailDialog.parentNode.removeChild(userEmailDialog);
          resolve(e.detail.userEmail);
        });
        userEmailDialog.addEventListener('Cancel', () => {
          userEmailDialog.parentNode.removeChild(userEmailDialog);
          reject('Failed to get user email.');
        });
      });
    }

    // Authenticate the user.  Configurations that use a real identity
    // provider (IDP) will redirect the browser to the IDP's authentication
    // page.
    const authUserInfo = await authManager.authenticate();
    if (!authUserInfo) {
      console.warn('Redirecting for authentication.');
      return;
    }

    // Instantiate the SDK.
    const sdk = new BBMEnterprise({
      domain: DOMAIN_ID,
      environment: ENVIRONMENT,
      userId: authUserInfo.userId,
      getToken: () => authManager.getBbmSdkToken(),
      description: navigator.userAgent,
      kmsArgonWasmUrl: KMS_ARGON_WASM_URL
    });

    // Setup is asynchronous.  Create a promise we can use to wait on
    // until the SDK setup has completed.
    const sdkSetup = new Promise((resolve, reject) => {
      // Handle changes to the SDK's setup state.
      let isSyncStarted = false;
      sdk.on('setupState', (state) => {
        console.log(
          `DataTransfer: BBMEnterprise setup state: ${state.value}`);
        switch (state.value) {
          case BBMEnterprise.SetupState.Success: {
            // Setup was successful.
            resolve();
            break;
          }
          case BBMEnterprise.SetupState.SyncRequired: {
            if (isSyncStarted) {
              // We have already tried to sync the user's keys using the
              // given passcode.  For simplicity in this example, we don't
              // try to recover when the configured passcode cannot be
              // used.
              reject(new Error(
                'Failed to get user keys using provided KEY_PASSCODE.'));
              return;
            }

            // We need to provide the SDK with the user's key passcode.
            sdk.syncStart(
              // For simplicity in this example, we always use the
              // configured passcode.
              KEY_PASSCODE,

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
          case BBMEnterprise.SetupState.SyncStarted: {
            // Syncing of the user's keys has started.  Remember this so
            // that we can tell if the setup state regresses.
            isSyncStarted = true;
            break;
          }
        }
      });

      // Any setup error received will fail the SDK setup promise.
      sdk.on('setupError', error => {
       reject(new Error(
         `Endpoint setup failed: ${error.value}`));
      });

      // Start the SDK setup.
      sdk.setupStart();
    });

    // Wait for the SDK setup to complete.
    await sdkSetup;

    // This example doesn't remove the event listeners on the setupState
    // or setupErrors events that were used to monitor the setup progress.
    // It also doesn't setup new listeners to monitor these events going
    // forward to act on any issue that causes the SDK's state to regress.

    // Allow the dataTransferElement to interact with the SDK to perform
    // peer-to-peer data transfers.
    const dataTransferElement =
      Polymer.dom(document.body).querySelector('#data-transfer');
    dataTransferElement.setBbmSdk(sdk);
  }
  catch(error) {
    alert(`DataTransfer encountered an error; error=${error}`);
  }
};

