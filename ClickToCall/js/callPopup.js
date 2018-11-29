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

(function() {

  let bbmeSdk = null;
  let contactsManager = null;
  let bbmCallWidget = null;
  let isSyncStarted = false;

  window.onload = async () => {
    try {
      bbmCallWidget = document.createElement('bbm-call');
      await window.customElements.whenDefined('bbm-call');
      await BBMEnterprise.validateBrowser();
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
        console.warn('Popup will be redirected to the authentication page.');
        return;
      }

      // Instantiate BBMEnterprise.
      bbmeSdk = new BBMEnterprise({
        domain: ID_PROVIDER_DOMAIN,
        environment: ID_PROVIDER_ENVIRONMENT,
        userId: authUserInfo.userId,
        getToken: authManager.getBbmSdkToken,
        description: navigator.userAgent,
        kmsArgonWasmUrl: KMS_ARGON_WASM_URL
      });

      // Handle changes of BBM Enterprise setup state.
      bbmeSdk.on('setupState', async state => {
        console.log(`ClickToCall: BBMEnterprise setup state: ${state.value}`);
        switch (state.value) {
          case BBMEnterprise.SetupState.Success: {
            // Setup was successful. Create user manager and initiate call.
            try {
              const userRegId = bbmeSdk.getRegistrationInfo().regId;
              contactsManager = await createUserManager(userRegId,
                authManager,
                bbmeSdk.getIdentitiesFromAppUserIds);
              await contactsManager.initialize();
              makeCall(CONTACT_REG_ID, true);
            }
            catch(error) {
              alert(`Failed to start call. Error: ${error}`);
              window.close();
            }
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
    }
    catch (error) {
      alert(`Failed to start call. Error: ${error}`);
    }
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

 }());
