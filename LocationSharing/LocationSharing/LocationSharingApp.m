/* Copyright (c) 2017 BlackBerry.  All Rights Reserved.
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

* This sample code was created by BlackBerry using SDKs from Apple Inc.
* and may contain code licensed for use only with Apple products.
* Please review your Apple SDK Agreement for additional details.
*/

#import "LocationSharingApp.h"
#import "BBMFirebaseKeyStorageProvider.h"
#import "BBMFirebaseUserManager.h"
#import "BBMGoogleTokenManager.h"
#import "Firebase.h"
#import "BBMAuthenticatedAccount.h"
#import "BBMAccess.h"

@interface LocationSharingApp ()

@property (nonatomic, strong) BBMKeyManager *keyManager;
@property (nonatomic, strong) BBMUserManager *userManager;
@property (nonatomic, strong) BBMAuthController *authController;
@property (nonatomic, strong) LocationManager *locationManager;
@property (nonatomic, strong) ChatListLoader *chatListLoader;

@property (nonatomic, strong) ObservableMonitor *serviceMonitor;
@property (nonatomic, strong) ObservableMonitor *authMonitor;
@end

@implementation LocationSharingApp

+ (instancetype)application
{
    static LocationSharingApp *application;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        application = [[self alloc] init];
    });
    return application;
}

- (id)init
{
    self = [super init];
    if(self) {
        //Echo all BBME SDK logs to the console
        [[BBMEnterpriseService service] setLoggingMode:kBBMLogModeFileAndConsole];
        [self.authController startBBMEnterpriseService];
        [self startServiceMonitor];
        [self startAuthMonitor];
    }
    return self;
}

- (BBMKeyManager *)keyManager
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        BBMFirebaseKeyStorageProvider *storageProvider = [[BBMFirebaseKeyStorageProvider alloc] init];
        BBMKeyManager *protectedMgr = [[BBMKeyManager alloc] initWithKeyStorageProvider:storageProvider];
        self.keyManager = protectedMgr;
    });
    return _keyManager;
}

- (BBMUserManager *)userManager
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        BBMFirebaseUserManager *userSource = [[BBMFirebaseUserManager alloc] init];
        self.userManager = [[BBMUserManager alloc] initWithSource:userSource];
    });
    return _userManager;
}

- (BBMAuthController *)authController
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        self.authController = [[BBMAuthController alloc] initWithTokenManager:[BBMGoogleTokenManager class]];
    });
    return _authController;
}

- (LocationManager *)locationManager
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        self.locationManager = [[LocationManager alloc] init];
    });
    return _locationManager;
}

- (ChatListLoader *)chatListLoader
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        self.chatListLoader = [[ChatListLoader alloc] init];
    });
    return _chatListLoader;
}

#pragma mark - Monitors

//Monitor the main BBMEnterprise Service.  Once it has started, we should start firebase if it
//has not already been started.  This monitor only needs to run until firebase is started so
//we use a self-terminating monitor
- (void)startServiceMonitor
{
    typeof(self) __weak weakSelf = self;

    self.serviceMonitor = [ObservableMonitor monitorActivatedWithName:@"serviceMonitor" block:^{
        if(weakSelf.authController.serviceStarted) {
            //Firebase only needs to be configured once
            static dispatch_once_t onceToken;
            dispatch_once(&onceToken, ^{
                [FIRApp configure];
            });
            //Once the service starts we start the location manager and start monitoring
            [self.locationManager startLocationManager];
            [self.locationManager startMonitoring];
        }
        else {
            [self.locationManager stopMonitoring];
        }
    }];
}

//Monitor the user credentials and login/out of firebase as neccesary.  The BBMAuthController
//instance handles login/logout of the BBMEnterprise Service.  Once logged into firebase, we
//will syncronize our own profile keys (either download or upload them as neccesary).  See
//BBMKeyManager
- (void)startAuthMonitor
{
    typeof(self) __weak weakSelf = self;
    self.authMonitor = [ObservableMonitor monitorActivatedWithName:@"AppAuthMonitor" block:^{
        //Monitor the authState from the authController.
        BBMAuthState *authState = weakSelf.authController.authState;

        //We have not auth state, account or regId... We should log out of firebase if we're logged
        //in already...
        if(authState == nil || authState.account == nil || authState.regId == nil) {
            if([FIRAuth auth].currentUser) {
                [[weakSelf keyManager] stopAutomaticKeySync];
                [[FIRAuth auth] signOut:nil];
            }
            return;
        }

        //We are authenticated.  Log into firebase.  See BBMKeyManager.
        FIRAuthCredential *credential = [FIRGoogleAuthProvider credentialWithIDToken:authState.account.idToken
                                                                         accessToken:authState.account.accessToken];


        //Start Firebase with the GoogleSignIn credentials
        [[FIRAuth auth] signInWithCredential:credential completion:^(FIRUser * _Nullable firebaseUser, NSError * _Nullable error) {
            if(error) {
                NSLog(@"FIRAuth sign-in error: %@",error.localizedDescription);
            }
            else {

                //The accountId we want for firebase is the firebase accountId.  This will allow us
                //to set read/write permissions on private keys so that only the user that sets the
                //keys can read those keys
                [weakSelf.userManager registerLocalUserWithAccount:authState.account
                                                             regId:authState.regId.stringValue];

                //Once we are logged into firebase, we need to syncronize the profile keys.  This will
                //either upload or download the keys as required.  At this point, we should also start
                //monitoring chats so we can syncronize any chat keys as required.
                [[weakSelf keyManager] syncProfileKeysForLocalUser:firebaseUser.uid regId:authState.regId.stringValue];
                [[weakSelf keyManager] startAutomaticKeySync];

                //Once Firebase is setup tell the login delegate.
                if([authState.setupState isEqualToString:kBBMSetupStateSuccess]) {
                    [weakSelf.loginDelegate loggedIn];
                }
            }
        }];
    }];
}



@end
