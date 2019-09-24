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

import android.Manifest;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Toast;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.inbound.ChatStartFailed;
import com.bbm.sdk.bbmds.outbound.ChatMessageSend;
import com.bbm.sdk.media.BBMECall;
import com.bbm.sdk.media.BBMECallCreationObserver;
import com.bbm.sdk.media.BBMECallObserver;
import com.bbm.sdk.media.BBMEMediaManager;
import com.bbm.sdk.support.util.ChatStartHelper;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.util.PermissionsUtil;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CallUtils {

    // Keep a copy of the registration id we are attempting to call, we will use this to retry after a permission request
    private static long sRegIdToCall;

    public static final String CALL_EVENT_TAG = "CALL_EVENT";

    //Create a call observer we can add to each call
    private static BBMECallObserver mCallObserver = new BBMECallObserver() {

        private MediaPlayer mOutgoingRingPlayer;
        private MediaPlayer mEndCallBeep;

        @Override
        public void onIncomingCallMissed(@NonNull BBMECall bbmeCall) {

        }

        @Override
        public void onIncomingCallDeclined(@NonNull BBMECall bbmeCall) {

        }

        @Override
        public void onOutgoingCallProceeding(@NonNull BBMECall bbmeCall) {

        }

        @Override
        public void onOutgoingCallRinging(@NonNull BBMECall bbmeCall) {
            //When the call starts ringing on the other side start playing our ringer.
            if (mOutgoingRingPlayer == null) {
                try {
                    mOutgoingRingPlayer = new MediaPlayer();
                    mOutgoingRingPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
                    AssetFileDescriptor afd = SoftPhoneApplication.getInstance().getResources().openRawResourceFd(R.raw.bbm_outgoing_call);
                    if (afd == null) {
                        Logger.e("Outgoing call resource not found");
                        return;
                    }
                    mOutgoingRingPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                    mOutgoingRingPlayer.setLooping(true);

                    mOutgoingRingPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            mOutgoingRingPlayer.start();
                        }
                    });
                    mOutgoingRingPlayer.prepare();
                } catch (final IOException ioe) {
                    Logger.e(ioe, "Error playing outgoing call ringtone");
                    mOutgoingRingPlayer = null;
                } catch (final Resources.NotFoundException nfe) {
                    Logger.e(nfe, "Error loading outgoing call ringtone");
                    mOutgoingRingPlayer = null;
                }
            }
        }

        @Override
        public void onCallConnected(@NonNull BBMECall bbmeCall) {
            //When the call connects stop our outgoing ringer
            if (mOutgoingRingPlayer != null) {
                mOutgoingRingPlayer.stop();
                mOutgoingRingPlayer.release();
                mOutgoingRingPlayer = null;
            }
        }

        @Override
        public void onCallFailed(@NonNull BBMECall bbmeCall) {
            postCallEvent(bbmeCall);
        }

        @Override
        public void onCallEnded(@NonNull final BBMECall bbmeCall) {
            postCallEvent(bbmeCall);
        }

        /**
         * Play a beep tone when the call ends. This is helpful is the user is holding the phone to their ear, they will know the call is over.
         */
        private void playCallEndedBeep() {
            try {
                mEndCallBeep = new MediaPlayer();
                mEndCallBeep.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
                AssetFileDescriptor afd = SoftPhoneApplication.getInstance().getResources().openRawResourceFd(R.raw.bbm_end_call);
                if (afd == null) {
                    Logger.e("End call resource not found");
                    return;
                }
                mEndCallBeep.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();

                mEndCallBeep.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mEndCallBeep.start();
                    }
                });

                mEndCallBeep.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mp.release();
                    }
                });
                mEndCallBeep.prepare();
            } catch (final IOException ioe) {
                Logger.e(ioe, "Error playing end call beep ringtone");
                mEndCallBeep = null;
            } catch (final Resources.NotFoundException nfe) {
                Logger.e(nfe, "Error loading outgoing end call beep ringtone");
                mEndCallBeep = null;
            }
        }

        /**
         * Adds a new chat message for each call to create a call history log.
         */
        private void postCallEvent(final BBMECall bbmeCall) {

            final long callEndTime = System.currentTimeMillis();
            playCallEndedBeep();

            if (mOutgoingRingPlayer != null) {
                mOutgoingRingPlayer.stop();
                mOutgoingRingPlayer.release();
                mOutgoingRingPlayer = null;
            }

            if (bbmeCall.isIncomingCall()) {
                //Only outgoing caller will generate a call log entry
                return;
            }

            //Start a new chat (or find the existing chat) and add a new call entry message
            ChatStartHelper.startNewOneToOneChat(bbmeCall.getRegId(), new ChatStartHelper.ChatStartedCallback() {
                @Override
                public void onChatStarted(@NonNull String chatId) {
                    //Create a CallHistoryEvent using the meta data from the call
                    CallHistoryEvent callHistoryEvent = new CallHistoryEvent()
                            .setCallEndTime(callEndTime)
                            .setCallLogReason(bbmeCall.getCallLog())
                            .setCallDuration(System.currentTimeMillis() - bbmeCall.getCallStartTime());

                    //Send the call log chat message
                    //We are creating the chat message with a custom tag "CALL_EVENT"
                    //This will allow us to retrieve only the CALL_EVENT chat messages to create a call history
                    ChatMessageSend callMessage = new ChatMessageSend(chatId, CALL_EVENT_TAG);
                    //Attach the call history event data to the chat message.
                    callMessage.data(callHistoryEvent.getJSONObject());
                    BBMEnterprise.getInstance().getBbmdsProtocol().send(callMessage);
                }

                @Override
                public void onChatStartFailed(ChatStartFailed.Reason reason) {
                    //Ignoring the chat start failure
                }
            });
        }
    };

    /**
     * Starts a call with the registration id provided. If RECORD_AUDIO permission has not be granted it will prompt the user first.
     */
    @SuppressWarnings("MissingPermission")
    public static void makeCall(final AppCompatActivity activity, Fragment fragment, final long regId) {

        sRegIdToCall = regId;
        //Check for permission to access the microphone before starting an outgoing call
        if (PermissionsUtil.checkOrPromptSelfPermission(activity, fragment,
                Manifest.permission.RECORD_AUDIO,
                PermissionsUtil.PERMISSION_RECORD_AUDIO_FOR_VOICE_CALL,
                R.string.rationale_record_audio, PermissionsUtil.sEmptyOnCancelListener)) {

            //Ask the media service to start a call with the specified regId and include an observer to be notified of the result
            BBMEnterprise.getInstance().getMediaManager().startCall(regId, false, new BBMECallCreationObserver() {
                @Override
                public void onCallCreationSuccess(int callId) {
                    addObserverToCall(callId);

                    //The call was started successfully. Open our call activity
                    Intent inCallIntent = new Intent(activity, InCallActivity.class);
                    inCallIntent.putExtra(InCallActivity.EXTRA_CALL_ID, callId);
                    activity.startActivity(inCallIntent);
                }

                @Override
                public void onCallCreationFailure(@NonNull BBMEMediaManager.Error error) {
                    //The call wasn't able to be started, provide an error to the user
                    Toast.makeText(activity, activity.getString(R.string.error_starting_call, error.name()), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    static void addObserverToCall(int callId) {
        //Add a call observer
        BBMECall call = BBMEnterprise.getInstance().getMediaManager().getCall(callId).get();
        call.addObserver(mCallObserver);
    }

    /**
     * Make a call to the previously selected registration id after permission has been granted.
     */
    public static void makeCallPermissionGranted(AppCompatActivity activity, Fragment fragment) {
        if (sRegIdToCall != 0) {
            makeCall(activity, fragment, sRegIdToCall);
            sRegIdToCall = 0;
        }
    }

    /**
     * Utility method to convert a time in ms to an hours:minutes:seconds string to be displayed in the call activity.
     */
    public static String getCallElapsedTimeFromMilliseconds(final long millis) {
        final long hours = TimeUnit.MILLISECONDS.toHours(millis);
        final long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        final long minutes = totalMinutes - TimeUnit.HOURS.toMinutes(hours);
        final long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(totalMinutes);

        if (hours >= 1) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }
    }
}
