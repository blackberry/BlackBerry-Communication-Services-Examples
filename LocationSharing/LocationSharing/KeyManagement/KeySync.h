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

@class BBMUser;
@class BBMChat;

@interface KeySync : NSObject

+ (KeySync*)sharedInstance;

- (void)syncUserKey:(BBMUser*)user;

- (void)syncChatKey:(BBMChat*)chat;

- (void)syncProfileKeysForLocalUser:(NSString *)uid regId:(NSString *)regId;

@end
