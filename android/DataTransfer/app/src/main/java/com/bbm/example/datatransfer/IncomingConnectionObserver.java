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

package com.bbm.example.datatransfer;

import android.content.Context;
import android.content.Intent;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.media.BBMEIncomingDataConnectionObserver;

/**
 * The IncomingConnectionObserver will be notified when a new call has arrived.
 */
public class IncomingConnectionObserver implements BBMEIncomingDataConnectionObserver {

    private Context mContext;
    public static final String INCOMING_CONNECTION_ID = "INCOMING_CONNECTION_ID";

    public IncomingConnectionObserver(Context context) {
        mContext = context;
    }

    @Override
    public void onIncomingDataConnection(final int connectionId) {
        //Accept the data connection
        BBMEnterprise.getInstance().getMediaManager().acceptDataConnection(connectionId);

        //Launch the incoming data connection activity
        Intent incomingCallIntent = new Intent(mContext, IncomingDataConnectionActivity.class);
        incomingCallIntent.putExtra(IncomingConnectionObserver.INCOMING_CONNECTION_ID, connectionId);
        incomingCallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(incomingCallIntent);
    }
}
