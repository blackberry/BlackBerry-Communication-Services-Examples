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

package com.bbm.example.softphone;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.bbm.sdk.support.util.Logger;


/**
 * Simple prompt for registration id to start a call.
 */
public class CallUserPrompter {

    public interface SelectedRegIdCallback {
        void selectedRegId(long regId);
    }

    public static void promptToStartCall(Activity activity, SelectedRegIdCallback callback) {
        new CallUserPrompter(activity, callback);
    }

    private CallUserPrompter(final Activity activity, final SelectedRegIdCallback callback) {

        LayoutInflater inflater = activity.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_message_with_input, null);

        ((TextView)dialogView.findViewById(R.id.dialog_info_first_line)).setText(R.string.reg_id_to_call);

        final AutoCompleteTextView regIdInputEditText = (AutoCompleteTextView) dialogView.findViewById(R.id.dialog_input_value);
        regIdInputEditText.setThreshold(1);

        regIdInputEditText.setInputType(InputType.TYPE_CLASS_NUMBER);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.start_call)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.start_call, null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {

                Window window = dialog.getWindow();
                WindowManager.LayoutParams layoutParams = window.getAttributes();
                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
                window.setAttributes(layoutParams);

                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            long regId = Long.parseLong(regIdInputEditText.getText().toString().trim());
                            callback.selectedRegId(regId);
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
