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

package com.bbm.example.whiteboard;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.GlobalSetupState;
import com.bbm.sdk.bbmds.inbound.ChatStartFailed;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.SetupRetry;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.support.identity.UserIdentityMapper;
import com.bbm.sdk.support.ui.widgets.UserIdPrompter;
import com.bbm.sdk.support.util.AuthIdentityHelper;
import com.bbm.sdk.support.util.ChatStartHelper;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.util.SetupHelper;

/**
 * For this simple chat app the main activity is just a chats list with a few
 * menu options in the toolbar to create new chats.
 * There is not any navigation in this app, just a list of chats, and ability to see a chat.
 * This chats list is much simpler than BBM, it just displays the chat subject/participants and count of messages in it.
 * For the BBM implementation see the Rich Chat app.
 */
public class MainActivity extends AppCompatActivity {

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
                    SetupHelper.registerDevice("Whiteboard", "Whiteboard example");
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Listen to the setup events
        final ObservableValue<GlobalSetupState> globalSetupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState();

        //Add setup observer to the globalSetupStateObservable
        globalSetupState.addObserver(mBbmSetupObserver);
        //Call changed to trigger our observer to run immediately
        mBbmSetupObserver.changed();

        AuthIdentityHelper.setActivity(this);

        //create the list of chats
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.chats_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new com.bbm.example.whiteboard.ChatRecyclerViewAdapter(this));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Logger.d("onActivityResult: requestCode="+requestCode+" resultCode="+resultCode+" data="+data);

        if (requestCode == AuthIdentityHelper.TOKEN_REQUEST_CODE) {
            //Handle an authentication result
            AuthIdentityHelper.handleAuthenticationResult(this, requestCode, resultCode, data);
        }
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

        Logger.d("onOptionsItemSelected: item="+item+" id="+id);
        if (id == R.id.start_whiteboard_chat) {
            //display the dialog that has a list of users with avatars and allows multiselect to create a chat or MPC
            UserIdPrompter prompter = new UserIdPrompter();
            prompter.setTitle(getString(R.string.menu_whiteboard_chat));
            prompter.setSecondaryInputLabel(getString(R.string.chat_subject));
            prompter.show(this, new UserIdPrompter.SelectedUserIdCallback() {
                @Override
                public void selectedUserId(String userId, String secondaryInput) {
                    SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                        @Override
                        public boolean run() {
                            //Lookup the Spark registration id for the provided user id
                            UserIdentityMapper.IdentityMapResult mapResult =
                                    UserIdentityMapper.getInstance().getRegIdForUid(userId, true).get();
                            if (mapResult.existence == Existence.MAYBE) {
                                return false;
                            }
                            if (mapResult.existence == Existence.YES) {
                                //Add a prefix to know its whiteboards
                                createNewChat(new long[]{mapResult.regId}, WhiteboardUtils.WHITEBOARD_CHAT_SUBJECT_PREFIX + secondaryInput);
                            } else {
                                Toast.makeText(MainActivity.this, getString(R.string.user_id_not_found, userId), Toast.LENGTH_LONG).show();
                            }

                            return false;
                        }
                    });
                }
            });

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void createNewChat(final long[] regIds, String chatSubject) {
        ChatStartHelper.startNewChat(regIds, chatSubject, new ChatStartHelper.ChatStartedCallback() {
            @Override
            public void onChatStarted(@NonNull String chatId) {
                WhiteboardUtils.openChat(MainActivity.this, chatId);
            }

            @Override
            public void onChatStartFailed(ChatStartFailed.Reason reason) {
                Logger.user("Failed to create chat due to "+reason+" with regIds="+regIds);
            }
        });
    }
}
