/*
 * Copyright (c) 2017 BlackBerry Limited. All Rights Reserved.
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

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.text.Html;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.media.BBMECall;
import com.bbm.sdk.media.BBMEIncomingCallObserver;
import com.bbm.sdk.media.BBMEMediaManager;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.util.PermissionsUtil;

/**
 * The IncomingCallObserver will be notified when a new call has arrived.
 */

public class IncomingCallObserver implements BBMEIncomingCallObserver {

    private static final String ACTION_ACCEPT_CALL = "softphone.notification.action.acceptCall";
    private static final String ACTION_DECLINE_CALL = "softphone.notification.action.rejectCall";

    private static final String SOFT_PHONE_NOTIFICATION_CHANNEL = "softphone.example.notification.channel";
    private static final int INCOMING_CALL_NOTIFICATION_ID = 8986;

    private Context mContext;

    /**
     * Create a new IncomingCallObserver.
     * The IncomingCallObserver will create a notification channel to be used when notifying the
     * user of incoming calls.
     * @param context Android application context
     */
    public IncomingCallObserver(Context context) {
        mContext = context;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            // Create a new notification channel to be used for incoming calls.
            NotificationChannel channel = new NotificationChannel(SOFT_PHONE_NOTIFICATION_CHANNEL, mContext.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);

            // Use the default ringtone.
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            channel.setSound(ringtoneUri, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());

            NotificationManager mgr = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mgr.createNotificationChannel(channel);
        }

        // Register a BroadcastReceiver to be called when the actions in the incoming call
        // notification are used.
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_ACCEPT_CALL);
        filter.addAction(ACTION_DECLINE_CALL);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null) {
                    BBMEMediaManager mediaManager = BBMEnterprise.getInstance().getMediaManager();
                    int callId = mediaManager.getActiveCallId().get();
                    if (callId != -1) {
                        switch (intent.getAction()) {
                            case ACTION_ACCEPT_CALL:
                                Logger.i(ACTION_ACCEPT_CALL);
                                answerCall(context, callId);
                                break;
                            case ACTION_DECLINE_CALL:
                                Logger.i(ACTION_DECLINE_CALL);
                                declineCall(callId);
                                break;
                        }
                    }
                    stopNotification(context);
                }
            }
        }, filter);
    }

    /**
     * Answer the incoming call. If the application does not yet have the RECORD_AUDIO
     * permission the IncomingCallActivity will be launched to prompt the user to provide
     * permission before continuing.
     * @param context Android application context
     * @param callId the id of the incoming call
     */
    private void answerCall(Context context, int callId) {
        BBMEMediaManager mediaManager = BBMEnterprise.getInstance().getMediaManager();

        // Answer the incoming call
        boolean hasMicrophonePermission =
                PermissionsUtil.checkSelfPermission(context,
                        Manifest.permission.RECORD_AUDIO);
        if (hasMicrophonePermission) {
            // If the user has already provided the RECORD_AUDIO permission
            // continue with answering the call
            if (mediaManager.answerCall(callId) ==
                    BBMEMediaManager.Error.NO_ERROR) {
                //Start our call activity
                Intent inCallIntent =
                        new Intent(context, InCallActivity.class);
                inCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                inCallIntent.putExtra(InCallActivity.EXTRA_CALL_ID, callId);
                context.startActivity(inCallIntent);
            }
        } else {
            // Start the incoming call activity and prompt the user to
            // provide the required permission to continue
            Intent incomingCallIntent =
                    new Intent(context, IncomingCallActivity.class);
            incomingCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            incomingCallIntent.putExtra(
                    IncomingCallActivity.INCOMING_CALL_ID, callId);
            incomingCallIntent.putExtra(
                    IncomingCallActivity.ACCEPT_AND_REQUEST_PERMISSIONS,
                    true);
            context.startActivity(incomingCallIntent);
        }
    }

    /**
     * Decline (end) the call.
     * @param callId the id of the incoming call
     */
    private void declineCall(int callId) {
        // Decline the call
        BBMEnterprise.getInstance().getMediaManager().endCall(callId);
    }

    /**
     * Triggered when an incoming call arrives.
     * The call is presented to the user through a high priority notification.
     * If the device is locked or the screen is turned off the IncomingCallActivity is displayed
     * to the user.
     * @param callId the id of the incoming call.
     */
    @Override
    public void onIncomingCall(final int callId) {
        // If we have audio permissions we can accept the call immediately
        // Accepting the call allows early negotiation of the media stream for a faster connection.
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            BBMEnterprise.getInstance().getMediaManager().acceptCall(callId);
        }

        CallUtils.addObserverToCall(callId);

        NotificationManager mgr = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create an intent to launch the IncomingCallActivity
        Intent incomingCallIntent = new Intent(mContext, IncomingCallActivity.class);
        incomingCallIntent.putExtra(IncomingCallActivity.INCOMING_CALL_ID, callId);
        incomingCallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 1, incomingCallIntent, 0);

        // Create the notification builder. On Oreo and newer use the notification channel.
        final NotificationCompat.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
           builder = new NotificationCompat.Builder(mContext, SOFT_PHONE_NOTIFICATION_CHANNEL);
        } else {
            //noinspection deprecation
            builder = new NotificationCompat.Builder(mContext);
        }
        builder.setOngoing(true);
        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        builder.setContentIntent(pendingIntent);
        builder.setFullScreenIntent(pendingIntent, true);

        // Get the call
        BBMECall call = BBMEnterprise.getInstance().getMediaManager().getCall(callId).get();
        // Find the AppUser participant in the call
        AppUser user = UserManager.getInstance().getUser(call.getRegId()).get();
        String name = user.getExists() == Existence.YES && !TextUtils.isEmpty(user.getName()) ?
                user.getName() : Long.toString(call.getRegId());

        // Set the icon and text for the notification
        builder.setSmallIcon(R.drawable.call_start);
        builder.setContentTitle(mContext.getString(R.string.incoming_call));
        builder.setContentText(name);

        // Create an intent to call our decline action.
        final Intent declineCallIntent = new Intent(ACTION_DECLINE_CALL);
        final PendingIntent piDeclineCall = PendingIntent.getBroadcast(mContext, 1,
                declineCallIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        // Create an intent to call our answer action.
        final Intent answerCallIntent = new Intent(ACTION_ACCEPT_CALL);
        final PendingIntent piAnswerCall = PendingIntent.getBroadcast(mContext, 2,
                answerCallIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        // Format the text for the Accept and Decline buttons to display with green and red colors.
        CharSequence greenAccept = Html.fromHtml("<font color=\"green\">" + mContext.getString(R.string.incoming_call_accept) + "</font>");
        CharSequence redDecline = Html.fromHtml("<font color=\"red\">" + mContext.getString(R.string.incoming_call_decline) + "</font>");

        // Add the reject and answer buttons to the notification
        builder.addAction(new NotificationCompat.Action(0, redDecline, piDeclineCall));
        builder.addAction(new NotificationCompat.Action(0, greenAccept, piAnswerCall));

        Notification notification = builder.build();
        // Set the insistent flag so the ringtone continues playing until we cancel the notification
        notification.flags |= Notification.FLAG_INSISTENT;
        mgr.notify(INCOMING_CALL_NOTIFICATION_ID, notification);
    }

    /**
     * Stop any active incoming call notification.
     * @param context Android application context
     */
    static void stopNotification(Context context) {
        NotificationManager mgr = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        mgr.cancel(INCOMING_CALL_NOTIFICATION_ID);
    }
}
