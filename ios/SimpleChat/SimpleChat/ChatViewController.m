/* Copyright (c) 2019 BlackBerry Limited.  All Rights Reserved.
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

#import "ChatViewController.h"
#import <BBMEnterprise/BBMEnterprise.h>

#import "BBMAccess.h"

@interface ChatViewController () <UITableViewDataSource, UITextFieldDelegate>

@property (weak,   nonatomic) IBOutlet UITextField *messageField;
@property (weak,   nonatomic) IBOutlet UITableView *tableView;

@property (nonatomic, strong) ObservableMonitor *chatMonitor;
@property (nonatomic, strong) NSArray *messages;

@end



@implementation ChatViewController

- (void)setChat:(BBMChat *)chat
{
    if(_chat != chat) {
        _chat = chat;
        [self startChatMonitor];
    }else {
        return;
    }
}

- (void)startChatMonitor
{
    //This monitor will lazy-load all of the messages in a given chat and add them to an array
    //that we can use to drive our tableView.  chat.lastMessage and chat.numMessages are both
    //observable properties so this block will run whenever these change - which will happen whenever
    //a message is added or removed.
    typeof(self) __weak weakSelf = self;
    self.chatMonitor = [ObservableMonitor monitorActivatedWithName:@"chatMonitor" block:^{
        unsigned long long lastMsg = weakSelf.chat.lastMessage;
        unsigned long long firstMsg = lastMsg - weakSelf.chat.numMessages + 1;   //Message ids are 1-indexed
        NSMutableArray *messages = [NSMutableArray array];

        BBMLiveMap *msgMap = [[BBMEnterpriseService service] model].chatMessage;

        for(unsigned long long msgId = firstMsg; msgId <= lastMsg; msgId++) {
            BBMChatMessageKey *key = [BBMChatMessageKey keyWithChatId:weakSelf.chat.chatId messageId:msgId];
            BBMChatMessage *msg = msgMap[key];

            //In practice, you may not wish to render all chatMessages.  Here, we render only
            //messages with the @"Text" tag.  Alternatively, you can use chatMessageWithCritera
            //to get customized chatMessage maps that include only messages for specific chats that
            //have a specific message tag.  Here we use the primary chatMessage map that will load
            //any message for any chat on demand.  Chats may also be very long, in which case you
            //may wish to load messages only as the user scrolls to them (see RichChat)

            //Ignore everything except messages with a "Text" tag.
            if([msg.tag isEqualToString:@"Text"]) {
                //Message ids should be in chronological order.  You can also sort by timestamp
                //if needed.
                [messages addObject:msg];
            }
        }

        if(![weakSelf.messages isEqualToArray:messages]) {
            //If the list of messages has changed, reload the table.
            weakSelf.messages = messages;
            [weakSelf.tableView reloadData];

            //Mark the loaded messages as read.  In practice, you will probably want to do this
            //on a scroll event or some other user interaction.  See Rich Chat.
            [weakSelf markLoadedMessagesAsRead];
        }
    }];
}

- (void)viewWillAppear:(BOOL)animated
{
    self.title = self.chat.subject;
    [self.chatMonitor activate];
}

- (void)viewWillDisappear:(BOOL)animated
{
    [self.chatMonitor deActivate];
}

- (void)markLoadedMessagesAsRead
{
    //Messages will be ordered from oldest to newest.  Marking the newest unread incoming message
    //as read will mark all previous incoming messages as read, likewise if the newest incoming
    //message is read, then all other incoming messages have already been marked as read.
    for(BBMChatMessage *message in self.messages.reverseObjectEnumerator) {
        if(message.isIncomingFlagSet && message.state != kBBMChatMessage_StateRead) {
            [BBMAccess markMessagesRead:@[message]];
            break;
        }else if(message.isIncomingFlagSet && message.state == kBBMChatMessage_StateRead) {
            //All of the messages are already marked as read
            break;
        }
    }
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    return self.messages.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
    BBMChatMessage *msg = self.messages[indexPath.row];
    NSString *reuseId = msg.isIncomingFlagSet ? @"incomingMessageCell" : @"outgoingMessageCell";
    MessageCell *cell = [tableView dequeueReusableCellWithIdentifier:reuseId];
    [cell setMessage:msg];
    return cell;
}

- (BOOL)textField:(UITextField *)textField shouldChangeCharactersInRange:(NSRange)range replacementString:(NSString *)string
{
    //Send the message if the user hits enter
    if([string rangeOfCharacterFromSet:[NSCharacterSet newlineCharacterSet]].location != NSNotFound) {
        [self sendPressed:nil];
    }
    return YES;
}

- (IBAction)sendPressed:(id)sender
{
    NSString *messageText = self.messageField.text;
    if(messageText.length == 0)return;

    self.messageField.text = @"";
    [self.messageField resignFirstResponder];

    BBMChatMessageSendMessage *chatMessageSend = [[BBMChatMessageSendMessage alloc] initWithChatId:self.chat.chatId
                                                                                               tag:@"Text"];
    chatMessageSend.content = messageText;

    //This will send the "BBMChatMessageSendMessage" to the BBMEnteprise service.
    [[BBMEnterpriseService service] sendMessageToService:chatMessageSend];
}

@end


#pragma mark - Cells

@interface MessageCell ()
@property (weak,   nonatomic) IBOutlet UILabel *messageLabel;
@property (strong, nonatomic) ObservableMonitor *messageMonitor;
@end

@implementation MessageCell

- (void)setMessage:(BBMChatMessage *)message
{
    if(message != _message) {
        _message = message;
        [self startMessageMonitor];
    } else {
        return;
    }
}

- (void)startMessageMonitor
{
    typeof(self) __weak weakSelf = self;
    self.messageMonitor = [ObservableMonitor monitorActivatedWithName:@"msgMonitor" block:^{
        //We want to check the bbmState here.  Messages in the chatMessage map will switch between
        //the pending and current state as they are faulted into the data model. Querying the state
        //in a monitor will ensure that the text is updated when the message data is updated
        //without having to reload the cell or the entire table.  The same applies for the delivery
        //status (message.state)
        NSString *indicator = [MessageCell messageStateIndicator:weakSelf.message.state];
        if(weakSelf.message.bbmState == kBBMStateCurrent) {
            weakSelf.messageLabel.text = [NSString stringWithFormat:@"(%@) %@", indicator, weakSelf.message.content];
        }else{
            weakSelf.messageLabel.text = @"Loading...";
        }
    }];
}

+ (NSString *)messageStateIndicator:(BBMChatMessageState)state
{
    //Strings to indicate the delivery status of messages
    switch(state) {
        case kBBMChatMessage_State_Unspecified:
            return @"?";
        case kBBMChatMessage_StateDelivered:
            return @"D";
        case kBBMChatMessage_StateFailed:
            return @"F";
        case kBBMChatMessage_StateRead:
            return @"R";
        case kBBMChatMessage_StateSending:
            return @"...";
        case kBBMChatMessage_StateSent:
           return @"S";
    }
}

@end

@implementation IncomingMessageCell
@end

@implementation OutgoingMessageCell
@end
