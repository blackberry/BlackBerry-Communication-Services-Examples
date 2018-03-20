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
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.bbm.sdk.support.util.Logger;

import java.util.HashSet;
import java.util.Set;


/**
 * Simple prompt for registration id to start a call.
 */
public class UserPrompter {

    public interface SelectedRegIdCallback {
        void selectedRegId(long regId, String metadata);
    }

    public static void promptForRegId(Activity activity, SelectedRegIdCallback callback) {
        new UserPrompter(activity, callback);
    }

    private UserPrompter(final Activity activity, final SelectedRegIdCallback callback) {

        LayoutInflater inflater = activity.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_message_with_input, null);

        ((TextView)dialogView.findViewById(R.id.dialog_info_first_line)).setText(R.string.reg_id_to_call);

        final AutoCompleteTextView regIdInputEditText = (AutoCompleteTextView) dialogView.findViewById(R.id.dialog_input_regId);
        regIdInputEditText.setThreshold(1);

        Set<String> savedRegIds = activity.getPreferences(Context.MODE_PRIVATE).getStringSet("regIds", new HashSet<String>());
        if (savedRegIds.size() > 0) {
            String[] savedRegIdArray = new String[savedRegIds.size()];
            savedRegIds.toArray(savedRegIdArray);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, savedRegIdArray);
            regIdInputEditText.setAdapter(adapter);
        }

        regIdInputEditText.setInputType(InputType.TYPE_CLASS_NUMBER);

        final EditText mEditText = (EditText)dialogView.findViewById(R.id.dialog_input_metadata);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.start_data_connection)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.start, null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {

                Window window = dialog.getWindow();
                WindowManager.LayoutParams layoutParams = window.getAttributes();
                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
                window.setAttributes(layoutParams);

                regIdInputEditText.showDropDown();

                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            long regId = Long.parseLong(regIdInputEditText.getText().toString().trim());
                            SharedPreferences prefs = activity.getPreferences(Context.MODE_PRIVATE);
                            Set<String> savedRegIds = prefs.getStringSet("regIds", new HashSet<String>());
                            savedRegIds.add(regIdInputEditText.getText().toString().trim());
                            prefs.edit().putStringSet("regIds", savedRegIds).apply();
                            callback.selectedRegId(regId, mEditText.getText().toString());
                            dialog.dismiss();
                        } catch (NumberFormatException e) {
                            Logger.e(e);
                            Toast.makeText(activity, R.string.invalid_regid, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });

        dialog.show();
    }
}
