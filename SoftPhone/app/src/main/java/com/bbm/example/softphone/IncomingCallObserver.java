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

package com.bbm.example.softphone;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.media.BBMEIncomingCallObserver;

/**
 * The IncomingCallObserver will be notified when a new call has arrived.
 */

public class IncomingCallObserver implements BBMEIncomingCallObserver {

    private Context mContext;

    public IncomingCallObserver(Context context) {
        mContext = context;
    }

    @Override
    public void onIncomingCall(final int callId) {
        //If we have audio permissions we can accept the call immediately
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            BBMEnterprise.getInstance().getMediaManager().acceptCall(callId);
        }

        CallUtils.addObserverToCall(callId);

        Intent incomingCallIntent = new Intent(mContext, IncomingCallActivity.class);
        incomingCallIntent.putExtra(IncomingCallActivity.INCOMING_CALL_ID, callId);
        incomingCallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(incomingCallIntent);
    }
}
