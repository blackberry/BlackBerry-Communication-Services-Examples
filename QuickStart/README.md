![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# QuickStart for iOS (objective-c)

The QuickStart sample application demonstrates the objective-c implementation for 
authenticating with Spark Communications using the [Identity Provider](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/identityManagement.html) 
of your application. We demonstrate how you can obtain the user ID and token of your user's account 
and pass them to the SDK to complete setup.  For integrating the SDK into a Swift app, see [Quick Start Swift](../QuickStartSwift/README.md) 

### Features

This sample allows you to do the following:

* Start the BBM Enterprise Service
* Authenticate a user via GoogleSignIn

## Getting Started

This sample requires the Spark Communications SDK, which you can find along with related resources at the location below.
    
* Getting started with the [Spark Communications SDK](https://developers.blackberry.com/us/en/products/blackberry-bbm-enterprise-sdk.html)
* [Development Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/index.html)
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/ios/index.html)

<p align="center">
    <a href="http://www.youtube.com/watch?feature=player_embedded&v=H1yiHSGsAIg"
      target="_blank"><img src="screenShots/bbme-sdk-ios-getting-started.jpg" 
      alt="YouTube Getting Started Video" width="486" height="" border="364"/></a>
</p>
<p align="center">
 <b>Getting started video</b>
</p>

### Configuration

This sample application is pre-configured to use simple unvalidated user authentication and the BlackBerry Key Management Service.  This allows you to get up and running quickly with minimal setup.

[Create your application](https://account.good.com/#/a/organization//applications/add) and configure a sandbox domain, with settings to use no identity provider and using the BlackBerry Key Management Service. 

Once your sandbox domain is configured, edit the ConfigSettings.plist file and enter the domain identifier under "testAuth/domain".  Signing-in will require you to enter a unique user identifier (such as a name or email) and a password for the BlackBerry Key Management Service.  

This sample application may also be configured to use Google Sign-In or Azure Active Directory:
* [Sample application configuration using Google Sign-In](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/googleSignInForiOSExamples.html)
* [Sample application configuration using Azure Active Directory](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/azureForiOSExamples.html)


## Walkthrough 

- [Support Library](#supportLibrary)
- [Account Controller](#accountController)
- [BBM Auth Controller](#bbmAuthController)
- [Observable Montior](#observableMonitor)
- [BBM Token Manager](#bbmTokenManager)

### <a name="supportLibrary"></a>Support Library

Common code for authenticating users, synchronizing keys and performing common tasks can be found in /examples/Support/Source.  Much of the heavy lifting can be found in these classes and they should be referenced before implementing a custom key management or authentication scheme.

*BBMAuthController* and *BBMKeyManager* can be instantiated with classes/instances that allow you to substitute your own user authentication or key management scheme while handling all of the SDK related functionality.

*BBMAccess* provides wrappers around common outgoing messages and the SDK data model.

You may use, extend, or modify this code as needed for your own application.


### <a name="accountController"></a>AccountViewController

*AccountViewController* is the primary entry point for the application.

To observe connectivity changes to the BBM Enterprise Service we first add our instance as a connectivity listener to the service:
```objective-c
[[BBMEnterpriseService service] addConnectivityListener:self];
```
And implement the delegate method to update the UI when connectivity changes:
```objective-c
- (void)connectivityStateChange:(BOOL)connected strict:(BOOL)connectedStrict
{
    self.platformConnectivityLabel.text = connected ? @"Connected" : @"Disconnected";
}
```

We then create an instance of a *BBMAuthController*, set ourselves as the rootController (which provides a UI context for pushing auth related modal UI) and add ourselves as a delegate:
```objective-c
Class tokenManager = [BBMGoogleTokenManager class];
//Alternatively: Class tokenManager = [BBMAzureTokenManager class];

self.authController = [[BBMAuthController alloc] initWithTokenManager:tokenManager
                                                           userSource:nil
                                                   keyStorageProvider:nil
                                                               domain:[BBMConfigManager defaultManager].sdkServiceDomain
                                                          environment:[BBMConfigManager defaultManager].environment];

self.authController.rootController = self;

//Auth controller using delegation
[self.authController addDelegate:self];
```

Once we have an authController instance we can use it to start the service and and sign in if we have cached credentials:
```objective-c
//Start the service.
[self.authController startBBMEnterpriseService];

//Resume our previous session
[self.authController signInSilently];
```

*BBMAuthControllerDelegate* calls back via two delegate methods where we update the user interface to show the auth state.  You may also use an *ObservableMonitor* from anywhere in you code to monitor the credentials and service state.  See the [Simple Chat](../SimpleChat/README.html) for a more detailed example on how to use *ObservableMonitor*s to monitor credential changes.

```objective-c
//The AuthController will invoke this callback any time the user credentials/auth state changes
- (void)authStateChanged:(BBMAuthState *)authState
{
    //Update our UI
}

- (void)serviceStateChanged:(BOOL)serviceStarted
{
    //Update our UI
}
```

### <a name="bbmAuthController"></a>BBMAuthController

*BBMAuthController* handles the details of starting the BBM Enterprise service and retrieving and setting user tokens.

The actual fetching of the tokens is handled by an id&lt;BBMTokenManager&gt; class.   You may substitue any class that implements the BBMTokenManager protocol in initWithTokenManager: if you wish to use a different oAuth provider.

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
        //This property is observable so changing it here will trigger any ObservableMonitors that dereference it.
        self.authState = authState;
        for(id<BBMAuthControllerDelegate> delegate in self.delegates) {
            if([delegate respondsToSelector:@selector(authStateChanged:)]) {
                [delegate authStateChanged:self.authState];
            }
        }
        return;
    }

    SetupStateBlock callback = ^(NSString *authTokenState, NSString *setupState, NSNumber *regId) {
        BBMAuthState *authState = [[BBMAuthState alloc] init];
        authState.account = accountData;
        authState.authTokenState = authTokenState;
        authState.setupState = setupState;
        authState.regId = regId;
        self.authState = authState;
        for(id<BBMAuthControllerDelegate> delegate in self.delegates) {
            if([delegate respondsToSelector:@selector(authStateChanged:)]) {
                [delegate authStateChanged:self.authState];
            }
        }
    };

    [[BBMEnterpriseService service] sendAuthToken:accountData.accessToken
                                      forUserName:accountData.accountId
                                  setupStateBlock:callback];
}

- (UIViewController *)authController
{
    //Provide the id<BBMTokenManager> with a root controller for presenting a modal sign in window.
    return self.rootController;
}
```

The critical part here is updating the service authentication tokens.  This is done via:
```objective-c
[[BBMEnterpriseService service] sendAuthToken:accountData.accessToken
                                  forUserName:accountData.accountId
                              setupStateBlock:callback];
```
The callback will be invoked multiple times as the setupState and authState of the user changes with some states such as *DeviceSwitchRequired* requiring user interaction.

#### <a name="observableMonitor"></a>Note on ObservableMontitor

To make a property on a class observable via an ObservableMonitor you must call ObservableTracker getterCalledForObject:propertyName: from the property setter.  Usage of ObservableMonitors 
is entirely optional, but is implemented for all properties on SDK model objects and can be leveraged in your code in lieu of directly using KVO.  See [Rich Chat](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/ios/RichChat/README.html) or [Simple Chat](../SimpleChat/README.md) for examples.  ObservableMonitors and observable properties are not thread safe.  See the [ObservableMonitor](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/ios/interface_observable_monitor.html) documentation for additional details.
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

