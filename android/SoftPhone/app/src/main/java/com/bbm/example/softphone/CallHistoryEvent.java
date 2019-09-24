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

package com.bbm.example.softphone;

import com.bbm.sdk.media.BBMECall;
import com.bbm.sdk.support.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class represents a single call history event holding the meta data from that call.
 * [Call time, Call duration, Log reason]
 */
public class CallHistoryEvent {

    private static final String CALL_DURATION = "callDuration";
    private static final String CALL_LOG_REASON = "callLogReason";
    private static final String CALL_END_TIME = "callEndTime";

    private long mParticipantRegId = 0l;
    private long mCallDuration = 0;
    private long mCallEndTime = 0;
    private BBMECall.CallLog mCallLogReason = BBMECall.CallLog.NONE;
    private boolean mIncomingCall = false;

    CallHistoryEvent() {

    }

    /**
     * Set a JSON object containing the call meta data
     *
     * @param callHistoryJSON call data as json
     */
    public void setAttributes(JSONObject callHistoryJSON) {
        mCallDuration = callHistoryJSON.optLong(CALL_DURATION, mCallDuration);
        mCallLogReason = BBMECall.CallLog.valueOf(callHistoryJSON.optString(CALL_LOG_REASON, ""));
        mCallEndTime = callHistoryJSON.optLong(CALL_END_TIME, mCallEndTime);
    }

    /**
     * @return the time at which the call was ended
     */
    public long getCallEndTime() {
        return mCallEndTime;
    }

    /**
     * @return the reg id of the other participant in the call
     */
    public long getParticipantRegId() {
        return mParticipantRegId;
    }

    /**
     * @return the duration of the call
     */
    public long getCallDuration() {
        return mCallDuration;
    }

    /**
     * @return the reason the call was ended
     */
    public BBMECall.CallLog getCallLogReason() {
        return mCallLogReason;
    }

    /**
     * @return true if the call was incoming to us
     */
    public boolean isIncomingCall() {
        return mIncomingCall;
    }

    /**
     * Set the registration id of the call participant
     */
    public CallHistoryEvent setParticipantRegId(long regId) {
        mParticipantRegId = regId;
        return this;
    }

    /**
     * Set the duration of the call
     */
    public CallHistoryEvent setCallDuration(long callDuration) {
        mCallDuration = callDuration;
        return this;
    }

    /**
     * Set the log reason of the call
     */
    public CallHistoryEvent setCallLogReason(BBMECall.CallLog callLogReason) {
        mCallLogReason = callLogReason;
        return this;
    }

    /**
     * Set if the call was incoming
     */
    public CallHistoryEvent setIsIncomingCall(boolean isIncomingCall) {
        mIncomingCall = isIncomingCall;
        return this;
    }

    /**
     * Set the call end time
     */
    public CallHistoryEvent setCallEndTime(long callEndTime) {
        mCallEndTime = callEndTime;
        return this;
    }

    /**
     * Return the call meta data as as JSON object
     * The participant regId and incoming values are left out as they differ for each user in the call.
     */
    public JSONObject getJSONObject() {
        JSONObject callDetails = new JSONObject();
        try {
            callDetails.put(CALL_DURATION, mCallDuration);
            callDetails.put(CALL_LOG_REASON, mCallLogReason.toString());
            callDetails.put(CALL_END_TIME, mCallEndTime);
        } catch (JSONException e) {
            Logger.e(e);
        }

        return callDetails;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final CallHistoryEvent event = (CallHistoryEvent) obj;

        if (mCallEndTime != event.mCallEndTime) {
            return false;
        } else if (mIncomingCall != event.mIncomingCall) {
            return false;
        } else if (mCallDuration != event.mCallDuration) {
            return false;
        } else if (mParticipantRegId != event.mParticipantRegId) {
            return false;
        } else if (mCallLogReason == null && event.mCallLogReason != null) {
            return false;
        } else if (mCallLogReason != null && !mCallLogReason.equals(event.mCallLogReason)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int retValue = 21;

        retValue += 21 * (int) (mParticipantRegId ^ (mParticipantRegId >>> 32));
        retValue += 21 * (int) (mCallDuration ^ (mCallDuration >>> 32));
        retValue += 21 * (int) (mCallEndTime ^ (mCallEndTime >>> 32));
        retValue += 21 * mCallLogReason.hashCode();
        return retValue;
    }
}
