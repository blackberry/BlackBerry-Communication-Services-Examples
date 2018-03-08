//
//  Global.m
//  colourPickerDemo
//
//  Copyright (c) 2018 BlackBerry.  All Rights Reserved.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//
//  This sample code was created by BlackBerry using SDKs from Apple Inc.
//  and may contain code licensed for use only with Apple products.
//  Please review your Apple SDK Agreement for additional details.
//


#import <BBMEnterprise/BBMEnterprise.h>

#import <UIKit/UIKit.h>
#import "Global.h"

//  Authentication via GoogleSignIn from Support classes
#import "BBMAuthController.h"
#import "BBMAuthenticatedAccount.h"
#import "BBMGoogleTokenManager.h"

//  Key Storage via Firebase from Support classes
#import "Firebase.h"
#import "BBMFirebaseKeyStorageProvider.h"
#import "BBMKeyManager.h"
#import "BBMEndpointManager.h"

#import "BBMAccess.h"


@interface Global () {
    BBMAuthController       *_authController;
    BBMKeyManager           *_keyManager;
    BBMEndpointManager      *_endpointManager;
    UIColor                 *_chosenColour;
}

@property (nonatomic, strong) ObservableMonitor     *serviceMonitor;
@property (nonatomic, strong) ObservableMonitor     *authMonitor;

@end

@implementation Global

+ (instancetype)sharedInstance
{
    static dispatch_once_t saToken;
    static Global *sharedInstance;
    dispatch_once(&saToken, ^{
        sharedInstance = [[self alloc] init];
    });
    return sharedInstance;
}

- (id)init
{
    self = [super init];
    if(self) {
        //  Echo all BBME SDK logs to the console
        [[BBMEnterpriseService service] setLoggingMode:kBBMLogModeFileAndConsole];
        [self startServiceMonitor];
        [self startAuthMonitor];
    }
    return self;
}

- (BBMAuthController *)authController
{
    static dispatch_once_t acToken;
    dispatch_once(&acToken, ^{
        //  Create our authController using BBMGoogleTokenManager to manage tokens
        _authController = [[BBMAuthController alloc] initWithTokenManager:[BBMGoogleTokenManager class]];
        
        //  Start the BBM Enterprise service.
        [_authController startBBMEnterpriseService];
        
        //  Resume our previous session
        [_authController signInSilently];
    });
    return _authController;
}

- (BBMKeyManager *)keyManager
{
    static dispatch_once_t kmToken;
    dispatch_once(&kmToken, ^{
        BBMFirebaseKeyStorageProvider *storageProvider = [[BBMFirebaseKeyStorageProvider alloc] init];
        BBMKeyManager *protectedMgr = [[BBMKeyManager alloc] initWithKeyStorageProvider:storageProvider];
        _keyManager = protectedMgr;
    });
    return _keyManager;
}

- (BBMEndpointManager *)endpointManager
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        _endpointManager = [[BBMEndpointManager alloc] init];
        //  The endpoint manager needs to know about auth changes to register an endpoint
        [_authController addDelegate:_endpointManager];
    });
    return _endpointManager;
}

- (UIColor *)chosenColour
{
    static dispatch_once_t ccToken;
    dispatch_once(&ccToken, ^{
        _chosenColour = [[UIColor alloc] init];
        _chosenColour = [UIColor colorWithRed:0 green:1 blue:0 alpha:1];
    });
    
    
    return _chosenColour;
}

- (void)setChosenColour:(UIColor *)newColour
{
    NSString *colourHex = MSHexStringFromColor(newColour);
    printf("%s\n", [colourHex UTF8String]);
    printf("%s\n", [@"-----" UTF8String]);
    _chosenColour = newColour;
}

#pragma mark - Monitors

//  Monitor the main BBMEnterprise Service.  Once it has started, we should start firebase if it
//  has not already been started.  This monitor only needs to run until firebase is started so
//  we use a self-terminating monitor
- (void)startServiceMonitor
{
    typeof(self) __weak weakSelf = self;
    self.serviceMonitor = [ObservableMonitor monitorActivatedWithName:@"service Monitor" selfTerminatingBlock:^BOOL{
        if(weakSelf.authController.serviceStarted) {
            //  [FIRApp configure];
            return YES;
        }
        return NO;
    }];
}

//  Monitor the user credentials and login/out of firebase as neccesary.  The BBMAuthController
//  instance handles login/logout of the BBMEnterprise Service.  Once logged into firebase, we
//  will syncronize our own profile keys (either download or upload them as neccesary).  See
//  BBMKeyManager
- (void)startAuthMonitor
{
    typeof(self) __weak weakSelf = self;
    self.authMonitor = [ObservableMonitor monitorActivatedWithName:@"AppAuthMonitor" block:^{
        //  Monitor the authState from the authController.
        BBMAuthState *authState = weakSelf.authController.authState;
        
        //  We have not auth state, account or regId... We should log out of firebase if we're logged
        //  in already...
        if(authState == nil || authState.account == nil || authState.regId == nil) {
            if([FIRAuth auth].currentUser) {
                [[weakSelf keyManager] stopAutomaticKeySync];
                [[FIRAuth auth] signOut:nil];
            }
            return;
        }
        
        //  We are authenticated.  Log into firebase.  See BBMKeyManager.
        FIRAuthCredential *credential = [FIRGoogleAuthProvider credentialWithIDToken:authState.account.idToken
                                                                         accessToken:authState.account.accessToken];
        
        
        //  Start Firebase with the GoogleSignIn credentials
        [[FIRAuth auth] signInWithCredential:credential completion:^(FIRUser * _Nullable firebaseUser, NSError * _Nullable error) {
            if(error) {
                NSLog(@"FIRAuth sign-in error: %@",error.localizedDescription);
            }
            else {
                //  Once we are logged into firebase, we need to syncronize the profile keys.  This will
                //  either upload or download the keys as required.  At this point, we should also start
                //  monitoring chats so we can syncronize any chat keys as required.
                [[weakSelf keyManager] syncProfileKeysForLocalUser:firebaseUser.uid regId:authState.regId.stringValue];
                [[weakSelf keyManager] startAutomaticKeySync];
            }
        }];
    }];
}

@end
