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

#import "ChatsListViewController.h"

#import <BBMEnterprise/BBMEnterprise.h>
#import "BBMAccess.h"
#import "ChatViewController.h"
#import "BBMKeyManager.h"
#import "SimpleChatApp.h"

@interface ChatsListViewController()  <UITableViewDataSource, UITableViewDelegate>
@property (weak,   nonatomic) IBOutlet UITableView *tableView;
@property (strong, nonatomic) ObservableMonitor *chatsMonitor;
@property (strong, nonatomic) NSArray *chats;
@end


@implementation ChatsListViewController 

- (void)viewDidAppear:(BOOL)animated
{
    //Observe the list of chats.  Note the use of the "observableArray" property on the chat
    //LiveList which will trigger this monitor any time there is a change in the chat LiveList.
    //We only need to be running this monitor while the view is visible.
    typeof(self) __weak weakSelf = self;
    self.chatsMonitor = [ObservableMonitor monitorActivatedWithName:@"chatsMonitor" block:^{
        BBMLiveList *chatsList = [[[BBMEnterpriseService service] model] chat];
        NSMutableArray *validChats = [NSMutableArray array];
        for(BBMChat *chat in chatsList.observableArray) {
            //Do not render hidden or defunct chats
            if(!chat.isHiddenFlagSet && !(chat.state == kBBMChat_StateDefunct)) {
                [validChats addObject:chat];
            }
        }

        //Reload the table if the list of chats has changed
        if(![weakSelf.chats isEqualToArray:validChats]) {
            weakSelf.chats = validChats;
            [weakSelf.tableView reloadData];
        }
    }];
}

//Deactivate the chat monitor when the view disappears.
- (void)viewDidDisappear:(BOOL)animated
{
    [self.chatsMonitor deActivate];
    self.chatsMonitor = nil;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    return self.chats.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
    ChatCell *cell = [tableView dequeueReusableCellWithIdentifier:@"chatCell"];
    [cell setChat:self.chats[indexPath.row]];
    return cell;
}

- (void)tableView:(UITableView *)tableView commitEditingStyle:(UITableViewCellEditingStyle)editingStyle forRowAtIndexPath:(NSIndexPath *)indexPath
{
    //Support for swipe-to-delete
    if(editingStyle == UITableViewCellEditingStyleDelete) {
        BBMChat *chat = self.chats[indexPath.row];
        //The leave action will eject this user from the chat entirely.  The hide action
        //will simply hide the chat until the user re-joins it.
        [BBMAccess leaveChat:chat];
    }
}

- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender
{
    if([segue.identifier isEqualToString:@"showChat"]) {
        ChatViewController *dest = segue.destinationViewController;
        NSIndexPath *path = [self.tableView indexPathForCell:sender];
        dest.chat = self.chats[path.row];
    }
}

@end



#pragma mark - Chat Cell

@interface ChatCell()
@property (weak,   nonatomic) IBOutlet UILabel *subjectLabel;
@property (weak,   nonatomic) IBOutlet UILabel *participantLabel;
@property (strong, nonatomic) ObservableMonitor *chatMonitor;
@end


@implementation ChatCell

- (void)setChat:(BBMChat *)chat
{
    if(chat != _chat){
        _chat = chat;
        [self startChatMonitor];
    }else {
        return;
    }

}

- (void)startChatMonitor
{
    //Monitor the subject.  This will automatically update the text in the
    //subject field if it changes on self.chat without having to reload the tableView.
    typeof(self) __weak weakSelf = self;
    self.chatMonitor = [ObservableMonitor monitorActivatedWithName:@"chatMonitor" block:^{
        weakSelf.subjectLabel.text = weakSelf.chat.subject;

        //Show the registration Id for the other participant.  In practice, you can have
        //many users in a chat.  Here, we will show only the regID of the first participant
        NSArray *participants = [BBMAccess participantsForChat:weakSelf.chat].array;
        if(participants.count) {
            BBMChatParticipant *participant = participants[0];
            self.participantLabel.text = [participant.resolvedUserUri.regId stringValue];
        }else{
            self.participantLabel.text = @"Empty Chat";
        }
    }];
}

@end
