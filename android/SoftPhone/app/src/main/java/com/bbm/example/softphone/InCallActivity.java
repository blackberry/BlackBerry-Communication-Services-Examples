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
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.media.BBMECall;
import com.bbm.sdk.media.BBMECallObserver;
import com.bbm.sdk.media.BBMECameraOperationCallback;
import com.bbm.sdk.media.BBMEMediaManager;
import com.bbm.sdk.media.BBMEVideoRenderer;
import com.bbm.sdk.reactive.Mutable;
import com.bbm.sdk.reactive.ObservableMonitor;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.util.PermissionsUtil;

import java.util.List;

/**
 * This activity displays an active call with controls for starting/stoping video and controlling audio.
 */
public class InCallActivity extends AppCompatActivity {

    public static final String EXTRA_CALL_ID = "com.bbm.example.softphone.CALL_ID";

    private final static long CALL_QUALITY_WINDOW = 5000;
    private final static long VIDEO_BUTTON_RENABLE_DELAY = 1000;

    private Mutable<Long> mCallDuration = new Mutable<>(0L);
    private int mCallId;

    private ImageButton mEnableCameraButton;
    private ImageButton mMuteButton;
    private MenuItem mSwitchCameraItem;
    private MenuItem mAudioSelectorItem;

    private TextView mSubTitle;
    private TextView mTitle;

    //Video
    private FrameLayout mRemoteVideoLayout;
    private FrameLayout mLocalVideoLayout;
    private BBMEVideoRenderer mLocalVideoRenderer;
    private ProgressBar mVideoProgessBar;
    private SurfaceView mRemoteVideoSurface;
    private SurfaceView mLocalVideoSurface;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mQualityChangeRunnable;


    /**
     * Monitor the quality of the call.
     */
    private final ObservableMonitor mQualityMonitor = new ObservableMonitor() {
        @Override
        protected void run() {

            final TextView qualityText = ((TextView) findViewById(R.id.call_quality_text));
            BBMECall call = getCall();
            BBMECall.CallQuality quality = call.getCallQuality();

            // Quality is moving to good or unknown.
            if (call.getCallState() != BBMECall.CallState.CALL_STATE_CONNECTED ||
                    (quality == BBMECall.CallQuality.QUALITY_GOOD ||
                    quality == BBMECall.CallQuality.QUALITY_UNKNOWN)) {

                //Add a delay before we remove the call quality indicator.
                //This will avoid flashing the indicator quickly on and off.
                mQualityChangeRunnable = new Runnable() {
                    @Override
                    public void run() {
                        qualityText.setVisibility(View.GONE);
                        mQualityChangeRunnable = null;
                    }
                };
                mHandler.postDelayed(mQualityChangeRunnable, CALL_QUALITY_WINDOW);
                return;
            }

            // Quality has decreased, clear any pending runnable that would remove the quality indicator
            if (mQualityChangeRunnable != null) {
                mHandler.removeCallbacks(mQualityChangeRunnable);
            }

            //Display the call quality indicator.
            qualityText.setVisibility(View.VISIBLE);

            Drawable icon = null;
            int textColor = Color.WHITE;
            Resources r = getResources();

            //Set an appropriate colored icon
            switch (quality) {
                case QUALITY_POOR:
                    icon = r.getDrawable(R.drawable.poor_connection);
                    textColor = r.getColor(R.color.call_poor_quality);
                    break;
                case QUALITY_MODERATE:
                    icon = r.getDrawable(R.drawable.moderate_connection);
                    textColor = r.getColor(R.color.call_moderate_quality);
                    break;
            }

            qualityText.setTextColor(textColor);
            qualityText.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        }
    };

    /**
     * Monitor the call state
     */
    private final ObservableMonitor mStateMonitor = new ObservableMonitor() {
        @Override
        public void run() {
            BBMECall call = getCall();

            String subTitle = "";

            //Find an appropriate string to display {Calling, Connecting, Call (TIME), Recovering}
            switch (call.getCallState()) {
                case CALL_STATE_ACCEPTED:
                    subTitle = getString(R.string.voicecall_status_connecting);
                    break;
                case CALL_STATE_INITIALIZED:
                case CALL_STATE_PROCEEDING:
                case CALL_STATE_RINGBACK:
                    subTitle = getString(R.string.voicecall_status_calling);
                    break;
                case CALL_STATE_CONNECTED:
                    if (call.isInCallRecovery()) {
                        //Display "recovering call" if platform is reconnecting.
                        subTitle = getString(R.string.call_recovery);
                    } else {
                        subTitle = getString(R.string.voicecall_status_duration,
                                CallUtils.getCallElapsedTimeFromMilliseconds(mCallDuration.get()));
                    }
                    break;
            }

            AppUser user = UserManager.getInstance().getUser(call.getRegId()).get();
            String name = user.getExists() == Existence.YES && !TextUtils.isEmpty(user.getName()) ?
                    user.getName() : Long.toString(call.getRegId());
            mTitle.setText(name);
            mSubTitle.setText(subTitle);
        }
    };

    /**
     * Simple runnable to update the call timer once every second.
     */
    private Runnable mUpdateDurationRunnable = new Runnable() {
        @Override
        public void run() {
            if (getCall().getCallState() == BBMECall.CallState.CALL_STATE_CONNECTED) {
                mCallDuration.set(System.currentTimeMillis() - getCall().getCallStartTime());
            }
            mHandler.postDelayed(this, 1000);
        }
    };

    /**
     * Monitor the camera available state.
     */
    private final ObservableMonitor mVideoEnabledMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            BBMEMediaManager mediaManager = BBMEnterprise.getInstance().getMediaManager();
            BBMECall call = getCall();
            if (call.isCameraAvailable()) {
                //Video is available
                //Switch camera is only available if 2 or more cameras are present and video is turned on
                if (mSwitchCameraItem != null) {
                    mSwitchCameraItem.setVisible(call.getLocalViewport() != null && mediaManager.getCameraCount() > 1);
                }

                //Show the camera button
                mEnableCameraButton.setVisibility(View.VISIBLE);

                //Enable the camera button if the call is connected
                mEnableCameraButton.setClickable(call.getCallState() == BBMECall.CallState.CALL_STATE_CONNECTED);
            } else {
                //Video is not supported for this call, remove the switch camera and enable camera buttons
                if (mSwitchCameraItem != null) {
                    mSwitchCameraItem.setVisible(false);
                }
                mEnableCameraButton.setVisibility(View.GONE);
            }
        }
    };

    /**
     * Monitor the muted state and the active audio device
     */
    private final ObservableMonitor mControlsActionMonitor = new ObservableMonitor() {
        @Override
        public void run() {

            BBMECall call = getCall();

            if (call.getCallState() == BBMECall.CallState.CALL_STATE_IDLE) {
                //In case this monitor gets triggered after the call is completed
                return;
            }

            //Display the appropriate mute icon
            boolean muted = call.isMuted();
            mMuteButton.setImageResource(muted ? R.drawable.ic_mute_on : R.drawable.ic_mute_off);

            if (mAudioSelectorItem != null) {
                //Get the active audio device and find the right icon for that device
                BBMEMediaManager mediaManager = BBMEnterprise.getInstance().getMediaManager();
                BBMEMediaManager.AudioDevice activeAudioDevice = mediaManager.getActiveAudioDevice().get();
                switch (activeAudioDevice) {
                    case SPEAKER:
                        mAudioSelectorItem.setIcon(R.drawable.ic_speaker);
                        break;
                    case HEADSET:
                        mAudioSelectorItem.setIcon(R.drawable.ic_wired_headset);
                        break;
                    case BLUETOOTH:
                        mAudioSelectorItem.setIcon(R.drawable.ic_bluetooth);
                        break;
                    case HANDSET:
                    default:
                        mAudioSelectorItem.setIcon(R.drawable.ic_handset);
                        break;
                }
            }
        }
    };

    /**
     * Monitor the video renderers.
     * When a local or remote video renderer is added or removed we will add or remove the video surface views.
     */
    private final ObservableMonitor mVideoRenderersMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            BBMECall call = getCall();

            mLocalVideoRenderer = call.getLocalVideoRenderer();
            if (mLocalVideoRenderer != null) {
                if (mLocalVideoSurface != mLocalVideoRenderer.getView()) {
                    if (mLocalVideoSurface != null) {
                        //Make sure the existing surface view is removed
                        removeViewFromParent(mLocalVideoSurface);
                    }
                    mLocalVideoSurface = mLocalVideoRenderer.getView();
                    mLocalVideoRenderer.setScalingType(BBMEVideoRenderer.SCALE_ASPECT_FIT, BBMEVideoRenderer.SCALE_ASPECT_FIT);
                    mLocalVideoSurface.setZOrderMediaOverlay(true);
                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                    layoutParams.gravity = Gravity.CENTER;
                    mLocalVideoSurface.setLayoutParams(layoutParams);
                    mLocalVideoLayout.addView(mLocalVideoSurface);
                }
            } else {
                removeViewFromParent(mLocalVideoSurface);
            }
            mEnableCameraButton.setImageResource(mLocalVideoRenderer == null ? R.drawable.ic_video_off : R.drawable.ic_video_on);

            BBMEVideoRenderer remoteVideoRenderer = call.getRemoteVideoRenderer();
            if (remoteVideoRenderer != null) {
                if (mRemoteVideoSurface != remoteVideoRenderer.getView()) {
                    if (mRemoteVideoSurface != null) {
                        //Make sure the existing surface view is removed
                        removeViewFromParent(mRemoteVideoSurface);
                    }
                    mRemoteVideoSurface = remoteVideoRenderer.getView();
                    remoteVideoRenderer.setScalingType(BBMEVideoRenderer.SCALE_ASPECT_BALANCED, BBMEVideoRenderer.SCALE_ASPECT_BALANCED);
                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                    layoutParams.gravity = Gravity.CENTER;
                    mRemoteVideoSurface.setLayoutParams(layoutParams);
                    mRemoteVideoLayout.addView(mRemoteVideoSurface, 0);
                }
            } else {
                removeViewFromParent(mRemoteVideoSurface);
            }
        }
    };

    private void removeViewFromParent(View view) {
        if (view != null && view.getParent() != null) {
            //Remove the video surface from its existing parent
            ((ViewGroup)view.getParent()).removeView(view);
        }
    }

    /**
     * CallObserver to listen for callFailed and callEnded events.
     */
    private final BBMECallObserver mCallStateObserver = new BBMECallObserver() {

        @Override
        public void onIncomingCallMissed(@NonNull BBMECall call) {
            Logger.d("onIncomingCallMissed: ");
        }

        @Override
        public void onIncomingCallDeclined(@NonNull BBMECall call) {
            Logger.d("onIncomingCallDeclined: ");
        }

        @Override
        public void onOutgoingCallProceeding(@NonNull BBMECall call) {
            Logger.d("onOutgoingCallProceeding: ");
        }

        @Override
        public void onOutgoingCallRinging(@NonNull BBMECall call) {
            Logger.d("onOutgoingCallRinging: ");
        }

        @Override
        public void onCallConnected(@NonNull BBMECall call) {
            Logger.d("onCallConnected: ");
        }

        @Override
        public void onCallFailed(@NonNull BBMECall call) {
            Logger.d("onCallFailed: "+call.getFailureReason());
            //Display an error toast
            Toast.makeText(getApplicationContext(), "CallFailed: " + call.getFailureReason(), Toast.LENGTH_LONG).show();
            finish();
        }

        @Override
        public void onCallEnded(@NonNull BBMECall call) {
            Logger.d("onCallEnded: ");

            //Call is ending normally.
            finish();
        }
    };


    /**
     * Start/Stop camera click listener
     */
    private View.OnClickListener mEnableCameraButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Logger.gesture("CameraEnable clicked", InCallActivity.class);

            if (getCall().isCameraAvailable()) {
                if (!PermissionsUtil.checkSelfPermission(InCallActivity.this, Manifest.permission.CAMERA)) {
                    //We don't have camera permission.
                    KeyguardManager keyGuard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                    //Check if the device is locked, we can't ask for permissions if it is...
                    if (keyGuard.inKeyguardRestrictedInputMode()) {
                        Logger.i("Camera permission required, device locked.", InCallActivity.class);
                        //Prompt the user to unlock the device
                        AlertDialog.Builder builder = new AlertDialog.Builder(InCallActivity.this);
                        builder.setMessage(R.string.unlock_for_camera_permission);
                        builder.setPositiveButton(R.string.unlock_for_permission, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                PermissionsUtil.checkOrPromptSelfPermission(InCallActivity.this, Manifest.permission.CAMERA,
                                        PermissionsUtil.PERMISSION_CAMERA_FOR_VIDEO_CALL, R.string.rationale_camera);
                            }
                        });
                        builder.setNegativeButton(android.R.string.cancel, null);
                        builder.setCancelable(false);
                        builder.show();
                        return;
                    } else {
                        //Ask for permission to the camera
                        if (!PermissionsUtil.checkOrPromptSelfPermission(InCallActivity.this, Manifest.permission.CAMERA,
                                PermissionsUtil.PERMISSION_CAMERA_FOR_VIDEO_CALL, R.string.rationale_camera)) {
                            return;
                        }
                    }
                }

                startStopCamera();
            }
        }
    };

    @SuppressWarnings("MissingPermission")
    /**
     * Utility method to start or stop the camera.
     * The setCameraEnabled method is asynchronous.
     * A BBMECameraOperationCallback can be included to be notified when the action is completed.
     */
    private void startStopCamera() {
        //If we dont' have a local viewport then enable the camera (true), otherwise disable the camera (false)
        BBMEnterprise.getInstance().getMediaManager().setCameraEnabled(mLocalVideoRenderer == null, mCameraOnCallback);
        //Disable the button until the current camera operation has completed
        //This avoids the user pressing the button multiple times and the service potentially being overloaded.
        mEnableCameraButton.setClickable(false);
        //Show a progress spinner over the camera icon, when the action is completed we will remove the spinner
        mVideoProgessBar.setVisibility(View.VISIBLE);
    }

    /**
     * Camera operation callback, informs us of the success/failure of our change to the camera.
     */
    private BBMECameraOperationCallback mCameraOnCallback = new BBMECameraOperationCallback() {

        @Override
        public void onSuccess() {
            //Re-enable button and clear the progress spinner
            //Waiting a second before re-enabling the button to prevent too many calls changing the camera state.
            //Waiting to clear the spinner just to avoid it flashing too briefly.
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    mEnableCameraButton.setClickable(true);
                    mVideoProgessBar.setVisibility(View.GONE);
                }
            }, VIDEO_BUTTON_RENABLE_DELAY);
        }

        @Override
        public void onError() {
            //Re-enable button and clear the progress spinner
            //Waiting a second before re-enabling the button to prevent too many calls changing the camera state.
            //Waiting to clear the spinner just to avoid it flashing too briefly.
            mHandler.postDelayed(new Runnable() {
                 public void run() {
                     mEnableCameraButton.setClickable(true);
                     mVideoProgessBar.setVisibility(View.GONE);
                 }
            }, VIDEO_BUTTON_RENABLE_DELAY);

            //The action the user was trying to do failed. Provide a toast with an error message
            AlertDialog.Builder builder = new AlertDialog.Builder(InCallActivity.this);
            builder.setMessage(R.string.video_chat_cannot_enable_camera);
            builder.show();
        }
    };

    /**
     * Mute microphone click listener
     */
    private View.OnClickListener mMuteClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Logger.gesture("Mute clicked", InCallActivity.class);
            BBMECall call = getCall();
            //Flip the mute state
            BBMEnterprise.getInstance().getMediaManager().muteMicrophone(call.getCallId(), !call.isMuted());
        }
    };

    /**
     * End Call click listener
     */
    private View.OnClickListener mEndCallClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Logger.gesture("Hangup clicked", InCallActivity.class);
            BBMECall call = getCall();
            if (call.getCallState() != BBMECall.CallState.CALL_STATE_IDLE) {
                //Tell the media manager to end the call
                BBMEnterprise.getInstance().getMediaManager().endCall(mCallId);
            } else {
                //Shouldn't really happen but just in case make sure that the hangup button kills the activity
                finish();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        //Get rid of window background to reduce GPU overdraw
        getWindow().setBackgroundDrawable(null);
        //Set flags to ensure the voice screen remains active
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        super.onCreate(savedInstanceState);

        //Retrieve the saved call id
        if (savedInstanceState != null) {
            mCallId = savedInstanceState.getInt(EXTRA_CALL_ID);
        } else if (getIntent() != null) {
            mCallId = getIntent().getIntExtra(EXTRA_CALL_ID, -1);
        }

        //Check if the call still exists before continuing.
        if (!checkValidCall()) {
            return;
        }

        setContentView(R.layout.activity_in_call);

        //Set the proper flags to allow our UI to draw behind the system windows
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        ImageButton endCallButton = (ImageButton) findViewById(R.id.end_call);
        mMuteButton = (ImageButton) findViewById(R.id.call_screen_mute);
        mEnableCameraButton = (ImageButton) findViewById(R.id.call_screen_enable_camera);

        mTitle = (TextView) findViewById(R.id.voice_mode_call_title);

        mSubTitle = (TextView) findViewById(R.id.voice_mode_call_subtitle);
        mRemoteVideoLayout = (FrameLayout) findViewById(R.id.call_video_contents);
        mLocalVideoLayout = (FrameLayout) findViewById(R.id.local_video_layout);

        mVideoProgessBar = (ProgressBar) findViewById(R.id.video_button_progress_bar);
        //Set the color of the spinner to white
        mVideoProgessBar.getIndeterminateDrawable().setColorFilter(getResources().getColor(android.R.color.white), PorterDuff.Mode.MULTIPLY);

        //Setup click listeners
        mMuteButton.setOnClickListener(mMuteClickListener);
        endCallButton.setOnClickListener(mEndCallClickListener);
        mEnableCameraButton.setOnClickListener(mEnableCameraButtonListener);
    }

    /**
     * Check if the call id still matches an active call
     */
    private boolean checkValidCall() {
        BBMECall call = getCall();
        //There is no call happening so we should not display the call activity
        if (call.getCallState() == BBMECall.CallState.CALL_STATE_IDLE ||
                call.getCallState() == BBMECall.CallState.CALL_STATE_DISCONNECTED) {

            //There may have been a failure, if so display a toast
            if (call.getFailureReason() != BBMECall.FailReason.NO_FAILURE) {
                Toast.makeText(getApplicationContext(), "CallFailed: " + call.getFailureReason(), Toast.LENGTH_LONG).show();
            }
            finish();
            return false;
        }

        return true;
    }

    /**
     * Get the call instance matching our call id from the media manager.
     */
    private BBMECall getCall() {
        BBMEMediaManager mediaManager = BBMEnterprise.getInstance().getMediaManager();
        return mediaManager.getCall(mCallId).get();
    }


    @Override
    public void onDestroy() {
        // Make sure we're not keeping the screen on (fail-safe)
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        //Check to make sure our call still exists before continuing.
        if (!checkValidCall()) {
            return;
        }

        //Activate our monitors
        mVideoEnabledMonitor.activate();
        mStateMonitor.activate();
        mControlsActionMonitor.activate();
        mQualityMonitor.activate();
        mVideoRenderersMonitor.activate();

        //Add our call state observer
        getCall().addObserver(mCallStateObserver);

        //Start our call duration timer
        mUpdateDurationRunnable.run();
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
    @SuppressWarnings("MissingPermission")
    protected void onPause() {
        //Stop our monitors
        mVideoEnabledMonitor.dispose();
        mStateMonitor.dispose();
        mControlsActionMonitor.dispose();
        mQualityMonitor.dispose();
        mVideoRenderersMonitor.dispose();

        getCall().removeObserver(mCallStateObserver);

        //Stop the call timer
        mHandler.removeCallbacks(mUpdateDurationRunnable);
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);

        //Remove the video surfaces, we may be given back the same surface again and we don't want it to be parented already.
        if (mLocalVideoSurface != null) {
            removeViewFromParent(mLocalVideoSurface);
            mLocalVideoSurface = null;
        }
        if (mRemoteVideoSurface != null) {
            removeViewFromParent(mRemoteVideoSurface);
            mRemoteVideoSurface = null;
        }

        super.onPause();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        //Save the call id
        outState.putInt(EXTRA_CALL_ID, mCallId);
        super.onSaveInstanceState(outState);
    }

    @Override
    @SuppressWarnings("MissingPermission")
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Logger.d("InCallActivity.onRequestPermissionsResult: requestCode=" + requestCode + " " + PermissionsUtil.resultsToString(permissions, grantResults));
        // Check the super first, we have a critical permission needed for the app.
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        //neither permissions or grantResults should be empty but google docs warns they could be and should be treated as a cancellation
        if (permissions.length == 0 && grantResults.length == 0) {
            Logger.w("empty permissions and/or grantResults");
            return;
        }

        if (requestCode == PermissionsUtil.PERMISSION_CAMERA_REQUEST
                || requestCode == PermissionsUtil.PERMISSION_CAMERA_FOR_VIDEO_CALL) {
            //Turn on camera if permission granted.
            startStopCamera();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (menu != null) {
            getMenuInflater().inflate(R.menu.call_menu, menu);
            mSwitchCameraItem = menu.findItem(R.id.switch_camera);
            mAudioSelectorItem = menu.findItem(R.id.change_audio_device);
            //Trigger the audio controls monitor to update the audio device state
            mControlsActionMonitor.activate();
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @SuppressWarnings("MissingPermission")
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item != null) {
            switch (item.getItemId()) {
                case android.R.id.home:
                    finish();
                    return true;
                case R.id.change_audio_device:
                    Logger.gesture("Audio selector clicked", InCallActivity.class);
                    BBMEMediaManager mediaManager = BBMEnterprise.getInstance().getMediaManager();

                    //Get a list of available audio devices
                    List<BBMEMediaManager.AudioDevice> devices = mediaManager.getAvailableAudioDevices().get();

                    //Cycle the list to the next available audio device
                    int nextIndex = (devices.indexOf(mediaManager.getActiveAudioDevice().get()) + 1) % devices.size();
                    mediaManager.setActiveAudioDevice(devices.get(nextIndex));
                    return true;
                case R.id.switch_camera:
                    Logger.gesture("SwitchCamera clicked", InCallActivity.class);

                    //Limit the calls to switch camera until the current call has finished
                    mSwitchCameraItem.setEnabled(false);

                    BBMEnterprise.getInstance().getMediaManager().switchCamera(new BBMECameraOperationCallback() {
                        @Override
                        public void onSuccess() {
                            //Temporarily disable the button to avoid the user potentially clicking multiple times.
                            mHandler.postDelayed(new Runnable() {
                                public void run() {
                                    mSwitchCameraItem.setEnabled(true);
                                }
                            }, VIDEO_BUTTON_RENABLE_DELAY);
                        }

                        @Override
                        public void onError() {
                            //Unable to switch cameras, inform the user with a toast
                            AlertDialog.Builder builder = new AlertDialog.Builder(InCallActivity.this);
                            builder.setMessage(R.string.video_chat_cannot_switch_cameras);
                            builder.show();
                            mSwitchCameraItem.setEnabled(true);
                        }
                    });
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }
        return false;
    }

}
