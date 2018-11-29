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

#import "AppDelegate.h"

#import <BBMEnterprise/BBMEnterprise.h>
#import "BBMConfigManager.h"

#import <GoogleSignIn/GoogleSignIn.h>
#import <MSAL/MSAL.h>

@implementation AppDelegate

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    return YES;
}


- (BOOL)application:(UIApplication *)app
            openURL:(NSURL *)url
            options:(NSDictionary *)options
{
    if([BBMConfigManager defaultManager].type == kGoogleSignIn) {
        return [[GIDSignIn sharedInstance] handleURL:url
                                   sourceApplication:options[UIApplicationOpenURLOptionsSourceApplicationKey]
                                          annotation:options[UIApplicationOpenURLOptionsAnnotationKey]];
    }else if([BBMConfigManager defaultManager].type == kAzureAD) {
        return [MSALPublicClientApplication handleMSALResponse:url];
    }
    return NO;
}



@end
