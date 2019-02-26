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

package com.bbm.example.quickstart;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.BbmdsProtocol;
import com.bbm.sdk.bbmds.GlobalAuthTokenState;
import com.bbm.sdk.bbmds.GlobalLocalUri;
import com.bbm.sdk.bbmds.GlobalSetupState;
import com.bbm.sdk.bbmds.GlobalSyncPasscodeState;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.inbound.EndpointDeregisterResult;
import com.bbm.sdk.bbmds.inbound.EndpointUpdateResult;
import com.bbm.sdk.bbmds.inbound.Endpoints;
import com.bbm.sdk.bbmds.inbound.SetupError;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.AuthToken;
import com.bbm.sdk.bbmds.outbound.EndpointDeregister;
import com.bbm.sdk.bbmds.outbound.EndpointUpdate;
import com.bbm.sdk.bbmds.outbound.EndpointsGet;
import com.bbm.sdk.bbmds.outbound.SetupRetry;
import com.bbm.sdk.bbmds.outbound.SyncStart;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.BBMEnterpriseState;
import com.bbm.sdk.service.InboundMessageObservable;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.UUID;


/**
 * This example demonstrates the required steps to setup a Spark SDK application. This includes
 * providing an authentication token, and handling endpoint registration.
 * The mock authentication token that is generated that can only used to register with the
 * Spark SDK sandbox servers.
 */
public class SetupActivity extends AppCompatActivity {

    private BBMEnterprise mBbmEnterprise;

    //Global values to monitor during setup
    private ObservableValue<GlobalSetupState> mSetupState;
    private ObservableValue<GlobalAuthTokenState> mAuthTokenState;
    private ObservableValue<GlobalSyncPasscodeState> mSyncPasscodeState;
    private ObservableValue<GlobalLocalUri> mLocalUri;
    private ObservableValue<BBMEnterpriseState> mBbmEnterpriseState;  //overall state of the service
    private InboundMessageObservable<SetupError> mSetupErrorObservable;

    //UI elements
    private TextView mEnterpriseStateView;
    private TextView mSetupStateView;
    private TextView mAuthTokenStateView;
    private TextView mLocalRegIdView;

    private TextView mSetupErrorView;
    private Button mServiceStopButton;
    private View mSetupErrorContainer; //for controlling visibility of error msg only


    //Create an observer to monitor the BBM Enterprise SDK state
    private final Observer mEnterpriseStateObserver = new Observer() {
        @Override
        public void changed() {
            final BBMEnterpriseState bbmEnterpriseState = mBbmEnterpriseState.get();
            mEnterpriseStateView.setText(bbmEnterpriseState.toString());
            switch (bbmEnterpriseState) {
                case STARTING:
                case STARTED:
                    updateServiceButtonState(false);
                    break;
                case STOPPED:
                    mSetupStateView.setText("");
                    mAuthTokenStateView.setText("");
                    mLocalRegIdView.setText("");
                case FAILED:
                default:
                    updateServiceButtonState(true);
                    break;
            }
        }
    };

    //Create an observer to monitor the setup state global
    private final Observer mSetupStateObserver = new Observer() {
        @Override
        public void changed() {
            final GlobalSetupState setupState = mSetupState.get();
            mSetupStateView.setText(setupState.state.toString());

            if (setupState.getExists() != Existence.YES) {
                return;
            }

            switch (setupState.state) {
                case NotRequested:
                    //Register this device as a new endpoint
                    registerDevice();
                    break;
                case Full:
                    //Handle the case where this account has reached the maximum number of registered endpoints
                    handleFullState();
                    break;
                case Ongoing:
                    //Ongoing has additional information in the progressMessage
                    mSetupStateView.setText(setupState.state.toString() + ":" + setupState.progressMessage.toString());
                    break;
                case SyncRequired:
                    //SyncRequired state is processed by the syncPasscodeStateObserver
                case Success:
                    //Setup completed
                    break;
                case Unspecified:
                    break;
            }
        }
    };

    //Observes the GlobalSetupState and the GlobalSyncPasscodeState.
    //When the required a passcode is sent to complete setup using the 'SyncStart' message.
    private Observer mSyncPasscodeStateObserver = new Observer() {
        @Override
        public void changed() {
            GlobalSetupState setupState = mSetupState.get();
            //When the GlobalSetupState is 'SyncRequired' then send the passcode to the SDK to continue setup
            if (setupState.state == GlobalSetupState.State.SyncRequired) {
                GlobalSyncPasscodeState syncPasscodeState = mSyncPasscodeState.get();
                //For simplicity, this example hard codes a passcode.
                //A passcode obtained from a user is a more secure solution.
                SyncStart syncStart = new SyncStart("user-passcode");
                switch (syncPasscodeState.value) {
                    case New:
                        //No existing keys were found, so send the SyncStart with action 'New'
                        syncStart.action(SyncStart.Action.New);
                        BBMEnterprise.getInstance().getBbmdsProtocol().send(syncStart);
                        break;
                    case Existing:
                        //Existing keys stored in KMS were found, so send the SyncStart with action 'Existing'
                        syncStart.action(SyncStart.Action.Existing);
                        BBMEnterprise.getInstance().getBbmdsProtocol().send(syncStart);
                        break;
                    default:
                        //No action
                }
            }
        }
    };

    //Create an observer to monitor the auth token state
    private final Observer mAuthTokenStateObserver = new Observer() {

        @Override
        public void changed() {
            final GlobalAuthTokenState authTokenState = mAuthTokenState.get();
            mAuthTokenStateView.setText(authTokenState.value.toString());

            if (authTokenState.getExists() != Existence.YES) {
                return;
            }

            switch (authTokenState.value) {
                case Ok:
                    break;
                case Needed:
                    //Generate an unsigned token to authenticate with the spark servers
                    generateAuthToken();
                    break;
                case Rejected:
                    break;
                case Unspecified:
                    break;
            }
        }
    };

    private final Observer mLocalRegIdObserver = new Observer() {
        @SuppressLint("SetTextI18n")
        @Override
        public void changed() {
            //Check if the local uri is populated
            if (mLocalUri.get().getExists() == Existence.YES) {
                final String localUserUri = mLocalUri.get().value;
                //Check if the user is populated
                ObservableValue<User> user = mBbmEnterprise.getBbmdsProtocol().getUser(localUserUri);
                if (user.get().getExists() == Existence.MAYBE) {
                    user.addObserver(this);
                } else {
                    //Set the text view to the regId of the local user
                    mLocalRegIdView.setText(Long.toString(user.get().regId));
                }
            }
        }
    };

    private final Observer mSetupErrorObserver = new Observer() {
        @Override
        public void changed() {
            SetupError setupError = mSetupErrorObservable.get();
            mSetupErrorContainer.setVisibility(View.VISIBLE);
            mSetupErrorView.setText(setupError.error.toString());
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        // Load the view.
        mEnterpriseStateView = findViewById(R.id.service_state);

        mSetupStateView = findViewById(R.id.setup_state);
        mAuthTokenStateView = findViewById(R.id.auth_token_state);
        mLocalRegIdView = findViewById(R.id.reg_id);
        mSetupErrorContainer = findViewById(R.id.setup_error);
        mSetupErrorView = findViewById(R.id.error_message);

        //STOP button
        mServiceStopButton = findViewById(R.id.service_stop_button);
        mServiceStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mServiceStopButton.isEnabled()) {
                    mServiceStopButton.setEnabled(false);  //disable button to avoid multiple clicks
                    stopBBMEnterpriseService();
                }
            }
        });

        // initialize the view
        updateServiceButtonState(true);

        // Initialize the Spark SDK
        mBbmEnterprise = BBMEnterprise.getInstance();
        mBbmEnterprise.initialize(this);

        //Start the SDK
        final boolean startSuccessful = mBbmEnterprise.start();
        if (!startSuccessful) {
            //implies BBMEnterprise was already started.  Call stop before trying to start again
            Toast.makeText(SetupActivity.this, "Service already started.", Toast.LENGTH_LONG).show();
        }
        updateServiceButtonState(false);  //show stop on the button

        // Initialize our observers of the Spark SDK setup states
        initializeObservers();
    }

    @Override
    public void onResume() {
        super.onResume();
        mEnterpriseStateObserver.changed();
        mSetupStateObserver.changed();
        mAuthTokenStateObserver.changed();
        mLocalRegIdObserver.changed();
    }

    private void initializeObservers() {

        mBbmEnterpriseState = mBbmEnterprise.getState();
        mBbmEnterpriseState.addObserver(mEnterpriseStateObserver);

        //Create an observable to listen for SetupError messages
        mSetupErrorObservable = new InboundMessageObservable<>(
                new SetupError(),
                mBbmEnterprise.getBbmdsProtocolConnector()
        );
        mSetupErrorObservable.addObserver(mSetupErrorObserver);

        final BbmdsProtocol protocol = mBbmEnterprise.getBbmdsProtocol();
        //fetch setup related globals for monitoring
        mSetupState = protocol.getGlobalSetupState();
        mSetupState.addObserver(mSetupStateObserver);

        mAuthTokenState = protocol.getGlobalAuthTokenState();
        mAuthTokenState.addObserver(mAuthTokenStateObserver);

        mSyncPasscodeState = protocol.getGlobalSyncPasscodeState();
        //The passcode state observer needs to monitor the GlobalSetupState and the GlobalSyncPasscodeState
        mSyncPasscodeState.addObserver(mSyncPasscodeStateObserver);
        mSetupState.addObserver(mSyncPasscodeStateObserver);

        mLocalUri = protocol.getGlobalLocalUri();
        mLocalUri.addObserver(mLocalRegIdObserver);
    }

    /**
     * Generate an unsigned JWT token that can be used for authentication with the sandbox Spark servers.
     * This token includes a hard-coded userId value and uses the application package name as the audience value.
     */
    private void generateAuthToken() {
        //Generate a mock token
        int base64Flags = Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP;
        String token = null;
        //User Id is hard-coded for convenience here
        String userId = "sampleSparkUserId";

        try {
            JSONObject header = new JSONObject();
            header.put("alg", "none");

            SecureRandom rand = new SecureRandom();
            byte[] bytes = new byte[128];
            //Get some random bytes
            rand.nextBytes(bytes);
            //Use the first 18 characters as the token id
            String jti = Base64.encodeToString(bytes, base64Flags).substring(0, 18);

            JSONObject body = new JSONObject();
            body.put("jti", jti);
            body.put("sub", userId);
            //Intialized at the current time - 60 seconds, to allow for clock differences between the client and server
            body.put("iat", System.currentTimeMillis() / 1000 - 60);
            //Expires in one hour.
            body.put("exp", System.currentTimeMillis() / 1000 + 60 * 60);

            String base64Header = Base64.encodeToString(header.toString().getBytes(), base64Flags);
            String base64Body = Base64.encodeToString(body.toString().getBytes(), base64Flags);

            token = base64Header + '.' + base64Body + '.';
        } catch (JSONException e) {
        }
        if (token != null) {
            AuthToken authToken = new AuthToken(token, userId);
            mBbmEnterprise.getBbmdsProtocol().send(authToken);
        }
    }

    private void stopBBMEnterpriseService() {
        try {
            mBbmEnterprise.wipe();   //wipe credentials
        } catch (RuntimeException e) {
            Toast.makeText(SetupActivity.this, "Unable to wipe credentials.", Toast.LENGTH_LONG).show();
        }

        mBbmEnterprise.stop(); //stop the service
        updateServiceButtonState(true); // show the sign in button
    }

    private void updateServiceButtonState(final boolean stopped) {
        //update the click behavior
        mServiceStopButton.setVisibility(stopped ? View.GONE : View.VISIBLE);
        mServiceStopButton.setEnabled(true);
    }

    /**
     * This will register the current device. This MUST be called when {@link GlobalSetupState} is
     * in the {@link GlobalSetupState.State#NotRequested}. This will pre-register the device with
     * the SDK. Once a {@link com.bbm.sdk.bbmds.outbound.AuthToken} is sent to the SDK, the SDK
     * shall attempt to register the device during setup.
     */
    private void registerDevice() {

        final ObservableValue<GlobalSetupState> globalSetupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState();

        // Don't send if setup if the state is not Not Requested.
        if (globalSetupState.get().state != GlobalSetupState.State.NotRequested) {
            return;
        }

        // Create a cookie to track the request.
        final String requestCookie = UUID.randomUUID().toString();

        // Create a one time inbound message observer
        final InboundMessageObservable<EndpointUpdateResult> updateObservable = new InboundMessageObservable<>(
                new EndpointUpdateResult(), requestCookie, BBMEnterprise.getInstance().getBbmdsProtocolConnector());

        // Create a monitor to wait for the response.
        final SingleshotMonitor updateResultMonitor = new SingleshotMonitor() {
            @Override
            protected boolean runUntilTrue() {
                if (updateObservable.get().getExists() == Existence.MAYBE) {
                    return false;
                }

                EndpointUpdateResult result = updateObservable.get();
                if (result.result == EndpointUpdateResult.Result.Failure) {
                    mSetupErrorView.setText(R.string.endpoint_update_failed);
                }

                return true;
            }
        };

        // Send an EndpointUpdate to register this local device.
        String initialName = "Simple Setup";
        String initialDescription = "Simple setup example";

        EndpointUpdate update = new EndpointUpdate(
                requestCookie,
                initialDescription,
                initialName
        );

        updateResultMonitor.activate();
        BBMEnterprise.getInstance().getBbmdsProtocol().send(update);
    }

    /**
     * Setup encounters the {@link GlobalSetupState.State#Full}. At this point the current account has been
     * registered on a number of devices, aka endpoints. To allow the user to continue with setup a single
     * registered device needs to be removed. For this example we will remove the first registered device.
     * <p>
     * Note, in practice it is better to get the list of {@link Endpoints} and present some UI to ask the
     * device user to select one to be removed.
     */
    private void handleFullState() {
        final ObservableValue<GlobalSetupState> globalSetupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState();

        // Don't send if setup if the state is not Full.
        if (globalSetupState.get().state != GlobalSetupState.State.Full) {
            return;
        }

        // Create a cookie to track the request.
        final String requestCookie = UUID.randomUUID().toString();

        // Create a one time inbound message observer that will have the results.
        final InboundMessageObservable<Endpoints> getObserver = new InboundMessageObservable<>(
                new Endpoints(), requestCookie, BBMEnterprise.getInstance().getBbmdsProtocolConnector());

        // Create a results monitor to wait for the response.
        final SingleshotMonitor getResultsMonitor = new SingleshotMonitor() {
            @Override
            protected boolean runUntilTrue() {
                // No data yet to wait.
                if (getObserver.get().getExists() == Existence.MAYBE) {
                    return false;
                }

                // Get the results and check for Success/Fail.
                Endpoints result = getObserver.get();
                if (result.result == Endpoints.Result.Success) {

                    // We have a list of Endpoints! Now lets remove one.
                    removeDevice(getObserver.get());
                } else {
                    mSetupErrorView.setText(R.string.endpoint_get_failed);
                }
                return true;
            }
        };

        // activate the monitor and issue the protocol request.
        getResultsMonitor.activate();
        BBMEnterprise.getInstance().getBbmdsProtocol().send(new EndpointsGet(requestCookie));
    }

    /**
     * Removes the first registered endpoint from a list of {@link Endpoints}
     *
     * @param endpoints A list of endpoints. Must not be null.
     */
    private void removeDevice(@NonNull final Endpoints endpoints) {

        final String requestCookie = UUID.randomUUID().toString();

        // Create a one time inbound message observer for the remove results.
        final InboundMessageObservable<EndpointDeregisterResult> removeObserver = new InboundMessageObservable<>(
                new EndpointDeregisterResult(), requestCookie, BBMEnterprise.getInstance().getBbmdsProtocolConnector());

        // Create a monitor to wait for the response.
        final SingleshotMonitor removeResultsMonitor = new SingleshotMonitor() {
            @Override
            protected boolean runUntilTrue() {
                if (removeObserver.get().getExists() == Existence.MAYBE) {
                    return false;
                }

                //
                EndpointDeregisterResult result = removeObserver.get();
                if (result.result == EndpointDeregisterResult.Result.Success) {
                    BBMEnterprise.getInstance().getBbmdsProtocol().send(new SetupRetry());
                } else {
                    mSetupErrorView.setText(R.string.endpoint_remove_failed);
                }

                return true;
            }
        };

        // Remove the first result to continue setup.
        removeResultsMonitor.activate();
        final String endpointId = endpoints.registeredEndpoints.get(0).endpointId;
        BBMEnterprise.getInstance().getBbmdsProtocol().send(new EndpointDeregister(requestCookie, endpointId));
    }
}
