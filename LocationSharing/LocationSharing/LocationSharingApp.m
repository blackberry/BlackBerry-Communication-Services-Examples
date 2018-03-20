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
#import "ConfigSettings.h"

@interface LocationSharingApp ()

@property (nonatomic, strong) BBMAuthController *authController;
@property (nonatomic, strong) LocationManager *locationManager;
@property (nonatomic, strong) ChatListLoader *chatListLoader;
@property (nonatomic, strong) BBMEndpointManager *endpointManager;

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
    }
    return self;
}

- (BBMAuthController *)authController
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        [FIRApp configure];
        _authController = [[BBMAuthController alloc] initWithTokenManager:[BBMGoogleTokenManager class]
                                                               userSource:[BBMFirebaseUserManager class]
                                                       keyStorageProvider:[BBMFirebaseKeyStorageProvider class]
                                                                   domain:SDK_SERVICE_DOMAIN
                                                              environment:SDK_ENVIRONMENT];

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
            //Once the service starts we start the location manager and start monitoring
            [weakSelf.locationManager startLocationManager];
            [weakSelf.locationManager startMonitoring];
        }
        else {
            [weakSelf.locationManager stopMonitoring];
        }
    }];
}



@end
