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

#import "ChatsTableViewController.h"
#import <BBMEnterprise/BBMEnterprise.h>
#import "LocationSharingViewController.h"
#import "ContactPickerTableViewController.h"
#import "ChatTableViewCell.h"
#import "ChatListLoader.h"
#import "BBMChatCreator.h"
#import "BBMAccess.h"
#import "BBMAppUser.h"
#import "LocationSharingApp.h"

@interface ChatsTableViewController () <ChatListListener, BBMAppUserListener>

@property (nonatomic) NSArray *chatList;
@property (nonatomic) ChatListLoader *chatListLoader;
@property (nonatomic) BBMChatCreator *chatCreator;

@end

@implementation ChatsTableViewController

- (void)viewDidLoad
{
    [super viewDidLoad];
    
    self.navigationItem.backBarButtonItem = [[UIBarButtonItem alloc] initWithTitle:@""
                                                                             style:self.navigationItem.backBarButtonItem.style
                                                                            target:nil
                                                                            action:nil];
    
    // To hide extra separators at the end of the table
    self.tableView.tableFooterView = [UIView new];
    self.tableView.estimatedRowHeight = 50.0;
    self.tableView.rowHeight = UITableViewAutomaticDimension;

    //See ChatListLoader for details on how to get notified of any changes in the list of chats. That
    //class uses observable monitors.
    [[LocationSharingApp application].chatListLoader addChangeListener:self];
    
    //This is used to create new chats
    self.chatCreator = [[BBMChatCreator alloc] init];

    //When there is change in the users list reload the table
    [[LocationSharingApp application].authController.userManager addUserListener:self];
}

#pragma mark - Table view data source

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    return self.chatList.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
    //Each cell represents a chat.
    ChatTableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:@"ChatTableViewCell" forIndexPath:indexPath];
    BBMChat *chat = self.chatList[indexPath.row];
    [cell showChat:chat];
    return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    // When selecting a chat the chat screen is shown so that the user can see existing messages or
    // send new content.
    BBMChat *chat = self.chatList[indexPath.row];
    self.lastChatId = [chat.chatId lastPathComponent];
    [self performSegueWithIdentifier:@"ShowChatSegue" sender:self];
}

- (NSArray *)tableView:(UITableView *)tableView editActionsForRowAtIndexPath:(NSIndexPath *)indexPath
{
    // Swiping allows the user to leave a chat
    typeof(self) __weak weakSelf = self;
    // Leave ends the chat with the kBBMStopConversationMessage_Conversations_ActionLeave action.
    // This removes the conversation from the conversation list and the user leaves the chat.
    UITableViewRowAction *leaveAction = [UITableViewRowAction rowActionWithStyle:UITableViewRowActionStyleDestructive
                                                                           title:@"Leave"
                                                                         handler:^(UITableViewRowAction *action, NSIndexPath *indexPath) {
                                                                             [weakSelf leaveChatAtIndexPath:indexPath];
                                                                         }];
    return @[leaveAction];
}

- (void)leaveChatAtIndexPath:(NSIndexPath *)indexPath {
    BBMChat *chat = self.chatList[indexPath.row];
    [BBMAccess leaveChat:chat];
}

#pragma mark - Navigation

- (void)startChatWithRegIds:(NSArray *)regIds subject:(NSString *)subject
{
    if(regIds.count <= 0) {
        return;
    }
    //User chat creator to start a chat with one or multiple contacts
    [self.chatCreator startConferenceWithRegIds:regIds subject:subject callback:^(NSString *chatId, BBMChatStartFailedMessageReason failReason) {
        if(chatId)
        {
            //If a chat with the given regId already exists there is no need to create a new one.
            self.lastChatId = chatId;
            //Show the chat screen
            [self performSegueWithIdentifier:@"ShowChatSegue" sender:self];
        }
        else {
            NSLog(@"Chat creation failed failReason = %d", failReason);
        }
    }];
}

- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender
{
    if([segue.identifier isEqualToString:@"ShowChatSegue"]) {
        //This view controller will display the locations that have been shared.
        LocationSharingViewController *locationSharingViewController = (LocationSharingViewController *)segue.destinationViewController;
        //By setting the conversation uri the chat screen will know which messages to load
        locationSharingViewController.chatId = self.lastChatId;
        self.lastChatId = nil;
    }
    else if([segue.identifier isEqualToString:@"ContactPickerSegue"]) {
        UINavigationController *navController = (UINavigationController*)segue.destinationViewController;
        ContactPickerTableViewController *contactPicker = (ContactPickerTableViewController*)navController.topViewController;
        typeof(self) __weak weakSelf = self;
        contactPicker.callback = ^(NSArray *contacts, NSString *subject){
            if(contacts.count > 0) {
                NSMutableArray *regIds = [[NSMutableArray alloc] init];
                for(BBMAppUser *contact in contacts) {
                    [regIds addObject:@(contact.regId)];
                }
                [weakSelf startChatWithRegIds:regIds subject:subject];
            }
        };
    }
}

#pragma mark - ChatListListener methods

- (void)chatListChanged:(NSArray *)chatList
{
    self.chatList = chatList;
    [self.tableView reloadData];
}

#pragma mark - ContactsListener

- (void)usersChanged:(NSArray*)contacts
{
    [self.tableView reloadData];
}

@end
