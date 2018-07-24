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
 * @class CallPopup
 * @memberof Examples
 */

var bbmeSdk = null;
var contactsManager = null;
var bbmCallWidget = null;
var isSyncStarted = false;

window.onload = () => {
  bbmCallWidget = document.createElement('bbm-call');
  window.customElements.whenDefined('bbm-call')
  .then(() => {
    const authManager = createAuthManager();
    authManager.authenticate().then(authUserInfo => {
      // Instantiate BBMEnterprise.
      bbmeSdk = new BBMEnterprise({
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
        console.log(`ClickToCall: BBMEnterprise setup state: ${state.value}`);
        switch (state.value) {
          case BBMEnterprise.SetupState.Success: {
            // Setup was successful. Create user manager and initiate call.
            const userRegId = bbmeSdk.getRegistrationInfo().regId;
            createUserManager(userRegId, authManager,
              bbmeSdk.getIdentitiesFromAppUserId,
              bbmeSdk.getIdentitiesFromAppUserIds)
              .then(userManager => {
                contactsManager = userManager;
                contactsManager.initialize()
                .then(() => {
                  makeCall(CONTACT_REG_ID, true);
                });
              });
          }
          break;
          case BBMEnterprise.SetupState.SyncRequired: {
            if (isSyncStarted) {
              alert('Failed to get user keys using provided USER_SECRET.');
              window.close();
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
        alert(`BBM Enterprise registration failed: ${error.value}`);
      });

      // Start BBM Enterprise setup.
      bbmeSdk.setupStart();
    });
  });
};

// Function makes call to the specified contact.
function makeCall(regId, isVideo) {
  document.body.appendChild(bbmCallWidget);
  bbmCallWidget.isFullScreen = true;
  bbmCallWidget.isResizeAllowed = false;
  bbmCallWidget.setContactManager(contactsManager);
  bbmCallWidget.setBbmSdk(bbmeSdk);
  bbmCallWidget.makeCall(regId, isVideo);
  bbmCallWidget.addEventListener('CallEnded', () => {
    document.body.removeChild(bbmCallWidget);
    bbmCallWidget = null;
    window.close();
  });
}
