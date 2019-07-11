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
import CoreTelephony

// The CallLister is an application-level object that listens for calls and presents the incoming
// and/or outgoing call UI over the main application window.

class CallListener : NSObject, BBMMediaDelegate
{
    var incomingCallAlert : UIAlertController?

    let mediaManager = BBMEnterpriseService.shared().mediaManager()!
    let callKitManager = SoftPhoneApp.app().callKitManager

    //Lazy so that we only create the chat creator *after* the service has 
    //started.  The chat creator will register listeners for the ListAdd and ChatStartFailed
    //messages which must be done only after the service has started.
    private lazy var chatCreator : BBMChatCreator = BBMChatCreator()

    override init() {
        super.init()
        mediaManager.add(self)
    }

    //MARK: Incoming Call Alert

    //If iOS CallKit is not supported, we will notify via an alert view.  A UILocalNotification
    //will also be required if the call is in the background
    private func notifyOfIncomingCall(_ call: BBMCall!) {
        let message = String(format: "Incoming Call From %@", call.peerRegId)
        incomingCallAlert = UIAlertController(title: "Incoming Call",
                                            message: message,
                                     preferredStyle: UIAlertControllerStyle.alert)

        //It's also possible to answer with video enabled, by calling toggleOutgoingVideo 
        //immediately after answer()
        let answerAction = UIAlertAction(title: "Answer", style: UIAlertActionStyle.default) {
            [weak self] (action) -> Void in
            if (self?.mediaManager.currentCallInfo != nil) {
                self?.mediaManager.answer()
            }
        }

        let hangupAction = UIAlertAction(title: "Decline", style: UIAlertActionStyle.default) {
            [weak self] (action) -> Void in
            if (self?.mediaManager.currentCallInfo != nil) {
                self?.mediaManager.decline()
            }
        }

        incomingCallAlert!.addAction(answerAction)
        incomingCallAlert!.addAction(hangupAction)

        let keyWindow = UIApplication.shared.keyWindow!
        keyWindow.rootViewController?.present(incomingCallAlert!, animated: true, completion: nil)
    }


    //MARK: BBMMediaDelegate

    //When the call ends, post a call event message if necessary and dismiss the incoming alert
    //controller.  The in-call UI will dismiss itself.
    func callEnded(_ call: BBMCall!) {
        if let ac = incomingCallAlert {
            ac.dismiss(animated: true, completion: nil)
            incomingCallAlert = nil;
        }

        if let callKitManager = self.callKitManager {
            callKitManager.updateCall(call, state: .Ended)
        }

        if( call.incoming == false ) {
            postCallEventMessage(call)
        }
    }

    func callDidFail(_ call: BBMCall!) {
        if let ac = incomingCallAlert {
            ac.dismiss(animated: true, completion: nil)
            incomingCallAlert = nil;
        }

        self.callKitManager?.updateCall(call, state: .Failed)
    }

    func outgoingCallInitiated(_ call: BBMCall!) {
        self.callKitManager?.reportOutgoingCall(call)
    }

    func outgoingCallRinging(_ call: BBMCall!) {
        self.callKitManager?.updateCall(call, state: .Connecting)
    }

    //A call has been connected.  Show the internal call UI
    func callConnected(_ call: BBMCall!) {
        let storyboard = UIStoryboard(name: "Main", bundle: nil)
        let controller = storyboard.instantiateViewController(withIdentifier: "mediaViewController")

        let keyWindow : UIWindow = UIApplication.shared.keyWindow!
        keyWindow.rootViewController?.present(controller, animated: true, completion: nil)

       self.callKitManager?.updateCall(call, state: .Connected)
    }


    //A new incoming call has arrived.  Accept it to start the ringers and show our incoming call UI
    func incomingCallDidArrive(_ call: BBMCall!) {
        self.mediaManager.acceptCall()

        //Use the native call UI if it's available
        if let callKitManager = self.callKitManager {
            callKitManager.showIncomingCall(call: call) {
                (error: Error?) -> Void in
                NSLog("Showing native call UI")
            }
        }else{
            self.notifyOfIncomingCall(call)
            NSLog("Showing in-app call UI")
        }
    }
    

    //MARK: Call Log

    //On the completion of an outgoing call, we'll post a message with the "CallEvent" tag to the
    //1-1 chat with the other party.  The chat will be created if it doesn't already exist
    private func postCallEventMessage(_ call:BBMCall!) {
        guard let regIdInt = CLongLong(call.peerRegId) else {
            NSLog("Invalid RegId when posting call Event")
            return;
        }

        let regId = NSNumber(value: regIdInt)
        let endTime = NSDate().timeIntervalSince1970
        var duration = endTime - call.connectedTime
        if(duration < 1.0 || call.missed || call.declined || call.connectedTime == 0) { duration = 0 }

        self.chatCreator.startChat(withRegId: regId , subject: "") {
            (chatId, failReason) -> Void in
            guard let chatId = chatId else {
                NSLog("Chat start failed.  Unable to post CallEvent message")
                return
            }
            let send = BBMChatMessageSendMessage(chatId: chatId, tag: kCallEventTag)
            //The rawData field can contain any arbitrary data... The call end reason, the
            //call duration, etc.  Here we populate only the time the call ended.
            send?.rawData = [kCallEventTimestampKey : endTime,
                             kCallEventReasonKey : callLogToString(call.logType),
                             kCallEventDurationKey : duration]
            BBMEnterpriseService.shared().sendMessage(toService: send)
        }
    }

}

//Converts the raw call event reason to a string.
func callLogToString(_ log:BBMCallLogType) -> String {
    switch (log) {
        case kCallLogNone:                  return ""
        case kCallLogEnded:                 return "Call Ended"
        case kCallLogDisconnected:          return "Call Disconnected"
        case kCallLogMissed:                return "Call Missed"
        case kCallLogBusy:                  return "Busy"
        case kCallLogUnavailable:           return "Remote Party Unavailalbe"
        case kCallLogCancelled:             return "Call Cancelled"
        case kCallLogDeclined:              return "Call Declined"
        case kCallLogConnectionError:       return "Connection Error"
        case kCallLogCompletedElsewhere:    return "Call Completed Elsewhere"
        case kCallLogDeclinedElsewhere:     return "Call Declined Elsewhere"
        default:                            return ""
    }
}




