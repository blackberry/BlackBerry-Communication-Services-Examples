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

#import <BBMEnterprise/BBMEnterprise.h>

@class BBMGlobal;
@class BBMChat;
@class BBMChatMessage;
@class BBMUser;

#define BBMCore [CoreAccess sharedInstance]

extern NSString * const kMessageTag_Text;
extern NSString * const kMessageTag_Picture;
extern NSString * const kMessageTag_Quote;
extern NSString * const kMessageTag_Youtube;
extern NSString * const kMessageTag_Join;
extern NSString * const kMessageTag_Leave;
extern NSString * const kMessageTag_Shred;

extern NSString * const kMessageDataKey_Priority;
extern NSString * const kMessageDataValue_PriorityHigh;

typedef enum : NSUInteger {
    kMessagePriority_Default,
    kMessagePriority_High
} MessagePriority;

typedef void (^FindPinCallback)(NSDictionary *pins);

@interface CoreAccess : NSObject

+ (instancetype)sharedInstance;

@property (nonatomic,readonly) BBMDSGeneratedModel *model;


#pragma mark -

- (void)sendDeviceSwitch;

- (void)sendWipe;


#pragma mark - User

- (BBMUser *)currentUser;

- (NSString *)currentUserPin;

- (NSString *)currentUserURI;

- (BBMUser *)getUserForUserUri:(NSString *)userUri;

- (BBMUser *)getUserForUserRegId:(NSNumber *)userRegId;

#pragma mark - Messages

- (void)sendMessage:(NSString *)message
           toChatId:(NSString *)chatId
       withPriority:(MessagePriority)priority;

#pragma mark - Conversations

- (NSArray *)getChats;

- (BBMChat *)getChatForId:(NSString *)chatId;

- (BBMLiveList *)participantsForChat:(BBMChat *)chat;

- (NSArray *)getTypingUsersForChat:(NSString *)chatUri;

- (void)sendTypingNotification:(BOOL)isTyping toChat:(NSString *)chatUri;

- (void)updateChatSubject:(NSString *)subject chatId:(NSString *)chatId;

- (void)endChat:(BBMChat *)chat;

- (BBMChatMessage *)lastMessageForChat:(BBMChat *)chat;

#pragma mark - Message Retraction

- (void)recallMessageWithChatUri:(NSString *)chatUri andID:(long long)messageID;

- (void)deleteMessageWithChatUri:(NSString *)chatUri andID:(long long)messageID;



#pragma mark - Key Management

- (NSString *)getProfileKeysState;

- (void)requestKeys;

- (void)sendPrivateEncryptionKey:(NSString *)privateEncryptionKey
               privateSigningKey:(NSString *)privateSigningKey
             publicEncryptionKey:(NSString *)publicEncryptionKey
                publicSigningKey:(NSString *)publicSigningKey;


- (NSString *)requestChatKey:(NSString *)chatId;

- (void)sendKey:(NSString *)key forMailboxId:(NSString *)mailboxId;

- (void)sendPublicEncryptionKey:(NSString *)encryptionKey
                     signingKey:(NSString *)signingKey
                        forUser:(NSString *)regId;

- (void)setChatKeySynced:(NSString *)chatId;

- (void)setProfileKeySynced;

#pragma mark - Utilities

- (NSString *)getTempTransferDirectory;



#pragma mark - Search

- (void)inAppSearch:(NSString *)searchString;

@end
