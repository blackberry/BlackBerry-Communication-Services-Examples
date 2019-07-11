/*
 * Copyright (c) 2017 BlackBerry.  All Rights Reserved.
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
 */

package com.bbm.example.datatransfer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.GlobalAuthTokenState;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.service.BBMEnterpriseState;
import com.bbm.sdk.support.util.Logger;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.Set;

public class DataTransferFirebaseMessagingService extends FirebaseMessagingService {

    private Handler mMainHandler = new Handler(Looper.getMainLooper());

    /**
     * Need to keep a hard reference to the observer so it isn't garbage collected while waiting
     * for BBM to start and auth to be ok.
     * Keep as a static reference in case the instance is garbage collected before the observer is done.
     */
    private static Observer sObserver;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Logger.d("onMessageReceived: "+remoteMessage);
        FirebaseApp fbApp = FirebaseApp.getInstance();
        if (fbApp != null && remoteMessage.getFrom().equals(fbApp.getOptions().getGcmSenderId())) {
            // We convert the remote message into a bundle to be able to pass it to the SDK
            final Bundle bundle = new Bundle();
            final Set<Map.Entry<String, String>> data = remoteMessage.getData().entrySet();
            for (Map.Entry<String, String> entry : data) {
                bundle.putString(entry.getKey(), entry.getValue());
            }

            // Handling of push notification must be done of UI thread.
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Logger.i("Handle incoming push.");
                    final ObservableValue<BBMEnterpriseState> state = BBMEnterprise.getInstance().getState();
                    final ObservableValue<GlobalAuthTokenState> authTokenStateObservableValue = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalAuthTokenState();

                    //check if everything is ready
                    if (state.get() == BBMEnterpriseState.STARTED
                            && authTokenStateObservableValue.get().value == GlobalAuthTokenState.State.Ok) {
                        Logger.i("ready to handle push without waiting. BBMEnterpriseState=" + state.get()
                                + ", AuthTokenState=" + authTokenStateObservableValue.get() + " exists=" + authTokenStateObservableValue.get().exists);

                        handlePushNotification(bundle);
                    } else {
                        //wait for BBM to start and auth to be ok before passing push to BBM
                        sObserver = new Observer() {
                            @Override
                            public void changed() {
                                Logger.i("changed: BBMEnterpriseState=" + state.get()
                                        + ", AuthTokenState=" + authTokenStateObservableValue.get() + " exists=" + authTokenStateObservableValue.get().exists);

                                //wait for BBM to start, auth token to exist and value be OK before trying to handle push
                                if (state.get() == BBMEnterpriseState.STARTED
                                        && authTokenStateObservableValue.get().value == GlobalAuthTokenState.State.Ok) {
                                    //this is done for this push, remove this observer right away so it doesn't get called again
                                    authTokenStateObservableValue.removeObserver(sObserver);
                                    state.removeObserver(sObserver);
                                    sObserver = null;

                                    handlePushNotification(bundle);
                                }
                            }
                        };

                        authTokenStateObservableValue.addObserver(sObserver);
                        state.addObserver(sObserver);

                        Logger.d("Waiting for observer to handle incoming push...");

                        //call the observer manually in case everything is already ready and nothing needs to change
                        sObserver.changed();
                    }
                }
            });
        }
    }

    private void handlePushNotification(Bundle bundle) {
        try {
            //now handle the push
            Logger.d("calling handlePushNotification");
            BBMEnterprise.getInstance().handlePushNotification(bundle);
            Logger.d("done calling handlePushNotification");
        } catch (Exception e) {
            // Failed to process Push
            Logger.e(e, "Failed to process push");
        }
    }
}