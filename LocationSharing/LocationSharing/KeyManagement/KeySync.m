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


#import "KeySync.h"
#import "CoreAccess.h"
#import "Firebase.h"
#import "NSString+Base64.h"
#import "ContactManager.h"
#import "LocalUser.h"



static NSString *const kDBPrivateKeyStorePath           = @"privateKeyStore";
static NSString *const kDBPublicKeyStorePath            = @"publicKeyStore";
static NSString *const kDBRegIdsPath                    = @"regIds";

static NSString *const kDBPrivateKeyStoreEncryptionName = @"encryptionKey";
static NSString *const kDBPrivateKeyStoreSigningName    = @"signingKey";
static NSString *const kDBPrivateKeyStoreRegIdName      = @"regId";

static NSString *const kDBPublicKeyStoreEncryptionName  = @"encryptionKey";
static NSString *const kDBPublicKeyStoreSigningName     = @"signingKey";

static NSString *const kBbmdsProfileKeysMessageName     = @"profileKeys";
static NSString *const kBbmdsChatKeyMessageName         = @"chatKey";



@interface KeySync () <BBMDSMessageConsumer> {
    NSMutableDictionary *_chatIdsToKeyRequestCookies;
}

@property (nonatomic) LocalUser *localUser;
@property (nonatomic) ObservableMonitor *profileKeysMonitor;

@property (nonatomic) FIRDatabaseReference *localUserPrivateKeystoreDBReference;
@property (nonatomic) FIRDatabaseReference *localUserPublicKeystoreDBReference;

@end


@implementation KeySync

+ (KeySync*)sharedInstance
{
    static KeySync *sharedInstance;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        sharedInstance = [[KeySync alloc] init];
        sharedInstance.localUser = [[LocalUser alloc] init];
        [BBMCore.model.connection addConsumer:sharedInstance forMessageNames:@[kBbmdsChatKeyMessageName, kBbmdsProfileKeysMessageName]];
        sharedInstance->_chatIdsToKeyRequestCookies = [NSMutableDictionary dictionary];
    });
    return sharedInstance;
}

- (void)dealloc
{
    [BBMCore.model.connection removeConsumer:self forMessageNames:@[kBbmdsChatKeyMessageName, kBbmdsProfileKeysMessageName]];
}

-(void)syncProfileKeysForLocalUser:(NSString *)uid regId:(NSString *)regId
{
    self.localUser.uid = uid;
    self.localUser.regId = regId;

    typeof(self) __weak weakSelf = self;
    self.profileKeysMonitor = [ObservableMonitor monitorActivatedWithName:@"ProfileKeysMonitor" selfTerminatingBlock:^BOOL
    {
        // We get the global value from the model
        NSString *profileKeysState = [[CoreAccess sharedInstance] getProfileKeysState];
        if (profileKeysState == nil) {
           return NO;
        }
       
        // If the key state is 'Synced' then we have nothing to do
        if ([profileKeysState isEqualToString:@"NotSynced"]) {
            // Check to see if we have keys stored in Firebase
            NSString *localUserPrivateKeyPath = [NSString stringWithFormat:@"%@/%@", kDBPrivateKeyStorePath, uid];
            weakSelf.localUserPrivateKeystoreDBReference = [[FIRDatabase database] referenceWithPath:localUserPrivateKeyPath];
            [weakSelf.localUserPrivateKeystoreDBReference observeSingleEventOfType:FIRDataEventTypeValue withBlock:^(FIRDataSnapshot * _Nonnull snapshot)
            {
                if (snapshot.exists) {
                    NSDictionary *privateKeyDict = snapshot.value;
                    NSString *storedRegId = [privateKeyDict objectForKey:kDBPrivateKeyStoreRegIdName];
                    if ([storedRegId isEqualToString:regId] == NO) {
                        NSLog(@"Error: Stored regId does not match our current regId");
                        return;
                    }
                    
                    // The regIds match so we're certain that we have the right keys
                    weakSelf.localUser.privateEncryptionKey = [privateKeyDict objectForKey:kDBPrivateKeyStoreEncryptionName];
                    weakSelf.localUser.privateSigningKey = [privateKeyDict objectForKey:kDBPrivateKeyStoreSigningName];
                    
                    [weakSelf sendKeysToCoreIfRequired];
                } else {
                    [[CoreAccess sharedInstance] requestKeys];
                }
            }];
           
            NSString *localUserPublicKeyPath = [NSString stringWithFormat:@"%@/%@", kDBPublicKeyStorePath, regId];
            weakSelf.localUserPublicKeystoreDBReference = [[FIRDatabase database] referenceWithPath:localUserPublicKeyPath];
            [weakSelf.localUserPublicKeystoreDBReference observeSingleEventOfType:FIRDataEventTypeValue withBlock:^(FIRDataSnapshot * _Nonnull snapshot)
            {
                if (snapshot.exists) {
                    NSDictionary *publicKeyDict = snapshot.value;
                   
                    weakSelf.localUser.publicEncryptionKey = [publicKeyDict objectForKey:kDBPublicKeyStoreEncryptionName];
                    weakSelf.localUser.publicSigningKey = [publicKeyDict objectForKey:kDBPublicKeyStoreSigningName];
                   
                    [weakSelf sendKeysToCoreIfRequired];
                } else {
                    [[CoreAccess sharedInstance] requestKeys];
                }
            }];
        }
       
        // We no longer need this monitor
        return YES;
    }];
}

- (void)sendKeysToCoreIfRequired
{
    if (self.localUser.privateSigningKey.length == 0 ||
        self.localUser.privateEncryptionKey.length == 0 ||
        self.localUser.publicEncryptionKey.length == 0 ||
        self.localUser.publicSigningKey.length == 0)
    {
        // We don't have all the required keys, do nothing
        return;
    }
    
    NSString *profileKeysState = [[CoreAccess sharedInstance] getProfileKeysState];
    if ([profileKeysState isEqualToString:@"NotSynced"])
    {
        [[CoreAccess sharedInstance] sendPrivateEncryptionKey:self.localUser.privateEncryptionKey
                                            privateSigningKey:self.localUser.privateSigningKey
                                          publicEncryptionKey:self.localUser.publicEncryptionKey
                                             publicSigningKey:self.localUser.publicSigningKey];
    }
}

- (void)syncUserKey:(BBMUser*)user
{
    if (user.keyState == kBBMUser_KeyStateImport) {
        NSString *userPublicKeyPath = [NSString stringWithFormat:@"publicKeyStore/%@", user.regId];
        FIRDatabaseReference *userPublicKeyDBReference = [[FIRDatabase database] referenceWithPath:userPublicKeyPath];
        [userPublicKeyDBReference observeSingleEventOfType:FIRDataEventTypeValue withBlock:^(FIRDataSnapshot * _Nonnull snapshot)
        {
            if (snapshot.exists) {
                NSDictionary *publicKeys = snapshot.value;
                NSString *encryptionKey = publicKeys[kDBPublicKeyStoreEncryptionName];
                NSString *signingKey = publicKeys[kDBPublicKeyStoreSigningName];
                NSString *regId = [user.regId stringValue];

                NSLog(@"Sending public user keys to bbmcore");
                [[CoreAccess sharedInstance] sendPublicEncryptionKey:encryptionKey signingKey:signingKey forUser:regId];
            }
        }];
    }
}

- (void)syncChatKey:(BBMChat*)chat
{
    if (chat.keyState == kBBMChat_KeyStateExport) {
        // Export the key from bbmcore, it will be returned in a 'chatKey' message
        NSLog(@"Requesting chat key from bbmcore");
        NSString *cookie = [[CoreAccess sharedInstance] requestChatKey:chat.chatId];

        //We need to map the request key cookie to the chatId we're requesting the key for
        //The chatKey message does not include the chatId.  The cookie can be used to
        //determine which response belongs to which request
        _chatIdsToKeyRequestCookies[cookie] = chat.chatId;
    } else if (chat.keyState == kBBMChat_KeyStateImport) {
        // Look for the key in Firebase
        FIRDatabaseReference *chatKeyDBReference = [self getChatKeyDBReferenceForMailboxId:chat.mailboxId];
        [chatKeyDBReference observeSingleEventOfType:FIRDataEventTypeValue withBlock:^(FIRDataSnapshot * _Nonnull snapshot)
         {
             if (snapshot.exists == NO) {
                 // The key doesn't exist so we need to ask for one.
                 // The key will be returned in a chatKey message.
                 NSLog(@"Requesting chat key from bbmcore, key not available in Firebase.");
                 [[CoreAccess sharedInstance] requestChatKey:chat.chatId];
                 return;
             }
             
             // We already have a key in Firebase, use it
             NSLog(@"Sending key found in Firebase to bbmcore");
             NSString *key = snapshot.value;
             
             // Send the key to bbmcore
             [[CoreAccess sharedInstance] sendKey:key forMailboxId:chat.mailboxId];
         }];
    }
}

- (FIRDatabaseReference*)getChatKeyDBReferenceForMailboxId:(NSString *)mailboxId
{
    NSString *uid = self.localUser.uid;
    NSString *encodedMailboxId = [mailboxId asUnpaddedBase64UrlSafeString];
    NSString *chatKeyPath = [NSString stringWithFormat:@"privateKeyStore/%@/mailboxes/%@", uid, encodedMailboxId];
    return [[FIRDatabase database] referenceWithPath:chatKeyPath];
}

#pragma mark - BBMDSConsumer

- (void)handleJSONMessage:(NSDictionary *)message
              messageType:(NSString *)messageType
                   listId:(NSString *)listId
{
    if ([message.allKeys.firstObject isEqualToString:kBbmdsChatKeyMessageName]) {
        NSDictionary *chatKey = message[kBbmdsChatKeyMessageName];
        NSString *key = chatKey[@"key"];
        NSAssert(key != nil, @"key cannot be nil");

        NSString *cookie = chatKey[@"cookie"];
        NSString *chatId = _chatIdsToKeyRequestCookies[cookie];
        if(nil == chatId) {
            NSLog(@"Error - could not find chat Id for chatKey message");
            return;
        }
        BBMChat *chat = [BBMCore getChatForId:chatId];
        NSString *mailboxId = chat.mailboxId;
        NSAssert(mailboxId != nil, @"mailboxId cannot be nil");

        [_chatIdsToKeyRequestCookies removeObjectForKey:cookie];

        FIRDatabaseReference *chatKeyDBReference = [self getChatKeyDBReferenceForMailboxId:mailboxId];
        [chatKeyDBReference observeSingleEventOfType:FIRDataEventTypeValue withBlock:^(FIRDataSnapshot * _Nonnull snapshot)
         {
             if (snapshot.exists) {
                 NSString *existingKey = snapshot.value;
                 if ([existingKey isEqualToString:key]) {
                     // We already have this key stored in Firebase, do nothing.
                     NSLog(@"Received chatKey from bbmcore but identical key exists in Firebase, ignoring.");
                     return;
                 }
             }
             NSLog(@"Writing chatKey received from bbmcore to Firebase.");
             [chatKeyDBReference setValue:key];

             //Notify that we have sync'd the chat key. This will set the keyState
             //to "Synced"
             [BBMCore setChatKeySynced:chatId];
         }];
    } else if ([message.allKeys.firstObject isEqualToString:kBbmdsProfileKeysMessageName]) {
        NSDictionary *profileKey = message[kBbmdsProfileKeysMessageName];
        NSDictionary *privateKeys = profileKey[@"privateKeys"];
        NSDictionary *publicKeys = profileKey[@"publicKeys"];
        
        self.localUser.privateEncryptionKey = privateKeys[@"encryption"];
        self.localUser.privateSigningKey = privateKeys[@"signing"];
        self.localUser.publicEncryptionKey = publicKeys[@"encryption"];
        self.localUser.publicSigningKey = publicKeys[@"signing"];

        [self.localUserPrivateKeystoreDBReference setValue:self.localUser.privateKeystoreDict];
        
        NSString *localUserRegIdsPath = [NSString stringWithFormat:@"%@/%@", kDBRegIdsPath, self.localUser.regId];
        FIRDatabaseReference *regIdsDBReference = [[FIRDatabase database] referenceWithPath:localUserRegIdsPath];
        [regIdsDBReference setValue:self.localUser.uid];

        [self.localUserPublicKeystoreDBReference setValue:self.localUser.publicKeystoreDict];

        //Notify that we have synced the profile keys.  This will set the global profileKeyState
        //to "Synced"
        [BBMCore setProfileKeySynced];
    }
}

@end
