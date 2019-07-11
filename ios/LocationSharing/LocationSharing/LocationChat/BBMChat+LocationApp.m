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

#import "BBMChat+LocationApp.h"
#import <BBMEnterprise/BBMUser.h>
#import "BBMAppUser.h"
#import "LocationSharingApp.h"
#import "BBMAccess.h"

@implementation BBMChat (LocationApp)

- (NSString *)chatTitle
{
    //If the chat has a subject display it, otherwise display a list of the participants.
    if (self.subject.length > 0) {
        return self.subject;
    }
    else {
        return [self participantsDisplayNamesForTitle];
    }

}

- (NSString *)participantsDisplayNamesForTitle
{
    NSArray *participants = [BBMAccess participantsForChat:self].array;
    NSMutableArray *displayNames = [[NSMutableArray alloc] init];
    // Get userUris from the participants so that we can get the BBMUser objects from the user map
    NSArray *userUris = [participants valueForKey:@"userUri"];
    // user is an instance of BBMLiveMap. Using an array of userUris is faster than having a for loop
    // and calling valueForKey for each individual userUri.
    NSArray *users = [[[BBMEnterpriseService service] model].user valuesForKeys:userUris];
    for (BBMUser *user in users) {
        if(user.bbmState == kBBMStatePending) {
            return @"";
        }
        BBMAppUser *contact = [[LocationSharingApp application].authController.userManager userForRegId:user.regId];
        NSString *name = contact.name;
        if (name != nil) {
            [displayNames addObject:name];
        }
    }
    return [displayNames componentsJoinedByString:@", "];
}
@end
