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
#import "ConfigSettings.h"

#import "BBMAccess.h"
#import "BBMAuthController.h"
#import "BBMAuthenticatedAccount.h"
#import "BBMEndpointManager.h"
#import "BBMAppUserListener.h"
#import "BBMUserManager.h"

#import <BBMEnterprise/BBMEnterprise.h>



#if USE_GOOGLEID
#import "BBMGoogleTokenManager.h"
#import <GoogleSignIn/GIDSignInButton.h>
#endif

#if USE_AZUREAD
#import "BBMAzureTokenManager.h"
#import "BBMAzureUserManager.h"
#endif

@interface AccountViewController () <BBMConnectivityListener, BBMAuthControllerDelegate>

@property (weak, nonatomic) IBOutlet UILabel *serviceStateLabel;
@property (weak, nonatomic) IBOutlet UILabel *authTokenStateLabel;
@property (weak, nonatomic) IBOutlet UILabel *setupStateLabel;
@property (weak, nonatomic) IBOutlet UILabel *regIdLabel;
@property (weak, nonatomic) IBOutlet UILabel *domainLabel;
@property (weak, nonatomic) IBOutlet UILabel *userEmailLabel;
@property (weak, nonatomic) IBOutlet UILabel *serviceConnectivityLabel;
@property (weak, nonatomic) IBOutlet UIButton *switchDeviceButton;
@property (weak, nonatomic) IBOutlet UIView   *signInButton;
@property (weak, nonatomic) IBOutlet UIButton *signOutButton;

@property (nonatomic, strong) BBMAuthController *authController;
@property (nonatomic, strong) ObservableMonitor *serviceMonitor;

@end

//USE_GOOGLEID/USE_AZUREAD are set in the project target additional compiler flags setting.
//The "QuickStart" target will use GoogleID automatically.  The QuickStartAzure target will
//use Azure Active Directory automatically.  User management for GoogleId is implemented via
//Firebase and is not demonstrated in this app.  User management for Azure AD is implemented
//via the Microsoft Graph API and is implemented below for registering the BBM registration ID
//for the local user with their active directory user entry.
#if USE_GOOGLEID
  #define TokenManagerClass BBMGoogleTokenManager
#elif USE_AZUREAD
  #define TokenManagerClass BBMAzureTokenManager
#endif

@implementation AccountViewController

- (void)viewDidLoad
{
    [super viewDidLoad];

#if USE_AZUREAD
    //Configuration for Azure active directory authentication.  These values are pulled from
    //ConfigSettings.h.
    [BBMAzureTokenManager setClientId:AZURE_CLIENT_ID
                             tenantId:AZURE_TENANT_ID
                             bbmScope:AZURE_BBMCLIENT_SCOPE];
    NSString *domain = SDK_SERVICE_DOMAIN_AZURE;
#elif USE_GOOGLEID
    //GoogleID will be configured automatically using the GoogleServicesInfo.plist file
    NSString *domain = SDK_SERVICE_DOMAIN;
#endif

    //Configure our buttons and labels
    self.signInButton.hidden = YES;
    self.switchDeviceButton.enabled = NO;
    self.signOutButton.hidden = YES;
    self.domainLabel.text = domain;

    //Listen for connectivity changes to the BBM Enterprise Service
    [[BBMEnterpriseService service] addConnectivityListener:self];



    //Create an AuthController instance.  This view controller will act as the delegate and the
    //root controller for any modally presented auth UI.  We will use the BBMGoogleTokenManager
    //(a wrapper around the GoolgeSignIn API) to fetch tokens
    self.authController = [[BBMAuthController alloc] initWithTokenManager:[TokenManagerClass class]
                                                               userSource:nil
                                                       keyStorageProvider:nil
                                                                   domain:domain
                                                              environment:SDK_ENVIRONMENT];

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

    self.signInButton.hidden = self.authController.startedAndAuthenticated;
    self.signOutButton.hidden = !self.authController.startedAndAuthenticated;

    //Whenever a user switches devices, a device switch should occur. This button gets
    //enabled whenever a device switch is required.
    self.switchDeviceButton.enabled = [authState.setupState isEqualToString:kBBMSetupStateDeviceSwitch];

    //In order to complete the setup process, we must synchronize the profile keys.  For the sake of
    //brevity, we will simply set the key state to synced.  See the other BBM Enterprise samples
    //and/or BBMKeyManager in /examples/support for details on how to export/import profile keys
    if([authState.setupState isEqualToString:kBBMSetupStateOngoing] &&
       [[[[BBMEnterpriseService service] model] globalProfileKeysState] isEqualToString:kBBMKeyStateNotSynced])
    {
        NSDictionary* element = @{@"name":@"profileKeysState", @"value": @"Synced" };
        BBMRequestListChangeMessage *syncProfileKey = [[BBMRequestListChangeMessage alloc] initWithElements:@[element] type:@"global"];
        [[BBMEnterpriseService service] sendMessageToService:syncProfileKey];
    }

    //Our token has been rejected
    if([authState.authTokenState isEqualToString:kBBMAuthStateRejected]) {
        self.signOutButton.hidden = NO;
        self.signInButton.hidden = YES;
    }

    //We're logged in but setup of BBM hasn't completed yet
    if([authState.setupState isEqualToString:kBBMSetupStateOngoing]) {
        self.signInButton.hidden = YES;
        self.signOutButton.hidden = NO;
    }

    // If state is full, ask for list of endpoints and remove one so that setup can continue.
    if([authState.setupState isEqualToString:kBBMSetupStateFull]) {
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

- (IBAction)switchDevice:(id)sender
{
    [BBMAccess sendSetupRetry];
}

- (IBAction)signOut:(id)sender
{
    //Wipe all local BBM data
    [[BBMEnterpriseService service] resetService];
    [self.authController signOut];
}

//This is unneeded for GoogleSignIn.  The GIDSignInButton implements this internally and presents
//the auth web view automatically.
#if USE_AZUREAD
- (IBAction)signInPressed:(id)sender
{
    [(BBMAzureTokenManager *)self.authController.tokenManager signIn];
}
#endif



@end
