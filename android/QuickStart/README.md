![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Quick Start for Android

The Quick Start example application demonstrates how you can authenticate the BlackBerry Spark
Communications Services with user authentication disabled while using the BlackBerry
Key Management Service.

<br>

<p align="center">
<a href="screenShots/quickstart.png"><img src="screenShots/quickstart.png" width="25%" height="25%"></a>
</p>

## Getting Started

This example requires the Spark Communications SDK, which you can find along with related resources at the locations below.

* Instructions to
[Download and Configure](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html)
the SDK.
* [Android Getting Started](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-android.html)
instructions in the Developer Guide.
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/index.html)

<p align="center">
    <a href="https://youtu.be/3OOJMNF7sjs"
      target="_blank"><img src="screenShots/bbme-sdk-android-getting-started.jpg"
      alt="YouTube Getting Started Video" width="486" height="" border="364"/></a>
</p>
<p align="center">
 <b>Getting started video</b>
</p>

By default, this example application is configured to work in a domain with user
authentication disabled and the BlackBerry Key Management Service enabled.
See the [Download & Configure](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html)
section of the Developer Guide to get started configuring a
[domain](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/faq.html#domain)
in the [sandbox](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/faq.html#sandbox).

Once you have a domain in the sandbox, edit Quick Start's `app.properties` file
to configure the example with your domain ID.

```
# Your Spark Domain ID
user_domain="your_spark_domain"
```

When you run Quick Start, it will prompt you for a user ID and a password. Since you've configured your domain to have user authentication disabled, you can enter any string you like for the user ID and an SDK identity will be created for it. Other applications that you run in the same domain will be able to find this identity by this user ID. The password is used to protected the keys stored in the [BlackBerry Key Management Service](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/security.html).

Notes:

* To complete a release build you must create your own signing key. To create your own signing key, visit https://developer.android.com/studio/publish/app-signing.html.
  * After creating your signing key set the key store password, key password, key alias and path to the keystore file in the 'app.properties' file.
* This application has been built using gradle 4.2.1 (newer versions have not been validated).

## Walkthrough

Follow this guide for a walkthrough showing how to set up and authenticate with
the Spark Communications SDK.

* [Initialize the SDK](#initialize)
* [Observe the SDK State](#observe)
* [Generate an authentication token](#generateToken)
* [Monitor the GlobalAuthTokenState](#monitorAuthState)
* [Monitor the GlobalSetupState](#monitorSetup)
* [Monitor for setup errors](#monitorErrors)
* [Observe the local user](#observeUser)
* [Observe the GlobalSyncPasscodeState](#observeGlobalSyncPasscodeState)

### <a name="initialize"></a>Initialize the Spark SDK

Before starting the SDK you must [`initialize()`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/BBMEnterprise.html#initialize-Context-) it with an Android [Context](https://developer.android.com/reference/android/content/Context.html).

```java
// get the service
mBbmEnterprise = BBMEnterprise.getInstance();
mBbmEnterprise.initialize(this);

//Start the SDK
final boolean startSuccessful = mBbmEnterprise.start();
if (!startSuccessful) {
    //implies BBMEnterprise was already started.  Call stop before trying to start again
    Toast.makeText(SetupActivity.this, "Service already started.", Toast.LENGTH_LONG).show();
}
```

### <a name="observe"></a>Observe the SDK state

Your application needs to monitor the state of the SDK and enable the sign-in button only when the SDK is *STARTED*. To do this, you add an [observer](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/reactive/Observer.html) to the [BBMEnterpriseState](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/service/BBMEnterpriseState.html).

***Tip: observers are weakly referenced by [ObservableValues](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/reactive/ObservableValue.html), you must maintain a hard reference to an observer to receive change notifications.***

```java
mBbmEnterpriseState = mBbmEnterprise.getState();
mBbmEnterpriseState.addObserver(mEnterpriseStateObserver);
```

```java
//Create an observer to monitor the BBM Enterprise SDK state
private final Observer mEnterpriseStateObserver = new Observer() {
    @Override
    public void changed() {
        final BBMEnterpriseState bbmEnterpriseState = mBbmEnterpriseState.get();
        mEnterpriseStateView.setText(bbmEnterpriseState.toString());
        switch (bbmEnterpriseState) {
            case STARTING:
            case STARTED:
                updateServiceButtonState(false);
                break;
            case STOPPED:
                mSetupStateView.setText("");
                mAuthTokenStateView.setText("");
                mLocalPinView.setText("");
            case FAILED:
            default:
                updateServiceButtonState(true);
                break;
        }
    }
};
```



### <a name="generateToken"></a>Generate an authentication token

To simplify authentication the Spark sandbox servers support authentication with unsigned JWT tokens. Our generated tokens header algorithm ("alg") parameter is set to "none". The token id is randomly generated, the userId is hardcoded value for simplicity.

```java
//User Id is hard-coded for convenience here
String userId = "sampleSparkUserId";

JSONObject header = new JSONObject();
header.put("alg", "none");

SecureRandom rand = new SecureRandom();
byte[] bytes = new byte[128];
//Get some random bytes
rand.nextBytes(bytes);
//Use the first 18 characters as the token id
String jti = Base64.encodeToString(bytes, base64Flags).substring(0, 18);

JSONObject body = new JSONObject();
body.put("jti", jti);
body.put("sub", userId);
body.put("iat", System.currentTimeMillis() / 1000);
//Expires in one hour.
body.put("exp", System.currentTimeMillis() / 1000 + 60 * 60);

String base64Header = Base64.encodeToString(header.toString().getBytes(), base64Flags);
String base64Body = Base64.encodeToString(body.toString().getBytes(), base64Flags);

token = base64Header + '.' + base64Body + '.';
```

You can send the token to the SDK with the [`AuthToken`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/outbound/AuthToken) message.

```java
AuthToken authToken = new AuthToken(token, userId);
mBbmEnterprise.getBbmdsProtocol().send(authToken);
```



### <a name="monitorAuthState"></a>Monitor the `GlobalAuthTokenState`

The `GlobalAuthTokenState` indicates when the SDK requires an [`AuthToken`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/outbound/AuthToken.html). When the `GlobalAuthTokenState` is *Needed* you must provide an `AuthToken` to maintain authorization.

**Note: `AuthToken` expires, so you need to make sure your application is always monitoring the `GlobalAuthTokenState`, and refreshing the AuthToken when needed.**
```java
mAuthTokenState = protocol.getGlobalAuthTokenState();
mAuthTokenState.addObserver(mAuthTokenStateObserver);
```
```java
//Create an observer to monitor the auth token state
private final Observer mAuthTokenStateObserver = new Observer() {

    @Override
    public void changed() {
        final GlobalAuthTokenState authTokenState = mAuthTokenState.get();
        mAuthTokenStateView.setText(authTokenState.value.toString());

        if (authTokenState.getExists() != Existence.YES) {
            return;
        }

        switch (authTokenState.value) {
            case Ok:
                break;
            case Needed:
                //Generate an unsigned token to authenticate with the spark servers
                generateAuthToken();
                break;
            case Rejected:
                break;
            case Unspecified:
                break;
        }
    }
};
```

### <a name="monitorSetup"></a>Monitor the `GlobalSetupState`

The [`GlobalSetupState`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/GlobalSetupState.html) indicates the state of the SDK setup process. When the setup state is *NotRequested* we will register the local device as a new [Endpoint](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference//android/com/bbm/sdk/bbmds/inbound/Endpoints.html). When the `GlobalSetupState` is Ongoing the progressMessage will indicate the current phase of the setup.

```java
final BbmdsProtocol protocol = mBbmEnterprise.getBbmdsProtocol();
//fetch setup related globals for monitoring
mSetupState = protocol.getGlobalSetupState();
mSetupState.addObserver(mSetupStateObserver);
```
```java
//Create an observer to monitor the setup state global
private final Observer mSetupStateObserver = new Observer() {
    @Override
    public void changed() {
        final GlobalSetupState setupState = mSetupState.get();
        mSetupStateView.setText(setupState.state.toString());

        if (setupState.getExists() != Existence.YES) {
            return;
        }

        switch (setupState.state) {
            case NotRequested:
                //Register this device as a new endpoint
                registerDevice();
                break;
            case Full:
                //Handle the case where this account has reached the maximum number of registered endpoints
                handleFullState();
                break;
            case Ongoing:
                //Ongoing has additional information in the progressMessage
                mSetupStateView.setText(setupState.state.toString() + ":" + setupState.progressMessage.toString());
                break;
            case SyncRequired:
                //SyncRequired state is processed by the syncPasscodeStateObserver
            case Success:
                //Setup completed
                break;
            case Unspecified:
                break;
        }
    }
};
```



### <a name="monitorErrors"></a>Monitor for setup errors

The SDK will send a [SetupError](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/inbound/SetupError.html) message if an error occured during setup. To listen to for a SetupError we are creating an [InboundMessageObservable](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/service/InboundMessageObservable.html) of type SetupError. The InboundMessageObservable can be used to observe any [inbound messages](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/internal/InboundMessage.html) from the SDK.

```java
//Create an observable to listen for SetupError messages
mSetupErrorObservable = new InboundMessageObservable<>(
        new SetupError(),
        mBbmEnterprise.getBbmdsProtocolConnector()
);
mSetupErrorObservable.addObserver(mSetupErrorObserver);
```
```java
private final Observer mSetupErrorObserver = new Observer() {
    @Override
    public void changed() {
        SetupError setupError = mSetupErrorObservable.get();
        mSetupErrorContainer.setVisibility(View.VISIBLE);
        mSetupErrorView.setText(setupError.error.toString());
    }
};
```

### <a name="observeGlobalSyncPasscodeState"></a>Observe the `GlobalSyncPasscodeState`

To complete setup when using the BlackBerry Key Management System we also need to provide a passcode to the SDK. The passcode could be obtained from the user or provided by the app. To recover existing security keys the app must be able to provide a previously set passcode.

When the [`GlobalSetupState`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/GlobalSetupState.html)  is *SyncRequired* the SDK requires a passcode to be provided to complete setup. The [`GlobalSyncPasscodeState`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/GlobalSyncPasscodeState.html) should be checked to determine if we should provide a new passcode, or if existing security keys exist that could be recovered by providing the existing passcode. When the `GlobalSyncPasscodeState` value is 'New' there are no security keys stored in the BlackBerry KMS for this user. When the value is 'Existing' there are security keys that can be recovered by providing the passcode previously used. To complete setup we send a [`SyncStart`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/outbound/SyncStart.html) message.

```java
//Observes the GlobalSetupState and the GlobalSyncPasscodeState.
//When the required a passcode is sent to complete setup using the 'SyncStart' message.
private Observer mSyncPasscodeStateObserver = new Observer() {
    @Override
    public void changed() {
        GlobalSetupState setupState = mSetupState.get();
        //When the GlobalSetupState is 'SyncRequired' then send the passcode to the SDK to continue setup
        if (setupState.state == GlobalSetupState.State.SyncRequired) {
            GlobalSyncPasscodeState syncPasscodeState = mSyncPasscodeState.get();
            //For simplicity, this example hard codes a passcode.
            //A passcode obtained from a user is a more secure solution.
            SyncStart syncStart = new SyncStart("user-passcode");
            switch (syncPasscodeState.value) {
                case New:
                    //No existing keys were found, so send the SyncStart with action 'New'
                    syncStart.action(SyncStart.Action.New);
                    BBMEnterprise.getInstance().getBbmdsProtocol().send(syncStart);
                    break;
                case Existing:
                    //Existing keys stored in KMS were found, so send the SyncStart with action 'Existing'
                    syncStart.action(SyncStart.Action.Existing);
                    BBMEnterprise.getInstance().getBbmdsProtocol().send(syncStart);
                    break;
                default:
                    //No action
            }
        }
    }
};
```
***Tip: Listen for the [`SyncError`](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/inbound/SyncError.html) message to be notified if the passcode sync failed.***

Visit the [Security](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/security.html) guide for more information about the use of security keys in the SDK. Visit the Cloud Key Storage](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/cloudKeyStorage.html) guide for information on how your application can manage key storage.

### <a name="observeUser"></a>Observe the local user

The QuickStart example also demonstrates how to retreive the regId of the local user. The regId is the unique identifier for every user in the SDK. It's used to start chats and voice and video calls. To get the local [User](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/User.html), you first need to find the local user's uri. The uri of the local user is provided by the [GlobalLocalUri](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/GlobalLocalUri.html).

```java
mLocalUri = protocol.getGlobalLocalUri();
mLocalUri.addObserver(mLocalRegIdObserver);
```

Some Observable objects also implement the
[JSONConstructable](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/internal/JsonConstructable.html)
interface. Objects that implement the JSONConstructable interface can be queried
for their
[Existence](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/android/com/bbm/sdk/bbmds/internal/Existence.html). If
the Existence is *MAYBE*, then the value is still being asynchronously fetched
from the SDK. Your observer first checks to confirm that the GlobalLocalUri
exists before we request the local user using the value. Once we have retrieved
the local user we populate the text view with the regId value.

```java
private final Observer mLocalRegIdObserver = new Observer() {
    @Override
    public void changed() {
        //Check if the local uri is populated
        if (mLocalUri.get().getExists() == Existence.YES) {
            final String localUserUri = mLocalUri.get().value;
            //Check if the user is populated
            ObservableValue<User> user = mBbmEnterprise.getBbmdsProtocol().getUser(localUserUri);
            if (user.get().getExists() == Existence.MAYBE) {
                user.addObserver(this);
            } else {
                //Set the text view to the regId of the local user
                mLocalRegIdView.setText(Long.toString(user.get().regId));
            }
        }
    }
};
```

## License

These samples are released as Open Source and licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the [Creative Commons 3.0 Attribution License](https://creativecommons.org/licenses/by/3.0/).

This page includes icons from: https://material.io/icons/ used under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find an issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-android-samples/issues).
