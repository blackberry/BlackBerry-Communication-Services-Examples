/*
 * Copyright (c) 2018 BlackBerry.  All Rights Reserved.
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

package com.bbm.example.announcements;


import android.app.Activity;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.ChatMessageSend;

import java.util.Collections;

/**
 * Helper class for random useful things.
 */
public final class Helper {

    /**
     * Create and show an alert dialog to allow a user to edit a chatMessage. For our case an announcement.
     *
     * @param activity            The activity to be used to associate the alert dialog to
     * @param announcementMessage The ChatMessage to be edited
     */
    public static void showEditAnnouncementDialog(@NonNull final Activity activity, @NonNull final ChatMessage announcementMessage) {

        final View contents = LayoutInflater.from(activity).inflate(R.layout.dialog_edit_announcement, null);
        final EditText editText = contents.findViewById(R.id.edit_text);

        // Get the announcement message, we want to update the latest edit, so need to find the
        // appropriate message
        editText.setText(getMessageContent(announcementMessage));

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AppTheme_dialog)
                .setTitle(R.string.edit_announcement)
                .setView(contents)
                .setCancelable(true)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        if (TextUtils.equals(editText.getText(), announcementMessage.content)) {
                            // Do nothing, no change in message
                            return;
                        }

                        // 1. Create the new message to be sent
                        final ChatMessageSend editedMessage = new ChatMessageSend(announcementMessage.chatId, ChatMessageSend.Tag.Text)
                                .content(editText.getText().toString().trim());

                        // 2. Set the reference of the editedMessage to the announcement message identifier and set the reference tag to "edit"
                        editedMessage.ref(Collections.singletonList(new ChatMessageSend.Ref(announcementMessage.messageId, ChatMessageSend.Ref.Tag.Edit)));

                        // 3. Send the message
                        BBMEnterprise.getInstance().getBbmdsProtocol().send(editedMessage);
                    }
                })
                .setNegativeButton(android.R.string.no, null);

        final AppCompatDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(ChatActivity.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        dialog.show();
        resizeAlertDialog(dialog);
    }

    /**
     * Resize a dialog to the width of the display screen.
     *
     * @param dialog Dialog to be modified
     */
    public static void resizeAlertDialog(@NonNull final AppCompatDialog dialog) {
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams dialogParams = dialog.getWindow().getAttributes();
            if (dialogParams != null) {
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(dialog.getWindow().getAttributes());
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

                dialog.getWindow().setAttributes(lp);
            }
        }
    }

    /**
     * Get the content for a chat message. If the message is referenced by another message
     * with reference tag "Edit" then return the content in the referenced message.
     * This method must be used in a monitor to guarantee the correct content is returned.
     *
     * @param message a chat message
     * @return the content which should be displayed for the chat message
     */
    public static String getMessageContent(ChatMessage message) {

        for (ChatMessage.RefBy ref : message.refBy) {
            if (ref.tag == ChatMessage.RefBy.Tag.Edit && ref.newestRef != 0) {
                //We only care about the most recent edit reference to this message
                ChatMessage.ChatMessageKey key = new ChatMessage.ChatMessageKey(message.chatId, ref.newestRef);

                ChatMessage refedByMessage = BBMEnterprise.getInstance().getBbmdsProtocol().getChatMessage(key).get();
                if (refedByMessage.exists == Existence.YES) {
                    return refedByMessage.content;
                }
            }
        }
        return message.content;
    }
}
