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
    const dataTransferElement = document.querySelector('data-transfer-element');
    await window.customElements.whenDefined(dataTransferElement.localName);
    await BBMEnterprise.validateBrowser();
    const bbmeSdk = await initBbme();
    dataTransferElement.setBbmSdk(bbmeSdk);
  }
  catch(error) {
    alert(`Failed to start application. Error: ${error}`);
  }
};

// Function initializes BBM Enterprise SDK for JavaScript.
function initBbme() {
  return new Promise(async (resolve, reject) => {
    try {
      let isSyncStarted = false;
      const authManager = new AuthenticationManager(AUTH_CONFIGURATION);
      // Override getUserId() used by the MockAuthManager.
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

      const authUserInfo = await authManager.authenticate();
      if (!authUserInfo) {
        console.warn('Application will be redirected to the '
          + 'authentication page');
        return;
      }

      const bbmeSdk = new BBMEnterprise({
        domain: ID_PROVIDER_DOMAIN,
        environment: ID_PROVIDER_ENVIRONMENT,
        userId: authUserInfo.userId,
        getToken: authManager.getBbmSdkToken,
        description: navigator.userAgent,
        kmsArgonWasmUrl: KMS_ARGON_WASM_URL
      });

      // Handle changes of BBM Enterprise setup state.
      bbmeSdk.on('setupState', state => {
        console.log(`BBMEnterprise setup state: ${state.value}`);
        switch (state.value) {
          case BBMEnterprise.SetupState.Success:
            resolve(bbmeSdk);
          return;
          case BBMEnterprise.SetupState.SyncRequired: {
            if (isSyncStarted) {
              reject(new Error('Failed to get user keys using provided '
                + 'USER_SECRET'));
              return;
            }
            const isNew =
              bbmeSdk.syncPasscodeState === BBMEnterprise.SyncPasscodeState.New;
            const syncAction = isNew
              ? BBMEnterprise.SyncStartAction.New
              : BBMEnterprise.SyncStartAction.Existing;
            bbmeSdk.syncStart(USER_SECRET, syncAction);
          }
          break;
          case BBMEnterprise.SetupState.SyncStarted:
            isSyncStarted = true;
          break;
        }
      });

      // Handle setup error.
      bbmeSdk.on('setupError', error => {
        reject(error.value);
      });

      bbmeSdk.setupStart();
    }
    catch(error) {
      alert(`Failed to start application. Error: ${error}`);
    }
  });
}
