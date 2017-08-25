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


#import "ContactManager.h"

#import <BBMEnterprise/BBMEnterprise.h>

#import "Contact.h"
#import "Firebase.h"
#import "CoreAccess.h"
#import "KeySync.h"

#define kDBUsersPath @"bbmsdk/identity/users"

@interface ContactManager ()

@property (nonatomic) FIRDatabaseReference *usersDBReference;
@property (nonatomic) FIRDatabaseHandle usersDBHandle;

@property (nonatomic) NSHashTable *listeners;
@property (nonatomic) NSArray *contacts;
@property (nonatomic) NSMutableDictionary *contactsDictionary;

@end

@implementation ContactManager

+ (ContactManager *)sharedInstance
{
    static ContactManager *sharedInstance;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        sharedInstance = [[ContactManager alloc] init];
        sharedInstance.usersDBReference = [[FIRDatabase database] referenceWithPath:kDBUsersPath];
        sharedInstance.listeners = [NSHashTable weakObjectsHashTable];
        sharedInstance.usersDBHandle = [sharedInstance.usersDBReference observeEventType:FIRDataEventTypeValue withBlock:^(FIRDataSnapshot * _Nonnull snapshot) {
            NSDictionary *rawContacts = snapshot.value;
            sharedInstance.contactsDictionary = [[NSMutableDictionary alloc] init];
            NSMutableArray *temp = [[NSMutableArray alloc] init];
            for(NSDictionary *key in rawContacts.allKeys) {

                NSDictionary *contactDictionary = rawContacts[key];
                Contact *contact = [[Contact alloc] initWithDictionary:contactDictionary];
                //Key is regId. This will be used for lookups.
                if(contact.regId > 0) {
                    sharedInstance.contactsDictionary[@(contact.regId.longLongValue)] = contactDictionary;

                    //Don't include local user as a contact
                    if ([BBMCore currentUser].regId.longLongValue != contact.regId.longLongValue) {
                        [temp addObject:contact];
                    }
                }
            }
            //Note that all users of the app become contacts for other users. This is not scalable
            //and is a simplification of how contact management could be handled. In production usually
            //the user would be able to add a subset of all the users as contacts.
            sharedInstance.contacts = temp;
            [sharedInstance notifyListeners];
        }];
    });
    return sharedInstance;
}

- (void)registerLocalUser:(NSString *)uid
                    regId:(NSString *)regId
                     name:(NSString *)name
                      pin:(NSString *)pin
                    email:(NSString *)email
                avatarUrl:(NSString *)avatarUrl
{
    if(uid.length > 0 && regId.length > 0 && name.length > 0) {
        Contact *contact = [[Contact alloc] init:uid regId:regId name:name pin:pin email:email avatarUrl:avatarUrl];
        [[self.usersDBReference child:uid] setValue:[contact asDictionary]];

        [[KeySync sharedInstance] syncProfileKeysForLocalUser:uid regId:regId];
    }
}

- (void)addContactsListener:(id<ContactsListener>)listener
{
    if(listener) {
        [self.listeners addObject:listener];
        [listener contactsChanged:self.contacts];
    }
}

- (void)removeContactsListener:(id<ContactsListener>)listener
{
    if(listener) {
        [self.listeners removeObject:listener];
    }
}

- (void)notifyListeners
{
    for(id<ContactsListener> listener in self.listeners)
    {
        [listener contactsChanged:self.contacts];
    }
}

- (Contact *)contactForRegId:(NSNumber *)regId
{
    if(regId) {
        NSDictionary *dictionary = self.contactsDictionary[regId];
        return [[Contact alloc] initWithDictionary:dictionary];
    }
    return nil;
}

@end
