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
import com.bbm.sdk.bbmds.RegIdUserCriteria;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.media.BBMECall;
import com.bbm.sdk.media.BBMEIncomingCallObserver;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.support.protect.ProtectedManager;

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

        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {
                BBMECall call = BBMEnterprise.getInstance().getMediaManager().getCall(callId).get();

                //Stop monitoring if the call times out
                if (call.getExists() == Existence.NO) {
                    return true;
                }

                //Find the user who is calling by regId criteria.
                User user = BBMEnterprise.getInstance().getBbmdsProtocol().getUser(new RegIdUserCriteria().regId(call.getRegId())).get();

                if (user.getExists() == Existence.MAYBE) {
                    return false;
                }

                //If the user doesn't exist or is in keyState import we must provide keys before the call can be accepted
                if (user.getExists() == Existence.NO || user.keyState == User.KeyState.Import) {
                    //Provide keys for this user before we can accept the incoming call
                    //If the import should fail the incoming call will timeout on its own.
                    ProtectedManager.getInstance().importUserKeys(call.getRegId());
                    return false;
                } else {
                    startIncomingCallActivity(callId);
                }

                return true;
            }
        });
    }

    private void startIncomingCallActivity(int callId) {
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
