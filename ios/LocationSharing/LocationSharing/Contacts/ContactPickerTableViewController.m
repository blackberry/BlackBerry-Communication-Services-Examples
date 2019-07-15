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


#import "ContactPickerTableViewController.h"

#import "LocationSharingApp.h"
#import "ChatSubjectViewCell.h"
#import "ContactTableViewCell.h"

static NSString *const kContactTableViewCell = @"ContactTableViewCell";
static NSString *const kChatSubjectCell = @"ChatSubjectViewCell";

@interface ContactPickerTableViewController () <UITextFieldDelegate, UIScrollViewDelegate, BBMAppUserListener>

@property (nonatomic) NSArray *contacts;
@property (nonatomic) NSString *subject;
@property (nonatomic) NSMutableDictionary *selectedContacts;
@property (nonatomic, weak) IBOutlet UIBarButtonItem *doneButton;
@property (nonatomic) UITextField *subjectTextField;

@end

@implementation ContactPickerTableViewController

- (void)viewDidLoad
{
    [super viewDidLoad];

    // To hide extra separators at the end of the table
    self.tableView.tableFooterView = [UIView new];
    self.tableView.estimatedRowHeight = 50.0;
    self.tableView.rowHeight = UITableViewAutomaticDimension;

    self.selectedContacts = [[NSMutableDictionary alloc] init];

}

- (void)dealloc
{
    [[LocationSharingApp application].authController.userManager removeUserListener:self];
}

- (void)viewWillAppear:(BOOL)animated
{
    [super viewWillAppear:animated];
    [[LocationSharingApp application].authController.userManager addUserListener:self];
    [self updateNavigationBar];
}


- (IBAction)cancelPressed:(id)sender
{
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (IBAction)startPressed:(id)sender
{
    [self dismissViewControllerAnimated:YES completion:^{
        if(self.callback) {
            NSString *subject = (self.subject.length > 0) ? self.subject : @"";
            self.callback(self.selectedContacts.allValues, subject);
        }
    }];
}

- (NSInteger)adjustedContactRow:(NSIndexPath *)indexPath
{
    return indexPath.row -  1;
}

- (void)updateNavigationBar
{
    if(self.selectedContacts.count == 0) {
        self.navigationItem.title =  @"Select Contacts";
        self.doneButton.enabled = NO;
    }
    else {
        self.navigationItem.title = [NSString stringWithFormat:@"%lu selected",(unsigned long)self.selectedContacts.count];
        self.doneButton.enabled = YES;
    }
}


#pragma mark - ContactsListener

- (void)usersChanged:(NSArray*)contacts
{
    self.contacts = contacts;
    [self.tableView reloadData];
}

#pragma mark - UITableViewDataSource

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    return self.contacts.count + 1;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
    if(indexPath.row == 0) {
        ChatSubjectViewCell *cell = [tableView dequeueReusableCellWithIdentifier:kChatSubjectCell forIndexPath:indexPath];
        cell.subjectTextField.delegate = self;
        [cell setSubject:self.subject];
        self.subjectTextField = cell.subjectTextField;
        return cell;
    }
    ContactTableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:kContactTableViewCell forIndexPath:indexPath];
    BBMAppUser *contact = self.contacts[[self adjustedContactRow:indexPath]];
    [cell setContact:contact];

    return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    BBMAppUser *contact = self.contacts[[self adjustedContactRow:indexPath]];
    self.selectedContacts[contact.regId] = contact;
    [self updateNavigationBar];
}

- (void)tableView:(UITableView *)tableView didDeselectRowAtIndexPath:(NSIndexPath *)indexPath
{
    BBMAppUser *contact = self.contacts[[self adjustedContactRow:indexPath]];
    self.selectedContacts[contact.regId] = nil;
    [self updateNavigationBar];
}


#pragma mark - UITextFieldDelegate

- (void)textFieldDidEndEditing:(UITextField *)textField
{
    self.subject = textField.text;
}

#pragma mark - UIScrollViewDelegate

- (void)scrollViewDidScroll:(UIScrollView *)scrollView
{
    if(self.subjectTextField) {
        [self.subjectTextField resignFirstResponder];
    }
}

#pragma mark - Contact Management

- (IBAction)addContact:(id)sender
{
    [self dismissViewControllerAnimated:YES completion:^{
        [[[[LocationSharingApp application] authController] userManager] addUser];
    }];
}

@end
