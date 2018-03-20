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

#import <Foundation/Foundation.h>
#import "BBMKeyManager.h"
#import "BBMUserManager.h"
#import "BBMAuthController.h"
#import "LocationManager.h"
#import "ChatListLoader.h"
#import "BBMEndpointManager.h"

// Protocol to inform delegate when login has been succesful
@protocol LocationSharingLoginDelegate <NSObject>

- (void)loggedIn;

@end

@interface LocationSharingApp : NSObject
@property (nonatomic, weak) NSObject<LocationSharingLoginDelegate> *loginDelegate;

// Shared application instance
+ (instancetype)application;

#pragma mark - Application Controllers

// Starts the BBM Enterprise service and authenticates
- (BBMAuthController *)authController;

// Tracks user's location and sends location info to existing chats
- (LocationManager *)locationManager;

// Loads the list of chats the local user is a participant in
- (ChatListLoader *)chatListLoader;


@end
