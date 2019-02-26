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
import AVFoundation

class MediaViewController : UIViewController, BBMMediaDelegate, BBMMediaVideoPresenter
{
    //MARK: Outlets

    @IBOutlet weak var incomingVideoContainerView: UIView!
    @IBOutlet weak var outgoingVideoContainerView: UIView!

    @IBOutlet weak var muteButton: UIButton!
    @IBOutlet weak var speakerButton: UIButton!
    @IBOutlet weak var videoButton: UIButton!
    @IBOutlet weak var camButton: UIButton!

    @IBOutlet weak var durationField: UILabel!
    @IBOutlet weak var remotePartyField: UILabel!

    var controlsMonitor : ObservableMonitor!

    var outgoingVideoView : UIView?
    var incomingVideoView : UIView?
    var callTimer : Timer!

    let mediaManager : BBMMediaManager = BBMEnterpriseService.shared().mediaManager()


    override func viewWillAppear(_ animated: Bool) {
        mediaManager.add(self as BBMMediaDelegate)
        mediaManager.add(self as BBMMediaVideoPresenter)
        monitorControlStates()
        startCallTimer()
    }

    override func viewWillDisappear(_ animated: Bool) {
        mediaManager.remove(self as BBMMediaDelegate)
        mediaManager.remove(self as BBMMediaVideoPresenter)

        controlsMonitor.deActivate()
        callTimer.invalidate()
    }

    private func startCallTimer() {
        //Update the call duration once per second
        callTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            if let call: BBMCall = BBMEnterpriseService.shared().mediaManager().currentCallInfo {
                let duration = NSDate().timeIntervalSince1970 - call.connectedTime;
                self?.durationField.text = "Duration: " + BBMUtilities.duration(asString: duration)
            }
        }
    }

    private func monitorControlStates() {
        //Use an ObservableMonitor to track the states of the call and mediaManager in order
        //to keep our buttons configured correctly
        controlsMonitor = ObservableMonitor(activatedWithName: "controlsMonitor") {
            [weak self] () -> Void in

            guard let weakSelf = self else { return }

            //Update the mute button based on the mute state of the call
            if let call = BBMEnterpriseService.shared().mediaManager().currentCallInfo {
                let isMuted = call.muted
                let muteTitle = isMuted ? "UNMUTE" : "MUTE"
                weakSelf.muteButton.setTitle(muteTitle, for: UIControlState.normal)
                weakSelf.remotePartyField.text = "Call With: " + call.peerRegId

                //The mediaManager tells us when we are able to start/stop our camera.  If camera commands
                //are issued when enableCameraAllowed, videoSupport or isVideoCallingSupported are false
                //they will fail
                weakSelf.videoButton.isEnabled = weakSelf.mediaManager.enableCameraAllowed &&
                                                 call.videoSupported &&
                                                 weakSelf.mediaManager.isVideoCallingSupported
            }else{
                weakSelf.muteButton.setTitle("MUTE", for: UIControlState.normal)
                weakSelf.videoButton.isEnabled = false
            }

            //Update the video button state based on whether we have an outgoing video stream
            let hasOutGoingVideo = weakSelf.mediaManager.outgoingVideoView != nil;
            let vidTitle = hasOutGoingVideo ? "STOP VIDEO" : "START VIDEO"
            weakSelf.videoButton.setTitle(vidTitle, for: UIControlState.normal)

            //Camera toggling can take a second or two.  the toggleCameraAllowed property will
            //transition telling us when we are able to toggle the camera
            weakSelf.camButton.isEnabled = hasOutGoingVideo &&
                                           weakSelf.mediaManager.toggleCameraAllowed
        }
    }

    //MARK: MediaPresenter

    func incomingVideoContainer() -> UIView! {
        //Return a container into which the incoming video will be painted
        return incomingVideoContainerView
    }

    func outgoingVideoContainer() -> UIView! {
        return outgoingVideoContainerView
    }

    func videoPresenterPriority() -> Int {
        //We only have a single presenter so this value is arbitrary.  If you have multiple views
        //consuming the video feeds, then the presenter that is registered with the highest priority
        //will get the feeds.
        return 100
    }

    //There is no need to add/remove views from their containers, this is done automatically
    //for you.  However, you may want to hide/show the video conatiners when a feed is added
    //or removed.  You can do so in these callbacks.
    func incomingVideoDisabled() {
        incomingVideoView = nil;
        incomingVideoContainerView.isHidden = true
    }

    func outgoingVideoDisabled() {
        outgoingVideoView = nil;
        outgoingVideoContainerView.isHidden = true;
    }

    func incomingVideoReady(_ view: (UIView & BBMVideoView)!) {
        incomingVideoView = view;
        incomingVideoContainerView.isHidden = false
    }

    func outgoingVideoReady(_ view: (UIView & BBMVideoView)!) {
        outgoingVideoView = view;
        outgoingVideoContainerView.isHidden = false
    }

    //We need to monitor size changes to the video feed.  This will allow us to 
    //properly set the frame of the video feed with respect to our container.
    //The function AVMakeRect(aspectRatio:, insideRect:) is provided by AVFoundation for
    //just this purpose.  

    //For rotation, you may need to cache the sizes of the feeds and update the frames
    //on a rotation event.

    func videoView(_ view: (BBMVideoView & UIView)!, didChangeVideoSize size: CGSize) {
        updateAspectRatioForView(view, size: size)
    }

    func updateAspectRatioForView(_ view: UIView!, size: CGSize) {
        if(size.width == 0 || size.height == 0) {
            //Invalid video sizes
            return;
        }

        //Determine which container we're dealing with
        var container : UIView?
        if(view == outgoingVideoView) {
            container = outgoingVideoContainerView
        }
        else if(view == incomingVideoView) {
            container = incomingVideoContainerView
        }

        //Fit the video video to the conatianer
        if let container = container {
            let bounds = container.bounds;
            let frame = AVMakeRect(aspectRatio: size, insideRect: bounds);
            view.frame = frame;
        }else{
            NSLog("Update size requested for an unknown video view");
        }
    }


    //MARK: MediaDelegate

    func callEnded(_ call: BBMCall!) {
        //Dismiss ourselves once the call has ended
        dismiss(animated: true, completion: nil)
    }


    //MARK: Actions

    @IBAction func switchCamPressed(_ sender: UIButton) {
        mediaManager.toggleCamera()
    }

    @IBAction func startVideoPressed(_ sender: UIButton) {
        mediaManager.toggleOutgoingVideo(true)
    }


    @IBAction func speakerPressed(_ sender: UIButton) {
        mediaManager.setSpeakerPhoneEnabled(!mediaManager.isOnSpeakerPhone)
    }

    @IBAction func mutePressed(_ sender: UIButton) {
        if let call = BBMEnterpriseService.shared().mediaManager().currentCallInfo {
            mediaManager.setMutingEnabled(!call.muted)
        }
    }

    @IBAction func hangupPressed(_ sender: UIButton) {
        mediaManager.hangup()
    }
}
