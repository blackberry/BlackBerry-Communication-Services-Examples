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

import Foundation



//Our main application instance.
//This handles logging into firebase, configuring the media manager and monitoring and 
//synchronizing chats and their associated keys via BBMKeyManager
class DataTransferApp
{
    private let _authController : BBMAuthController! = {
        let instance = BBMAuthController.fromConfigFile();
        return instance;
    }();

    var authMonitor: ObservableMonitor?
    var serviceMonitor : ObservableMonitor?
    var callListener : DataConnectionListener!

    private static let appInstance : DataTransferApp = {
        let instance = DataTransferApp()

        //Set our logging mode to echo to the console

        instance.authController().startBBMEnterpriseService()
        BBMEnterpriseService.shared().setLoggingMode(kBBMLogModeFileAndConsole)
        BBMEnterpriseService.shared().setLogLevel(kLogLevelVerbose)

        instance.startServiceMonitor()

        instance.authController().signInSilently()

        return instance
    }()

    class func app() -> DataTransferApp {
        return appInstance
    }

    //Get the shared authentication controller
    func authController() -> BBMAuthController {
        return _authController
    }

    private func startMediaManager() {
        //Start the media manager with the default log path
        let mediaManager = BBMEnterpriseService.shared().mediaManager()!
        mediaManager.start(withLogPath: nil)
    }

    private func startServiceMonitor() {
        serviceMonitor = ObservableMonitor(activatedWithName: "ServiceMonitor") {
            [weak self] () -> Bool in
            if(self?.authController().serviceStarted == true) {
                self?.startMediaManager()
                self?.callListener = DataConnectionListener()

                return true
            }
            return false
        }
    }
}
