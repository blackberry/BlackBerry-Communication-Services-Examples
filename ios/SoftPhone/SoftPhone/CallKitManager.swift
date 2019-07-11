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
import CallKit
import AVFoundation

typealias CKManagerCompletion =  (_ error:Error?) -> Void

enum CallState {
    case Connecting
    case Connected
    case Ended
    case Failed
    case Missed
}

//MARK: -

class CallKitManager : NSObject, CXProviderDelegate
{
    let callController : CXCallController = CXCallController.init(queue: DispatchQueue.main)

    let provider : CXProvider = {
        let config : CXProviderConfiguration = CXProviderConfiguration.init(localizedName: "Soft Phone")
        config.maximumCallGroups = 1;
        config.maximumCallsPerCallGroup = 1;
        config.supportsVideo = false;
        config.ringtoneSound = kIncomingRingtone

        return CXProvider.init(configuration: config)
    }()

    public var activeCallId : UUID?

    override init() {
        super.init()
        self.provider.setDelegate(self, queue: DispatchQueue.main)
    }

    public func reportOutgoingCall(_ call: BBMCall!)
    {
        let callId = UUID.init()
        self.activeCallId = callId

        let regId : String  = call.peerRegId
        let remoteHandle = CXHandle.init(type: .generic, value: regId)
        let startCallAction = CXStartCallAction.init(call: callId, handle: remoteHandle)
        let transaction = CXTransaction.init(action: startCallAction)

        self.callController.request(transaction) {
            (error: Error?) -> Void in
            if let error = error {
                NSLog("Error requesting transaction: \(error)")
            }
        }
    }

    public func showIncomingCall(call: BBMCall!, completion: @escaping CKManagerCompletion)
    {
        let regId : String = call.peerRegId

        //We'll use the regId for both the callee name an the caller identifier.  In practice, one
        //would typically map the regId to a contact
        let update = callUpdateWithName(regId, identifier: regId)
        let callId = UUID.init()
        self.activeCallId = callId

        self.provider.reportNewIncomingCall(with: callId, update: update) {
            (error: Error?) -> Void in
            completion(error)
        }
    }

    private func callUpdateWithName(_ name:String, identifier: String) -> CXCallUpdate
    {
        let update = CXCallUpdate.init()
        update.supportsDTMF = false
        update.supportsHolding = false
        update.supportsGrouping = false
        update.supportsUngrouping = false
        update.localizedCallerName = name

        //The CXHandle is used by CallKit to look up user information via an app extension which
        //is not implemented in this sample.
        //See the CallKit documentation for more details.
        let remoteHandle = CXHandle.init(type: .generic, value: identifier)
        update.remoteHandle = remoteHandle

        return update
    }

    public func updateCall(_ call:BBMCall?, state:CallState)
    {
        guard let callId = self.activeCallId, let call = call else {
            return;
        }

        let regId : String = call.peerRegId
        let update = callUpdateWithName(regId, identifier: regId)
        self.provider.reportCall(with: callId, updated: update)

        switch(state) {
            case .Connecting:
                break;
            case .Connected:
                self.provider.reportOutgoingCall(with: callId, connectedAt: Date())
            case .Ended:
                self.provider.reportCall(with: callId, endedAt: Date(), reason: .remoteEnded)
                self.activeCallId = nil
            case .Failed:
                self.provider.reportCall(with: callId, endedAt: Date(), reason: .failed)
                self.activeCallId = nil
            case .Missed:
                self.provider.reportCall(with: callId, endedAt: Date(), reason: .unanswered)
                self.activeCallId = nil
        }
    }

    //MARK: CXProviderDelegate

    func providerDidReset(_ provider: CXProvider) {
        BBMEnterpriseService.shared().mediaManager().hangup()
    }

    //Once callKit has activated the audio session, we have to update the category to
    //AVAudioSessionPlayAndRecord to match what the BBME Voice API expects internally
    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        do {
            try AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayAndRecord)
        } catch {
            NSLog("Failed to update audio session category")
        }
    }

    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        //Incoming calls need to be answered. Outgoing calls do not
        if(BBMEnterpriseService.shared().mediaManager().currentCallInfo?.incoming)! {
            BBMEnterpriseService.shared().mediaManager().answer()
        }
        action.fulfill(withDateConnected: Date())
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        BBMEnterpriseService.shared().mediaManager().decline()
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        BBMEnterpriseService.shared().mediaManager().setMutingEnabled(action.isMuted)
        action.fulfill()
    }

}

