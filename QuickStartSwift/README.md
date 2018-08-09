![BlackBerry Spark Communications Platform](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# QuickStartSwift for iOS

The QuickStartSwift sample application demonstrates how you can authenticate with Spark
using the [Identity Provider](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/identityManagement.html) 
of your application.  We demonstrate how you can obtain the user ID and token of your user's account 
and pass them to the Spark SDK to complete setup.  For integrating the SDK into an Objective-C based app, see [Quick Start](../QuickStart/README.md)

### Features

This sample allows you to do the following:

* Start the BBM Enterprise Service
* Authenticate a user via GoogleSignIn

## Getting Started

This sample requires the Spark SDK, which you can find along with related resources at the location below.
    
* Getting started with the [Spark SDK](https://developers.blackberry.com/us/en/products/blackberry-bbm-enterprise-sdk.html)
* [Development Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/index.html)
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/ios/index.html)

<p align="center"> 
    <a href="http://www.youtube.com/watch?feature=player_embedded&v=H1yiHSGsAIg"
      target="_blank"><img src="../QuickStart/screenShots/bbme-sdk-ios-getting-started.jpg" 
      alt="YouTube Getting Started Video" width="486" height="" border="364"/></a>
</p>
<p align="center">
 <b>Getting started video</b>
</p>


### Configuration

* [Sample application configuration using Google Sign-In and Firebase](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/googleSignInForiOSExamples.html)

## Walkthrough 

- [Support Library](#supportLibrary)
- [Account Controller](#accountController)
- [BBM Auth Controller](#bbmAuthController)
- [Observable Montior](#observableMonitor)
- [BBM Token Manager](#bbmTokenManager)

### <a name-"supportLibrary"></a>Support Library

Common code for authenticating users, synchronizing keys and performing common tasks can be found in /examples/Support/Source.  Much of the heavy lifting can be found in these classes and they should be referenced before implementing a custom key management or authentication scheme.

*BBMAuthController* and *BBMKeyManager* can be instantiated with classes/instances that allow you to substitute your own user authentication or key management scheme while handling all of the Spark related functionality.

*BBMAccess* provides wrappers around common outgoing messages and the SDK data model.

You may use, extend, or modify this code as needed for your own application.


### <a name="accountController"></a>AccountViewController

*AccountViewController* is the primary entry point for the application.

To observe connectivity changes to the service we first add our instance as a connectivity listener to the service:
```swift
BBMEnterpriseService.shared().add(self)
```
And implement the delegate method to update the UI when connectivity changes:
```swift
public func connectivityStateChange(_ connected: Bool, strict connectedStrict: Bool) {
  serviceConnectivityLabel.text = connected ? "Connected" : "Disconnected";
}
```

We then create an instance of a *BBMAuthController*, set ourselves as the rootController (which provides a UI context for pushing auth related modal UI) and add ourselves as a delegate:
```swift
//In QuckStartApp.swift
private let _authController : BBMAuthController = BBMAuthController(tokenManager:BBMGoogleTokenManager.self,
                                                                          domain:SDK_SERVICE_DOMAIN,
                                                                     environment:kBBMConfig_Sandbox)


//In App Delegate.swift
QuickStartApp.app().authController().rootController = self

//Auth controller using observable monitors
serviceMonitor = ObservableMonitor(name:"ServiceStateMonitor") {
    [weak self]
    () -> Void in

    if let weakSelf = self {
        //serviceStarted and authState are observable properties.  This means this block
        //of code will be called each time the monitor activates, and each time either
        //of these property values change
        weakSelf.serviceStateChanged(QuickStartApp.app().authController().serviceStarted)
        weakSelf.authStateChanged(QuickStartApp.app().authController().authState)
    }
}

```

Once we have an authController instance we can use it to start the service and and sign in if we have cached credentials:
```swift
//Start the service.
QuickStartApp.app().authController().startBBMEnterpriseService()

//Resume our previous session
QuickStartApp.app().authController().signInSilently()
```

*BBMAuthControllerDelegate* has the properties serviceStarted and authState which can be monitored using an *ObservableMonitor*.  See the [Simple Chat](../SimpleChat/README.md) for a more detailed example on how to use *ObservableMonitor*s to monitor credential changes.

```swift
//The AuthController will invoke this callback any time the user credentials/auth state changes
//via the serviceMonitor created above
func authStateChanged(_ authState: BBMAuthState) {
    //Update our UI
}

func serviceStateChanged(_ serviceStarted : Bool) {
    //Update our UI
}
```

### <a name="bbmAuthController"></a>BBMAuthController

*BBMAuthController* handles the details of starting the service and retrieving and setting user tokens.

The actual fetching of the tokens is handled by an id&lt;BBMTokenManager&gt; class.  This sample uses BBMGoogleTokenManager which implements the BBMTokenManager protocol and updates tokens via the GoogleSignIn API.  You may substitue any class that implements the BBMTokenManager protocol in initWithTokenManager: if you wish to use a different oAuth provider.

To start the service call:
```objective-c
[[BBMEnterpriseService service] start:SDK_SERVICE_DOMAIN environment:SDK_ENVIROMENT completionBlock:^(BOOL success) {
    self.serviceStarted = success;
    //Inform our delegates (AccountViewController) that the service state has changed
}];
```

The primary purpose of *BBMAuthController* is to respond to delegate callbacks from the *BBMTokenManager* instance.  These happen via two delegate callbacks defined in *BBMAuthenticationDelegate.h* and in turn update the UI via the *BBMAuthControllerDelegate* methods and/or via monitored changes to the observable authState and serviceState properties:
```objective-c
- (void)localAuthDataChanged:(BBMAuthenticatedAccount *)accountData error:(NSError *)error
{
    //Handle changes in the auth state.  This method will be called whenever the authenticated
    //account information changes.
    if(error) {
        NSLog(@"Sign in error %@", error.localizedDescription);
    }

    //No account data means we have logged out.
    if(nil == accountData) {
        BBMAuthState *authState = [BBMAuthState emptyAuthState];
        self.authState = authState;
        return;
    }

    SetupStateBlock callback = ^(NSString *authTokenState, NSString *setupState, NSNumber *regId) {
        BBMAuthState *authState = [[BBMAuthState alloc] init];
        authState.account = accountData;
        authState.authTokenState = authTokenState;
        authState.setupState = setupState;

        authState.regId = regId;
        self.authState = authState;

        [self registerLocalUser];
    };

    //Send the access token and userId to the BBM service.  The supplied callback will be invoked
    //with the resulting tokenState, setupState and registrationId for the local user.  The callback
    //may be invoked several times as the setup progresses through the various states.  States such
    //as "deviceSwitch" may require user interaction.
    [[BBMEnterpriseService service] sendAuthToken:accountData.accessToken 
                                   userIdentifier:accountData.accountId
                                  setupStateBlock:callback];
}

- (UIViewController *)authController
{
    //Provide the id<BBMTokenManager> with a root controller for presenting a modal sign in window.
    return self.rootController;
}
```

The critical part here is updating the tokens for the service.  This is done via:
```objective-c
[[BBMEnterpriseService service] sendAuthToken:accountData.accessToken
                                  forUserName:accountData.accountId
                              setupStateBlock:callback];
```
The callback will be invoked multiple times as the setupState and authState of the user changes with some states such as *DeviceSwitchRequired* requiring user interaction.

#### <a name="observableMonitor"></a>Note on ObservableMontitor

To make a property on a class observable via an ObservableMonitor you must call ObservableTracker getterCalledForObject:propertyName: from the property setter.  Usage of ObservableMonitors is entirely optional, but is implemented for all properties on SDK model objects and can be leveraged in your code in lieu of directly using KVO.  See [Rich Chat](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/ios/RichChat/README.html) or [Simple Chat](../SimpleChat/README.md) for examples.  ObservableMonitors and observable properties are not thread safe.  See the [ObservableMonitor](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/ios/interface_observable_monitor.html) documentation for additional details.
```objective-c
- (BBMAuthState *)authState
{
    //propertyName must match the actual property name.
    [ObservableTracker getterCalledForObject:self propertyName:@"authState"];
    return _authState;
}
```

### <a name="bbmTokenManager"></a>BBMTokenManager

localAuthDataChanged:account:error is called from a BBMTokenManager instance when the credentials change.  This is implemented in BBMGoogleTokenManager for the GoogleSignIn API as follows:
```objective-c
- (void)googleSignInStateChanged:(NSError *)error
{
    GIDGoogleUser *user = [GIDSignIn sharedInstance].currentUser;

    if(nil == user) {
        [self.delegate localAuthDataChanged:nil error:error];
        return;
    }

    //name, email and avatarURL are optional.  userID is implementation dependent.
    BBMAuthenticatedAccount *accountData = [BBMAuthenticatedAccount accountDataWithId:user.userID
                                                                                idToken:user.authentication.idToken
                                                                          accessToken:user.authentication.accessToken
                                                                                 name:user.profile.name
                                                                                email:user.profile.email
                                                                            avatarURL:[user.profile imageURLWithDimension:120]];

    [self.delegate localAuthDataChanged:accountData error:error];
}
```


## License

These samples are released as Open Source and licensed under the
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html). 

These samples were created using SDKs from Apple Inc. and may contain code
licensed for use only with Apple products. Please review your Apple SDK
Agreement for additional details.

This page includes icons from: https://material.io/icons/ used under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find a issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-ios-samples/issues).

