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

#import "StartChatViewController.h"
#import "BBMChatCreator.h"

@interface StartChatViewController () <UITextFieldDelegate>

@property (weak,   nonatomic) IBOutlet UITextField *regIdField;
@property (weak,   nonatomic) IBOutlet UITextField *subjectField;
@property (weak, nonatomic) IBOutlet UIButton *startChatButton;

@property (strong, nonatomic) IBOutlet BBMChatCreator *chatCreator;

@end

@implementation StartChatViewController


- (IBAction)startPressed:(id)sender
{
    if([self validateFields]) {
        [self.regIdField resignFirstResponder];
        [self.subjectField resignFirstResponder];
        [self startChat];
    }
}

- (BOOL)validateFields
{
    if(self.subjectField.text.length < 1) {
        [self.subjectField becomeFirstResponder];
        return NO;
    }

    if(self.regIdField.text.length < 1) {
        [self.regIdField becomeFirstResponder];
        return NO;
    }

    return YES;
}

- (void)startChat
{
    self.startChatButton.enabled = NO;
    self.regIdField.enabled = NO;
    self.subjectField.enabled = NO;

    NSNumber *regId = @([self.regIdField.text longLongValue]);
    NSString *subject = self.subjectField.text;

    //We start a conference here since this allows creation of a new chat every time.  1-1 chats
    //assume there is only ever a single 1-1 chat between two participants and will reuse an
    //existing mailbox if one can be found.
    [self.chatCreator startConferenceWithRegIds:@[regId]
                                               subject:subject
                                              callback:^(NSString *chatId, BBMChatStartFailedMessageReason failReason) {
        if(chatId) {
            [self.navigationController popViewControllerAnimated:YES];
        }else{
            self.startChatButton.enabled = YES;
            self.regIdField.enabled = YES;
            self.subjectField.enabled = YES;
            UIAlertController *ac = [UIAlertController alertControllerWithTitle:@"Chat Start Failed"
                                                                        message:@"Unable to start chat"
                                                                 preferredStyle:UIAlertControllerStyleAlert];
            UIAlertAction *dismiss = [UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleCancel handler:nil];
            [ac addAction:dismiss];
            [self presentViewController:ac animated:YES completion:nil];
        }
    }];
}

@end
