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

class DataSendViewController : UIViewController,
                               BBMEMediaDelegate,
                               UIDocumentInteractionControllerDelegate,
                               UITextFieldDelegate
{

    let mediaManager = BBMEnterpriseService.shared().mediaManager()!

    @IBOutlet weak var userIdField: UITextField!
    @IBOutlet weak var openConnectionButton: UIButton!
    @IBOutlet weak var statusField: UILabel!
    @IBOutlet weak var streamConsole: UITextView!
    @IBOutlet var actionButtons: [UIButton]!

    var connection : BBMDataConnection?
    var streamSendCount = 0
    var streamId : String?      //The channelId for the text stream

    override func viewDidLoad() {
        BBMEnterpriseService.shared().mediaManager().add(self)
        userIdField.text = ""
        statusField.text = "Ready"
        statusField.backgroundColor = UIColor.green
        setActionButtonsEnabled(false)
    }

    override func viewWillAppear(_ animated: Bool) {
        //Check for an active data connection and configure the UI and the callbacks
        if(DataTransferApp.app().callListener.activeDataConnection != nil) {
            connection = DataTransferApp.app().callListener.activeDataConnection
            dataConnectionAccepted(connection)
        }
    }

    func openDataConnection(_ userId: String, metaData: String!) {
        DataTransferApp.app().authController().userManager.getRegId(forUserId: userId) {
            mapping,success in
            if(success && mapping?.regId != nil) {
                self.mediaManager.startDataConnection(mapping?.regId, metaData: metaData) {
                    connection, error in
                    if(error != kMediaErrorNoError) {
                        self.statusField.text = "Connection Error"
                        self.statusField.backgroundColor = UIColor.red
                        self.openConnectionButton.isEnabled = true
                        return
                    }
                }
            }else{
                self.statusField.text = "Invalid User Id"
                self.openConnectionButton.isEnabled = true
            }
        }
    }

    @IBAction func openConnectionPressed(_ sender: Any) {
        self.userIdField.resignFirstResponder()

        if(self.connection != nil) {
            BBMEnterpriseService.shared().mediaManager().end(self.connection)
            return
        }

        guard let userIdStr = userIdField.text else {
            statusField.text = "Invalid User Id"
            return;
        }

        openConnectionButton.isEnabled = false
        openDataConnection(userIdStr, metaData: "")
        statusField.text = "Opening Connection"
        statusField.backgroundColor = self.openConnectionButton.tintColor
    }

    //Send a file
    @IBAction func sendFilePressed(_ sender: Any) {
        let dataPath = Bundle.main.path(forResource: "TestData", ofType: "txt")
        _ = self.connection?.sendFile(dataPath) {
            progress, done, error in
            var status = ""
            self.statusField.backgroundColor = UIColor.green
            if error != nil {
                status = "File Send Error"
                self.statusField.backgroundColor = UIColor.red
            }else if done {
                status =  "File Sent"
            }else {
                status =  "Sending File: \(progress)"
            }
            self.statusField.text = status
        }

    }

    //Send a block of NSData.  Here we load a file from disk and send the data.  We'll use the
    //description field to specifiy a name we can use to save the data on the other end.
    @IBAction func sendDataPressed(_ sender: Any) {
        guard let dataPath = Bundle.main.path(forResource: "TestData", ofType: "txt"),
            let data = NSData.init(contentsOfFile: dataPath) else
        {
            let status = "Error loading file"
            statusField.text = status
            self.statusField.backgroundColor = UIColor.red
            return
        }

        _ = self.connection?.send(data as Data, description: "TestData.txt") {
            progress, done, error in
            var status = ""
            self.statusField.backgroundColor = UIColor.green
            if error != nil {
                status = "Data Send Error"
                self.statusField.backgroundColor = UIColor.red
            }else if done {
                status = "Data Sent"
            }else { //No error, not done
                status = String(format: "Sending Data: %.0f", progress * 100)
            }
            self.statusField.text = status
        }
    }

    @IBAction func sendTextPessed(_ sender: Any) {
        streamSendCount = streamSendCount + 1
        if streamId == nil {
            streamId = connection?.createDataChannel("HelloWorld Stream")
        }

        let data = "Hello World \(streamSendCount)\n".data(using: .utf8)
        _ = self.connection?.send(data, toChannel: self.streamId!) {
            _, done, error in  //Progress has no meaning for streams
            var status = ""
            self.statusField.backgroundColor = UIColor.green
            if error != nil {
                status = "Data Send Error"
                self.statusField.backgroundColor = UIColor.red
            }else if done {
                status =  "Stream Data Sent"
            }else { //No error, not done
                status = "Sending Steamed Data"
            }
            self.statusField.text = status
        }
    }

    //Send a string.  Here we send a small test string
    @IBAction func sendStringPressed(_ sender: Any) {
        let stringToSend = "**********  TEST STRING  ************"

        _ = self.connection?.send(stringToSend, description: nil){
            progress, done, error in
            var status = ""
            self.statusField.backgroundColor = UIColor.green
            if error != nil {
                status = "String Send Error"
                self.statusField.backgroundColor = UIColor.red
            }else if done {
                status = "String Sent"
            }else { //No error, not done
                status = String(format: "Sending String: %.0f", progress * 100)
            }
            self.statusField.text = status
        }
    }

    //MARK: - MediaDelegate

    func incomingDataConnectionAvailable(_ connection: BBMDataConnection!) {
        statusField.text = "Incoming Connection"
        statusField.backgroundColor = openConnectionButton.tintColor
        userIdField.text = connection.peerRegId
        openConnectionButton.setTitle("Close Connection", for: .normal)
    }

    func dataConnectionAccepted(_ connection: BBMDataConnection!) {
        statusField.text = "Connection Open"
        statusField.backgroundColor = UIColor.green
        openConnectionButton.setTitle("Close Connection", for: .normal)
        self.connection = connection
        openConnectionButton.isEnabled = true
        setActionButtonsEnabled(true)
        setupCallbacks(connection)
    }

    func dataConnectionEnded(_ connection: BBMDataConnection!) {
        statusField.text = "Connection Ended"
        openConnectionButton.setTitle("Open Connection", for: .normal)
        statusField.backgroundColor = openConnectionButton.tintColor
        self.connection = nil
        openConnectionButton.isEnabled = true
        streamId = nil
        setActionButtonsEnabled(false)
    }

    func dataConnectionFailed(_ connection: BBMDataConnection!) {
        //dataConnectionEnded will always get called.   dataConnectionFailed will get called prior
        //to dataConnectionEnded if a failure of any kind was reported.
        NSLog("Connection ended with failure...")
        statusField.backgroundColor = UIColor.red
        statusField.text = "Connection Failed"
    }

    func setupCallbacks(_ connection: BBMDataConnection) {
        //Handle an incoming Data blob or stream.  For data blobs, we'll just write them out to
        //a temp file.  For streams, we'll render the stream text as a string if we can.

        connection.dataAvailableCallback = {
            channelData, error in
            guard let channelData = channelData else {
                return
            }

            var status : String!
            self.statusField.backgroundColor = UIColor.green
            if(channelData.type == kDataChannelData || channelData.type == kDataChannelString) {
                if error != nil {
                    status = "Data Receive Error"
                    self.statusField.backgroundColor = UIColor.red
                }else if(channelData.transferComplete) {
                    if(channelData.type == kDataChannelData) {
                        status = "Data Received"
                        self.saveTransfer(channelData)
                    }else if(channelData.type == kDataChannelString) {
                        status = "String Received"
                        self.showStringTransfer(channelData)
                    }
                }else {
                    //Note that the incoming default packet size is 128Kb so progress reported in
                    //~128Kb intervals
                    status = "Recieving data: \(channelData.progress)"
                }
            }else if(channelData.type == kDataChannelStream) {
                if error != nil {
                    status = "Data Receive Error"
                    self.statusField.backgroundColor = UIColor.red
                }else if(channelData.transferComplete) {
                    if let dataStr = String.init(data: channelData.data, encoding: .utf8) {
                        let consoleStr = self.streamConsole.text + dataStr
                        self.streamConsole.text = consoleStr
                        self.streamConsole.flashScrollIndicators()
                        status = "Streamed Data Received"
                    }
                }else {
                    status = "Recieving data: \(channelData.progress)"
                }
            }
            self.statusField.text = status
        }

        connection.fileAvailableCallback = {
            channelData, error in
            var status : String!
            self.statusField.backgroundColor = UIColor.green
            if nil != error {
                status = "File Receive Error"
                self.statusField.backgroundColor = UIColor.red
            }
            if(channelData?.transferComplete)! {
                status = "File Received"
                self.saveTransfer(channelData)
            }else{
                status = "Recieving file: \(channelData?.progress ?? 0)"
            }
            self.statusField.text = status
        }
    }

    func showStringTransfer(_ data: BBMChannelData?) {
        guard let channelData = data?.data else {
            NSLog("No transfer to save...")
            return
        }

        if let stringToRender = String(data: channelData, encoding: String.Encoding.utf8) {
            let stringAlert = UIAlertController(title: "String Received",
                                                      message: stringToRender,
                                                      preferredStyle: UIAlertController.Style.alert)
            let closeAction = UIAlertAction(title: "Close", style: UIAlertAction.Style.default, handler: nil)
            stringAlert.addAction(closeAction)

            self.present(stringAlert, animated: true, completion: nil)
        }
    }

    func saveTransfer(_ data: BBMChannelData?) {
        guard let data = data else {
            NSLog("No transfer to save...")
            return
        }

        let appDir = NSSearchPathForDirectoriesInDomains(.libraryDirectory, .userDomainMask, true)[0]

        if data.type == kDataChannelFile,
            let name = data.name,
            let filePath = data.filePath
        {
            let urlIn  = URL(fileURLWithPath:filePath)
            let urlOut = URL(fileURLWithPath:appDir).appendingPathComponent(name)
            do {
                if (FileManager.default.fileExists(atPath: urlOut.path)) {
                    do{
                        try FileManager.default.removeItem(at: urlOut)

                    } catch {}
                }
                try FileManager.default.moveItem(at: urlIn, to: urlOut)
                NSLog("Wrote Data To: \(urlOut.path)")
                notifyOfIncomingTransfer(urlOut)
            }catch{
                NSLog("Error copying file to \(urlOut.path)")
            }
        }else if data.type == kDataChannelData {
            let name = data.name ?? "Data.dat"
            guard let urlOut = NSURL(fileURLWithPath:appDir).appendingPathComponent(name) else {
                return
            }
            do {
                if (FileManager.default.fileExists(atPath: urlOut.path)) {
                    do{
                        try FileManager.default.removeItem(at: urlOut)
                    } catch {}
                }
                try data.data.write(to: urlOut)
                NSLog("Wrote Data To: \(urlOut.path)")
                notifyOfIncomingTransfer(urlOut)
            }catch{
                NSLog("Error writing data to \(urlOut.path)")
            }

        }
    }

    func notifyOfIncomingTransfer(_ path : URL)  {
        let incomingCallAlert = UIAlertController(title: "Incoming File",
                                                  message: "",
                                                  preferredStyle: UIAlertController.Style.alert)


        let viewAction = UIAlertAction(title: "View", style: UIAlertAction.Style.default) {
            _ in
            self.viewFile(path)
        }

        let declineAction = UIAlertAction(title: "Cancel", style: UIAlertAction.Style.default, handler: nil)

        incomingCallAlert.addAction(viewAction)
        incomingCallAlert.addAction(declineAction)
        self.present(incomingCallAlert, animated: true, completion: nil)
    }

    func viewFile(_ path: URL) {
        let docViewer = UIDocumentInteractionController.init()
        docViewer.url = path
        docViewer.delegate = self
        docViewer.presentPreview(animated: true)
    }

    public func documentInteractionControllerViewControllerForPreview(_ controller: UIDocumentInteractionController) -> UIViewController {
        return self
    }

    func setActionButtonsEnabled(_ enabled: Bool) {
        for button in actionButtons {
            button.isEnabled = enabled
        }
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        return true
    }
}



