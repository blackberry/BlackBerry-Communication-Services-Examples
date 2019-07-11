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

#import "LocationManager.h"
#import "LocationMapAnnotation.h"
#import <CoreLocation/CoreLocation.h>
#import <MapKit/MKMapView.h>
#import <AudioToolbox/AudioToolbox.h>
#import <BBMEnterprise/BBMEnterprise.h>
#import "BBMAccess.h"
#import "LocationSharingApp.h"

NSString * const kMessageTag_Location = @"Location";
NSString * const kLongitudeKey = @"longitude";
NSString * const kLatitudeKey = @"latitude";

@interface LocationManager () <CLLocationManagerDelegate, BBMLiveListener>

@property (nonatomic, strong) CLLocationManager *locationManager;
@property (nonatomic, assign) BOOL isMonitoring;
@property (nonatomic, strong) ObservableMonitor *chatListMonitor;

/* Stores ids of all the chats we are currently sending location updates to. */
@property (nonatomic, strong) NSMutableSet *chatIds;

/* The key is the reg id of the user and the object is a set of ids for all chats this user is in. */
@property (nonatomic, strong) NSMutableDictionary *locationChatsForRegId;

@property (nonatomic, strong) NSDictionary *previousLocationData;

@end

@implementation LocationManager

- (instancetype)init
{
    self = [super init];
    if (self) {
        self.chatIds = [[NSMutableSet alloc] init];
        self.locationChatsForRegId = [[NSMutableDictionary alloc] init];
    }

    return self;
}

- (void)startLocationManager
{
    if (self.locationManager)return;

    self.locationManager = [[CLLocationManager alloc] init];
    self.locationManager.delegate = self;
    //distanceFilter and desiredAccuracy are set to arbitrary values.
    self.locationManager.distanceFilter = 20;
    self.locationManager.desiredAccuracy = kCLLocationAccuracyBest;
    [self.locationManager requestAlwaysAuthorization];
}

- (void)startMonitoring
{
    if (self.isMonitoring)return;

    typeof(self) __weak weakSelf = self;
    self.chatListMonitor = [ObservableMonitor monitorActivatedWithName:@"chatListMonitor"  selfTerminatingBlock:^BOOL{
        if ([[BBMEnterpriseService service] model].chat.bbmState != kBBMStateCurrent) {
            return NO;
        }
        NSArray *chats = [[BBMEnterpriseService service] model].chat.array;
        // Send location updates to all existing chats.
        for (BBMChat *chat in chats) {
            [weakSelf startSendingLocationUpdatesForChatId:chat.chatId];
        }

        // Listen to the chat lists so we send our location to all chats
        [[[BBMEnterpriseService service] model].chat addListener:weakSelf];
        // Terminate this monitor once we have all the chats. New chats are tracked using
        // BBMLiveListener methods.
        return YES;
    }];
    self.isMonitoring = YES;
}

- (void)stopMonitoring
{
    if (self.isMonitoring)return;
    NSArray *chatIdsCopy = [self.chatIds copy];
    for (NSString *chatId in chatIdsCopy) {
        [self stopSendingLocationUpdatesForChatId:chatId];
    }
    [[[BBMEnterpriseService service] model].chat removeListener:self];
    self.isMonitoring = NO;
}

#pragma mark - BBMLiveListener

- (void)elementsAdded:(NSArray *)elements
{
    for (BBMElement *element in elements) {
        if ([element isKindOfClass:[BBMChat class]]) {
            // A new chat was added, add it to the set of chat ids so we will send location updates to it.
            BBMChat *chat = (BBMChat *)element;
            [self startSendingLocationUpdatesForChatId:chat.chatId];
        }
    }
}

- (void)elementsRemoved:(NSArray *)elements
{
    for (BBMElement *element in elements) {
        if ([element isKindOfClass:[BBMChat class]]) {
            // If a chat was removed we want to stop sending updates to it and remove all the users
            // that were in the chat.
            BBMChat *chat = (BBMChat *)element;
            [self stopSendingLocationUpdatesForChatId:chat.chatId];
        }
    }
}

#pragma mark - BBMAccess

- (void)sendLocation:(NSDictionary *)locationData toChatId:(NSString *)chatId
{
    BBMChatMessageSendMessage *msg = [[BBMChatMessageSendMessage alloc] initWithChatId:chatId tag:kMessageTag_Location];
    msg.rawData = locationData;
    [[BBMEnterpriseService service] sendMessageToService:msg];
}

#pragma mark - Start/Stop sending location

- (void)startSendingLocationUpdatesForChatId:(NSString *)chatId
{
    @synchronized (self.chatIds) {
        [self.chatIds addObject:chatId];
        [self.locationManager startUpdatingLocation];
    }
}

- (void)stopSendingLocationUpdatesForChatId:(NSString *)chatId
{
    @synchronized (self.chatIds) {
        [self.chatIds removeObject:chatId];
        if (self.chatIds.count == 0) {
            [self.locationManager stopUpdatingLocation];
        }
    }
}

#pragma mark - CLLocationManagerDelegate

- (void)locationManager:(CLLocationManager *)manager didFailWithError:(NSError *)error
{
    NSLog(@"locationManager:didFailWithError: %@", error);
}

- (void)locationManager:(CLLocationManager *)manager didUpdateLocations:(NSArray *)locations
{
    CLLocation *location = [locations lastObject];
    NSDictionary *locationData = @{kLatitudeKey : [NSString stringWithFormat:@"%f", location.coordinate.latitude],
                                   kLongitudeKey : [NSString stringWithFormat:@"%f", location.coordinate.longitude]};
    if([locationData[kLatitudeKey] isEqualToString:self.previousLocationData[kLatitudeKey]] &&
       [locationData[kLongitudeKey] isEqualToString:self.previousLocationData[kLongitudeKey]]) {
        return;
    }
    // Send the new location data to all chats.
    for (NSString *chatId in self.chatIds) {
        [self sendLocation:locationData toChatId:chatId];
    }
    self.previousLocationData = locationData;
}

@end
