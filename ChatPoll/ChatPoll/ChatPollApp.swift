/* Copyright (c) 2018 BlackBerry.  All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import Firebase

class ChatPollApp {
    private let _authController : BBMAuthController = {
        let controller = BBMAuthController(tokenManager:BBMGoogleTokenManager.self,
                          userSource:BBMFirebaseUserManager.self,
                          keyStorageProvider:BBMFirebaseKeyStorageProvider.self,
                          domain:SDK_SERVICE_DOMAIN,
                          environment:kBBMConfig_Sandbox)
        return controller!
    }()

    private static let appInstance : ChatPollApp = {
        FIRApp.configure()
        let instance = ChatPollApp()
        instance.authController().startBBMEnterpriseService()
        BBMEnterpriseService.shared().setLoggingMode(kBBMLogModeFileAndConsole)
        BBMEnterpriseService.shared().setLogLevel(kLogLevelVerbose)


        instance.authController().signInSilently()

        return instance
    }()

    class func app() -> ChatPollApp {
        return appInstance
    }

    func authController() -> BBMAuthController {
        return _authController;
    }

}
