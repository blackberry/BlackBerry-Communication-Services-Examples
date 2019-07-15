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
import UIKit


class PlaceCallViewController : UIViewController, BBMMediaDelegate
{
    @IBOutlet weak var userIdField: UITextField!
    @IBOutlet weak var callButton: UIButton!
    @IBOutlet weak var statusLabel: UILabel!

    let mediaManager = BBMEnterpriseService.shared().mediaManager()!

    override func viewWillAppear(_ animated: Bool) {
        mediaManager.add(self)
        statusLabel.text = ""
        resetCallButton()
    }

    func resetCallButton() {
        callButton.isEnabled = true
        callButton.setTitle("Start Call", for: UIControlState.normal)
    }

    @IBAction func placeCallPressed(_ sender: Any) {
        userIdField.resignFirstResponder()

        if mediaManager.currentCallInfo != nil {
            mediaManager.hangup()
            return
        }

        if (false == SoftPhoneApp.app().authController().startedAndAuthenticated) {
            statusLabel.text = String("Call Failed.  User not authenticated")
            return;
        }

        guard let userIdStr = userIdField.text else{
            return;
        }

        let placeCallAction : MediaPermissionsCallback = {
            (granted: Bool) -> Void in
            if(granted == false) {
                return;
            }
            self.placeCall(userIdStr)
            self.callButton.setTitle("Cancel Call", for: UIControlState.normal)
        }

        //Before placing a call, we need to request permission to access the microphone.
        //Camera access request is automatic if the camera is enabled for a call once connected
        //If placeing a call with video pre-enabled, specify kVideo as the mode here
        mediaManager.requestMediaPermissions(callback: placeCallAction, mode: kVoice)
    }


    func placeCall(_ userId : String) {
        statusLabel.text = String(format: "Looking Up %@", userId)
        SoftPhoneApp.app().authController().userManager.getRegId(forUserId: userId) {
            mapping,success in
            if(success && mapping?.regId != nil) {
                self.mediaManager.callRegId(mapping?.regId, mediaMode: kVoice) {
                    (error) -> Void in
                    self.callButton.isEnabled = true
                    if(error != kMediaErrorNoError) {
                        self.statusLabel.text = "Unable to place call (" + String(error.rawValue) + ")"
                        self.resetCallButton()
                        return;
                    }

                    self.statusLabel.text = String(format: "Calling %@", userId)
                }

            }else{
                self.statusLabel.text = "Invalid User Id"
                self.resetCallButton()
            }
        }

    }


    //Tap gesture recognizer callback
    @IBAction func dismissKeyboard(_ sender: Any) {
        userIdField.resignFirstResponder()
    }


    //MARK: MediaManager Delegate

    //We're only monitoring the call state to update our UI here.  The CallListener will handle
    //presenting the various modal UI elements as needed.  

    func callEnded(_ call: BBMCall!) {
        //If the call failed, we don't want to update the UI here, we'll update in in call callDidFail
        if(!call.failed) {
            statusLabel.text = "Call Ended"
        }
        resetCallButton()
    }

    func callDidFail(_ call: BBMCall!) {
        statusLabel.text = "Call Failed"
        resetCallButton()
    }

}
