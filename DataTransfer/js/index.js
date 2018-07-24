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

window.onload = () => {
  const dataTransferElement = document.querySelector('data-transfer-element');
  window.customElements.whenDefined(dataTransferElement.localName).then(() => {
    initBbme()
    .then(bbmeSdk => {
      dataTransferElement.setBbmSdk(bbmeSdk);
    })
    .catch(error => {
      alert(error);
    });
  });
};

// Function initializes BBM Enterprise SDK for JavaScript.
function initBbme() {
  return new Promise((resolve, reject) => {
    let isSyncStarted = false;
    const authManager = createAuthManager();
    authManager.authenticate()
    .then(authUserInfo => {
      const bbmeSdk = new BBMEnterprise({
        domain: ID_PROVIDER_DOMAIN,
        environment: ID_PROVIDER_ENVIRONMENT,
        userId: authUserInfo.userId,
        getToken: authManager.getBbmSdkToken,
        description: navigator.userAgent,
        messageStorageFactory: BBMEnterprise.StorageFactory.SpliceWatcher,
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
              reject(new Error('Failed to get user keys using provided USER_SECRET'));
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
    });
  });
}
