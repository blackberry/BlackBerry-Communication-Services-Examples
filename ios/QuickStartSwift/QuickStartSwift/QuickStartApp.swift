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


/*!
 Our main application class.  .app() will give you the shared application instance from which you
 can query authController() for the authentication controller.
 */
class QuickStartApp
{
    private let _authController : BBMAuthController = BBMAuthController(tokenManager: BBMTestTokenManager.self,
                                                                          userSource: nil,
                                                                  keyStorageProvider: nil,
                                                                              domain: BBMConfigManager.default().sdkServiceDomain,
                                                                         environment: BBMConfigManager.default().environment)
    lazy private var _endpointManager : BBMEndpointManager = BBMEndpointManager()

    private static let appInstance : QuickStartApp = {
        let instance = QuickStartApp()
        instance.authController().startBBMEnterpriseService()
        return instance
    }()

    class func app() -> QuickStartApp {
        return appInstance;
    }

    //Get the shared authentication controller
    func authController() -> BBMAuthController {
        return _authController
    }

    //Get the shared endpoint manager
    func endpointManager() -> BBMEndpointManager {
        return _endpointManager
    }

}
