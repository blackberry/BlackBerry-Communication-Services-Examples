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

import android.app.Application;

import com.bbm.example.datatransfer.utils.AuthProvider;
import com.bbm.sdk.BBMEnterprise;


public class DataTransferApplication extends Application {

    private static DataTransferApplication mApp;

    public static DataTransferApplication getInstance() {
        return mApp;
    }

    private IncomingConnectionObserver mConnectionObserver;

    @Override
    public void onCreate() {
        super.onCreate();

        mApp = this;

        //Init the auth provider (get authentication token, start protected manager, sync users)
        AuthProvider.initAuthProvider(getApplicationContext());

        // Initialize BBMEnterprise SDK then start it
        BBMEnterprise.getInstance().initialize(this);
        BBMEnterprise.getInstance().start();

        //Add incoming connection observer
        mConnectionObserver = new IncomingConnectionObserver(DataTransferApplication.this);
        BBMEnterprise.getInstance().getMediaManager().addIncomingDataConnectionObserver(mConnectionObserver);
    }

    public final IncomingConnectionObserver getConnectionObserver() {
        return mConnectionObserver;
    }

}
