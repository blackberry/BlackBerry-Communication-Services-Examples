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

import android.accounts.Account;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.BbmdsProtocol;
import com.bbm.sdk.bbmds.GlobalAuthTokenState;
import com.bbm.sdk.bbmds.GlobalLocalUri;
import com.bbm.sdk.bbmds.GlobalProfileKeysState;
import com.bbm.sdk.bbmds.GlobalSetupState;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.inbound.EndpointDeregisterResult;
import com.bbm.sdk.bbmds.inbound.EndpointUpdateResult;
import com.bbm.sdk.bbmds.inbound.Endpoints;
import com.bbm.sdk.bbmds.inbound.ProfileKeys;
import com.bbm.sdk.bbmds.inbound.SetupError;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.AuthToken;
import com.bbm.sdk.bbmds.outbound.EndpointDeregister;
import com.bbm.sdk.bbmds.outbound.EndpointUpdate;
import com.bbm.sdk.bbmds.outbound.EndpointsGet;
import com.bbm.sdk.bbmds.outbound.ProfileKeysExport;
import com.bbm.sdk.bbmds.outbound.SetupRetry;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.BBMEnterpriseState;
import com.bbm.sdk.service.InboundMessageObservable;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;
import java.util.UUID;


/**
 * This example shows how to use Google Sign-in for Android to obtain a user Id and token that can be used
 * as the AuthToken for the BBM SDK. The intention is not to be an overview of Google Sign-in for Android, for
 * more information see https://developers.google.com/identity/sign-in/android/. This example is using
 * the simple auto managed mode of the Google API.
 * <p>
 * Please note you will need to setup a configuration file and a example server client id for this example,
 * steps are found https://developers.google.com/identity/sign-in/android/start
 */
public class SetupActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {

    private BBMEnterprise mBbmEnterprise;

    //Global values to monitor during setup
    private ObservableValue<GlobalSetupState> mSetupState;
    private ObservableValue<GlobalAuthTokenState> mAuthTokenState;
    private ObservableValue<GlobalLocalUri> mLocalUri;
    private ObservableValue<BBMEnterpriseState> mBbmEnterpriseState;  //overall state of the service
    private ObservableValue<GlobalProfileKeysState> mProfileKeysState;
    private InboundMessageObservable<SetupError> mSetupErrorObservable;
    private InboundMessageObservable<ProfileKeys> mInboundProfileKeys;

    //UI elements
    private TextView mEnterpriseStateView;
    private TextView mSetupStateView;
    private TextView mAuthTokenStateView;
    private TextView mLocalRegIdView;
    private TextView mProfileKeysView;

    private TextView mSetupErrorView;
    private Button mServiceStopButton;
    private View mSetupErrorContainer; //for controlling visibility of error msg only

    private static final int RC_SIGN_IN = 9001; // The result code to be used when calling startActivityForResult

    private GoogleApiClient mGoogleApiClient;
    private SignInButton mSignInButton;

    private String mGoogleUserId;
    private String mGoogleToken;

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

            final BbmdsProtocol protocol = mBbmEnterprise.getBbmdsProtocol();
            switch (setupState.state) {
                case NotRequested:
                    if (!TextUtils.isEmpty(mGoogleToken) && !TextUtils.isEmpty(mGoogleUserId)) {
                        registerDevice();
                        final AuthToken message = new AuthToken(mGoogleToken, mGoogleUserId);
                        mBbmEnterprise.getBbmdsProtocol().send(message);
                    }
                    break;
                case DeviceSwitchRequired:
                    AlertDialog.Builder builder = new AlertDialog.Builder(SetupActivity.this);
                    builder.setMessage(R.string.device_switch_confirmation);
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //Send SetupRetry msg. The BBM Enterprise SDK will switch the users account to this device.
                            protocol.send(new SetupRetry());
                        }
                    });
                    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mBbmEnterprise.stop();
                        }
                    }).show();
                    break;
                case Full:
                    handleFullState();
                    break;
                case Ongoing:
                    //Ongoing has additional information in the progressMessage
                    mSetupStateView.setText(setupState.state.toString() + ":" + setupState.progressMessage.toString());
                    break;
                case Success:
                    break;
                case Unspecified:
                    break;
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
                    if (!TextUtils.isEmpty(mGoogleToken) && !TextUtils.isEmpty(mGoogleUserId)) {
                        final AuthToken message = new AuthToken(mGoogleToken, mGoogleUserId);
                        mBbmEnterprise.getBbmdsProtocol().send(message);
                    }
                    break;
                case Rejected:
                    break;
                case Unspecified:
                    break;
            }
        }
    };

    private final Observer mLocalRegIdObserver = new Observer() {
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

    //Create an observer to monitor the GlobalProfileKeysState
    private final Observer mProfileKeysObserver = new Observer() {
        @Override
        public void changed() {
            mProfileKeysView.setText(mProfileKeysState.get().value.name());
            if (mProfileKeysState.get().getExists() == Existence.YES && mProfileKeysState.get().value == GlobalProfileKeysState.State.NotSynced) {
                //In your application you should import existing keys if they are available in your Cloud Key Storage.
                //Export profile keys from the BBM Enterprise SDK
                BBMEnterprise.getInstance().getBbmdsProtocol().send(new ProfileKeysExport());
            }
        }
    };

    private final Observer mInboundProfileKeysObserver = new Observer() {
        @Override
        public void changed() {
            if (mInboundProfileKeys.get().exists == Existence.YES) {
                //In your application you should save the keys to your cloud storage as described in the Cloud Key Storage guide.
                //Now that the keys have been exported successfully we can change the key state to synced.
                GlobalProfileKeysState.AttributesBuilder stateChange = new GlobalProfileKeysState.AttributesBuilder();
                stateChange.value(GlobalProfileKeysState.State.Synced);
                BBMEnterprise.getInstance().getBbmdsProtocol().send(mProfileKeysState.get().requestListChange(stateChange));
            }
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        // Initialize BBMEnterprise SDK
        initializeBBMEnterprise();

        // Load the view.
        mEnterpriseStateView = (TextView) findViewById(R.id.service_state);

        mSetupStateView = (TextView) findViewById(R.id.setup_state);
        mAuthTokenStateView = (TextView) findViewById(R.id.auth_token_state);
        mLocalRegIdView = (TextView) findViewById(R.id.reg_id);
        mSetupErrorContainer = findViewById(R.id.setup_error);
        mSetupErrorView = (TextView) findViewById(R.id.error_message);

        //STOP button
        mServiceStopButton = (Button) findViewById(R.id.service_stop_button);
        mServiceStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mServiceStopButton.isEnabled()) {
                    mServiceStopButton.setEnabled(false);  //disable button to avoid multiple clicks

                    Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                            new ResultCallback<Status>() {
                                @Override
                                public void onResult(@NonNull Status status) {
                                    mGoogleUserId = "";
                                    mGoogleToken = "";
                                    stopBBMEnterpriseService();
                                }
                            });

                }
            }
        });

        final String clientServerId = BuildConfig.CLIENT_SERVER_ID;
        // Configure sign-in to request the user's ID, email address, and its Id token.
        // ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientServerId)
                .requestEmail()
                .build();

        // Build a GoogleApiClient with access to the Google Sign-In API and the
        // options specified by gso.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        mSignInButton = (SignInButton) findViewById(R.id.google_sign_in_button);
        mSignInButton.setSize(SignInButton.SIZE_STANDARD);
        mSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
                startActivityForResult(signInIntent, RC_SIGN_IN);
            }
        });

        mProfileKeysView = (TextView)findViewById(R.id.profile_keys);

        // initialize the view
        updateServiceButtonState(true);
    }

    @Override
    public void onResume() {
        super.onResume();

        mEnterpriseStateObserver.changed();
        mSetupStateObserver.changed();
        mAuthTokenStateObserver.changed();
        mLocalRegIdObserver.changed();
        mProfileKeysObserver.changed();
    }

    private void initializeBBMEnterprise() {
        // get the service
        mBbmEnterprise = BBMEnterprise.getInstance();
        mBbmEnterprise.initialize(this);

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

        mLocalUri = protocol.getGlobalLocalUri();
        mLocalUri.addObserver(mLocalRegIdObserver);

        mProfileKeysState = protocol.getGlobalProfileKeysState();
        mProfileKeysState.addObserver(mProfileKeysObserver);

        mInboundProfileKeys = new InboundMessageObservable<>(
                new ProfileKeys(),
                mBbmEnterprise.getBbmdsProtocolConnector()
        );
        mInboundProfileKeys.addObserver(mInboundProfileKeysObserver);
    }

    private void startBBMEnterpriseService() {
        mSignInButton.setEnabled(false);  //disable button to avoid multiple clicks
        mSetupErrorView.setText(null); //clear any outstanding errors

        final boolean startSuccessful = mBbmEnterprise.start();
        if (!startSuccessful) {
            //implies BBMEnterprise was already started.  Call stop before trying to start again
            Toast.makeText(SetupActivity.this, "Service already started.", Toast.LENGTH_LONG).show();
        }
        updateServiceButtonState(false);  //show stop on the button
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
        mSignInButton.setVisibility(stopped ? View.VISIBLE : View.GONE);

        mSignInButton.setEnabled(true);
        mServiceStopButton.setEnabled(true);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        mSetupErrorContainer.setVisibility(View.VISIBLE);
        mSetupErrorView.setText(connectionResult.getErrorMessage());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);

            if (result.isSuccess()) {
                mSetupErrorContainer.setVisibility(View.GONE);
                mSetupErrorView.setText("");

                // Signed in successfully, show authenticated UI.
                GoogleSignInAccount acct = result.getSignInAccount();
                if (acct != null) {
                    FetchAccessToken worker = new FetchAccessToken(acct);
                    worker.execute();
                } else {
                    mSetupErrorContainer.setVisibility(View.VISIBLE);
                    mSetupErrorView.setText(R.string.google_signed_in_no_acct);
                }
            } else {
                mSetupErrorContainer.setVisibility(View.VISIBLE);
                mSetupErrorView.setText(R.string.google_sign_failed);
            }
        }
    }

    //Asynchronous task to fetch a token from the GoogleAuth service.
    private class FetchAccessToken extends AsyncTask<Void, Void, String> {

        private final GoogleSignInAccount mSignInAccount;

        private FetchAccessToken(@NonNull final GoogleSignInAccount googleSigninAccount) {
            mSignInAccount = googleSigninAccount;
        }

        @Override
        protected String doInBackground(Void... voids) {
            Account account = mSignInAccount.getAccount();
            String accessToken = null;
            if (account != null) {
                try {
                    accessToken = GoogleAuthUtil.getToken(SetupActivity.this.getApplicationContext(), account, "oauth2:openid");
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (UserRecoverableAuthException e) {
                    accessToken = null;
                } catch (GoogleAuthException e) {
                    e.printStackTrace();
                }
            }

            return accessToken;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            mGoogleUserId = mSignInAccount.getId();
            mGoogleToken = result;

            startBBMEnterpriseService();
        }
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
