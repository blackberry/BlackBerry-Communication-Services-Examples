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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.GlobalLocalUri;
import com.bbm.sdk.bbmds.GlobalSetupState;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.SetupRetry;
import com.bbm.sdk.media.BBMECall;
import com.bbm.sdk.media.BBMEMediaManager;
import com.bbm.sdk.reactive.ObservableMonitor;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.support.identity.UserIdentityMapper;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.ui.widgets.UserIdPrompter;
import com.bbm.sdk.support.util.AuthIdentityHelper;
import com.bbm.sdk.support.util.BbmUtils;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.util.PermissionsUtil;
import com.bbm.sdk.support.util.SetupHelper;


public class MainActivity extends AppCompatActivity  {

    private View mActiveCallBar;
    private TextView mActiveCallText;

    // Handle the setup events
    private Observer mBbmSetupObserver = new Observer() {
        @Override
        public void changed() {
            final ObservableValue<GlobalSetupState> globalSetupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState();

            if (globalSetupState.get().exists != Existence.YES) {
                return;
            }

            GlobalSetupState currentState = globalSetupState.get();

            switch (currentState.state) {
                case NotRequested:
                    SetupHelper.registerDevice("SoftPhone", "SoftPhone example");
                    break;
                case DeviceSwitchRequired:
                    //Automatically switch to this device.
                    BBMEnterprise.getInstance().getBbmdsProtocol().send(new SetupRetry());
                    break;
                case Full:
                    SetupHelper.handleFullState();
                    break;
                case Ongoing:
                case Success:
                case Unspecified:
                    break;
            }
        }
    };

    /**
     * Track our registration id to display it.
     */
    private ObservableMonitor myRegistrationIdObserver = new ObservableMonitor() {
        @Override
        public void run() {
            GlobalLocalUri uri = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalLocalUri().get();
            if (uri.getExists() == Existence.YES) {
                User localUser = BBMEnterprise.getInstance().getBbmdsProtocol().getUser(uri.value).get();
                ((TextView) findViewById(R.id.my_reg_id)).setText(getString(R.string.my_registration_id, localUser.regId));
            }
        }
    };

    /**
     * Track the local app user and display their user name
     */
    private ObservableMonitor mLocalUserObserver = new ObservableMonitor() {
        @Override
        public void run() {
            AppUser localAppUser = UserManager.getInstance().getLocalAppUser().get();
            if (localAppUser.getExists() == Existence.YES) {
                ((TextView)findViewById(R.id.my_user_id)).setText(getString(R.string.my_user_name, localAppUser.getName()));
            }
        }
    };

    /**
     * Track if a call is currently in progress
     */
    private ObservableMonitor mInACallMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            int callId = BBMEnterprise.getInstance().getMediaManager().getActiveCallId().get();
            BBMECall activeCall = BBMEnterprise.getInstance().getMediaManager().getCall(callId).get();
            if (activeCall.getExists() == Existence.YES
                    && activeCall.getCallState() != BBMECall.CallState.CALL_STATE_DISCONNECTED
                    && activeCall.getCallState() != BBMECall.CallState.CALL_STATE_RECEIVING) {
                AppUser user = UserManager.getInstance().getUser(activeCall.getRegId()).get();
                String name = user.getExists() == Existence.YES && !TextUtils.isEmpty(user.getName()) ?
                        user.getName() : Long.toString(activeCall.getRegId());
                mActiveCallText.setText(getString(R.string.in_active_call, name));
                mActiveCallBar.setVisibility(View.VISIBLE);
            } else {
                mActiveCallBar.setVisibility(View.GONE);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        //Listen to the setup events
        final ObservableValue<GlobalSetupState> globalSetupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState();

        //Add setup observer to the globalSetupStateObservable
        globalSetupState.addObserver(mBbmSetupObserver);
        //Call changed to trigger our observer to run immediately
        mBbmSetupObserver.changed();

        //Provide the activity to the Auth helper so it can prompt the user for credentials
        AuthIdentityHelper.setActivity(this);

        //Set the click listener for the start call button
        FloatingActionButton startCallFloatingButton = (FloatingActionButton)findViewById(R.id.start_call_fab);
        startCallFloatingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BBMEMediaManager mediaManager = BBMEnterprise.getInstance().getMediaManager();
                int activeCallId = mediaManager.getActiveCallId().get();
                if (mediaManager.getCall(activeCallId).get().getCallState() != BBMECall.CallState.CALL_STATE_IDLE) {
                    //We are in a call, reopen the call activity
                    Intent inCallIntent = new Intent(MainActivity.this, InCallActivity.class);
                    inCallIntent.putExtra(InCallActivity.EXTRA_CALL_ID, activeCallId);
                    startActivity(inCallIntent);
                } else {
                    UserIdPrompter prompter = new UserIdPrompter();
                    prompter.setTitle(getString(R.string.start_call));
                    prompter.show(MainActivity.this, new UserIdPrompter.SelectedUserIdCallback() {
                        @Override
                        public void selectedUserId(String userId, String secondaryInput) {
                            SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                                @Override
                                public boolean run() {
                                    UserIdentityMapper.IdentityMapResult mapResult =
                                            UserIdentityMapper.getInstance().getRegIdForUid(userId, false).get();
                                    if (mapResult.existence == Existence.MAYBE) {
                                        return false;
                                    }

                                    if (mapResult.existence == Existence.YES) {
                                        //Start a call (including permission check)
                                        CallUtils.makeCall(MainActivity.this, null, mapResult.regId);
                                    } else {
                                        Toast.makeText(MainActivity.this,
                                                getString(R.string.user_id_not_found, userId),
                                                Toast.LENGTH_LONG).show();
                                    }
                                    return true;
                                }
                            });
                        }
                    });
                }
            }
        });

        mActiveCallBar = findViewById(R.id.active_call_bar);
        mActiveCallText = ((TextView) findViewById(R.id.active_call_text));
        mActiveCallBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int callId = BBMEnterprise.getInstance().getMediaManager().getActiveCallId().get();
                //We are in a call, reopen the call activity
                Intent inCallIntent = new Intent(MainActivity.this, InCallActivity.class);
                inCallIntent.putExtra(InCallActivity.EXTRA_CALL_ID, callId);
                startActivity(inCallIntent);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        Logger.d("onOptionsItemSelected: item=" + item + " id=" + id);
        if (id == R.id.action_send_log_files) {
            if (PermissionsUtil.checkOrPromptSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    PermissionsUtil.PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_TO_ATTACH_FILES, R.string.rationale_write_external_storage, null)) {
                //This will create a zip with the BBM SDK log files and send to intent so the user can choose to send by email or some other action
                BbmUtils.sendBbmLogFiles(BuildConfig.APPLICATION_ID, this);
            }
            return true;
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Logger.d("onRequestPermissionsResult: requestCode=" + requestCode + " " + PermissionsUtil.resultsToString(permissions, grantResults));

        //neither permissions or grantResults should be empty but google docs warns they could be and should be treated as a cancellation
        if (permissions.length == 0 && grantResults.length == 0) {
            Logger.w("empty permissions and/or grantResults");
            return;
        }

        if (requestCode == PermissionsUtil.PERMISSION_RECORD_AUDIO_FOR_VOICE_CALL) {
            if (PermissionsUtil.isGranted(grantResults, 0)) {
                //If the user granted us permission to start the call we can do so immediately.
                CallUtils.makeCallPermissionGranted(this, null);
            } else {
                PermissionsUtil.displayCanNotContinueIfCanNotAsk(this, android.Manifest.permission.RECORD_AUDIO,
                        R.string.rationale_record_audio_denied);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        myRegistrationIdObserver.activate();
        mInACallMonitor.activate();
        mLocalUserObserver.activate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        myRegistrationIdObserver.dispose();
        mInACallMonitor.dispose();
        mLocalUserObserver.activate();
    }
}
