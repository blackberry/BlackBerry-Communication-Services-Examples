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

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bbm.example.simplechat.utils.AuthProvider;
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
import com.bbm.sdk.bbmds.outbound.SetupRetry;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.BBMEnterpriseState;
import com.bbm.sdk.service.ProtocolMessage;
import com.bbm.sdk.service.ProtocolMessageConsumer;
import com.bbm.sdk.support.identity.UserIdentityMapper;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.ui.widgets.UserIdPrompter;
import com.bbm.sdk.support.util.AuthIdentityHelper;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.util.SetupHelper;
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
    private Observer mRegistrationIdObserver;
    private Observer mAppUserObserver;
    private Observer mBbmSetupObserver;

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

    private SetupHelper.EndpointDeregisteredListener mEndpointDeregisteredListener =
            new SetupHelper.EndpointDeregisteredListener() {
        @Override
        public void onEndpointDeregistered() {
            //Handle any required work (ex sign-out) from the auth service and wipe BBME
            AuthIdentityHelper.handleEndpointDeregistered(getApplicationContext());

            SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                private BBMEnterpriseState prevState;
                @Override
                public boolean run() {
                    BBMEnterpriseState state = BBMEnterprise.getInstance().getState().get();
                    if (prevState != null && prevState != BBMEnterpriseState.STARTED && state == BBMEnterpriseState.STARTED) {
                        AuthProvider.initAuthProvider(getApplicationContext());
                        return true;
                    }
                    prevState = state;
                    return false;
                }
            });
        }
    };


    //Simple view holder to display a chat
    private class ChatViewHolder extends RecyclerView.ViewHolder {

        TextView title;
        Chat chat;

        ChatViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.chat_title);

            //when the chat is clicked open the chat in a new activity
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                    intent.putExtra("chat-id", chat.chatId);
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

        BBMEnterprise.getInstance().initialize(MainActivity.this);
        //Create an observer to start BBM Enterprise SDK.
        Observer bbmeStateObserver = new Observer() {
            @Override
            public void changed() {
                //Only start the SDK if its stopped
                if (BBMEnterprise.getInstance().getState().get() == BBMEnterpriseState.STOPPED) {
                    // Initialize BBMEnterprise SDK then start it
                    BBMEnterprise.getInstance().start();
                }
            }
        };
        BBMEnterprise.getInstance().getState().addObserver(bbmeStateObserver);
        //Trigger our observer to run once
        bbmeStateObserver.changed();

        //Init the auth provider (get authentication token, start protected manager, sync users)
        AuthProvider.initAuthProvider(getApplicationContext());
        AuthIdentityHelper.setActivity(this);

        //Listen to the setup events
        final ObservableValue<GlobalSetupState> globalSetupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState();
        mBbmSetupObserver = new Observer() {
            @Override
            public void changed() {
                final ObservableValue<GlobalSetupState> globalSetupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState();

                if (globalSetupState.get().exists != Existence.YES) {
                    return;
                }

                GlobalSetupState currentState = globalSetupState.get();

                switch (currentState.state) {
                    case NotRequested:
                        SetupHelper.registerDevice("Simple Chat", "Simple Chat example");
                        break;
                    case DeviceSwitchRequired:
                        //Ask the BBM Enterprise SDK to move the users profile to this device
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

        //Add setup observer to the globalSetupStateObservable
        globalSetupState.addObserver(mBbmSetupObserver);

        //Call changed to trigger our observer to run immediately
        mBbmSetupObserver.changed();
        SetupHelper.listenForAndHandleDeregistered(mEndpointDeregisteredListener);

        setContentView(R.layout.main);

        final TextView myRegIdTextView = (TextView) findViewById(R.id.my_registration_id);
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

        //Observe the local AppUser from the UserManager and display their user name
        mAppUserObserver = new Observer() {
            @Override
            public void changed() {
                AppUser appUser = UserManager.getInstance().getLocalAppUser().get();
                if (appUser.getExists() == Existence.YES) {
                    ((TextView)findViewById(R.id.my_user_id)).setText(getString(R.string.my_user_name, appUser.getName()));
                }
            }
        };
        UserManager.getInstance().getLocalAppUser().addObserver(mAppUserObserver);
        mAppUserObserver.changed();

        //Get the chat list and keep a hard reference to it
        mChatList = BBMEnterprise.getInstance().getBbmdsProtocol().getChatList();
        //Add our incremental list observer to the chat list
        mChatList.addIncrementalListObserver(mChatListObserver);

        //Set the adapter in the recyclerview
        final RecyclerView chatsRecyclerView = (RecyclerView) findViewById(R.id.chats_list);
        chatsRecyclerView.setAdapter(mAdapter);
        chatsRecyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
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
            UserIdPrompter prompter = new UserIdPrompter();
            //Prompt the user to enter a user id and chat subject
            prompter.setTitle(getString(R.string.start_chat))
                    .setSecondaryInputLabel(getString(R.string.subject))
                    .setSecondaryInputHint(getString(R.string.subject_hint))
                    .setSecondaryInputRequired(getString(R.string.subject_required))
                    .show(MainActivity.this, new UserIdPrompter.SelectedUserIdCallback() {
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
                                String subject = TextUtils.isEmpty(secondaryInput) ? userId : secondaryInput;
                                startChat(mapResult.regId, subject);
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

    private void startChat(final long regId, String subject) {
        //Create a cookie to track the chat creation
        final String cookie = UUID.randomUUID().toString();

        //Create the invitee using the registration id
        ChatStart.Invitees invitee = new ChatStart.Invitees();
        invitee.regId(regId);

        //Ask the BBM Enterprise SDK to start a new chat with the invitee and subject provided.
        ChatStart chatStart = new ChatStart(cookie, Lists.newArrayList(invitee));
        chatStart.subject(subject);
        BBMEnterprise.getInstance().getBbmdsProtocol().send(chatStart);

        //Add a ProtocolMessageConsumer to track the creation of the chat.
        BBMEnterprise.getInstance().getBbmdsProtocolConnector().addMessageConsumer(new ProtocolMessageConsumer() {
            @Override
            public void onMessage(ProtocolMessage message) {
                final JSONObject json = message.getData();
                Logger.d("onMessage: " + message);
                //If the cookie in the incoming message matches the cookie we provided
                //we know this message is the response to our chatStart request.
                if (cookie.equals(json.optString("cookie", ""))) {
                    //this is for us, stop listening
                    BBMEnterprise.getInstance().getBbmdsProtocolConnector().removeMessageConsumer(this);

                    String chatId = null;
                    if ("chatStartFailed".equals(message.getType())) {
                        ChatStartFailed chatStartFailed = new ChatStartFailed().setAttributes(message.getJSON());
                        //If the message type is chatStartFailed the BBM Enterprise SDK was unable to create the chat
                        if (chatStartFailed.reason == ChatStartFailed.Reason.AlreadyExists) {
                            //If the reason is AlreadyExists we can open the existing chat.
                            chatId = chatStartFailed.chatId;
                        } else {
                            //If the reason wasn't AlreadyExists then display an error toast to the user
                            Logger.i("Failed to create chat with " + regId);
                            Toast.makeText(MainActivity.this,
                                    "Failed to create chat for reason " + chatStartFailed.reason.toString(),
                                    Toast.LENGTH_LONG)
                                    .show();
                        }
                    } else if ("listAdd".equals(message.getType())) {
                        //The chat was created successfully
                        try {
                            final JSONArray elementsArray = json.getJSONArray("elements");
                            Chat chat = new Chat().setAttributes((JSONObject) elementsArray.get(0));
                            chatId = chat.chatId;
                        } catch (final JSONException e) {
                            Logger.e(e, "Failed to process start chat message " + message);
                        }
                    }
                    if (chatId != null) {
                        //Start our chat activity
                        Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                        intent.putExtra("chat-id", chatId);
                        startActivity(intent);
                    }
                }
            }

            @Override
            public void resync() {
            }
        });
    }
}
