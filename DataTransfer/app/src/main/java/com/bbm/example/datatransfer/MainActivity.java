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

package com.bbm.example.datatransfer;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.GlobalLocalUri;
import com.bbm.sdk.bbmds.GlobalSetupState;
import com.bbm.sdk.bbmds.RegIdUserCriteria;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.inbound.UserKeysImportFailure;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.SetupRetry;
import com.bbm.sdk.media.BBMEDataChannelCreatedObserver;
import com.bbm.sdk.media.BBMEDataChannelReceiver;
import com.bbm.sdk.media.BBMEDataChannelSender;
import com.bbm.sdk.media.BBMEDataConnection;
import com.bbm.sdk.media.BBMEDataConnectionCreatedObserver;
import com.bbm.sdk.media.BBMEMediaManager;
import com.bbm.sdk.reactive.ObservableMonitor;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.InboundMessageObservable;
import com.bbm.sdk.support.protect.ProtectedManager;
import com.bbm.sdk.support.protect.SimplePasswordProvider;
import com.bbm.sdk.support.util.AuthIdentityHelper;
import com.bbm.sdk.support.util.BbmUtils;
import com.bbm.sdk.support.util.IOUtils;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.util.PermissionsUtil;
import com.bbm.sdk.support.util.SetupHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity implements BBMEDataChannelCreatedObserver {

    public static final String ACTIVE_CONNECTION_ID = "ACTIVE_CONNECTION_ID";
    private static final int REQUEST_CODE_PICK_FILE = 10001;

    private int mDataConnectionId = -1;
    private TextView mConnectionStatusTextView;
    private TextView mConnectionErrorTextView;
    private Button mStartStopConnectionButton;
    private FloatingActionButton sendFileFab;
    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private DataChannelsAdapter mDataChannelsAdapter;

    private ArrayList<DataChannelsAdapter.TransferItem> mTransfers = new ArrayList<>();

    //Monitor to observe the data connection
    ObservableMonitor mConnectionMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            BBMEDataConnection connection = BBMEnterprise.getInstance().getMediaManager().getDataConnection(mDataConnectionId).get();
            switch (connection.getState()) {
                case CONNECTED:
                    mConnectionStatusTextView.setText(getString(R.string.connection_status, "Connected"));
                    //Show the send button when connected
                    sendFileFab.setVisibility(View.VISIBLE);
                    break;
                case CONNECTING:
                    mConnectionStatusTextView.setText(getString(R.string.connection_status, "Connecting"));
                    //Clear the list when we are starting a new connection
                    mTransfers.clear();
                    mDataChannelsAdapter.notifyDataSetChanged();
                    break;
                case OFFERING:
                    mConnectionStatusTextView.setText(getString(R.string.connection_status, "Offering"));
                    break;
                case DISCONNECTED:
                    mConnectionStatusTextView.setText(getString(R.string.connection_status, "Disconnected"));
                    mConnectionStatusTextView.setBackgroundColor(getResources().getColor(R.color.disconnected_color));
                    mStartStopConnectionButton.setText(R.string.create_connection);
                    //Hide the send button when disconnected
                    sendFileFab.setVisibility(View.GONE);
                    connection.setDataChannelCreatedObserver(null);
                    //Display a connection error if one exists
                    if (connection.getFailureReason() != BBMEDataConnection.FailReason.NO_FAILURE) {
                        mConnectionErrorTextView.setVisibility(View.VISIBLE);
                        mConnectionErrorTextView.setText(getString(R.string.connection_error, connection.getFailureReason().toString()));
                    }
                    break;
            }

            if (connection.getState() != BBMEDataConnection.ConnectionState.DISCONNECTED) {
                mConnectionErrorTextView.setVisibility(View.GONE);
                mConnectionStatusTextView.setBackgroundColor(getResources().getColor(R.color.connected_color));
                mStartStopConnectionButton.setText(R.string.disconnect);
                connection.setDataChannelCreatedObserver(MainActivity.this);
            }
        }
    };

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
                    SetupHelper.registerDevice("DataTransfer", "DataTransfer example");
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

    private SetupHelper.GoAwayListener mGoAwayListener = new SetupHelper.GoAwayListener() {
        @Override
        public void onGoAway() {
            //Handle any required work (ex sign-out) from the auth service
            AuthIdentityHelper.handleGoAway(MainActivity.this);
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        mDataConnectionId = getIntent().getIntExtra(ACTIVE_CONNECTION_ID, -1);

        //Listen to the setup events
        final ObservableValue<GlobalSetupState> globalSetupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState();

        //Add setup observer to the globalSetupStateObservable
        globalSetupState.addObserver(mBbmSetupObserver);
        //Call changed to trigger our observer to run immediately
        mBbmSetupObserver.changed();

        SetupHelper.listenForAndHandleGoAway(mGoAwayListener);

        //set this activity in case it is needed to prompt the user to sign in with their Google account
        AuthIdentityHelper.setActivity(this);
        SimplePasswordProvider.getInstance().setActivity(this);

        mConnectionStatusTextView = (TextView)findViewById(R.id.connection_status);
        mConnectionErrorTextView = (TextView)findViewById(R.id.connection_error);

        mStartStopConnectionButton = (Button)findViewById(R.id.start_or_end_connection_button);
        mStartStopConnectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BBMEDataConnection connection = BBMEnterprise.getInstance().getMediaManager().getDataConnection(mDataConnectionId).get();
                if (connection.getState() == BBMEDataConnection.ConnectionState.DISCONNECTED) {
                    //If there is no connection then start one
                    UserPrompter.promptForRegId(MainActivity.this, new UserPrompter.SelectedRegIdCallback() {
                        @Override
                        public void selectedRegId(long regId, String metaData) {
                            startDataConnection(regId, metaData);
                        }
                    });
                } else {
                    //End the active data connection
                    BBMEnterprise.getInstance().getMediaManager().endDataConnection(mDataConnectionId);
                }
            }
        });

        //Set the click listener for the send file button
        sendFileFab = (FloatingActionButton)findViewById(R.id.send_file_fab);
        sendFileFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Prompt for file
                //Start an intent to pick a file to send
                Intent pickFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
                pickFileIntent.setType("*/*");
                startActivityForResult(pickFileIntent, REQUEST_CODE_PICK_FILE);
            }
        });

        //Setup an adapter to display a list of sending/received files
        mDataChannelsAdapter = new DataChannelsAdapter(this, mTransfers);
        RecyclerView recyclerView = (RecyclerView)findViewById(R.id.transfer_list);
        recyclerView.setAdapter(mDataChannelsAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    public void onDataChannelCreated(String s, BBMEDataChannelReceiver receiver) {
        //Add a new "transfer item" to the data channels adapter
        DataChannelsAdapter.TransferItem transferItem = new DataChannelsAdapter.TransferItem(null, receiver);
        mTransfers.add(transferItem);
        mDataChannelsAdapter.notifyDataSetChanged();
        writeFile(receiver, transferItem);
    }

    private void writeFile(final BBMEDataChannelReceiver receiver, final DataChannelsAdapter.TransferItem transferItem) {
        //Create an asynctask to copy the data from the channel into a file
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                //Create a folder to write the incoming data to
                String outputDirectoryPath = Environment.getExternalStorageDirectory() + "/data_transfer_example";
                File outputDir = new File(outputDirectoryPath);
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }

                //Create a file using the "name" provided in the data channel
                final File outputFile = new File(outputDirectoryPath + "/" + receiver.getName());
                OutputStream fileOutputStream = null;

                //Get the input stream from the data channel
                InputStream dataChannelInputStream = receiver.getInputStream();

                try {
                    fileOutputStream = new FileOutputStream(outputFile);
                    //Read data from the input stream and copy it to the file
                    IOUtils.copy(dataChannelInputStream, fileOutputStream);
                } catch (IOException e) {
                    //If an error occurred then mark the transfer as failed and display an error to the user
                    Logger.e(e);
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            transferItem.mError = true;
                            mDataChannelsAdapter.notifyDataSetChanged();
                            Toast.makeText(MainActivity.this, "IOException when writing received file " + receiver.getName(), Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    IOUtils.safeClose(dataChannelInputStream);
                    IOUtils.safeClose(fileOutputStream);
                }

                if (!transferItem.mError) {
                    //Set the file uri in the transfer item
                    transferItem.mFileUri = FileProvider.getUriForFile(
                            MainActivity.this,
                            getApplicationContext().getPackageName() + ".fileprovider",
                            outputFile
                    );
                }
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

        if (requestCode == PermissionsUtil.PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_TO_ATTACH_FILES) {
            //the check from onResume will handle action if it is granted, so we only take action here when denied
            if (!PermissionsUtil.isGranted(grantResults, 0)) {
                PermissionsUtil.displayCanNotContinue(this, android.Manifest.permission.RECORD_AUDIO,
                        R.string.rationale_write_external_storage_denied,
                        PermissionsUtil.PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_TO_ATTACH_FILES, null);
            }
        }
    }

    /**
     * Starts a data connection with the provided regid.
     * If the {@link User} matching the regId has {@link com.bbm.sdk.bbmds.User.KeyState#Import} or does not exist keys will be imported via the ProtectedManager.
     */
    public void startDataConnection(final long regId, final String metaData) {

        //Create a monitor to look for the user matching the regId and import keys if necessary.
        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            ObservableValue<Integer> keyOperationState;
            InboundMessageObservable<UserKeysImportFailure> mKeyImportFailureObservable;
            boolean hasImportedKeys = false;
            @Override
            public boolean run() {
                //First check to see if the BBM Enterprise SDK knows about the user we want to create a connection with
                User user = BBMEnterprise.getInstance().getBbmdsProtocol().getUser(new RegIdUserCriteria().regId(regId)).get();

                if (user.getExists() == Existence.MAYBE) {
                    return false;
                }

                //If the BBM Enterprise SDK doesn't know about the user, or the user is in keyState Import we need to ensure we add the keys
                if (user.getExists() == Existence.NO || user.keyState == User.KeyState.Import) {
                    if (!hasImportedKeys) {
                        hasImportedKeys = true;
                        //Tell the protected manager to find and import keys for this regId
                        keyOperationState = ProtectedManager.getInstance().importUserKeys(regId);
                        mKeyImportFailureObservable = new InboundMessageObservable<>(
                                new UserKeysImportFailure(),
                                BBMEnterprise.getInstance().getBbmdsProtocolConnector()
                        );
                    }

                    //If the keys could not be found, or the key import failed inform the user we cannot start a connection
                    if (keyOperationState.get() == ProtectedManager.NO_KEYS || mKeyImportFailureObservable.get().regIds.contains(Long.toString(regId))) {
                        Toast.makeText(MainActivity.this, getString(R.string.error_creating_connection, "KEY_IMPORT_ERROR"), Toast.LENGTH_LONG).show();
                        return true;
                    }

                    return false;
                } else {
                    //The user exists and has keyState=Synced, we can start the connection
                    //Ask the media service to start a connection with the specified regId and include an observer to be notified of the result
                    BBMEnterprise.getInstance().getMediaManager().startDataConnection(regId, metaData, new BBMEDataConnectionCreatedObserver() {
                        @Override
                        public void onConnectionCreationSuccess(int connectionId) {
                            mDataConnectionId = connectionId;
                            mConnectionMonitor.activate();
                        }

                        @Override
                        public void onConnectionCreationFailure(@NonNull BBMEMediaManager.Error error) {
                            //The connection wasn't created. Display an error
                            Toast.makeText(MainActivity.this, getString(R.string.error_creating_connection, error.name()), Toast.LENGTH_LONG).show();
                        }
                    });
                }

                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Logger.d("onActivityResult: requestCode="+requestCode+" resultCode="+resultCode+" data="+data);

        if (requestCode == AuthIdentityHelper.TOKEN_REQUEST_CODE) {
            //Handle an authentication result
            AuthIdentityHelper.handleAuthenticationResult(this, requestCode, resultCode, data);
        } else if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK) {

            final Uri fileUri = data.getData();

            if (fileUri == null || !ContentResolver.SCHEME_CONTENT.equals(fileUri.getScheme())) {
                //Only handling content URI's for simplicity
                return;
            }

            //Send the file via the connection
            sendFile(fileUri);
        }
    }

    private void sendFile(final Uri fileUri) {
        //Check if the connection is still active before proceeding
        BBMEDataConnection connection = BBMEnterprise.getInstance().getMediaManager().getDataConnection(mDataConnectionId).get();
        if (connection.getState() != BBMEDataConnection.ConnectionState.CONNECTED) {
            Toast.makeText(this, "The connection is no longer active", Toast.LENGTH_LONG).show();
        }

        //Get the file name and size
        Cursor fileCursor = getContentResolver().query(fileUri, null, null, null, null);
        int sizeIndex = fileCursor.getColumnIndex(OpenableColumns.SIZE);
        int nameIndex = fileCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        fileCursor.moveToFirst();
        final long fileSize = fileCursor.getLong(sizeIndex);
        final String fileName = fileCursor.getString(nameIndex);
        IOUtils.safeClose(fileCursor);

        //Create a new data channel of type "File" specifying the name and size.
        final BBMEDataChannelSender sender = connection.createDataChannel(
                fileName,
                fileSize,
                BBMEDataConnection.ChannelType.FILE
        );

        //Add the channel to the list and notify the adapter
        final DataChannelsAdapter.TransferItem transferItem = new DataChannelsAdapter.TransferItem(fileUri, sender);
        mTransfers.add(transferItem);
        mDataChannelsAdapter.notifyDataSetChanged();

        //Start a background task to write the file to the data channel
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                OutputStream dataChannelOutputStream = sender.getOutputStream();
                InputStream fileInputStream = null;
                try {
                    fileInputStream = getContentResolver().openInputStream(fileUri);
                    //Copy the data from the file to the data channel output stream
                    IOUtils.copy(fileInputStream, dataChannelOutputStream);
                } catch (IOException e) {
                    //If an error occurred then mark the transfer as failed and display an error to the user
                    Logger.e(e);
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            transferItem.mError = true;
                            mDataChannelsAdapter.notifyDataSetChanged();
                            Toast.makeText(MainActivity.this, "IOException occurred while sending " + fileName, Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    IOUtils.safeClose(fileInputStream);
                    IOUtils.safeClose(dataChannelOutputStream);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        myRegistrationIdObserver.activate();
        mConnectionMonitor.activate();
        PermissionsUtil.checkOrPromptSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                PermissionsUtil.PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_TO_ATTACH_FILES, R.string.rationale_write_external_storage, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        myRegistrationIdObserver.dispose();
        mConnectionMonitor.dispose();
    }
}
