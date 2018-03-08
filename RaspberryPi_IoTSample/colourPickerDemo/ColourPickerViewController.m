//
//  ColourPickerViewController.m
//  colourPickerDemo
//
//  Copyright (c) 2018 BlackBerry.  All Rights Reserved.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

#import "BBMAccess.h"
#import "BBMGoogleTokenManager.h"
#import "BBMAuthController.h"
#import "BBMAuthenticatedAccount.h"
#import "BBMEndpointManager.h"

#import "ConfigSettings.h"

#import "Global.h"
#import "BBMChatCreator.h"

#import <BBMEnterprise/BBMEnterprise.h>
#import <GoogleSignIn/GIDSignInButton.h>

#import "ColourPickerViewController.h"
#import "SelectColourViewController.h"
#import <MSColorPicker/MSColorPicker.h>

@interface ColourPickerViewController () <UIPopoverPresentationControllerDelegate, MSColorSelectionViewControllerDelegate>

@property (nonatomic, strong) UIView                *bgView;
@property (nonatomic, strong) UIButton              *openColourPickerButton;
@property (nonatomic, strong) UIButton              *signOutButton;

@property (nonatomic, strong) UIImageView           *ledMaskImageView;
@property (nonatomic, strong) UIView                *ledColorView;

@property (nonatomic, strong) ObservableMonitor     *serviceMonitor;
@property (nonatomic, strong) BBMChatCreator        *chatCreator;

@property (nonatomic, strong) NSString              *chatId;
@property (nonatomic, strong) ObservableMonitor     *chatMonitor;

@end

@implementation ColourPickerViewController

BOOL hasIstantiatedViewController = NO;
BOOL okayToSendColour = YES;
float sendRefreshTime = 0.1;

- (void)viewDidLoad {
    [super viewDidLoad];
    
    self.view.backgroundColor = [UIColor whiteColor];
    
    //  Set up ledMaskImageView UI.
    //  This UIImageView holds the led mask UIImage included in assets, presented
    //  overtop of the ledColourView UIView which displays the current selected colour.
    CGFloat maskWidth = self.view.frame.size.width;
    CGFloat maskHeight = maskWidth * 0.908;
    self.ledMaskImageView = [[UIImageView alloc] init];
    [self.ledMaskImageView setImage:[UIImage imageNamed:@"led-mask"]];
    self.ledMaskImageView.frame = CGRectMake(0, self.view.frame.size.height - maskHeight, maskWidth, maskHeight);
    
    //  Set up ledMaskImageView UI.
    self.ledColorView = [[UIView alloc] init];
    [self.ledColorView setBackgroundColor:[UIColor greenColor]];
    self.ledColorView.frame = CGRectMake(0, self.ledMaskImageView.frame.origin.y + 1, self.view.frame.size.width, self.ledMaskImageView.frame.size.height - 1);
    
    [self.view addSubview:self.ledColorView];
    [self.view addSubview:self.ledMaskImageView];
    
    //  Set up the openColourPickerButton UI.
    self.openColourPickerButton = [UIButton buttonWithType: UIButtonTypeRoundedRect];
    [self.openColourPickerButton addTarget: self
                                    action: @selector(pickNewColourButtonPressed:)
                          forControlEvents: UIControlEventTouchUpInside];
    self.openColourPickerButton.frame = CGRectMake(([self.view frame].size.width / 2) - 115, ([self.view frame].size.height / 2) - 24, 230, 48);
    [self.openColourPickerButton setTitle:@"Pick new colour" forState: UIControlStateNormal];
    [self.openColourPickerButton setExclusiveTouch: YES];
    [self.openColourPickerButton setTitleColor: [UIColor blackColor] forState: UIControlStateNormal];
    [[self.openColourPickerButton titleLabel] setFont:[UIFont fontWithName:@"AvenirNext-Regular" size:20.0]];
    [self.view addSubview: self.openColourPickerButton];
    
    //  Set up the signOutButton UI.
    self.signOutButton = [UIButton buttonWithType: UIButtonTypeRoundedRect];
    [self.signOutButton addTarget: self
                           action: @selector(signOut:)
                 forControlEvents: UIControlEventTouchUpInside];
    self.signOutButton.frame = CGRectMake(([self.view frame].size.width / 2) + 20, [self.view frame].size.height * 0.9, ([self.view frame].size.width / 2) - 20, 48);
    [self.signOutButton setTitle:@"Sign out" forState: UIControlStateNormal];
    [self.signOutButton setExclusiveTouch: YES];
    [self.signOutButton setTitleColor: [UIColor blackColor] forState: UIControlStateNormal];
    [[self.signOutButton titleLabel] setFont:[UIFont fontWithName:@"AvenirNext-Regular" size:14.0]];
    [self.view addSubview: self.signOutButton];
    
    
    NSNumber *regId = [[Global sharedInstance] authController].authState.regId;
    NSString *regIdString = regId.stringValue;
    
    printf("%s\n", [regIdString UTF8String]);
    
    //  Create chat with the peripheral device.
    //  Make sure that PERIPHERAL_REG_ID is set in ConfigSettings.h
    self.chatCreator = [[BBMChatCreator alloc] init];
    NSString *regID = PERIPHERAL_REG_ID;
    NSNumber *periphRegId = @([regID longLongValue]);
    
    [self.chatCreator startChatWithRegId:periphRegId
                                 subject:@"LEDColourChanger"
                                callback:^(NSString *chatId, BBMChatStartFailedMessageReason failReason)
     {
         self.chatId = chatId;
         NSLog(@"Chat ID: %@", chatId);
         
         if (failReason) {
             printf("Failed with reason: %d", failReason);
         }
     }];
}

- (void)viewDidAppear:(BOOL)animated{
    [super viewDidAppear:animated];
    
    UIColor *newColour = [Global sharedInstance].chosenColour;
    
    //  Set background colour of ledColourView to the newly selected colour.
    self.ledColorView.backgroundColor = newColour;
    
    //  Create hexidecimal string that represents the newly selected colour.
    NSString *colourHex = MSHexStringFromColor(newColour);
    printf("%s\n", [colourHex UTF8String]);
    
    if (okayToSendColour == YES) {
        //  Create timer to prevent constant data stream between app and peripheral device.
        //  Timer execution calls (void)changeSendColourParam, which sets okayToSendColour to YES.
        //  Configure time between sends using the sendRefreshTime parameter.
        [NSTimer scheduledTimerWithTimeInterval:sendRefreshTime
                                         target:self
                                       selector:@selector(changeSendColourParam)
                                       userInfo:nil
                                        repeats:NO];
        //  Set okayToSendColour to NO.
        //  Paramter will not become YES again until above timer fires.
        okayToSendColour = NO;
        
        //  Compose BBM chat message with colourHex as content payload.
        BBMChatMessageSendMessage *chatMessageSend = [[BBMChatMessageSendMessage alloc] initWithChatId:self.chatId
                                                                                                   tag:@"Text"];
        chatMessageSend.content = colourHex;
        
        //  Send the "BBMChatMessageSendMessage" to the BBMEnteprise service.
        [[BBMEnterpriseService service] sendMessageToService:chatMessageSend];
    }
    
}

- (IBAction)pickNewColourButtonPressed:(id)sender
{
    //  Pick new colour button has been pressed.
    //  Transition to MSColourSelectionViewController.
    UIStoryboard *storyBoard = [UIStoryboard storyboardWithName:@"Main" bundle:nil];
    SelectColourViewController *newVC = (SelectColourViewController *)[storyBoard instantiateViewControllerWithIdentifier:@"SelectColourViewController"];
    
    //  Set current ViewController as delegate for MSColorSelectionViewController.
    //newVC.delegate = self;
    newVC.color = self.ledColorView.backgroundColor;
    [self presentViewController:newVC animated:true completion:nil];
    
}

- (IBAction)signOut:(id)sender
{
    //  Wipe all local BBM data
    [[BBMEnterpriseService service] resetService];
    [[Global sharedInstance].authController signOut];
    
    //  Transition back to SignInViewController.
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (void)changeSendColourParam {
    okayToSendColour = YES;
}

@end
