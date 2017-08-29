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
#import <UIKit/UIKit.h>

typedef void (^ChatCreationCallback)(NSString *chatId, NSString *failReason);
typedef void (^ChatCreationAlertCallback)(NSString *regId, NSString *subject);

@interface ChatCreator : NSObject

- (void)showStartChatAlert:(UIViewController *)presenter
                     regId:(NSString *)regId
                  callback:(ChatCreationAlertCallback)callback;

- (void)startChatWithRegId:(NSNumber *)regId
                   subject:(NSString *)subject
                  callback:(ChatCreationCallback)callback;

- (void)startConferenceWithRegIds:(NSArray *)regIds
                          subject:(NSString *)subject
                         callback:(ChatCreationCallback)callback;
@end
