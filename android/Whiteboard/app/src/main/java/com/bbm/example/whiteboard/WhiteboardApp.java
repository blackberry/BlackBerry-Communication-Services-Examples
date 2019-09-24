/*
 * Copyright (c) 2017 BlackBerry Limited. All Rights Reserved.
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

package com.bbm.example.whiteboard;

import android.app.Application;
import android.util.Log;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.BBMEnterpriseState;
import com.bbm.sdk.support.identity.auth.MockTokenProvider;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.kms.BlackBerryKMSSource;
import com.bbm.sdk.support.protect.KeySource;
import com.bbm.sdk.support.protect.UserChallengePasscodeProvider;
import com.bbm.sdk.support.push.PushHelper;
import com.bbm.sdk.support.support.identity.user.MockUserSource;
import com.bbm.sdk.support.util.KeySourceManager;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.util.SetupHelper;

public class WhiteboardApp extends Application {

    @Override
    public void onCreate() {
        //first customize logging
        Logger.setLogWriter(new Logger.LogWriter() {
            @Override
            public void log(int priority, Throwable t, String message, String tag) {
                //just write to logcat like default
                Log.println(priority, tag, Logger.formatMessage(t, message));
                if (Logger.USER_TAG.equals(tag)) {
                    //This is a message that could be displayed to a developer/tester user
                    //This is not recommended for production apps!
                    if (t != null) {
                        //this is even less user friendly, just for devs only recommended. Add exception type and message
                        message = message + "\n" + t;
                    }
                    WhiteboardUtils.showDefaultToast(getApplicationContext(), message);
                }
            }
        });

        Logger.user("Starting app...");
        super.onCreate();

        //Initialize the "no authentication" configuration
        initializeConfiguration();

        //Initialize BBMEnterprise SDK then start it
        BBMEnterprise.getInstance().initialize(this);
        BBMEnterprise.getInstance().start();


        // Add a listener for "EndpointDeregistered". When this is received, it generally means the user has switched to another device.
        SetupHelper.listenForAndHandleDeregistered(new SetupHelper.EndpointDeregisteredListener() {
            @Override
            public void onEndpointDeregistered() {
                // Clear any saved tokens
                MockTokenProvider.clearSavedToken(getApplicationContext());

                SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                    private BBMEnterpriseState prevState;
                    @Override
                    public boolean run() {
                        BBMEnterpriseState state = BBMEnterprise.getInstance().getState().get();
                        if (prevState != null && prevState != BBMEnterpriseState.STARTED && state == BBMEnterpriseState.STARTED) {
                            initializeConfiguration();
                            return true;
                        }
                        prevState = state;
                        return false;
                    }
                });
            }
        });
    }

    /**
     * Initialize this application to use "No Authentication" and BlackBerry KMS
     */
    public void initializeConfiguration() {
        //Initialize a MockUserSource.
        //The MockUserSource will create a contact list that includes any users found by mapping userIds or regIds.
        MockUserSource userSource = new MockUserSource();
        UserManager.getInstance().setAppUserSource(userSource);
        userSource.addListener(UserManager.getInstance());

        //Mock IDP always uses KMS
        KeySource keySource = new BlackBerryKMSSource(new UserChallengePasscodeProvider(getApplicationContext()));
        KeySourceManager.setKeySource(keySource);
        keySource.start();
    }
}
