//
//  SignInViewController.m
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

#import "SignInViewController.h"
#import "ConfigSettings.h"
#import "ColourPickerViewController.h"

#import "Global.h"

#import "BBMAccess.h"
#import "BBMGoogleTokenManager.h"
#import "BBMAuthController.h"
#import "BBMAuthenticatedAccount.h"
#import "BBMEndpointManager.h"

#import <BBMEnterprise/BBMEnterprise.h>
#import <GoogleSignIn/GIDSignInButton.h>

@interface SignInViewController () <BBMConnectivityListener, BBMAuthControllerDelegate, UIViewControllerTransitioningDelegate>

@property (nonatomic, strong) GIDSignInButton       *googleSignInButton;

@property (nonatomic, strong) UITextView            *welcomeTextView;

@property (nonatomic, strong) UILabel               *headerLabelA;
@property (nonatomic, strong) UILabel               *headerLabelB;

@property (nonatomic, strong) BBMEndpointManager    *endpointManager;
@property (nonatomic, strong) ObservableMonitor     *serviceMonitor;

@end

@implementation SignInViewController

- (void)viewDidLoad
{    
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor whiteColor];
    
    //  Configure our buttons and labels
    
    //  Listen for connectivity changes to the BBM Enterprise Service
    [[BBMEnterpriseService service] addConnectivityListener:self];
    [Global sharedInstance].authController.rootController = self;
    
    
    //  Set up headerLabelA UI.
    self.headerLabelA = [[UILabel alloc] init];
    self.headerLabelA.text = @"Go ahead.";
    [self.headerLabelA setFont: [UIFont fontWithName: @"AvenirNext-Bold" size:44.0]];
    [self.headerLabelA setTextColor: [UIColor darkGrayColor]];
    self.headerLabelA.frame = CGRectMake([self.view frame].size.width * 0.11, [self.view frame].size.height * 0.42, [self.view frame].size.width * 0.78, [self.view frame].size.height * 0.065);
    [self.view addSubview:self.headerLabelA];
    
    //  Set up headerLabelB UI.
    self.headerLabelB = [[UILabel alloc] init];
    self.headerLabelB.text = @"Sign in with Google.";
    [self.headerLabelB setFont: [UIFont fontWithName: @"AvenirNext-Regular" size:27.0]];
    [self.headerLabelB setTextColor: [UIColor greenColor]];
    self.headerLabelB.frame = CGRectMake([self.view frame].size.width * 0.11, self.headerLabelA.frame.origin.y + self.headerLabelA.frame.size.height, self.headerLabelA.frame.size.width, self.headerLabelA.frame.size.height * 0.8);
    [self.view addSubview:self.headerLabelB];
    
    //  Set up googleSignInButton UI.
    self.googleSignInButton = [[GIDSignInButton alloc] init];
    self.googleSignInButton.frame = CGRectMake(([self.view frame].size.width / 2) - 115, (5 * [self.view frame].size.height / 6) - 24, 230, 48);
    self.googleSignInButton.style = kGIDSignInButtonStyleStandard;
    self.googleSignInButton.colorScheme = kGIDSignInButtonColorSchemeLight;
    [self.view addSubview: self.googleSignInButton];
    self.googleSignInButton.hidden = NO;
    
}

- (void)viewDidAppear:(BOOL)animated
{
    [self.serviceMonitor deActivate];
    self.serviceMonitor = nil;
}

#pragma mark - BBMAuthDelegate

//The AuthController will invoke this callback any time the user credentials/auth state changes
- (void)authStateChanged:(BBMAuthState *)authState
{
    //  print the user's Reg ID to the console.
    NSString *regId = authState.regId.integerValue ? authState.regId.stringValue : @"";
    
    printf("\n%s\n", [regId UTF8String]);
    
    // If state is full, ask for list of endpoints and remove one so that setup can continue.
    if([authState.setupState isEqualToString:kBBMSetupStateFull]) {
        // If state is full, ask for list of endpoints and remove one so that setup can continue.
        [[[Global sharedInstance] endpointManager] deregisterAnyEndpointAndContinueSetup];
    }
}

- (void)serviceStateChanged:(BOOL)serviceStarted
{
    NSString *serviceText = serviceStarted ? @"Started" : @"Stopped";
    printf("%s\n", [serviceText UTF8String]);
}

#pragma mark - BBMConnectivityListener

- (void)connectivityStateChange:(BOOL)connected strict:(BOOL)connectedStrict
{
    NSString *connectivityText = connected ? @"Connected" : @"Disconnected";
    printf("%s\n", [connectivityText UTF8String]);
    
    if (connected == YES) {
        //  Transition to the ColourPickerViewController.
        UIStoryboard *storyBoard = [UIStoryboard storyboardWithName:@"Main" bundle:nil];
        ColourPickerViewController *newVC = (ColourPickerViewController *)[storyBoard instantiateViewControllerWithIdentifier:@"ColourPickerViewController"];
        [self presentViewController:newVC animated:true completion:nil];
    }
}
@end
