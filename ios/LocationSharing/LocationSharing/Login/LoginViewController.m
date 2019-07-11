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

#import "LoginViewController.h"
#import <BBMEnterprise/BBMEnterprise.h>
#import "BBMAccess.h"
#import "BBMAuthenticationDelegate.h"
#import "LocationSharingApp.h"

@interface LoginViewController ()

@property (weak,   nonatomic) IBOutlet UILabel *setupStateLabel;
@property (weak,   nonatomic) IBOutlet UIButton *signInButton;
@property (weak,   nonatomic) IBOutlet UIActivityIndicatorView *activityIndicator;

@property (strong, nonatomic) ObservableMonitor *authControllerMonitor;

@end

@implementation LoginViewController

- (void)viewDidLoad
{
    [super viewDidLoad];

    self.signInButton.hidden = YES;

    //Configure the authentication controller.
    [[[LocationSharingApp application] authController] setRootController:self];

    typeof(self) __weak weakSelf = self;
    self.authControllerMonitor = [ObservableMonitor monitorActivatedWithName:@"authControllerMonitor" block:^{
        [weakSelf observeServiceStateAndAuthState];
    }];
}


- (void)viewDidAppear:(BOOL)animated
{
    [super viewDidAppear:animated];
    //After login, a view controller is pushed to the navigation stack. To sign out the user needs
    //to push the back button. Sign out happens here.
    if([[[LocationSharingApp application] authController] startedAndAuthenticated]) {
        [self signOut];
    }
}


- (void)observeServiceStateAndAuthState
{
    //Update the UI based on the values for the service state and the auth state.
    BBMAuthState *authState = [LocationSharingApp application].authController.authState;
    BOOL serviceStarted = [LocationSharingApp application].authController.serviceStarted;
    NSString *setupState = authState.setupState;

    if (serviceStarted && (setupState  == kBBMSetupStateNotRequested ||
                           setupState == nil)) {
        self.setupStateLabel.text = @"Tap sign in to start.";
        [self.activityIndicator stopAnimating];
        self.signInButton.hidden = NO;
    }
    if(setupState == kBBMSetupStateOngoing ||
       setupState == kBBMSetupStateSuccess){
        self.setupStateLabel.text = @"";
        [self.activityIndicator startAnimating];
        self.signInButton.hidden = YES;
    }
    else if(setupState == kBBMSetupStateFull) {
        // If state is full, ask for list of endpoints and remove one so that setup can continue.
        [[LocationSharingApp application].authController.endpointManager deregisterAnyEndpointAndContinueSetup];
    }
    else if(!serviceStarted){
        self.setupStateLabel.text = @"Service not started.";
        [self.activityIndicator stopAnimating];
        self.signInButton.hidden = YES;
    }

    if(setupState == kBBMSetupStateSuccess) {
         [self loggedIn];
     }
}


#pragma mark -

- (IBAction)signOut
{
    //Wipe all local BBM data
    [[BBMEnterpriseService service] resetService];
    [[[LocationSharingApp application] authController] signOut];
}

- (IBAction)signIn
{
    [[[LocationSharingApp application] authController].tokenManager signIn];
}


- (void) loggedIn
{
    if(self.navigationController.topViewController == self) {
        [self.activityIndicator stopAnimating];
        [self performSegueWithIdentifier:@"loginSegue" sender:self];
    }
}

@end
