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

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.bbm.example.common.reactive.SimpleChatMessageList;
import com.bbm.example.common.util.Utils;
import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.ChatInvite;
import com.bbm.sdk.bbmds.outbound.RetryServerRequests;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.support.identity.UserIdentityMapper;
import com.bbm.sdk.support.reactive.ObserveConnector;
import com.bbm.sdk.support.ui.widgets.UserIdPrompter;
import com.bbm.sdk.support.util.BbmUtils;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.util.PermissionsUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public class WhiteboardActivity extends AppCompatActivity {
    public static final int REQUEST_CODE_ATTACH_PICTURE = 5;

    private static final int PENCIL_STROKE_THIN = 6;
    private static final int PENCIL_STROKE_MEDIUM = 10;
    private static final int PENCIL_STROKE_THICK = 20;

    private static final int[] COLORS = new int[]{
            Color.BLACK,
            Color.YELLOW,
            Color.WHITE,
            Color.RED,
            Color.GREEN,
            Color.BLUE
    };

    private String mChatId = null;

    private WhiteboardView mWhiteboardView;

    /**
     * Used to keep a strong reference to any observers owned by this activity.
     * The observables only keep weak references, so this must keep the strong references.
     * Once this activity is destroyed and garbage collected the observables are automatically removed.
     */
    private ObserveConnector mObserveConnector = new ObserveConnector();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whiteboard);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        final ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        mChatId = getIntent().getStringExtra(WhiteboardUtils.EXTRA_CHAT_ID);

        //create an observable list of messages that we can use to auto update the UI
        final SimpleChatMessageList chatMessageList = new SimpleChatMessageList(mChatId);

        //if the subject is set display that in the title
        final ObservableValue<Chat> chat = BBMEnterprise.getInstance().getBbmdsProtocol().getChat(mChatId);
        if (!TextUtils.isEmpty(chat.get().subject)) {
            actionBar.setTitle(chat.get().subject);
        } else {
            //no subject, so get the list of participants, wrapped in a observable value and listen for when it changes to update title
            final ObservableValue<String> formattedParticipants = Utils.getFormattedParticipantList(chat.get().chatId);
            mObserveConnector.connect(formattedParticipants, new Observer() {
                @Override
                public void changed() {
                    actionBar.setTitle(formattedParticipants.get());
                }
            }, true);
        }


        mWhiteboardView = (WhiteboardView)findViewById(R.id.doodleView);

        mWhiteboardView.setChat(chat, chatMessageList);

        mWhiteboardView.setDoodleListener(new WhiteboardView.DoodleListener() {
            @Override
            public void onNewDoodle(WhiteboardView.DoodleEvent event) {

                Logger.d("onNewDoodle: "+event);
                if (event.type == WhiteboardView.DoodleType.DOODLE) {
                    WhiteboardUtils.sendDoodle(event, mWhiteboardView, mChatId, true);
                    mWhiteboardView.reset();
                }
            }
        });

        ImageButton sendButton = (ImageButton)findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //tell the view to send the event, will be handled as doodle event
                mWhiteboardView.sendDoodleEvent();
            }
        });

        //button to clear the whiteboard to white.  This is just another message that overrides previous ones
        //We could allow user to browse previous chats which would show what was there before the clear
        ImageButton clearBackgroundButton = (ImageButton)findViewById(R.id.clear_background);
        clearBackgroundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WhiteboardUtils.sendClearBackground(mChatId, Color.WHITE);
            }
        });

        ImageButton addPictureButton = (ImageButton)findViewById(R.id.add_picture);
        addPictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addPicture();
            }
        });

        //toggle the width of the line drawn by the users finger.
        //We could allow user to set specific thickness but to keep it simple just toggle through 3 values
        final ImageButton strokeButton = (ImageButton)findViewById(R.id.stroke_thickness);
        strokeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int old = mWhiteboardView.getStrokeWidth();
                if (old < PENCIL_STROKE_MEDIUM) { //was thin
                    mWhiteboardView.setStrokeWidth(PENCIL_STROKE_THICK);
                    strokeButton.setImageResource(R.drawable.stroke_thick);
                } else if (old > PENCIL_STROKE_MEDIUM) { //was thick
                    mWhiteboardView.setStrokeWidth(PENCIL_STROKE_MEDIUM);
                    strokeButton.setImageResource(R.drawable.stroke_medium);

                } else { //was medium
                    mWhiteboardView.setStrokeWidth(PENCIL_STROKE_THIN);
                    strokeButton.setImageResource(R.drawable.stroke_thin);

                }
            }
        });
        mWhiteboardView.setStrokeWidth(PENCIL_STROKE_MEDIUM);

        //The line color. We could allow user to set specific color but to keep it simple just toggle through a few values
        final ImageButton colorButton = (ImageButton)findViewById(R.id.stroke_color);
        colorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int color = mWhiteboardView.getStrokeColor();
                int colIndex = 0;
                for (int i=0;i<COLORS.length;++i) {
                    if (COLORS[i] == color) {
                        colIndex = i;
                        break;
                    }
                }

                ++colIndex;
                if (colIndex >= COLORS.length) {
                    colIndex = 0;
                }

                mWhiteboardView.setStrokeColor(COLORS[colIndex]);
                colorButton.setBackgroundColor(COLORS[colIndex]);
            }
        });

        //default to black
        mWhiteboardView.setStrokeColor(COLORS[0]);
        colorButton.setBackgroundColor(COLORS[0]);
    }

    private void addPicture() {
        attachFromGallery();
    }

    private boolean attachFromGallery() {
        if (PermissionsUtil.checkOrPromptSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                PermissionsUtil.PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_TO_ATTACH_PICTURES, R.string.rationale_write_external_storage)) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");


            startActivityForResult(intent, REQUEST_CODE_ATTACH_PICTURE);
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        Logger.d("onActivityResult: " + resultCode + " req: " + requestCode + " data: " + data);

        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }


        switch (requestCode) {
            case REQUEST_CODE_ATTACH_PICTURE:
                if (data == null) {
                    return;
                }

                Uri imageUri = data.getData();
                if (imageUri != null) {
                    try {
                        InputStream in = getContentResolver().openInputStream(imageUri);
                        Bitmap bmp = BitmapFactory.decodeStream(in);
                        in.close();
                        addPicture(bmp);
                    } catch (IOException ioe) {
                        Logger.e(ioe);
                    }
                }
                break;
        }
    }

    private void addPicture(Bitmap bmp) {
        //we could let user choose scale method and let them place it on the screen but keeping it simple
        //by just putting in top right corner with largest size that fits
        WhiteboardUtils.sendPicture(mChatId, mWhiteboardView, bmp);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //We don't do anything to mark messages as unread, but it would be nice to have a feature
        //that allows the user to open chat starting at oldest unread message and step through them
        //so they can see the doodles, pictures, and clears as they were received
        BbmUtils.markChatMessagesAsRead(mChatId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                //Allow BBM to retry any failed server messages
                BBMEnterprise.getInstance().getBbmdsProtocol().send(new RetryServerRequests());
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.whiteboard_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_invite_user:
                UserIdPrompter prompter = new UserIdPrompter();
                prompter.setTitle(getString(R.string.invite_to_whiteboard));
                prompter.show(this, new UserIdPrompter.SelectedUserIdCallback() {
                    @Override
                    public void selectedUserId(String userId, String secondaryInput) {
                        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                            @Override
                            public boolean run() {
                                UserIdentityMapper.IdentityMapResult result =
                                        UserIdentityMapper.getInstance().getRegIdForUid(userId, true).get();
                                if (result.existence == Existence.MAYBE) {
                                    return false;
                                }
                                if (result.existence == Existence.YES) {
                                    ChatInvite.Invitees invitee = new ChatInvite.Invitees();
                                    invitee.regId(result.regId);
                                    ChatInvite invite = new ChatInvite(mChatId, Collections.singletonList(invitee));
                                    BBMEnterprise.getInstance().getBbmdsProtocol().send(invite);
                                } else {
                                    Toast.makeText(WhiteboardActivity.this,
                                            getString(R.string.user_id_not_found, userId),
                                            Toast.LENGTH_LONG).show();
                                }
                                return true;
                            }
                        });
                    }
                });
        }

        return super.onOptionsItemSelected(item);
    }

}