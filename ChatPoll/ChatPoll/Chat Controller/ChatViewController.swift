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

import UIKit

class ChatViewController: UIViewController, UITableViewDataSource, UITextFieldDelegate {

    var messages : Array<BBMChatMessage>?
    var chat : BBMChat?
    var chatMonitor : ObservableMonitor?
    var pollResultsMonitor : ObservableMonitor?

    static let textTag = "Text"
    static let pollTag = "Poll"
    static let pollVoteUpTag = "PollVoteUp"
    static let pollVoteDownTag = "PollVoteDown"

    @IBOutlet weak var pollSwitch: UISwitch!
    @IBOutlet weak var messageField: UITextField!
    @IBOutlet weak var tableView: UITableView!

    //MARK: View Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()

        self.tableView.transform = CGAffineTransform.init(scaleX: 1, y: -1)
        self.tableView.estimatedRowHeight = 80.0
        self.tableView.rowHeight = UITableViewAutomaticDimension
    }

    override func viewDidAppear(_ animated: Bool) {

        chatMonitor = ObservableMonitor.init(activatedWithName: "messageMonitor") {
            [weak self]() -> Void in
            guard let weakSelf = self else {
                return;
            }
            var messageIsPending : Bool = false
            let lastMsg : UInt64 = weakSelf.chat!.lastMessage
            let firstMsg : UInt64 = weakSelf.chat!.numMessages > 0 ? lastMsg - weakSelf.chat!.numMessages + 1 : 0

            var messages = Array<BBMChatMessage>()

            let msgMap : BBMLiveMap = BBMEnterpriseService.shared().model().chatMessage

            for msgId in firstMsg...lastMsg {
                let key : BBMChatMessageKey = BBMChatMessageKey.init(chatId: weakSelf.chat!.chatId, messageId: msgId)
                let chatMessage : BBMChatMessage = msgMap[key] as! BBMChatMessage
                //Only display messages with known tags
                guard chatMessage.tag != nil && weakSelf.isKnownTag(tag : chatMessage.tag) else {
                    continue
                }
                messageIsPending = messageIsPending || chatMessage.bbmState == kBBMStatePending
                messages.insert(chatMessage, at: 0)
            }
            if(messageIsPending == false) {
                self?.messages = messages
                self?.tableView.reloadData()
            }
        }

        self.title = self.chat?.subject

    }
    //MARK: Table view
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if let messages = self.messages {
            return messages.count
        } else {
            return 0;
        }
    }


    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let message = self.messages![indexPath.row]
        let cellIdentifier  = self.cellIdentifier(message: message)
        let cell : MessageCell = tableView.dequeueReusableCell(withIdentifier: cellIdentifier) as! MessageCell
        cell.transform = CGAffineTransform.init(scaleX: 1, y: -1)
        cell.configureMessage(chatMessage: message)
        cell.viewResultsCallback = {
            (chatMessage : BBMChatMessage) -> Void in
            self.viewResults(message: chatMessage)
        }
        return cell
    }

    func cellIdentifier(message : BBMChatMessage) -> String {
        if(message.tag == ChatViewController.pollTag) {
            return message.isIncomingFlagSet ? "incomingPollCell" : "outgoingPollCell"
        }
        return message.isIncomingFlagSet ? "incomingMessageCell" : "outgoingMessageCell"
    }

    //MARK: IB Actions

    @IBAction func sendPressed(_ sender: Any) {
        if(self.messageField.text == nil || self.messageField.text?.characters.count == 0) {
            return
        }

        let tag : String = self.pollSwitch.isOn ? ChatViewController.pollTag : ChatViewController.textTag
        if let chat = self.chat {
            let chatMessageSend : BBMChatMessageSendMessage = BBMChatMessageSendMessage(chatId: chat.chatId, tag:tag)
            chatMessageSend.content = self.messageField.text
            BBMEnterpriseService.shared().sendMessage(toService: chatMessageSend)
        }

        self.messageField.text = ""
        self.messageField.resignFirstResponder()
        self.pollSwitch.isOn = false
    }

    //MARK: Helpers

    func viewResults(message: BBMChatMessage) {

        //If the tag is "PollVoteUp" or "PollVoteDown" we need to get the referenced message
        //which is the poll.
        var messageId = message.messageId
        if(message.tag == ChatViewController.pollVoteUpTag || message.tag == ChatViewController.pollVoteDownTag) {
            //Get messageId for poll, which is the referenced message
            let reference : BBMChatMessage_Ref = message.ref.first as! BBMChatMessage_Ref
            messageId = reference.messageId
        }

        //Get list for upvotes by matching a criteria
        let forChatMessageCriteria : BBMChatMessageCriteria = BBMChatMessageCriteria()
        forChatMessageCriteria.chatId = message.chatId
        let forRef : BBMChatMessageCriteria_Ref = BBMChatMessageCriteria_Ref()
        forRef.tag = ChatViewController.pollVoteUpTag
        forRef.messageId = messageId
        forChatMessageCriteria.ref = forRef
        let forRefs : BBMLiveList = BBMEnterpriseService.shared().model().chatMessage(with: forChatMessageCriteria)

        //Get list of downvotes by matching a criteria
        let againstChatMessageCriteria : BBMChatMessageCriteria = BBMChatMessageCriteria()
        againstChatMessageCriteria.chatId = message.chatId
        let againstRef : BBMChatMessageCriteria_Ref = BBMChatMessageCriteria_Ref()
        againstRef.tag = ChatViewController.pollVoteDownTag
        againstRef.messageId = messageId
        againstChatMessageCriteria.ref = againstRef
        let againstRefs : BBMLiveList = BBMEnterpriseService.shared().model().chatMessage(with: againstChatMessageCriteria)

        pollResultsMonitor = ObservableMonitor.init(activatedWithName: "voteMonitor", selfTerminatingBlock: {
            [weak self]() -> Bool in
            guard let weakSelf = self else {
                return true;
            }
            if(forRefs.bbmState == kBBMStatePending || againstRefs.bbmState == kBBMStatePending) {
                return false
            }

            //Go through for and against votes and create string for each user.
            var voteList : String = ""
            for ref in forRefs.array as! Array<BBMChatMessage> {
                let user : BBMUser = ref.resolvedSenderUri
                voteList += ChatViewController.userDisplayName(user: user) + " voted YES.\n"
            }

            for ref in againstRefs.array as! Array<BBMChatMessage> {
                let user : BBMUser = ref.resolvedSenderUri
                voteList += ChatViewController.userDisplayName(user: user) + " voted NO.\n"
            }

            //Create string for total for and against votes
            let countString : String = String(forRefs.count) + " votes for.\n " + String(againstRefs.count) + " votes against."
            let alert =  UIAlertController.init(title:countString , message: voteList, preferredStyle: .alert)
            let alertAction = UIAlertAction(title: "Ok", style: .default, handler: nil)
            alert.addAction(alertAction)
            weakSelf.present(alert, animated: true, completion:nil)
            return true
        })
    }

    func isKnownTag(tag : String) -> Bool {
        return tag == ChatViewController.textTag || tag == ChatViewController.pollTag || tag == ChatViewController.pollVoteUpTag || tag == ChatViewController.pollVoteDownTag
    }

    class func userDisplayName(user : BBMUser) ->String {
        if(BBMAccess.currentUser().regId == user.regId) {
            return "You"
        }
        guard let displayName = BBMUIUtilities.displayName(for: user) else {
            return user.regId.stringValue
        }
        return displayName

    }
}

class MessageCell : UITableViewCell {
    var message : BBMChatMessage?
    var messageMonitor : ObservableMonitor?
    var viewResultsCallback: ((BBMChatMessage) -> Void)?
    @IBOutlet weak var bubbleView: UIView!
    @IBOutlet weak var messageLabel: UILabel!
    @IBOutlet weak var senderLabel: UILabel!

    override func awakeFromNib() {
        self.bubbleView.layer.cornerRadius = 8.0;
    }

    func configureMessage(chatMessage : BBMChatMessage?) {
        messageMonitor = ObservableMonitor.init(activatedWithName: "messageMonitor", block: {
            [weak self]() -> Void in
            guard let weakSelf = self else {
                return
            }
            guard let message = chatMessage else {
                return
            }
            weakSelf.message = message
            let senderUser : BBMUser = message.resolvedSenderUri
            if(senderUser.bbmState == kBBMStatePending) {
                return
            }
            weakSelf.bubbleView.backgroundColor = message.isIncomingFlagSet ? UIColor.init(red: 54.0/255.0, green: 137.0/255.0, blue: 170.0/255.0, alpha: 1.0) : UIColor.lightGray
            if(message.tag == ChatViewController.pollVoteUpTag) {
                let referencedMessage : BBMChatMessage = weakSelf.referencedMessage(chatMessage : message)
                if(referencedMessage.bbmState == kBBMStatePending) {
                    return
                }
                weakSelf.messageLabel.text = ChatViewController.userDisplayName(user: senderUser) + " voted YES for the poll:\n" + referencedMessage.content
            }
            else if(message.tag == ChatViewController.pollVoteDownTag) {
                let referencedMessage : BBMChatMessage = weakSelf.referencedMessage(chatMessage : message)
                if(referencedMessage.bbmState == kBBMStatePending) {
                    return
                }
                weakSelf.messageLabel.text =  ChatViewController.userDisplayName(user: senderUser) + " voted NO for the poll:\n" + referencedMessage.content
            }
            else {
                weakSelf.messageLabel.text = message.content
            }

            //set sender label for incoming messages
            if(message.isIncomingFlagSet && message.resolvedSenderUri.bbmState == kBBMStateCurrent) {
                let user : BBMUser = message.resolvedSenderUri;
                weakSelf.senderLabel.text = ChatViewController.userDisplayName(user: user)
            }
        })

    }

    func referencedMessage(chatMessage : BBMChatMessage) -> BBMChatMessage {
        let msgMap : BBMLiveMap = BBMEnterpriseService.shared().model().chatMessage
        let reference : BBMChatMessage_Ref = chatMessage.ref.first as! BBMChatMessage_Ref
        let key : BBMChatMessageKey = BBMChatMessageKey.init(chatId: chatMessage.chatId, messageId: reference.messageId)
        let referencedMessage : BBMChatMessage = msgMap[key] as! BBMChatMessage
        return referencedMessage
    }

    //MARK: IB Actions
    @IBAction func viewResultsTapped(_ sender: Any) {
        if(viewResultsCallback != nil && self.message != nil) {
            viewResultsCallback!(self.message!)
        }
    }
}

class IncomingMessageCell : MessageCell {
}

class OutgoingMessageCell : MessageCell {
}

class IncomingPollCell : MessageCell {
    var voteMonitor : ObservableMonitor!
    @IBOutlet weak var thumbDownButton: UIButton!
    @IBOutlet weak var thumbUpButton: UIButton!
    @IBOutlet weak var viewResultsButton: UIButton!
    @IBOutlet weak var verticalSeparator: UIView!
    @IBOutlet weak var horizontalSeparator: UIView!

    @IBAction func thumbDownTapped(_ sender: Any) {
        self.sendVote(vote: false)
    }

    @IBAction func thumbUpTapped(_ sender: Any) {
        self.sendVote(vote: true)
    }

    func sendVote(vote : Bool) {
        guard let message = self.message else {
            return
        }
        let tag : String = vote ? ChatViewController.pollVoteUpTag : ChatViewController.pollVoteDownTag
        let chatMessageSend : BBMChatMessageSendMessage = BBMChatMessageSendMessage(chatId: message.chatId, tag: tag)


        //Create a reference to the poll message
        let ref : BBMChatMessageSendMessage_Ref = BBMChatMessageSendMessage_Ref.init(messageId: message.messageId, tag: tag)
        chatMessageSend.ref = [ref]
        BBMEnterpriseService.shared().sendMessage(toService: chatMessageSend)

        //Record vote locally in locaData to avoid a second vote
        var localData : [AnyHashable: Any]
        if(message.localData == nil) {
            localData = [AnyHashable: Any]()
        }
        else {
            localData = message.localData
        }
        localData["pollVote"] = vote

        let messageId : String = String(message.messageId)
        let elements : [String : Any] = ["messageId":messageId,"chatId":message.chatId , "localData": localData]
        let requestListChange : BBMRequestListChangeMessage = BBMRequestListChangeMessage.init(elements: [elements], type: "chatMessage")
        BBMEnterpriseService.shared().sendMessage(toService: requestListChange)
    }

    override func configureMessage(chatMessage : BBMChatMessage?) {
        super.configureMessage(chatMessage: chatMessage)
        //Once the user has voted we hide the vote buttons and show the view results button
        voteMonitor = ObservableMonitor.init(activatedWithName: "voteMonitor") {
            [weak self]() -> Void in
            guard let weakSelf = self, let message = weakSelf.message else {
                return
            }

            let hasVoted : Bool = message.localData != nil && message.localData["pollVote"] != nil
            weakSelf.thumbDownButton.isHidden = hasVoted
            weakSelf.thumbUpButton.isHidden = hasVoted
            weakSelf.viewResultsButton.isHidden = !hasVoted
            weakSelf.verticalSeparator.isHidden = hasVoted
        }
    }
}

class OutgoingPollCell : MessageCell {
    @IBOutlet weak var viewResultsButton: UIButton!
}


