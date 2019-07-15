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


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.media.BBMEDataConnection;
import com.bbm.sdk.reactive.SingleshotMonitor;

public class IncomingDataConnectionActivity extends Activity {

    private int mDataConnectionId = 0;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Set window flags to allow our activity to appear above the device lock screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_incoming_data_connection);

        mDataConnectionId = getIntent().getIntExtra(IncomingConnectionObserver.INCOMING_CONNECTION_ID, -1);

        //Monitor the connection and end this activity if it disconnects before the user has accepted
        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {
                BBMEDataConnection connection = BBMEnterprise.getInstance().getMediaManager().getDataConnection(mDataConnectionId).get();
                switch (connection.getState()) {
                    case DISCONNECTED:
                        //End this activity if the connection terminates
                        finish();
                        return true;
                }
                return false;
            }
        });

        findViewById(R.id.accept_connection_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Open the data connection, this will transition the connection to "connected"
                BBMEnterprise.getInstance().getMediaManager().openDataConnection(mDataConnectionId);

                //Launch main activity again and set the new data connection id
                Intent mainActivityIntent = new Intent(IncomingDataConnectionActivity.this, MainActivity.class);
                mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                mainActivityIntent.putExtra(MainActivity.ACTIVE_CONNECTION_ID, mDataConnectionId);
                startActivity(mainActivityIntent);
                finish();
            }
        });

        findViewById(R.id.decline_connection_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BBMEnterprise.getInstance().getMediaManager().endDataConnection(mDataConnectionId);
                finish();
            }
        });


        BBMEDataConnection connection = BBMEnterprise.getInstance().getMediaManager().getDataConnection(mDataConnectionId).get();
        ((TextView)findViewById(R.id.incoming_call_title)).setText(getString(R.string.incoming_data_connection_request, Long.toString(connection.getRegId())));
        //Display the meta data including in the connection
        ((TextView)findViewById(R.id.description)).setText(getString(R.string.incoming_data_connection_description, connection.getMetaData()));
    }
}
