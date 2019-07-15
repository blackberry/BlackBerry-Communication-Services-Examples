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
 */

/**
 * @class CallPopup
 * @memberof Examples
 */

window.onload = async () => {
  try {
    // Set the Argon2 WASM file location if it has not already been set.
    // If you have put the argon2.wasm file in a custom location, you can
    // override this option in the imported SDK_CONFIG.
    const kmsArgonWasmUrl =
      SDK_CONFIG.kmsArgonWasmUrl || '../../sdk/argon2.wasm';

    // Make sure that the browser supports all of the necessary functionality,
    // including support for interacting with the BlackBerry Key Management
    // Service (KMS).
    await BBMEnterprise.validateBrowser({
      kms: { argonWasmUrl: kmsArgonWasmUrl }
    });

    // Setup the authentication manager for the application.
    const authManager = new MockAuthManager();
    // We are using the MockAuthManager, so we need to override how it
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

    // Authenticate the user.  Configurations that use a real identity
    // provider (IDP) will redirect the browser to the IDP's authentication
    // page.
    const authUserInfo = await authManager.authenticate();
    if (!authUserInfo) {
      console.warn('Redirecting for authentication.');
      return;
    }

    // Instantiate the SDK.
    //
    // We use the SDK_CONFIG imported from the example's configuration file to
    // override some of the options used to configure the SDK.
    //
    // This example might not work if your SDK_CONFIG specifies any of the
    // parameters assigned below.
    const sdk = new BBMEnterprise(Object.assign(
      {
        // You must specify your domain in the SDK_CONFIG.

        // This example requires user authentication to be disabled, which is
        // not supported in production.
        sandbox: true,

        // The user ID to use when connecting to the BlackBerry
        // Infrastructure.  We use the value returned by our identity
        // provider.
        userId: authUserInfo.userId,

        // The access token to use when connecting to the BlackBerry
        // Infrastructure.  We use the value returned by our identity
        // provider.
        getToken: () => authManager.getBbmSdkToken(),

        // We just use the browser's userAgent string to describe this
        // endpoint.
        description: navigator.userAgent,

        // Use the same kmsArgonWasmUrl that was used to to validate our
        // browser environment above.
        kmsArgonWasmUrl
      },
      SDK_CONFIG
    ));

    // Setup is asynchronous.  Create a promise we can use to wait on until
    // the SDK setup has completed.
    const sdkSetup = new Promise((resolve, reject) => {
      // Handle changes to the SDK's setup state.
      let isSyncStarted = false;
      sdk.on('setupState', async state => {
        console.log(`ClickToCall: BBMEnterprise setup state: ${state.value}`);
        switch (state.value) {
          case BBMEnterprise.SetupState.Success: {
            // Setup was successful.
            resolve();
            break;
          }
          case BBMEnterprise.SetupState.SyncRequired: {
            if (isSyncStarted) {
              // We have already tried to sync the user's keys using the given
              // passcode.  For simplicity in this example, we don't try to
              // recover when the configured passcode cannot be used.
              reject(new Error(
                'Failed to get user keys using provided KEY_PASSCODE.'));
              return;
            }

            // We need to provide the SDK with the user's key passcode.
            sdk.syncStart(
              // For simplicity in this example, we always use the configured
              // passcode.
              KEY_PASSCODE,

              // Does the user have existing keys?
              sdk.syncPasscodeState === BBMEnterprise.SyncPasscodeState.New
              // No, we must create new keys.  The key passcode will be used
              // to protect the new keys.
              ? BBMEnterprise.SyncStartAction.New
              // Yes, we have existing keys.  The key passcode will be used to
              // unprotect the keys.
              : BBMEnterprise.SyncStartAction.Existing
            );
            break;
          }
          case BBMEnterprise.SetupState.SyncStarted: {
            // Syncing of the user's keys has started.  Remember this so that
            // we can tell if the setup state regresses.
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

    // This example doesn't remove the event listeners on the setupState or
    // setupErrors events that were used to monitor the setup progress.  It
    // also doesn't setup new listeners to monitor these events going forward
    // to act on any issue that causes the SDK's state to regress.

    // Create and initialize the user manager.
    const userManager = new MockUserManager(
      sdk.getRegistrationInfo().regId,
      authManager,
      (...args) => sdk.getIdentitiesFromAppUserIds(...args),
      SDK_CONFIG.domain
    );
    await userManager.initialize();

    // bbmCall is a single-use component.  Create an instance and add
    // it to the application.
    let bbmCall = document.createElement('bbm-call');
    await window.customElements.whenDefined('bbm-call');

    // Associate the bbmCall component with the SDK and user manager we
    // created.
    bbmCall.setBbmSdk(sdk);
    bbmCall.setContactManager(userManager);

    // Customize the component.
    bbmCall.isFullScreen = true;
    bbmCall.isResizeAllowed = false;

    // When the call is finished, the CallEnded event is fired.
    bbmCall.addEventListener('CallEnded', () => {
      // The call has ended.  We can now clean up the dynamically added
      // component and close the popup window.
      document.body.removeChild(bbmCall);
      bbmCall= null;
      window.close();
    });

    // Add the component to the application.
    document.body.appendChild(bbmCall);

    // Place the call to the configured user ID once we have looked up their
    // regId.
    const identity = await sdk.getIdentitiesFromAppUserId(AGENT_USER_ID);
    bbmCall.makeCall(identity.regId, true);
  }
  catch (error) {
    alert(`ClickToCall encountered an error; error=${error}`);
  }
};
