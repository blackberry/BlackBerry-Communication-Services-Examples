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
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.media.BBMECall;
import com.bbm.sdk.media.BBMEMediaManager;
import com.bbm.sdk.reactive.ObservableMonitor;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.util.PermissionsUtil;

import java.io.IOException;


/**
 * Displays an activity that allows a user to accept or reject an incoming call
 */
public class IncomingCallActivity extends Activity {

    private final long[] VIBRATE_RING_PATTERN = new long[]{1000, 1000};

    public static final String INCOMING_CALL_ID = "IncomingCallActivity.INCOMING_CALL_ID";

    private boolean mCallAccepted = false;
    private boolean mRequestingPermissions = false;

    private MediaPlayer mPlayer = null;
    private Vibrator mVibrator;

    private int mCallId = -1;

    /**
     * Monitor the call state, if the call state is not RECEIVING we should close this activity
     */
    private ObservableMonitor mCallMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            //Get the instance of the incoming call
            BBMECall incomingCall = getIncomingCall();

            if (incomingCall.getCallState() == BBMECall.CallState.CALL_STATE_DISCONNECTED) {
                //If we have a failure reason we should act on it
                if (incomingCall.getFailureReason() != BBMECall.FailReason.NO_FAILURE) {
                    //Show an error message to the user
                    Toast.makeText(IncomingCallActivity.this,
                            getString(R.string.call_failure, incomingCall.getFailureReason().name()),
                            Toast.LENGTH_LONG).show();
                }
                finish();
            }
        }
    };

    private final ObservableMonitor mUserMonitor =  new ObservableMonitor() {
        @Override
        protected void run() {
            BBMECall call = getIncomingCall();

            TextView displayName = (TextView) findViewById(R.id.incoming_call_display_name);

            AppUser user = UserManager.getInstance().getUser(call.getRegId()).get();
            String name = user.getExists() == Existence.YES && !TextUtils.isEmpty(user.getName()) ?
                    user.getName() : Long.toString(call.getRegId());
            displayName.setText(name);

            setTitle(getString(R.string.incoming_call));

            if (!TextUtils.isEmpty(user.getAvatarUrl())) {
                ImageTask.load(user.getAvatarUrl(), (ImageView) findViewById(R.id.incoming_call_avatar));
            }
        }
    };

    /**
     * Listener for if the user cancels the permission prompt
     */
    PermissionsUtil.OnCancelListener mOnCancelListener = new PermissionsUtil.OnCancelListener() {
        @Override
        public void onCancel() {
            Logger.d("IncomingCallActivity.mOnCancelListener.onCancel:");
            //this will first hangup call so caller knows, then close this activity
            onCallRejected();
        }
    };

    private BBMECall getIncomingCall() {
        //Get the instance of the call using the call id passed into the activity
        BBMEMediaManager mediaManager = BBMEnterprise.getInstance().getMediaManager();
        return mediaManager.getCall(mCallId).get();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Set window flags to allow our activity to appear above the device lock screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Find the saved call id
        if (savedInstanceState != null) {
            mCallId = savedInstanceState.getInt(INCOMING_CALL_ID);
        } else if (getIntent() != null) {
            mCallId = getIntent().getIntExtra(INCOMING_CALL_ID, -1);
        }

        BBMECall call = getIncomingCall();

        // Check to ensure that the person calling the user has not cancelled the call between
        // the time the receive incoming call event and the creation of the incoming call
        // activity.
        if (call == null || call.getCallState() == BBMECall.CallState.CALL_STATE_DISCONNECTED
                || call.getExists() == Existence.NO) {
            finish();
            return;
        }

        setContentView(R.layout.activity_incoming_call);

        //Add a click listener to the accept button
        findViewById(R.id.accept_call_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onCallAccepted();
            }
        });

        //Add a click listener to the decline button
        findViewById(R.id.ignore_call_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Logger.gesture("Call rejected, user pressed hangup.", IncomingCallActivity.class);
                onCallRejected();
            }
        });
    }

    public void onSaveInstanceState(Bundle outState) {
        //Save the call id
        outState.putInt(INCOMING_CALL_ID, mCallId);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        //Start call monitor
        mCallMonitor.activate();
        mUserMonitor.activate();
        startRingback();
    }

    private void startRingback() {

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final int ringerMode = audioManager.getRingerMode();
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                mPlayer = new MediaPlayer();
                mPlayer.setAudioStreamType(AudioManager.STREAM_RING);
                AssetFileDescriptor afd = SoftPhoneApplication.getInstance().getResources().openRawResourceFd(R.raw.bbm_incoming_call);
                if (afd == null) {
                    return;
                }
                mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                mPlayer.setLooping(true);
                mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mPlayer.start();
                    }
                });
                mPlayer.prepare();
            } catch (final IOException ioe) {
                Logger.e(ioe, "Error playing incoming call ringtone");
                mPlayer = null;
            } catch (final Resources.NotFoundException nfe) {
                Logger.e(nfe, "Error loading incoming call ringtone");
                mPlayer = null;
            }
        } else if (AudioManager.RINGER_MODE_VIBRATE == ringerMode) {
            mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (mVibrator != null && mVibrator.hasVibrator()) {
                Logger.d("Notification profile vibrate - starting vibrate", IncomingCallActivity.class);
                mVibrator.vibrate(VIBRATE_RING_PATTERN, 0);
            }
        }
    }

    private void stopRingback() {
        if (mPlayer != null) {
            Logger.d("stopRingback", IncomingCallActivity.class);
            //Only stop playing if the audio is playing
            if(mPlayer.isPlaying()) {
                mPlayer.stop();
            }
            mPlayer.release();
        }

        if (mVibrator != null && mVibrator.hasVibrator()) {
            Logger.d("stopping vibrate", IncomingCallActivity.class);
            mVibrator.cancel();
        }

        mPlayer = null;
        mVibrator = null;
    }

    @Override
    @SuppressWarnings("MissingPermission")
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Logger.d("IncomingCallActivity.onRequestPermissionsResult: requestCode=" + requestCode + " " + PermissionsUtil.resultsToString(permissions, grantResults));

        mRequestingPermissions = false;
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        //neither permissions or grantResults should be empty but google docs warns they could be and should be treated as a cancellation
        if (permissions.length == 0 && grantResults.length == 0) {
            Logger.w("empty permissions and/or grantResults");
            return;
        }

        if (requestCode == PermissionsUtil.PERMISSION_RECORD_AUDIO_FOR_VOICE_CALL) {
            //the check from onResume will handle action if it is granted, so we only take action here when denied
            if (PermissionsUtil.isGranted(grantResults, 0)) {
                BBMECall incomingCall = getIncomingCall();
                BBMEMediaManager.Error result = BBMEnterprise.getInstance().getMediaManager().acceptCall(incomingCall.getCallId());
                if (result == BBMEMediaManager.Error.NO_ERROR) {
                    onCallAccepted();
                } else {
                    //Display error message so the user knows the call is not going to connect
                    Toast.makeText(IncomingCallActivity.this, getString(R.string.call_error_unable_to_connect), Toast.LENGTH_LONG).show();
                }
            } else {
                PermissionsUtil.displayCanNotContinue(this, Manifest.permission.RECORD_AUDIO,
                        R.string.rationale_record_audio_denied,
                        PermissionsUtil.PERMISSION_RECORD_AUDIO_FOR_VOICE_CALL,
                        mOnCancelListener);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        //Stop the call state monitor, we don't need to act on any changes when the activity is not active
        mCallMonitor.dispose();
        mUserMonitor.dispose();
        stopRingback();
    }


    /**
     * Monitor for key events to reject the call if the user dismisses the activity
     */
    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                Logger.gesture("Call rejected, user dismissed activity.", IncomingCallActivity.class);
                onCallRejected();
                return true;
            }
            default: {
                return super.onKeyDown(keyCode, event);
            }
        }
    }

    /**
     * If the user minimizes the activity then reject the call.
     */
    @Override
    public void onUserLeaveHint() {
        if (!mRequestingPermissions && !mCallAccepted) {
            Logger.gesture("Call rejected, onUserLeaveHint", IncomingCallActivity.class);
            onCallRejected();
        }
    }

    private void requestPermissions() {
        Logger.gesture("onCallAccepted", IncomingCallActivity.class);

        //We don't have audio permission.
        if (!PermissionsUtil.checkOrPromptSelfPermission(this, Manifest.permission.RECORD_AUDIO,
                //pass in a listener that will close this activity if the user cancels the dialog
                PermissionsUtil.PERMISSION_RECORD_AUDIO_FOR_VOICE_CALL,
                R.string.rationale_record_audio,
                mOnCancelListener)) {
            //don't have permission but we either just asked user, displayed rationale, or our onRequestPermissionsResult will be called with denied
            mRequestingPermissions = true;
        }

    }

    private void onCallAccepted() {

        boolean hasMicrophonePermission = PermissionsUtil.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);

        //If we don't have a permission to access the microphone we need to ask
        if (!hasMicrophonePermission) {

            KeyguardManager keyGuard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            //Check if the device is locked, we can't ask for permissions if the device is locked.
            if (keyGuard.inKeyguardRestrictedInputMode()) {
                //To prompt the user to provide access to the microphone we need to prompt them to unlock the device first
                Logger.i("Microphone permission required, device locked.", IncomingCallActivity.class);
                //Prompt the user to unlock the device
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.unlock_for_microphone_permission);
                builder.setPositiveButton(R.string.unlock_for_permission, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        requestPermissions();
                    }
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.setCancelable(false);
                builder.show();
                return;
            } else {
                //Device is unlocked, we can ask for permissions immediately.
                requestPermissions();
                return;
            }

        }

        //We have microphone permission.
        mCallAccepted = true;
        BBMEMediaManager mediaManager = BBMEnterprise.getInstance().getMediaManager();
        //We can attempt to answer the call now
        if (mediaManager.answerCall(getIncomingCall().getCallId()) == BBMEMediaManager.Error.NO_ERROR) {
            //Start our call activity
            Intent inCallIntent = new Intent(IncomingCallActivity.this, InCallActivity.class);
            inCallIntent.putExtra(InCallActivity.EXTRA_CALL_ID, mCallId);
            startActivity(inCallIntent);
        }

        finish();
    }

    private void onCallRejected() {
        Logger.gesture("onCallRejected", IncomingCallActivity.class);

        //Ask the media call service to end the call
        BBMEMediaManager mediaManager = BBMEnterprise.getInstance().getMediaManager();
        mediaManager.endCall(getIncomingCall().getCallId());
        finish();
    }
}
