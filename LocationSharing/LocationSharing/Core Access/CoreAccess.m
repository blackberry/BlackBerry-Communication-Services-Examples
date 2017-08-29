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

#import "CoreAccess.h"

#import <BBMEnterprise/BBMEnterprise.h>
#import "BBMChatMessage+LocationApp.h"

NSString * const kMessageTag_Text = @"Text";
NSString * const kMessageTag_Picture = @"Photo";
NSString * const kMessageTag_Quote = @"Quote";
NSString * const kMessageTag_Youtube = @"Youtube";
NSString * const kMessageTag_Join = @"Join";
NSString * const kMessageTag_Leave = @"Leave";
NSString * const kMessageTag_Shred = @"Shred";


NSString * const kMessageDataKey_Priority = @"priority";
NSString * const kMessageDataValue_PriorityHigh = @"HIGH";

@interface CoreAccess () <BBMDSMessageConsumer>

@property (nonatomic, readwrite) BBMDSGeneratedModel *model;
@property (nonatomic) NSMutableDictionary *pinLookupCallbacks;
@property (nonatomic) ObservableMonitor *serviceStateMonitor;
@end

@implementation CoreAccess

static CoreAccess  *s_sharedInstance;
+ (instancetype)sharedInstance
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        s_sharedInstance = [[CoreAccess alloc] _initPrivate];
    });
    return s_sharedInstance;
}

- (id)_initPrivate
{
    if ((self = [super init])) {
        self.pinLookupCallbacks = [NSMutableDictionary dictionary];
        __weak typeof(self) weakSelf = self;
        self.serviceStateMonitor = [ObservableMonitor monitorActivatedWithName:@"BBMECoreAccess-monitorServiceStartup" selfTerminatingBlock:^BOOL{
            if([BBMServiceLayer sharedInstance].serviceState == BBMCoreServiceStateStarted) {
                weakSelf.model = (BBMDSGeneratedModel*)[[BBMServiceLayer sharedInstance] connectionForMessageType:messageTypeBBM].model;
                [weakSelf.model.connection addConsumer:weakSelf forMessageNames:@[@"pinResult", @"goAway"]];
                //Request local user pin
                [weakSelf currentUserPin];
                return YES;
            }
            return NO;
        }];
    }
    return self;
}

- (void)dealloc {
    [self.model.connection removeConsumer:self forMessageNames:@[@"pinResult"]];
}

#pragma mark - BBMDSMessageConsumer

- (void)handleJSONMessage:(NSDictionary *)jsonMessage messageType:(NSString *)messageType listId:(NSString *)listId {
    if ([jsonMessage objectForKey:@"goAway"] != nil) {
        // If bbmcore is resetting itself, we need to reset our own data. We should also reset our identity
        //   with bbmcore in here.
        [BBMCore.model resync];
        return;
    }

    NSDictionary *data = jsonMessage[@"pinResult"];
    if (data) {
        NSString *cookie = data[@"cookie"];
        if (data && cookie) {
            //Response is valid if we still have a callback corresponding to this cookie
            FindPinCallback callback = self.pinLookupCallbacks[cookie];
            if (callback) {
                NSMutableDictionary *result = nil;
                if ([data[@"result"] isEqualToString:@"Success"]) {
                    NSArray *pins = data[@"pins"];
                    result = [NSMutableDictionary dictionary];
                    for (NSDictionary *pair in pins) {
                        NSNumber *regId = pair[@"regId"];
                        NSString *pin = pair[@"pin"];
                        [result setObject:pin forKey:regId];
                    }
                    
                }
                callback(result);
                [self.pinLookupCallbacks removeObjectForKey:cookie];
            }
        }
    }
}

- (void) handleOverflow {
}

#pragma mark -

- (void)sendDeviceSwitch
{
    BBMJSONMessage *msg = [[BBMSetupDeviceSwitchMessage alloc] initWithMigrationMessage:@""];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:msg];
}

#pragma mark - User
- (NSString *)currentUserPin
{
    return [self.model.global valueForKeyPath:@"localPin.value"];
}
- (NSString *)currentUserURI
{
    return [self.model.global valueForKeyPath:@"localUri.value"]; }

+ (NSSet *)keyPathsForValuesAffectingCurrentUserURI
{
    return [NSSet setWithObjects:@"model.global.localUri.value", nil];
}

- (BBMUser *)currentUser
{
    if (!self.currentUserURI) {
        return nil;
    }
    
    return [self.model.user valueForKey:self.currentUserURI];
}

+ (NSSet *)keyPathsForValuesAffectingCurrentUser
{
    return [NSSet setWithObjects:@"currentUserURI", nil];
}

- (BBMUser *)getUserForUserUri:(NSString *)userUri
{
    if (!userUri) {
        return nil;
    }
    BBMLiveMap *userLiveMap = self.model.user;
    BBMUser *user = [userLiveMap objectForKeyedSubscript:userUri];
    return user;
}


- (BBMUser *)getUserForUserRegId:(NSNumber *)userRegId
{
    BBMUserCriteria *criteria = [[BBMUserCriteria alloc] init];
    criteria.regId = userRegId;
    BBMLiveList *list = [self.model userWithCriteria:criteria];

    // Wait for list state to be current.
    if (list.bbmState != kBBMStateCurrent) {
        return nil;
    }

    // If user with userRegId exists then the list should contain only this one user.
    if (list.array.count > 0) {
        if (list.array.count > 1) {;
            NSAssert(NO, @"There should be only one user object per regId");
        }
        BBMUser *userFound = list.array.firstObject;
        return userFound;
    }

    // No user found that matched userRegId. Return empty "missing" BBMUser.
    BBMUser *userFound = [[BBMUser alloc] init];
    userFound.bbmState = kBBMStateMissing;
    return userFound;
}


- (void)sendWipe
{
    BBMWipeMessage *wipeMessage = [[BBMWipeMessage alloc] init];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:wipeMessage];
}

#pragma mark - Messages

- (void)sendMessage:(NSString *)message
           toChatId:(NSString *)chatId
       withPriority:(MessagePriority)priority
{
    BBMChatMessageSendMessage *chatMessage = [[BBMChatMessageSendMessage alloc] initWithChatId:chatId tag:kMessageTag_Text];
    chatMessage.content = message;
    
    if (priority == kMessagePriority_High) {
        chatMessage.rawData = @{kMessageDataKey_Priority: kMessageDataValue_PriorityHigh};
    }
    
    [[BBMServiceLayer sharedInstance] sendCoreMessage:chatMessage];
}

#pragma mark - Conversations


- (NSArray *)getChats
{
    return self.model.chat.array;
}

- (BBMChat *)getChatForId:(NSString *)chatId
{
    if (chatId.length == 0) {
        return nil;
    }
    return self.model.chat[chatId];
}

- (BBMLiveList *)participantsForChat:(BBMChat *)chat
{
    NSString *chatUri = [BBMChatMessage chatIDToURI:chat.chatId];
    BBMParticipantCriteria *criteria = [[BBMParticipantCriteria alloc] initWithConversationUri:chatUri];
    BBMLiveList *list = [self.model participantWithCriteria:criteria];
    return list;
}

- (NSArray *)getTypingUsersForChat:(NSString *)conversationUri
{
    NSArray *typingUsers = [self.model.typingUser array];
    
    //Filter the list to only include users typing in the given conversation
    NSMutableArray *typingUsersInConversation = [[NSMutableArray alloc] init];
    for (BBMTypingUser *typingUser in typingUsers) {
        if ([typingUser.conversationUri isEqualToString:conversationUri]) {
            [typingUsersInConversation addObject:typingUser];
        }
    }
    return [typingUsersInConversation copy];
}

- (void)sendTypingNotification:(BOOL)isTyping toChat:(NSString *)chatUri
{
    BBMTypingNotificationMessage *typingMessage = [[BBMTypingNotificationMessage alloc] initWithConversationUri:chatUri
                                                                                                         typing:isTyping];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:typingMessage];
}

- (void)updateChatSubject:(NSString *)subject chatId:(NSString *)chatId
{
    BBMJSONMessage *request = [[BBMRequestListChangeMessage alloc] initWithElements:@[@{@"chatId":chatId, @"subject":(subject ?: @"")}] type:@"chat"];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:request];
}

- (void)endChat:(BBMChat *)chat
{
    NSString *chatURI = [BBMChatMessage chatIDToURI:chat.chatId];
    BBMStopConversationMessage_Conversations *conv = [[BBMStopConversationMessage_Conversations alloc] initWithAction:kBBMStopConversationMessage_Conversations_ActionLeave
                                                                                                      conversationUri:chatURI];
    NSDictionary *convDict = [conv asDictionary];
    BBMStopConversationMessage *request = [[BBMStopConversationMessage alloc] initWithConversations:@[convDict]];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:request];
}

- (BBMChatMessage *)lastMessageForChat:(BBMChat *)chat
{
    if(chat.numMessages == 0) {
        return nil;
    }
    NSString *messageIdentifier = [NSString stringWithFormat:@"%@|%lld", chat.chatId, chat.lastMessage];
    BBMChatMessage *message = [BBMCore.model.chatMessage valueForKey:messageIdentifier];
    return message;
}

#pragma mark - Message Retraction

- (void)recallMessageWithChatUri:(NSString *)conversationUri andID:(long long)messageID
{
    BBMRecallMessageMessage *recallMessageMessage = [[BBMRecallMessageMessage alloc] initWithConversationUri:conversationUri identifier:messageID];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:recallMessageMessage];
}

- (void)deleteMessageWithChatUri:(NSString *)conversationUri andID:(long long)messageID
{
    BBMDeleteMessageMessage *deleteMessageMessage = [[BBMDeleteMessageMessage alloc] initWithConversationUri:conversationUri identifier:messageID];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:deleteMessageMessage];
}

#pragma mark - Key Management

- (NSString *)getProfileKeysState
{
    BBMLiveMap *global = [[CoreAccess sharedInstance].model valueForKey:@"global"];
    BBMGlobal *profileKeysState = [global valueForKeyPath:@"profileKeysState"];
    if (profileKeysState.bbmState == kBBMStatePending || profileKeysState.bbmState == kBBMStateMissing) {
        return nil;
    }
    return profileKeysState.value;
}

- (void)requestKeys
{
    BBMProfileKeysExportMessage *keyExportMsg = [[BBMProfileKeysExportMessage alloc] init];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:keyExportMsg];
}

- (void)sendPrivateEncryptionKey:(NSString *)privateEncryptionKey
               privateSigningKey:(NSString *)privateSigningKey
             publicEncryptionKey:(NSString *)publicEncryptionKey
                publicSigningKey:(NSString *)publicSigningKey
{
    BBMProfileKeysImportMessage_PrivateKeys *privateKeys = [[BBMProfileKeysImportMessage_PrivateKeys alloc] initWithEncryption:privateEncryptionKey signing:privateSigningKey];
    BBMProfileKeysImportMessage_PublicKeys *publicKeys = [[BBMProfileKeysImportMessage_PublicKeys alloc] initWithEncryption:publicEncryptionKey signing:publicSigningKey];

    BBMProfileKeysImportMessage *exportMsg = [[BBMProfileKeysImportMessage alloc] initWithPrivateKeys:privateKeys publicKeys:publicKeys];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:exportMsg];
}

- (NSString *)requestChatKey:(NSString *)chatId
{
    BBMChatKeyExportMessage *keyExportMsg = [[BBMChatKeyExportMessage alloc] initWithChatId:chatId];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:keyExportMsg];
    return keyExportMsg.cookie;
}

- (void)sendKey:(NSString *)key forMailboxId:(NSString *)mailboxId
{
    BBMChatKeysImportMessage_Keys *keys = [[BBMChatKeysImportMessage_Keys alloc] initWithKey:key mailboxId:mailboxId];
    BBMChatKeysImportMessage *keyImportMsg = [[BBMChatKeysImportMessage alloc] initWithKeys:@[keys]];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:keyImportMsg];
}

- (void)sendPublicEncryptionKey:(NSString *)encryptionKey signingKey:(NSString *)signingKey forUser:(NSString *)regId
{
    BBMUserKeysImportMessage_Keys *keys = [[BBMUserKeysImportMessage_Keys alloc] initWithEncryptionPublicKey:encryptionKey regId:regId signingPublicKey:signingKey];
    BBMUserKeysImportMessage *importMsg = [[BBMUserKeysImportMessage alloc] initWithKeys:@[keys]];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:importMsg];
}

- (void)setChatKeySynced:(NSString *)chatId
{
    NSLog(@"Setting chat key for chat %@ to Synced", chatId);
    NSDictionary *element = @{@"chatId" : chatId, @"keyState" : @"Synced"};
    BBMRequestListChangeMessage *syncChatKey = [[BBMRequestListChangeMessage alloc] initWithElements:@[element] type:@"chat"];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:syncChatKey];
}

- (void)setProfileKeySynced
{
    NSLog(@"Setting profile key state to Synced");
    NSDictionary* element = @{@"name":@"profileKeysState", @"value": @"Synced" };
    BBMRequestListChangeMessage *syncProfileKey = [[BBMRequestListChangeMessage alloc] initWithElements:@[element] type:@"global"];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:syncProfileKey];
}

#pragma mark - Utilities

- (NSString *)getTempTransferDirectory
{
    NSString *tmpDir =  [BBMServiceLayer whitelistedTemporaryDirectory];
    NSString *transferDir = [tmpDir stringByAppendingPathComponent:@"transfer"];
    return transferDir;
}

#pragma mark - Search

- (void)inAppSearch:(NSString *)searchString
{
    BBMSearchMessage *searchMsg = [[BBMSearchMessage alloc] initWithText:searchString];
    [[BBMServiceLayer sharedInstance] sendCoreMessage:searchMsg];
}

@end
