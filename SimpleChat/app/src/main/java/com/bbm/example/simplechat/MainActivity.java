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

package com.bbm.example.simplechat;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.BbmdsProtocol;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.GlobalLocalUri;
import com.bbm.sdk.bbmds.GlobalSetupState;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.inbound.ChatStartFailed;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.lists.IncrementalListObserver;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.bbmds.outbound.ChatStart;
import com.bbm.sdk.bbmds.outbound.SetupDeviceSwitch;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.service.ProtocolMessage;
import com.bbm.sdk.service.ProtocolMessageConsumer;
import com.bbm.sdk.support.identity.auth.google.auth.GoogleAuthHelper;
import com.bbm.sdk.support.identity.user.firebase.FirebaseUserDbSync;
import com.bbm.sdk.support.util.FirebaseHelper;
import com.bbm.sdk.support.util.Logger;
import com.google.common.collect.Lists;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * For this simple chat app the main activity is a chats list with a
 * menu option in the toolbar to create a new chat.
 */
public class MainActivity extends AppCompatActivity {
    //The list of chats
    private ObservableList<Chat> mChatList;
    //Keep a hard reference to these observers to prevent them from being gc'd
    private Observer mDeviceSwitchObserver;
    private Observer mRegistrationIdObserver;

    //This observer will be used to notify the adapter when chats have been changed or added.
    private final IncrementalListObserver mChatListObserver = new IncrementalListObserver() {
        @Override
        public void onItemsInserted(int position, int itemCount) {
            mAdapter.notifyItemRangeInserted(position, itemCount);
        }

        @Override
        public void onItemsRemoved(int position, int itemCount) {
            mAdapter.notifyItemRangeRemoved(position, itemCount);
        }

        @Override
        public void onItemsChanged(int position, int itemCount) {
            mAdapter.notifyItemRangeChanged(position, itemCount);
        }

        @Override
        public void onDataSetChanged() {
            mAdapter.notifyDataSetChanged();
        }
    };

    //Simple view holder to display a chat
    private class ChatViewHolder extends RecyclerView.ViewHolder {

        TextView title;
        Chat chat;

        ChatViewHolder(View itemView) {
            super(itemView);
            title = (TextView)itemView.findViewById(R.id.chat_title);

            //when the chat is clicked open the chat in a new activity
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                    intent.putExtra("chat-id",  chat.chatId);
                    startActivity(intent);
                }
            });
        }
    }

    //Our chats recycler view adapter
    private final RecyclerView.Adapter<ChatViewHolder> mAdapter = new RecyclerView.Adapter<ChatViewHolder>() {
        @Override
        public ChatViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View chatView = LayoutInflater.from(MainActivity.this).inflate(R.layout.chat_item, parent, false);
            return new ChatViewHolder(chatView);
        }

        @Override
        public void onBindViewHolder(ChatViewHolder holder, int position) {
            if (!mChatList.isPending()) {
                holder.title.setText(mChatList.get(position).subject);
                holder.chat = mChatList.get(position);
            }
        }

        @Override
        public int getItemCount() {
            return mChatList.size();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize BBMEnterprise SDK then start it
        BBMEnterprise.getInstance().initialize(this);
        BBMEnterprise.getInstance().start();

        //Initialize our FirebaseHelper to sync the Protect chat and user keys
        FirebaseHelper.initUserDbSyncAndProtected();

        //prompt the user to sign in with their Google account, and pass that data to our user manager when ready
        GoogleAuthHelper.initGoogleSignIn(this, FirebaseUserDbSync.getInstance(), getString(R.string.default_web_client_id));

        //Listen to determine if a device switch is necessary.
        final ObservableValue<GlobalSetupState> globalSetupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState();
        mDeviceSwitchObserver = new Observer() {
            @Override
            public void changed() {
                if (globalSetupState.get().state == GlobalSetupState.State.DeviceSwitchRequired) {
                    //Ask the BBM Enterprise SDK to move the users profile to this device
                    BBMEnterprise.getInstance().getBbmdsProtocol().send(new SetupDeviceSwitch(""));
                }
            }
        };
        //Add deviceSwitchObserver to the globalSetupStateObservable
        globalSetupState.addObserver(mDeviceSwitchObserver);
        //Call changed to trigger our observer to run immediately
        mDeviceSwitchObserver.changed();

        setContentView(R.layout.main);

        final TextView myRegIdTextView = (TextView)findViewById(R.id.my_registration_id);
        //Observe the local user to get our registration id
        mRegistrationIdObserver = new Observer() {
            @Override
            public void changed() {
                BbmdsProtocol bbmdsProtocol = BBMEnterprise.getInstance().getBbmdsProtocol();
                //Get the uri of the local user first
                GlobalLocalUri localUserUri = bbmdsProtocol.getGlobalLocalUri().get();
                if (localUserUri.getExists() == Existence.YES) {
                    //Get the local user and add ourselves as an observer
                    ObservableValue<User> localUser = bbmdsProtocol.getUser(localUserUri.value);
                    localUser.addObserver(this);
                    if (localUser.get().getExists() == Existence.YES) {
                        myRegIdTextView.setText(getString(R.string.my_registration_id, localUser.get().regId));
                    }
                }
            }
        };
        BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalLocalUri().addObserver(mRegistrationIdObserver);
        mRegistrationIdObserver.changed();

        //Get the chat list and keep a hard reference to it
        mChatList = BBMEnterprise.getInstance().getBbmdsProtocol().getChatList();
        //Add our incremental list observer to the chat list
        mChatList.addIncrementalListObserver(mChatListObserver);

        //Set the adapter in the recyclerview
        final RecyclerView chatsRecyclerView = (RecyclerView)findViewById(R.id.chats_list);
        chatsRecyclerView.setAdapter(mAdapter);
        chatsRecyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
    }

    /**
     * The google sign in will send the results to this activity, this just passes to the helper to pass auth info to BBM SDK
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GoogleAuthHelper.RC_GOOGLE_SIGN_IN_ACTIVITY) {
            GoogleAuthHelper.handleOnActivityResult(this, FirebaseUserDbSync.getInstance(), requestCode, resultCode, data);
        }
    }

    /**
     * Create a simple menu with a menu item to create a new chat
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, R.string.new_chat);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            //Create a dialog with fields for entering a registration id and a subject
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this);
            dialogBuilder.setTitle(R.string.start_chat);
            View contents = LayoutInflater.from(MainActivity.this).inflate(R.layout.chat_start_dialog, null);
            dialogBuilder.setView(contents);

            final EditText regIdEdit = (EditText)contents.findViewById(R.id.reg_id);
            final EditText subjectEdit = (EditText)contents.findViewById(R.id.subject);
            dialogBuilder.setPositiveButton(R.string.start, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        final Long regId = Long.parseLong(regIdEdit.getText().toString());
                        String subject = subjectEdit.length() == 0 ? regIdEdit.getText().toString() : subjectEdit.getText().toString();
                        startChat(regId, subject);
                    } catch (NumberFormatException nfe) {
                        Toast.makeText(MainActivity.this, R.string.not_valid_regid, Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            });
            dialogBuilder.create().show();

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startChat(final long regId, String subject) {
        //Create a cookie to track the chat creation
        final String cookie = UUID.randomUUID().toString();

        //Create the invitee using the registration id
        ChatStart.Invitees invitee = new ChatStart.Invitees();
        invitee.regId(regId);

        //Ask the BBM Enterprise SDK to start a new chat with the invitee and subject provided.
        BBMEnterprise.getInstance().getBbmdsProtocol().send(new ChatStart(cookie, Lists.newArrayList(invitee), subject));

        //Add a ProtocolMessageConsumer to track the creation of the chat.
        BBMEnterprise.getInstance().getBbmdsProtocolConnector().addMessageConsumer(new ProtocolMessageConsumer() {
            @Override
            public void onMessage(ProtocolMessage message) {
                final JSONObject json = message.getData();
                Logger.d("onMessage: " + message);
                //If the cookie in the incoming message matches the cookie we provided
                //we know this message is the response to our chatStart request.
                if (cookie.equals(json.optString("cookie",""))) {
                    //this is for us, stop listening
                    BBMEnterprise.getInstance().getBbmdsProtocolConnector().removeMessageConsumer(this);


                    if ("chatStartFailed".equals(message.getType())) {
                        //If the message type is chatStartFailed the BBM Enterprise SDK was unable to create the chat
                        ChatStartFailed chatStartFailed = new ChatStartFailed().setAttributes(message.getJSON());
                        Logger.i("Failed to create chat with " + regId);
                        Toast.makeText(MainActivity.this, "Failed to create chat for reason " + chatStartFailed.reason.toString(), Toast.LENGTH_LONG).show();
                    } else if ("listAdd".equals(message.getType())) {
                        //The chat was created successfully
                        try {
                            final JSONArray elementsArray = json.getJSONArray("elements");
                            Chat chat = new Chat().setAttributes((JSONObject) elementsArray.get(0));
                            //Start our chat activity
                            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                            intent.putExtra("chat-id", chat.chatId);
                            startActivity(intent);
                        } catch (final JSONException e) {
                            Logger.e(e, "Failed to process start chat message " + message);
                        }
                    }
                }
            }

            @Override
            public void resync() {
            }
        });
    }
}
