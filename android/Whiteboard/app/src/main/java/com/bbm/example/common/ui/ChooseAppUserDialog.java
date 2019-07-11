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

package com.bbm.example.common.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import com.bbm.example.whiteboard.R;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.AppUserFilter;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.util.Logger;

import java.util.Collection;

/**
 * This is used to make it simpler to start chat with user(s) without needing to always manually type their IDs.
 * This is just for a sample app and wouldn't be used for a real app with proper user management.
 */
public class ChooseAppUserDialog {

    private Activity mActivity;
    private AppUserSelectedCallback mCallback;
    private AppUserRecyclerViewAdapter adapter;



    public static void promptToSelect(final Activity activity, String title, String buttonText, String extraEditTextHint, AppUserSelectedCallback callback) {
        new ChooseAppUserDialog(activity, title, buttonText, extraEditTextHint, callback, false, null, null);
    }

    public static void promptToSelect(final Activity activity, String title, String buttonText, String extraEditTextHint, AppUserSelectedCallback callback, ObservableList<AppUser> usersToPickFrom) {
        new ChooseAppUserDialog(activity, title, buttonText, extraEditTextHint, callback, false, null, usersToPickFrom);
    }

    /**
     *
     * @param activity
     * @param buttonText
     * @param extraEditTextHint
     * @param callback
     * @param defaultSelectedFilter
     */
    public static void promptToSelectMultiple(final Activity activity, String title, String buttonText, String extraEditTextHint, AppUserSelectedCallback callback,
                                              AppUserFilter defaultSelectedFilter) {
        new ChooseAppUserDialog(activity, title, buttonText, extraEditTextHint, callback, true, defaultSelectedFilter, null);
    }

    /**
     *
     * @param activity
     * @param title the optional title to display
     * @param buttonText optional button text.  The button is only shown if allowMultiSelect is true
     * @param extraEditTextHint optional hint text for an optional edit text view to show at the top of the contact list.
     *                          If this is null then the EditText will be hidden.
     *                          This is ignored if allowMultiSelect is false since it will be hidden.
     *                          When in multiselect mode any text entered here will be passed as the 2nd param
     *                          into {@Link AppUsersSelectedCallback#selected(Collection<AppUser>, String) }
     * @param callback
     * @param allowMultiSelect
     * @param defaultSelectedFilter the optional filter to specify which users if any should be selected by default.
     *                              This will be ignored if allowMultiSelect is false.
     */
    public ChooseAppUserDialog(final Activity activity, String title, String buttonText, String extraEditTextHint, final AppUserSelectedCallback callback,
                               final boolean allowMultiSelect, AppUserFilter defaultSelectedFilter, ObservableList<AppUser> usersToPickFrom) {
        mActivity = activity;
        mCallback = callback;

        LayoutInflater inflater = mActivity.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_contacts_with_input, null);

        final EditText editText = (EditText)dialogView.findViewById(R.id.edit_text);
        if (!allowMultiSelect || extraEditTextHint == null) {
            editText.setVisibility(View.GONE);
        } else {
            editText.setHint(extraEditTextHint);
        }



        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity, R.style.BBMAppTheme_dialog)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null);
        if (!TextUtils.isEmpty(title)) {
            builder.setTitle(title);
        }
        if (allowMultiSelect) {
            builder.setPositiveButton(buttonText, null);
        }
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {

                Window window = dialog.getWindow();
                WindowManager.LayoutParams layoutParams = window.getAttributes();
                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
                window.setAttributes(layoutParams);

                if (allowMultiSelect) {
                    //set button listener this way so the dialog stays open until we have valid regid and dismiss it
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Collection<AppUser> selected = adapter.getSelectedUsers();
                            Logger.d("onClick: selected=" + selected);
                            if (selected != null && selected.size() > 0) {
                                if (callback != null) {
                                    callback.selected(selected, editText.getText().toString());
                                }
                                dialog.dismiss();
                            } else {
                                Logger.user("You must select user(s) to chat with");
                            }
                        }
                    });
                }
            }
        });

        if (usersToPickFrom == null) {
            usersToPickFrom = UserManager.getInstance().getUsers();
        }
        RecyclerView contactsView = (RecyclerView)dialogView.findViewById(R.id.list_contacts);
        if (allowMultiSelect) {
            adapter = new AppUserRecyclerViewAdapter(usersToPickFrom, callback, defaultSelectedFilter);
        } else {
            //need to wrap the callback so we can close the dialog when user taps one
            AppUserSelectedCallback wrapperCallback = new AppUserSelectedCallback() {
                @Override
                public void selected(Collection<AppUser> contacts, String extraText) {
                    //this one is only called in multiselect mode
                }

                @Override
                public void selected(AppUser contact) {
                    dialog.dismiss();
                    if (callback != null) {
                        callback.selected(contact);
                    }
                }
            };
            adapter = new AppUserRecyclerViewAdapter(usersToPickFrom, wrapperCallback);
        }

        contactsView.setLayoutManager(new LinearLayoutManager(contactsView.getContext()));
        contactsView.setAdapter(adapter);

        dialog.show();
    }
}
