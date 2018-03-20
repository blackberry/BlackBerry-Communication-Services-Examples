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

package com.bbm.example.whiteboard;

import android.app.Application;
import android.util.Log;

import com.bbm.example.whiteboard.utils.AuthProvider;
import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.support.util.Logger;

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

        //Init the auth provider (get authentication token, start protected manager, sync users)
        AuthProvider.initAuthProvider(getApplicationContext());

        //Initialize BBMEnterprise SDK then start it
        BBMEnterprise.getInstance().initialize(this);
        BBMEnterprise.getInstance().start();
    }
}
