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

class StartChatViewController : UIViewController {

    @IBOutlet weak var regIdField: UITextField!
    @IBOutlet weak var subjectField: UITextField!
    @IBOutlet weak var startChatButton: UIButton!

    @IBOutlet var chatCreator: BBMChatCreator!

    @IBAction func startPressed(_ sender: Any) {
        if(self.validateFields()) {
            self.regIdField.resignFirstResponder()
            self.subjectField.resignFirstResponder()
            self.startChat()
        }
    }

    func validateFields () -> Bool {
        if((self.subjectField.text?.characters.count)! < 1) {
            self.subjectField.resignFirstResponder()
            return false
        }

        if((self.regIdField.text?.characters.count)! < 1) {
            self.regIdField.resignFirstResponder()
            return false
        }
        return true
    }

    func startChat() {
        self.startChatButton.isEnabled = false
        self.regIdField.isEnabled = false
        self.subjectField.isEnabled = false

        guard let regIdStr = regIdField.text else {
            return
        }
        let splitRegIds = regIdStr.components(separatedBy: ",")

        var regIds = Array<NSNumber>()
        for regIdStr in splitRegIds {
            let regIdInt = CLongLong(regIdStr)
            let regId = NSNumber(value: regIdInt!)
            regIds.append(regId)
        }

        guard let subject = self.subjectField.text else {
            return
        }


        self.chatCreator.startConference(withRegIds : regIds as [Any], subject : subject) {
            chatId, failReason in
            guard let chatId = chatId else {
                return
            }
            if(chatId != "") {
                self.navigationController?.popViewController(animated: true)
            }
            else {
                self.startChatButton.isEnabled = true
                self.regIdField.isEnabled = true
                self.subjectField.isEnabled = true

                let alertController = UIAlertController(title : "Chat Start Failed", message:"Unable to start chat", preferredStyle: .alert)
                let dismiss = UIAlertAction(title : "Ok", style : .default, handler: nil)
                alertController.addAction(dismiss)
                self.present(alertController, animated: true, completion: nil)
            }
        }
    }
}

