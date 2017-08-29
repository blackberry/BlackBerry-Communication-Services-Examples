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
#import "CoreAccess.h"
#import <GoogleSignIn/GIDSignInButton.h>
#import <GoogleSignIn/GoogleSignIn.h>
#import "LocationManager.h"
#import "ConfigSettings.h"
#import "Firebase.h"
#import "ContactManager.h"

@interface LoginViewController () <GIDSignInUIDelegate, GIDSignInDelegate>

@property (weak, nonatomic) IBOutlet UILabel *setupStateLabel;
@property (weak, nonatomic) IBOutlet GIDSignInButton *googleSignInButton;
@property (weak, nonatomic) IBOutlet UIActivityIndicatorView *activityIndicator;
@property (nonatomic, strong) ObservableMonitor *authControllerMonitor;
@property (assign) BOOL serviceStarted;
@property (nonatomic, strong) NSString *setupState;

@end

@implementation LoginViewController

- (void)viewDidLoad
{
    [super viewDidLoad];

    self.googleSignInButton.hidden = YES;

    //Start the service by sending the app identifier.
    [[BBMEnterpriseService service] start:SDK_SERVICE_DOMAIN environment:kBBMConfig_Sandbox completionBlock:^(BOOL success) {

        //User should only be able to sign in if the services are started
        self.googleSignInButton.enabled = success;
        self.serviceStarted = success;
        [self updateUI];

        if(success) {
            //Automatically sign in if the user has previously signed in and the credentials are still valid.
            [[GIDSignIn sharedInstance] signInSilently];
        }
    }];

    [GIDSignIn sharedInstance].uiDelegate = self;
    [GIDSignIn sharedInstance].delegate = self;


}

- (void)connectWithUserName:(NSString *)username token:(NSString *)token {
    [[BBMEnterpriseService service] sendAuthToken:token forUserName:username setupStateBlock:^(NSString *authTokenState, NSString *setupState, NSNumber *regId) {
        self.setupState = setupState;
        if([setupState isEqualToString:kBBMSetupStateSuccess]){
            static dispatch_once_t onceToken;
            dispatch_once(&onceToken, ^{
                [FIRApp configure];
            });
            [self registerLocalUser:regId];
        }
        else if([setupState isEqualToString:kBBMSetupStateDeviceSwitch]){
            [BBMCore sendDeviceSwitch];
        }
        else {
            [[LocationManager sharedInstance] stopMonitoring];
        }

        [self updateUI];
    }];
}

- (void)updateUI
{
    if (self.serviceStarted && ([self.setupState isEqualToString:kBBMSetupStateNotRequested] ||
                                self.setupState == nil)) {
        self.setupStateLabel.text = @"Tap sign in to start.";
        [self.activityIndicator stopAnimating];
        self.googleSignInButton.hidden = NO;
    }
    else if([self.setupState isEqualToString:kBBMSetupStateOngoing] ||
       [self.setupState isEqualToString:kBBMSetupStateSuccess]){
        self.setupStateLabel.text = @"";
        [self.activityIndicator startAnimating];
        self.googleSignInButton.hidden = YES;
    }
    else if(!self.serviceStarted){
        self.setupStateLabel.text = @"Service not started.";
        [self.activityIndicator stopAnimating];
        self.googleSignInButton.hidden = YES;
    }

}

- (void)registerLocalUser:(NSNumber *)regId
{
    if(regId == nil) {
        return;
    }
    GIDGoogleUser *user = [GIDSignIn sharedInstance].currentUser;
    NSURL *avatarUrl = [user.profile imageURLWithDimension:120];

    //One last step. Use Google Sign-In credentials to sign in with Firebase Auth.
    GIDAuthentication *authentication = user.authentication;
    FIRAuthCredential *credential = [FIRGoogleAuthProvider credentialWithIDToken:authentication.idToken accessToken:authentication.accessToken];
    [FIRGoogleAuthProvider credentialWithIDToken:authentication.idToken
                                     accessToken:authentication.accessToken];

    [[FIRAuth auth] signInWithCredential:credential completion:^(FIRUser * _Nullable firebaseUser, NSError * _Nullable error) {
        if(error) {
            NSLog(@"FIRAuth sign-in error: %@",error.localizedDescription);
        }
        else {
            [[ContactManager sharedInstance] registerLocalUser:firebaseUser.uid
                                                         regId:regId.stringValue
                                                          name:user.profile.name
                                                           pin:[BBMCore currentUserPin]
                                                         email:user.profile.email
                                                     avatarUrl:avatarUrl.absoluteString];
            [[LocationManager sharedInstance] startLocationManager];
            [[LocationManager sharedInstance] startMonitoring];
            if(self.navigationController.topViewController == self) {
                [self.activityIndicator stopAnimating];
                [self performSegueWithIdentifier:@"loginSegue" sender:self];
            }
        }
    }];
}

#pragma mark - Google Sign-In methods

- (void)googleSignInStateChanged
{
    GIDGoogleUser *user = [GIDSignIn sharedInstance].currentUser;
    if (user == nil) {
        NSLog(@"Sign-in error: no user available");
        self.googleSignInButton.hidden = NO;

        return;
    }

    NSString *userId = user.userID;
    NSString *accessToken = user.authentication.accessToken;
    [self connectWithUserName:userId token:accessToken];

    self.googleSignInButton.hidden = YES;
}

- (void)signIn:(GIDSignIn *)signIn didSignInForUser:(GIDGoogleUser *)user withError:(NSError *)error
{
    if (error) {
        NSLog(@"Google sign-in error: %@", error.localizedDescription);
    }

    [self googleSignInStateChanged];
}

- (void)signIn:(GIDSignIn *)signIn didDisconnectWithUser:(GIDGoogleUser *)user withError:(NSError *)error
{
    if (error) {
        NSLog(@"Google sign-in disconnect error: %@", error.localizedDescription);
    }
    [self googleSignInStateChanged];
}

@end
