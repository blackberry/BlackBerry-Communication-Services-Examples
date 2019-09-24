/* Copyright (c) 2019 BlackBerry Limited.  All Rights Reserved.
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

class CallLogViewController : UITableViewController {
    var callEvents : Array<BBMChatMessage>?
    var callEventMonitor : ObservableMonitor?

    override func viewDidAppear(_ animated: Bool) {
        monitorCallEvents()
    }

    private func monitorCallEvents() {
        //Monitor all of the existing chats for messages with the tag "CallEvent".
        //Add them to our call event array and sort them by timestamp
        callEventMonitor = ObservableMonitor(activatedWithName: "callEventMonitor") {
            [weak self] () -> Void in

            if (false == SoftPhoneApp.app().authController().startedAndAuthenticated) {
                self?.callEvents = Array()
                self?.tableView.reloadData()
                return
            }

            var allCallEvents = [BBMChatMessage]()
            let chatsList = BBMEnterpriseService.shared().model().chat!
            for chat in chatsList.observableArray as! Array<BBMChat> {
                //We can use BBMChatMessageCriteria to get a set list for each chat that includes
                //only the CallEvent messages.
                let criteria = BBMChatMessageCriteria()
                criteria.tag = kCallEventTag
                criteria.chatId = chat.chatId
                let callEventMessagMap = BBMEnterpriseService.shared().model().chatMessage(with: criteria)
                if(callEventMessagMap?.bbmState == kBBMStatePending) {
                    continue
                }
                for callEvent in callEventMessagMap?.observableArray as! Array<BBMChatMessage> {
                    if(callEvent.bbmState == kBBMStateCurrent) {
                        allCallEvents.append(callEvent)
                    }
                }
            }

            //Sort the call events by timestamp
            allCallEvents.sort() { (a,b) -> Bool in

                let aTime = Double(truncating: a.rawData[kCallEventTimestampKey] as! NSNumber)
                let bTime = Double(truncating: b.rawData[kCallEventTimestampKey] as! NSNumber)
                return aTime > bTime
            }

            self?.callEvents = allCallEvents
            self?.tableView.reloadData()
        }
    }

    ///MARK: TableView DataSource

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if let callEvents = callEvents {
            return callEvents.count
        }
        return 0
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: CallEventCell.defaultIdentifier, for: indexPath) as! CallEventCell
        let callEvent = callEvents?[indexPath.row]
        cell.setCallEvent(callEvent)
        return cell
    }
}


class CallEventCell : UITableViewCell {
    public static let defaultIdentifier = "callEventCell"
    var eventMonitor : ObservableMonitor?

    @IBOutlet weak var titleLabel: UILabel!
    @IBOutlet weak var durationLabel: UILabel!
    @IBOutlet weak var endTimeLabel: UILabel!
    @IBOutlet weak var reasonLabel: UILabel!

    private static let fmt : DateFormatter = {
        let fmt = DateFormatter()
        fmt.dateStyle = DateFormatter.Style.short
        fmt.timeStyle = DateFormatter.Style.medium
        return fmt
    }()

    public func setCallEvent(_ event: BBMChatMessage?) {
        guard let event = event else {
            titleLabel.text = "---"
            return;
        }

        self.eventMonitor = ObservableMonitor(activatedWithName: "EventMonitor") {
            [weak self] () -> Void in
            let chat = event.resolvedChatId
            var regIdStr = "(Unknown User)"

            if let participants = BBMAccess.participants(for: chat).array as? Array<BBMChatParticipant>,
               participants.count > 0,
               let user = BBMAccess.getUserForUserUri(participants[0].userUri),
               let regId = user.regId
            {
               regIdStr = regId.stringValue
            }
            self?.titleLabel.text = "Call with: " + regIdStr

            if let endTime = event.rawData[kCallEventTimestampKey] as? NSNumber {
                let date = Date(timeIntervalSince1970: TimeInterval(truncating: endTime))
                self?.endTimeLabel.text = "Ended: " + CallEventCell.fmt.string(from: date)
            }

            if let duration = event.rawData[kCallEventDurationKey] as? NSNumber {
                self?.durationLabel.text = "Duration: " + BBMUtilities.duration(asString: duration.doubleValue)
            }

            if let reason = event.rawData[kCallEventReasonKey] as? String {
                self?.reasonLabel.text = "Reason: " + reason
            }
        }
    }
}

