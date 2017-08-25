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

#import "ChatCreator.h"
#import "CoreAccess.h"

#import <BBMEnterprise/BBMEnterprise.h>

@interface ChatCreator () <BBMDSMessageConsumer>

@property NSMutableDictionary *callbacksByCookie;

@end

@implementation ChatCreator

- (id)init
{
    if( (self = [super init]) ) {
        self.callbacksByCookie = [NSMutableDictionary dictionary];
        [BBMCore.model.connection addConsumer:self forMessageNames:@[@"listAdd"]];
        [BBMCore.model.connection addConsumer:self forMessageNames:@[@"chatStartFailed"]];
    }
    return self;
}

- (void)dealloc
{
    [BBMCore.model.connection removeConsumer:self forMessageNames:@[@"listAdd"]];
    [BBMCore.model.connection removeConsumer:self forMessageNames:@[@"chatStartFailed"]];
}

#pragma mark - Public Interface
- (void)showStartChatAlert:(UIViewController *)presenter
                     regId:(NSString *)regId
                  callback:(ChatCreationAlertCallback)callback
{
    //Display alert asking the user to enter a reg id.
    UIAlertController *controller = [UIAlertController alertControllerWithTitle:@"Start Chat"
                                                                        message:@"Enter a reg id to start a chat."
                                                                 preferredStyle:UIAlertControllerStyleAlert];
    UIAlertAction *okAction = [UIAlertAction actionWithTitle:@"Start Chat"
                                                       style:UIAlertActionStyleDefault
                                                     handler:^(UIAlertAction *action) {
                                                         NSString *regId = controller.textFields[0].text;
                                                         NSString *subject = controller.textFields[1].text;
                                                         if(callback) {
                                                             callback(regId,subject);
                                                         }                                                        
                                                     }];
    [controller addAction:okAction];

    UIAlertAction *cancelAction = [UIAlertAction actionWithTitle:@"Cancel"
                                                           style:UIAlertActionStyleCancel
                                                         handler:^(UIAlertAction *action) {
                                                         }];
    [controller addAction:cancelAction];

    [controller addTextFieldWithConfigurationHandler:^(UITextField *textField) {
        textField.keyboardType = UIKeyboardTypeNumberPad;
        textField.placeholder = @"Reg Id";
        if(regId.length) {
            textField.text = regId;
        }
    }];
    [controller addTextFieldWithConfigurationHandler:^(UITextField *textField) {
        textField.returnKeyType = UIReturnKeyDone;
        textField.placeholder = @"Subject";
    }];
    [presenter presentViewController:controller animated:YES completion:^{
        if(regId.length > 0) {
            //If regId field is pre-populated then make subject field the first responder.
            [controller.textFields[1] becomeFirstResponder];
        }
    }];
}


- (void)startChatWithRegId:(NSNumber *)regId
                   subject:(NSString *)subject
                  callback:(ChatCreationCallback)callback
{
    [self startChatWithRegIds:@[regId]
                      subject:subject
                   isOneToOne:YES
                     callback:callback];
}


- (void)startConferenceWithRegIds:(NSArray *)regIds
                          subject:(NSString *)subject
                         callback:(ChatCreationCallback)callback {
    [self startChatWithRegIds:regIds
                      subject:subject
                   isOneToOne:NO
                     callback:callback];
}


#pragma mark - Private Interface

- (void)startChatWithRegIds:(NSArray *)regIds
                    subject:(NSString *)subject
                 isOneToOne:(BOOL)isOneToOne
                   callback:(ChatCreationCallback)callback
{
    NSMutableArray *invitees = [[NSMutableArray alloc] init];
    for (NSNumber *regId in regIds) {
        BBMChatStartMessage_Invitees *invitee = [[BBMChatStartMessage_Invitees alloc] init];
        invitee.regId = regId;
        [invitees addObject:invitee];
    }
    
    BBMChatStartMessage *chatStart = [[BBMChatStartMessage alloc] initWithInvitees:invitees
                                                                           subject:subject];
    chatStart.isOneToOne = isOneToOne;
    [[BBMServiceLayer sharedInstance] sendCoreMessage:chatStart];
    self.callbacksByCookie[chatStart.cookie] = [callback copy];
}

#pragma mark - BBMDSConsumer

- (void)handleJSONMessage:(NSDictionary *)message
              messageType:(NSString *)messageType
                   listId:(NSString *)listId
{
    //Listen for the list add for the chat or the failure message. One or the other is always
    //posted back on the connection when we request a chat.
    NSDictionary *element = message[@"listAdd"];
    if (element) {
        NSString *cookie = element[@"cookie"];
        ChatCreationCallback callback = self.callbacksByCookie[cookie];
        if (callback) {
            NSArray *elements = element[@"elements"];
            if(elements.count == 0) {
                return;
            }
            NSString *chatId = element[@"elements"][0][@"chatId"];
            callback(chatId, nil);
            
            [self.callbacksByCookie removeObjectForKey:cookie];
        }
    }
    else {
        //No listAdd, chat creation failed.
        element = message[@"chatStartFailed"];
        if (element) {
            NSString *cookie = element[@"cookie"];
            ChatCreationCallback callback = self.callbacksByCookie[cookie];
            if (callback) {
                NSString *reason = element[@"reason"];
                callback(nil, reason);
                
                [self.callbacksByCookie removeObjectForKey:cookie];
            }
        }
    }
}


- (void)handleOverflow
{
}

@end
