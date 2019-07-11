/* Copyright (c) 2018 BlackBerry.  All Rights Reserved.
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

#import "AccountViewController.h"
#import "BBMConfigManager.h"

#import "BBMAccess.h"
#import "BBMAuthController.h"
#import "BBMAuthenticatedAccount.h"
#import "BBMEndpointManager.h"
#import "BBMAppUserListener.h"
#import "BBMUserManager.h"
#import "UIView+Extra.h"

#import <BBMEnterprise/BBMEnterprise.h>

#import "BBMGoogleTokenManager.h"
#import <GoogleSignIn/GoogleSignIn.h>
#import <GoogleSignIn/GIDSignInButton.h>
#import "BBMAzureTokenManager.h"
#import "BBMAzureUserManager.h"

#import "BBMTestTokenManager.h"

@interface AccountViewController () <BBMConnectivityListener, BBMAuthControllerDelegate>

@property (weak, nonatomic) IBOutlet UILabel *serviceStateLabel;
@property (weak, nonatomic) IBOutlet UILabel *authTokenStateLabel;
@property (weak, nonatomic) IBOutlet UILabel *setupStateLabel;
@property (weak, nonatomic) IBOutlet UILabel *regIdLabel;
@property (weak, nonatomic) IBOutlet UILabel *domainLabel;
@property (weak, nonatomic) IBOutlet UILabel *userEmailLabel;
@property (weak, nonatomic) IBOutlet UILabel *serviceConnectivityLabel;

@property (weak, nonatomic) IBOutlet UIView   *signInButtonContainer;
@property (weak, nonatomic) IBOutlet UIButton *signOutButton;

@property (nonatomic, strong) GIDSignInButton *googleSignInButton;
@property (nonatomic, strong) UIButton        *signInButton;

@property (nonatomic, strong) BBMAuthController *authController;
@property (nonatomic, strong) ObservableMonitor *serviceMonitor;

@end

@implementation AccountViewController

- (void)viewDidLoad
{
    [super viewDidLoad];

    Class tokenManager;
    if([BBMConfigManager defaultManager].type == kGoogleSignIn)
    {
        self.googleSignInButton = [[GIDSignInButton alloc] initWithFrame:self.signInButtonContainer.bounds];
        [self.signInButtonContainer addSubviewAndContraintsWithSameFrame:self.googleSignInButton];
        tokenManager = [BBMGoogleTokenManager class];
    }
    else if([BBMConfigManager defaultManager].type == kAzureAD)
    {
        self.signInButton = [[UIButton alloc] init];
        [self.signInButton setTitle:@"Azure AD Sign In" forState:UIControlStateNormal];
        [self.signInButtonContainer addSubviewAndContraintsWithSameFrame:self.signInButton];
        [self.signInButton addTarget:self
                                   action:@selector(signIn:)
                         forControlEvents:UIControlEventTouchUpInside];
        [self.view layoutIfNeeded];
        self.signInButtonContainer.backgroundColor = [UIColor blueColor];
        tokenManager = [BBMAzureTokenManager class];
    }
    else if([BBMConfigManager defaultManager].type == kTestAuth)
    {
        self.signInButton = [[UIButton alloc] init];
        [self.signInButton setTitle:@"Sign In" forState:UIControlStateNormal];
        [self.signInButtonContainer addSubviewAndContraintsWithSameFrame:self.signInButton];
        [self.signInButton addTarget:self
                              action:@selector(signIn:)
                    forControlEvents:UIControlEventTouchUpInside];
        [self.view layoutIfNeeded];
        self.signInButtonContainer.backgroundColor = [UIColor blueColor];
        tokenManager = [BBMTestTokenManager class];
    }else{
        NSAssert(NO, @"Unsupported identity provider");
    }


    //Configure our buttons and labels
    self.signInButtonContainer.hidden = YES;
    self.signOutButton.hidden = YES;
    self.domainLabel.text = [BBMConfigManager defaultManager].sdkServiceDomain;

    //Listen for connectivity changes to the BBM Enterprise Service
    [[BBMEnterpriseService service] addConnectivityListener:self];



    //Create an AuthController instance.  This view controller will act as the delegate and the
    //root controller for any modally presented auth UI.  We will use the BBMGoogleTokenManager
    //(a wrapper around the GoolgeSignIn API) to fetch tokens
    self.authController = [[BBMAuthController alloc] initWithTokenManager:tokenManager
                                                               userSource:nil
                                                       keyStorageProvider:nil
                                                                   domain:[BBMConfigManager defaultManager].sdkServiceDomain
                                                              environment:[BBMConfigManager defaultManager].environment];

    self.authController.rootController = self;

    //Auth controller using delegation
    [self.authController addDelegate:self];


    /***********

    //Alternative: Auth controller using ObservableMonitor.  This approach monitors the serviceStarted
    //and authState properties on the authManager instance rather than registering the view controller
    //as an explicit delegate

    typeof(self) __weak weakSelf = self;
    self.serviceMonitor = [ObservableMonitor monitorActivatedWithName:@"serviceMonitor" block:^{
        [weakSelf serviceStateChanged:weakSelf.authController.serviceStarted];
        [weakSelf authStateChanged:weakSelf.authController.authState];
    }];
    
    *************/


    //Start the BBM Enterprise service.
    [self.authController startBBMEnterpriseService];

    //Resume our previous session
    [self.authController signInSilently];
}


#pragma mark - BBMAuthDelegate

//The AuthController will invoke this callback any time the user credentials/auth state changes
- (void)authStateChanged:(BBMAuthState *)authState
{
    self.authTokenStateLabel.text = authState.authTokenState ?: @"No Token";
    self.setupStateLabel.text = authState.setupState ?: @"Setup Not Started";
    self.regIdLabel.text = authState.regId.integerValue ? authState.regId.stringValue : @"";
    self.userEmailLabel.text = authState.account.email;

    self.signInButtonContainer.hidden = self.authController.startedAndAuthenticated;
    self.signOutButton.hidden = !self.authController.startedAndAuthenticated;

    //In order to complete the setup process, we must synchronize the profile keys.  For the sake of
    //brevity, we will simply set the key state to synced which allows setup to complete without setting up
    //a complete key provider.  See the other BBM Enterprise samples and/or BBMKeyManager in
    // /examples/support for details on how to export/import profile keys.
    //This is not required if you're using the default Spark Communications Key Management Service in which case, the
    //keys will be synced automatically after entering your application password
    if(![BBMConfigManager defaultManager].isUsingBlackBerryKMS &&
       authState.setupState == kBBMSetupStateOngoing &&
       [[[BBMEnterpriseService service] model] globalProfileKeysState] == kBBMKeyStateNotSynced)
    {
        NSDictionary* element = @{@"name":@"profileKeysState", @"value": @"Synced" };
        BBMRequestListChangeMessage *syncProfileKey = [[BBMRequestListChangeMessage alloc] initWithElements:@[element] type:@"global"];
        [[BBMEnterpriseService service] sendMessageToService:syncProfileKey];
    }

    //Our token has been rejected
    if(authState.authTokenState == kBBMAuthStateRejected) {
        self.signOutButton.hidden = NO;
        self.signInButtonContainer.hidden = YES;
    }

    //We're logged in but setup of BBM hasn't completed yet
    if(authState.setupState == kBBMSetupStateOngoing) {
        self.signInButtonContainer.hidden = YES;
        self.signOutButton.hidden = NO;
    }

    // If state is full, ask for list of endpoints and remove one so that setup can continue.
    // In practice, the user should be prompted to choose an endpoint to log out.
    if(authState.setupState == kBBMSetupStateFull) {
        [self.authController.endpointManager deregisterAnyEndpointAndContinueSetup];
    }

    [self.tableView reloadData];
}

- (void)serviceStateChanged:(BOOL)serviceStarted
{
    self.serviceStateLabel.text = serviceStarted ? @"Started" : @"Stopped";
    [self.tableView reloadData];
}

#pragma mark - BBMConnectivityListener

- (void)connectivityStateChange:(BOOL)connected strict:(BOOL)connectedStrict
{
    self.serviceConnectivityLabel.text = connected ? @"Connected" : @"Disconnected";
}


#pragma mark - UI Actions


- (IBAction)signOut:(id)sender
{
    //Wipe all local BBM data
    [[BBMEnterpriseService service] resetService];
    [self.authController signOut];
}

- (IBAction)signIn:(id)sender
{
    [self.authController.tokenManager signIn];
}



@end
