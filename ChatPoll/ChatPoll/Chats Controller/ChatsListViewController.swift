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

class ChatsListViewController : UIViewController, UITableViewDelegate, UITableViewDataSource {
    var chatsMonitor : ObservableMonitor!
    var chats : Array<BBMChat>?
    @IBOutlet weak var tableView: UITableView!

    override func viewDidAppear(_ animated: Bool) {
        chatsMonitor = ObservableMonitor.init(activatedWithName: "chatsMonitor") {
            [weak self] () -> Void in
            let chatsList : NSArray = BBMEnterpriseService.shared().model().chat.observableArray! as NSArray
            var validChats = Array<BBMChat>()
            for chat in chatsList as! Array<BBMChat>  {
                if(!chat.isHiddenFlagSet && chat.state != kBBMChat_StateDefunct) {
                    validChats.append(chat)
                }
            }

            self?.chats = validChats
            self?.tableView.reloadData()
        }
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int
    {
        if( self.chats != nil) {
            return (self.chats?.count)!
        }
        return 0;
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell
    {
        let cell : ChatCell =  tableView.dequeueReusableCell(withIdentifier: "chatCell", for: indexPath) as! ChatCell
        cell.displayChat(chat: (self.chats?[indexPath.row])!)
        return cell
    }

    override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
        if(segue.identifier == "showChat") {
            let destination = segue.destination
            if destination is ChatViewController {
                let chatViewController = segue.destination as! ChatViewController
                let path : NSIndexPath = self.tableView.indexPath(for: sender as! UITableViewCell)! as NSIndexPath
                chatViewController.chat = (self.chats?[path.row])!
            }
        }
    }
}

class ChatCell : UITableViewCell {
    var messageMonitor : ObservableMonitor!

    @IBOutlet weak var participantLabel: UILabel!
    @IBOutlet weak var subjectLabel: UILabel!

    func displayChat (chat: BBMChat?)
    {
        messageMonitor = ObservableMonitor.init(activatedWithName: "messageMonitor") {
            [weak self]() -> Void in
            guard let weakSelf = self else {
                return
            }
            guard let ch = chat else {
                return
            }
            weakSelf.subjectLabel.text = ch.subject;
            let participants = BBMAccess.participants(for: ch).array! as! Array<BBMChatParticipant>
            if(participants.count > 0) {
                let participant = participants[0]
                guard let user = participant.resolvedUserUri else {
                    return
                }
                if(user.bbmState == kBBMStatePending) {
                    return
                }
                if(user.regId != nil) {
                    if let displayName = BBMUIUtilities.displayName(for: user) {
                        let name : String = displayName.characters.count > 0 ? displayName : user.regId.stringValue
                        weakSelf.participantLabel.text = name
                    }
                }
            }
            else {
                weakSelf.participantLabel.text = "Empty Chat";
            }
        }
    }
}
