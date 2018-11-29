![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Quick Start Sample for JavaScript

The Quick Start application demonstrates how you can authenticate with the
Spark Communications SDK using the [Identity
Provider](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/identityManagement.html)
of your application. We demonstrate how you can obtain the user ID and token of
your user's account and pass them to the SDK to complete setup.

<br>

<p align="center">
<a href="screenShots/quickstart.png"><img src="screenShots/quickstart.png" width="25%" height="25%"></a>
</p>


## Getting Started

This sample requires the Spark Communications SDK for JavaScript, which you can find along with related resources at the location below.
    
* Getting started with the [Spark Communications SDK](https://developers.blackberry.com/us/en/products/blackberry-spark-communications-platform.html)
* [Development Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/index.html)
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/index.html)

<p align="center">
    <a href="https://www.youtube.com/watch?v=LAbxok2EQtI"
      target="_blank"><img src="screenShots/bb-spark-web-sdk-getting-started.jpg"
      alt="YouTube Getting Started Video" width="486" height="" border="364"/></a>
</p>
<p align="center">
 <b>Getting started video</b>
</p>

### Prerequisites

Visit the [Getting Started with Web](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html) section to see the minimum requirements.

To use this example, you must set up the following elements in config.js:

- Oauth2 configuration (AUTH_CONFIGURATION)
- Your sandbox domain (ID_PROVIDER_DOMAIN)
- User passcode (USER_SECRET)

## Walkthrough

Follow this guide for a walkthrough showing how to authenticate with the SDK using Google Sign-in for Web.

- [Validate the browser](#validateBrowser)
- [Request an access token using the Google Sign-in API](#requestToken)
- [Initialize the SDK](#initialize)
- [Monitor the setup state](#monitorSetup)
- [Monitor for setup errors](#monitorSetupErrors)
- [Perform setup](#performSetup)


### <a name="validateBrowser"></a>Validate that the browser supports the SDK

To verify that the browser has all required features, call BBMEnterprise.validateBrowser. It will return a rejected Promise in browsers which support Promise. If Promise is not supported, it will throw an exception.

```javascript
// Validate that the browser is supported.
try {
  BBMEnterprise.validateBrowser()
  .catch(reportBrowserError);
} catch(error) {
  reportBrowserError(error);
}

```

### <a name="requestToken"></a>Request an access token using the Google Sign-in API

To retrieve an access token, first update OAUTH_CONFIGURATION.clientId in config.js using web.clientId obtained from the **google-services.json**.

Next, obtain an object which performs generic oAuth authentication. Use it to obtain an access token and user information. Perform these actions when the 'Sign in' button is pressed.

```javascript
$('#signInButton').click(LogIn);
function LogIn() {
  // Create the auth manager for the configured auth service.
  const authManager = createAuthManager();
  authManager.authenticate()
  .then(userData => {
    //  ... Initialize the SDK here ...
  }
}

```

### <a name="initialize"></a>Initialize the SDK

```javascript
// Construct BBMEnterprise object.
const bbmeSdk = new BBMEnterprise({
  domain: ID_PROVIDER_DOMAIN,
  environment: ID_PROVIDER_ENVIRONMENT,
  userId: userData.userId,
  getToken: authManager.getBbmSdkToken,
  description: navigator.userAgent,
  kmsArgonWasmUrl: KMS_ARGON_WASM_URL
});
```

### <a name="monitorSetup"></a>Monitor the setup state

When the setup state change, a 'setupState' event will be emitted.
Listen for this to determine when the setup state changes.

```javascript
// Handle changes of BBM Enterprise setup state.
bbmeSdk.on('setupState', state => {
  console.log(`BBMEnterprise setup state: ${state.value}`);
  $('#setupState').text(state.value);
  switch (state.value) {
    case BBMEnterprise.SetupState.Success:
      const userRegId = bbmeSdk.getRegistrationInfo().regId;
      $('#regId').text(userRegId || '?');
    break;
    case BBMEnterprise.SetupState.SyncRequired: {
      if (isSyncStarted) {
        $('#setupState').text(`Failed to get user keys using provided USER_SECRET`);
        return;
      }
      const isNew = bbmeSdk.syncPasscodeState === BBMEnterprise.SyncPasscodeState.New;
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
```

### <a name="monitorSetupErrors"></a>Monitor for setup errors

The instance of BBMEnterprise will emit 'setupError' event on setup failure.

```javascript
// Handle setup error.
bbmeSdk.on('setupError', error => {
  // Notify user about the error.
  $('#setupState').text(`Failed to setup the SDK | Error: ${error.value}`);
});
```

### <a name="performSetup"></a>Perform setup
All that is left now is to set up the SDK.

```javascript
  // Start BBM Enterprise setup.
  bbmeSdk.setupStart();
```

## License

These samples are released as Open Source and licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find a issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-javascript-samples/issues).
