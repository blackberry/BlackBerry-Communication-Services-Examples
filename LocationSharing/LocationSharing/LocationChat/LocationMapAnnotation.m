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

#import "LocationMapAnnotation.h"
#import <BBMEnterprise/BBMEnterprise.h>
#import "LocationManager.h"
#import "LocationSharingApp.h"

@implementation LocationMapAnnotation

- (void)setMessage:(BBMChatMessage *)message
{
    _message = message;
    
    if ([message.tag isEqualToString:kMessageTag_Location]) {
        [self willChangeValueForKey:@"coordinate"];
        self.locationData = message.rawData;
        [self didChangeValueForKey:@"coordinate"];
    }
    
    [self willChangeValueForKey:@"title"];
    self.user = [[LocationSharingApp application].authController.userManager userForRegId:_message.resolvedSenderUri.regId];
    [self didChangeValueForKey:@"title"];
}

- (CLLocationCoordinate2D)coordinate
{
    CLLocationCoordinate2D coord;
    coord.latitude = [self.locationData[kLatitudeKey] doubleValue];
    coord.longitude = [self.locationData[kLongitudeKey] doubleValue];
    return coord;
}

- (NSString *)title
{
    return [NSString stringWithFormat:@"%@ - %@",self.user.name, self.user.email];
}

- (NSString *)subtitle
{
    static NSDateFormatter *dateFormatter;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        dateFormatter = [[NSDateFormatter alloc] init];
        [dateFormatter setDateStyle:NSDateFormatterMediumStyle];
        [dateFormatter setTimeStyle:NSDateFormatterMediumStyle];
    });
    NSDate *date = [NSDate dateWithTimeIntervalSince1970:(NSTimeInterval)self.message.timestamp];
    return [NSString stringWithFormat:@"%@",[dateFormatter stringFromDate:date]];
}

+ (UIColor *)colorForRegId:(NSNumber *)regId
{
    static NSArray *kBBMMessageColorAssignerPalette = nil;
    if(regId == nil){
        return nil;
    }
    static dispatch_once_t onceToken;
    static NSMutableDictionary *colorByRegId;
    static NSUInteger colorIndex;
    dispatch_once(&onceToken, ^{
        colorByRegId = [[NSMutableDictionary alloc] init];
        colorIndex = 0;
        kBBMMessageColorAssignerPalette = @[[UIColor colorWithRed:204.0/255.0 green:51.0/255.0 blue:51.0/255.0 alpha:1.0],
                                            [UIColor colorWithRed:61.0/255.0 green:173.0/255.0 blue:168.0/255.0 alpha:1.0],
                                            [UIColor colorWithRed:191.0/255.0 green:153.0/255.0 blue:29.0/255.0 alpha:1.0],
                                            [UIColor colorWithRed:153.0/255.0 green:51.0/255.0 blue:136.0/255.0 alpha:1.0],
                                            [UIColor colorWithRed:92.0/255.0 green:120.0/255.0 blue:41.0/255.0 alpha:1.0],
                                            [UIColor colorWithRed:208.0/255.0 green:119.0/255.0 blue:33.0/255.0 alpha:1.0],
                                            [UIColor colorWithRed:14.0/255.0 green:133.0/255.0 blue:134.0/255.0 alpha:1.0],
                                            [UIColor colorWithRed:14.0/255.0 green:133.0/255.0 blue:134.0/255.0 alpha:1.0],
                                            [UIColor colorWithRed:71.0/255.0 green:51.0/255.0 blue:128.0/255.0 alpha:1.0],
                                            [UIColor colorWithRed:114.0/255.0 green:102.0/255.0 blue:5.0/255.0 alpha:1.0],
                                            [UIColor colorWithRed:13.0/255.0 green:77.0/255.0 blue:102.0/255.0 alpha:1.0]
                                            ];
    });
    UIColor *color = colorByRegId[regId];
    if(color == nil) {
        NSUInteger index = colorIndex % kBBMMessageColorAssignerPalette.count;
        color = kBBMMessageColorAssignerPalette[index];
        colorByRegId[regId] = color;
        colorIndex++;
    }
    return color;
}

@end
