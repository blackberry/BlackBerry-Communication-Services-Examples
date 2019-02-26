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

#import "ChatListLoader.h"

#import <BBMEnterprise/BBMEnterprise.h>
#import "BBMAccess.h"

@interface ChatListLoader ()
@property (nonatomic) ObservableMonitor *chatsListMonitor;
@property (nonatomic) NSMutableSet *listeners;
@end


@implementation ChatListLoader


- (id)init
{
    self = [super init];
    if (self) {
        self.listeners = [NSMutableSet set];
    }
    return self;
}

- (void)addChangeListener:(id<ChatListListener>)listener
{
    if (listener == nil) {
        return;
    }

    [self.listeners addObject:listener];

    // We activate the monitor so that the new listener gets the current list of chats.
    //   If they need to wait for an update they could be waiting forever.
    if (self.chatsListMonitor) {
        [self.chatsListMonitor activate];
    } else {
        [self monitorChatList];
    }
}

- (void)removeChangeListener:(id<ChatListListener>)listener
{
    if (listener == nil) {
        return;
    }

    [self.listeners removeObject:listener];
}


- (void)monitorChatList
{
    // If the monitoring has already begun, we can run it without recreating the monitor.
    if (self.chatsListMonitor != nil) {
        return;
    }

    typeof(self) __weak weakSelf = self;
    //This monitor gets the list of chats and sorts it so that the chats with the newest messages
    //are displayed first. Since each cell displays the names of the chat participants any change
    //in any of those values will trigger the monitor. Note that the last message is required
    self.chatsListMonitor = [ObservableMonitor monitorActivatedWithName:@"chatsListMonitor" block:^{
        BOOL allMessagesAreCurrent = YES;
        BOOL allUsersAreCurrent = YES;

        NSArray *chats = [BBMAccess getChats];
        for (BBMChat *chat in chats) {
            NSArray *participants = [BBMAccess participantsForChat:chat].array;
            for (BBMChatParticipant *chatParticipant in participants) {
                BBMUser *chatUser = chatParticipant.resolvedUserUri;
                if (chatUser && chatUser.bbmState == kBBMStatePending) {
                    allUsersAreCurrent = NO;
                }
            }

        }
        //If there are any last messages or users that are still pending then we wait for them
        //to be current to avoid sorting the chat list until we have everything we need.
        if(!allMessagesAreCurrent || !allUsersAreCurrent) {
            return;
        }
        NSSortDescriptor *sortDescriptor = [NSSortDescriptor sortDescriptorWithKey:@"lastActivity" ascending:NO];

        for (id<ChatListListener> listener in weakSelf.listeners) {
            [listener chatListChanged:[chats sortedArrayUsingDescriptors:@[sortDescriptor]]];
        }
    }];
}


@end
