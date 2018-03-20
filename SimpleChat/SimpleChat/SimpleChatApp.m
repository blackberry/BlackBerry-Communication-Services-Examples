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

#import <BBMEnterprise/BBMEnterprise.h>

#import "SimpleChatApp.h"

//Authentication via GoogleSignIn from Support classes
#import "BBMAuthController.h"
#import "BBMAuthenticatedAccount.h"
#import "BBMGoogleTokenManager.h"

//Key Storage via Firebase from Support classes
#import "Firebase.h"
#import "BBMFirebaseKeyStorageProvider.h"
#import "BBMKeyManager.h"
#import "BBMFirebaseUserManager.h"
#import "BBMUserManager.h"
#import "BBMEndpointManager.h"

#import "BBMAccess.h"
#import "ConfigSettings.h"

@interface SimpleChatApp () {
    BBMAuthController *_authController;
    BBMEndpointManager *_endpointManager;
}

@property (nonatomic, strong) ObservableMonitor *serviceMonitor;
@property (nonatomic, strong) ObservableMonitor *authMonitor;

@end




@implementation SimpleChatApp

+ (instancetype)sharedApp
{
    static dispatch_once_t saToken;
    static SimpleChatApp *sharedApp;
    dispatch_once(&saToken, ^{
        sharedApp = [[self alloc] init];
    });
    return sharedApp;
}

- (id)init
{
    self = [super init];
    if(self) {
        //Echo all BBME SDK logs to the console
        [[BBMEnterpriseService service] setLoggingMode:kBBMLogModeFileAndConsole];
    }
    return self;
}

- (BBMAuthController *)authController
{
    static dispatch_once_t acToken;
    dispatch_once(&acToken, ^{
        [FIRApp configure];
        //Create our authController using BBMGoogleTokenManager to manage tokens
        _authController = [[BBMAuthController alloc] initWithTokenManager:[BBMGoogleTokenManager class]
                                                               userSource:[BBMFirebaseUserManager class]
                                                       keyStorageProvider:[BBMFirebaseKeyStorageProvider class]
                                                                   domain:SDK_SERVICE_DOMAIN
                                                              environment:SDK_ENVIRONMENT];

        //Start the BBM Enterprise service.
        [_authController startBBMEnterpriseService];

        //Resume our previous session
        [_authController signInSilently];
    });
    return _authController;
}


- (BBMEndpointManager *)endpointManager
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        _endpointManager = [[BBMEndpointManager alloc] init];
        // The endpoint manager needs to know about auth changes to register an endpoint
        [_authController addDelegate:_endpointManager];
    });
    return _endpointManager;
}




@end
