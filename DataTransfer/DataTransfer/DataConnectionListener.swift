/* Copyright (c) 2017 BlackBerry.  All Rights Reserved.
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
import UIKit

// The CallLister is an application-level object that listens for data transfers and notifies
//the user
class DataConnectionListener : NSObject, BBMEMediaDelegate
{
    var incomingCallAlert : UIAlertController?
    let mediaManager = BBMEnterpriseService.shared().mediaManager()!
    var activeDataConnection : BBMDataConnection?

    //Lazy so that we only create the chat creator *after* the service has 
    //started.  The chat creator will register listeners for the ListAdd and ChatStartFailed
    //messages which must be done only after the service has started.
    private lazy var chatCreator : BBMChatCreator = BBMChatCreator()

    override init() {
        super.init()
        mediaManager.add(self)
    }

    //MARK: Incoming Call Alert

    private func notifyOfIncomingDataConnection(regId: String!) {
        incomingCallAlert = UIAlertController(title:  String(format: "Incoming Data From %@", regId),
                                            message: "Data Connection",
                                     preferredStyle: UIAlertControllerStyle.alert)

        //It's also possible to answer with video enabled, by calling toggleOutgoingVideo 
        //immediately after answer()
        let acceptAction = UIAlertAction(title: "Accept", style: UIAlertActionStyle.default) {
            [weak self] (action) -> Void in
            if let connection = self?.activeDataConnection {
                self?.mediaManager.accept(connection)
            }
        }

        let declineAction = UIAlertAction(title: "Decline", style: UIAlertActionStyle.default) {
            [weak self] (action) -> Void in
            if let connection = self?.activeDataConnection {
                self?.mediaManager.end(connection)
            }
            self?.activeDataConnection = nil
        }

        incomingCallAlert!.addAction(acceptAction)
        incomingCallAlert!.addAction(declineAction)

        let keyWindow = UIApplication.shared.keyWindow!
        keyWindow.rootViewController?.present(incomingCallAlert!, animated: true, completion: nil)
    }

    //MARK: BBMMediaDelegate

    func incomingDataConnectionOffered(_ connection: BBMDataConnection!) {
        //This user interface in this sample supports only a single concurrent data connection
        //but the API is capable of several concurrent connections with different users
        if  activeDataConnection != nil {
            NSLog("This app supports only a single ongoing data connection")
            BBMEnterpriseService.shared().mediaManager().end(connection)
            return
        }

        //Like calls, data transfers are encrypted and first require us to load the public
        //keys for the other party.  The call will fail if dataConnectionSetupComplete is
        //called and the keys have not yet been loaded
        DataTransferApp.app().authController().keyManager.readUserKey(connection.peerRegId) {
            (regId,result) -> Void in
            if(result == kKeySyncResultSuccess) {
                self.activeDataConnection = connection
                self.mediaManager.dataConnectionSetupComplete(connection)
            }else{
                BBMEnterpriseService.shared().mediaManager().end(connection)
            }
        }
    }

    func incomingDataConnectionAvailable(_ connection: BBMDataConnection!) {
        self.notifyOfIncomingDataConnection(regId: connection.peerRegId)
    }

    func dataConnectionEnded(_ connection: BBMDataConnection!) {
        self.activeDataConnection = nil
    }

}




