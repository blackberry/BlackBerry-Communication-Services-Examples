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
import GoogleSignIn
import BBMEnterprise

class AccountViewController: UITableViewController, BBMConnectivityListener, BBMAuthControllerDelegate {
    @IBOutlet weak var serviceStateLabel : UILabel!
    @IBOutlet weak var authTokenStateLabel : UILabel!
    @IBOutlet weak var setupStateLabel : UILabel!
    @IBOutlet weak var regIdLabel : UILabel!
    @IBOutlet weak var domainLabel : UILabel!
    @IBOutlet weak var userEmailLabel : UILabel!
    @IBOutlet weak var serviceConnectivityLabel : UILabel!

    @IBOutlet weak var switchDeviceButton : UIButton!
    @IBOutlet weak var googleSignInButton : GIDSignInButton!
    @IBOutlet weak var signOutButton : UIButton!

    var serviceMonitor : ObservableMonitor!
    
    required init?(coder aDecoder: NSCoder) {
        super.init(coder: aDecoder)

        //Add ourselves as a BBMConnectivityListener
        BBMEnterpriseService.shared().add(self)

        //The rootController will be used to show the login UI
        ChatPollApp.app().authController().rootController = self

        //Use an observable monitor to monitor service state and auth state changes
        //This only sets up the monitor.  It is activated in viewWillAppear.  Alternatively, you
        //could add this class as a delegate to QuickStartApp.app().authController()
        serviceMonitor = ObservableMonitor(name:"ServiceStateMonitor") {
            [weak self] () -> Void in

            //serviceStarted and authState are observable properties.  This means this block
            //of code will be called each time the monitor activates, and each time either
            //of these property values change
            self?.serviceStateChanged(ChatPollApp.app().authController().serviceStarted)
            self?.authStateChanged(ChatPollApp.app().authController().authState)
       }

        ChatPollApp.app().authController().signInSilently()
    }

    deinit {
        ChatPollApp.app().authController().rootController = nil
        BBMEnterpriseService.shared().remove(self)
    }


    //MARK: View Lifecycle

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        googleSignInButton.isHidden = true
        switchDeviceButton.isEnabled = true
        signOutButton.isHidden = true
        domainLabel.text = SDK_SERVICE_DOMAIN

        //This will activate and run our serviceMonitor which will update all of the UI elements
        //via authStateChanged and serviceStateChanged
        serviceMonitor.activate()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        serviceMonitor.deActivate()
    }


    //MARK: BBMConnectivityListener

    public func connectivityStateChange(_ connected: Bool, strict connectedStrict: Bool) {
        serviceConnectivityLabel.text = connected ? "Connected" : "Disconnected";
    }


    //MARK: BBMAuthControllerDelegate

    func authStateChanged(_ authState: BBMAuthState) {
        authTokenStateLabel.text = authState.authTokenState != nil ? authState.authTokenState! : "No Token"
        setupStateLabel.text = authState.setupState != nil ? authState.setupState! : "Setup Not Started"
        regIdLabel.text = authState.regId != nil ? authState.regId.stringValue : ""

        let email = authState.account != nil ? authState.account.email : ""
        userEmailLabel.text = email

        googleSignInButton.isHidden = ChatPollApp.app().authController().startedAndAuthenticated;
        signOutButton.isHidden = !ChatPollApp.app().authController().startedAndAuthenticated;

        let setupState = authState.setupState != nil ? authState.setupState : ""
        switchDeviceButton.isEnabled = (setupState == kBBMSetupStateDeviceSwitch)

        if (authState.setupState != nil && authState.setupState == kBBMSetupStateFull) {
            ChatPollApp.app().authController().endpointManager.deregisterAnyEndpointAndContinueSetup()
        }

        tableView.reloadData();
    }


    func serviceStateChanged(_ serviceStarted : Bool) {
        serviceStateLabel.text = serviceStarted ? "Started" : "Stopped"
        tableView.reloadData()
    }


    //MARK: IB Actions

    @IBAction func switchDevice(sender: UIButton?) {
        BBMAccess.sendSetupRetry()
    }


    @IBAction func signOut(sender: UIButton?) {
        BBMEnterpriseService.shared().resetService()
        ChatPollApp.app().authController().signOut()
    }

}

