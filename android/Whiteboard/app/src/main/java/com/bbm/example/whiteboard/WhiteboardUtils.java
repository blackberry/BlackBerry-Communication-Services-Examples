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

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.Toast;

import com.bbm.sdk.BBMEConfig;
import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.ChatMessageSend;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.reactive.ObserveConnector;
import com.bbm.sdk.support.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

public class WhiteboardUtils {
    /**
     * Used to pass the chat ID from where this activity was invoked.
     */
    public static final String EXTRA_CHAT_ID = "chat-id";

    public static final String CHAT_MESSAGE_TAG_WHITEBOARD = "Whiteboard";
    public static final String CHAT_MESSAGE_TAG_PICTURE = "Picture";

    /**
     * "Clear" is a reserved tag, don't use it!
     */
    public static final String CHAT_MESSAGE_TAG_CLEAR = "ClearScreen";


    //keys used in the JSON object for the chat message data
    public static final String CHAT_MESSAGE_DATA_KEY_DOODLE_PNG_BYTES = "DoodlePngBytes";
    public static final String CHAT_MESSAGE_DATA_KEY_DOODLE_WIDTH = "DoodleWidth";
    public static final String CHAT_MESSAGE_DATA_KEY_DOODLE_HEIGHT = "DoodleHeight";

    public static final String CHAT_MESSAGE_DATA_KEY_SCALE_TO_WIDTH = "ScaleToWidth";
    public static final String CHAT_MESSAGE_DATA_KEY_SCALE_TO_HEIGHT = "ScaleToHeight";
    public static final String CHAT_MESSAGE_DATA_KEY_DOODLE_AVAILABLE_WIDTH = "DoodleAvailableWidth";
    public static final String CHAT_MESSAGE_DATA_KEY_DOODLE_AVAILABLE_HEIGHT = "DoodleAvailableHeight";

    public static final String CHAT_MESSAGE_DATA_KEY_DOODLE_TOP = "DoodleTop";
    public static final String CHAT_MESSAGE_DATA_KEY_DOODLE_LEFT = "DoodleLeft";

    public static final String CHAT_MESSAGE_DATA_KEY_BACKGROUND_COLOR = "BackgroundColor";

    /**
     * Prefix whiteboard chat subjects with this to know what type of chat.
     * This would not normally be necessary, but is helpful if this app is used in an environment
     * where multiple apps share the same user/chats to make it easier to know when chats can
     * be opened with this app.
     */
    public static final String WHITEBOARD_CHAT_SUBJECT_PREFIX = "WB:";

    private static final ObserveConnector sObserveConnector = new ObserveConnector();

    public static void openChat(final Context context, final String chatId) {
        //get the chat, use observer in case it doesn't exist yet, and
        //use connector to keep hard reference to observer until observer is done
        final ObservableValue<Chat> chat = BBMEnterprise.getInstance().getBbmdsProtocol().getChat(chatId);
        sObserveConnector.connect(chat, new Observer() {
            @Override
            public void changed() {
                if (chat.get().exists == Existence.YES) {
                    Intent intent = new Intent(context, com.bbm.example.whiteboard.WhiteboardActivity.class);
                    intent.putExtra(EXTRA_CHAT_ID, chatId);

                    context.startActivity(intent);

                    chat.removeObserver(this);
                    sObserveConnector.remove(this);
                }
            }
        }, true);
    }

    public static void sendDoodle(final WhiteboardView.DoodleEvent event, final WhiteboardView WhiteboardView, final String chatId, final boolean trimAllSides) {
        final int viewWidth = WhiteboardView.getWidth();
        final int viewHeight = WhiteboardView.getHeight();
        final int halfStrokeWidth = WhiteboardView.getStrokeWidth() / 2;
        final Bitmap origBmp = WhiteboardView.getDoodle();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void[] params) {

                if (origBmp != null) {
                    Logger.d("Trimming origBmp W="+origBmp.getWidth()+" H="+origBmp.getHeight());

                    Bitmap bmp;
                    if (trimAllSides) {
                        //The stroke thickness is more than 1 pixel, so we should grab a bit more than the edges that the users finger touched
                        //otherwise 1/2 the thickness will be cut on the square edges
                        int left = (int)event.leftMostX - halfStrokeWidth;
                        int right =  (int)event.rightMostX + halfStrokeWidth;
                        int top = (int)event.highestY - halfStrokeWidth;
                        int bottom =  (int)event.lowestY + halfStrokeWidth;
                        //can't go outside
                        if (left < 0) {
                            left = 0;
                        }
                        if (right >= origBmp.getWidth()) {
                            right = origBmp.getWidth() - 1;
                        }
                        if (top < 0) {
                            top = 0;
                        }
                        if (bottom >= origBmp.getHeight()) {
                            bottom = origBmp.getHeight() - 1;
                        }
                        bmp = Bitmap.createBitmap(origBmp, left, top, right - left, bottom - top);
                    } else {
                        //cut off the right whitespace part of the doodle (5 pix padding only), leave whitespace on left so user can add padding if they want. Use full height
                        int width = Math.min((int) event.rightMostX + 5, origBmp.getWidth());
                        bmp = Bitmap.createBitmap(origBmp, 0, 0, width, origBmp.getHeight());
                    }

                    Logger.d("doodle origBmp: W=" + origBmp.getWidth() + " H=" + origBmp.getHeight() + " BC=" + origBmp.getByteCount()
                            +" modBmp:  W=" + bmp.getWidth() + " H=" + bmp.getHeight() + " BC=" + bmp.getByteCount()
                    );

                    JSONObject jsonObject = new JSONObject();
                    try {
                        //the image data is base 64 encoded into a string and put into the JSON for the chat message data object
                        //Another option would be to use the Chat Message thumb but that would require writing the data to
                        //file and passing the url to the file as the thumb value.
                        //since that would have the overhead of writing to the file system and since most should be very small
                        //we just send it in the data object

                        if (addEncodedBitmap(bmp, jsonObject)) {
                            //for whiteboard let it know where to position
                            jsonObject.put(CHAT_MESSAGE_DATA_KEY_DOODLE_LEFT, (int) event.leftMostX);
                            jsonObject.put(CHAT_MESSAGE_DATA_KEY_DOODLE_TOP, (int) event.highestY);

                            jsonObject.put(CHAT_MESSAGE_DATA_KEY_DOODLE_AVAILABLE_WIDTH, viewWidth);
                            jsonObject.put(CHAT_MESSAGE_DATA_KEY_DOODLE_AVAILABLE_HEIGHT, viewHeight);

                            ChatMessageSend messageSend = new ChatMessageSend(chatId, WhiteboardUtils.CHAT_MESSAGE_TAG_WHITEBOARD);
                            //Attach our custom data to the chat message
                            messageSend.data(jsonObject);
                            BBMEnterprise.getInstance().getBbmdsProtocol().send(messageSend);
                        }
                    } catch (JSONException e) {
                        Logger.e(e);
                    }
                }
                return null;
            }
        }.execute();
    }

    public static void sendPicture(final String chatId, final WhiteboardView whiteboardView, final Bitmap originalBmp) {
        final int viewWidth = whiteboardView.getWidth();
        final int viewHeight = whiteboardView.getHeight();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Bitmap bmp = originalBmp;
                //scale image up or down to fill either width or height without cutting the other dimension
                if (bmp.getWidth() != viewWidth || bmp.getHeight() != viewHeight) {
                    int w;
                    int h;
                    //need to scale, pick which is best fit
                    float widthDiff = (float)bmp.getWidth() / (float)viewWidth;
                    float heightDiff = (float)bmp.getHeight() / (float)viewHeight;
                    if (widthDiff > heightDiff) {
                        w = viewWidth;
                        h = (int) (viewWidth * ((float) bmp.getHeight() / (float) bmp.getWidth()));
                    } else {
                        h = viewHeight;
                        w = (int) (viewHeight * ((float) bmp.getWidth() / (float) bmp.getHeight()));
                    }

                    Logger.d("scaling from w=" + bmp.getWidth() + " h=" + bmp.getHeight() + " to w=" + w + " h=" + h + " BC=" + bmp.getByteCount());
                    bmp = Bitmap.createScaledBitmap(bmp, w, h, false);
                }

                JSONObject jsonObject = new JSONObject();
                try {
                    if (addEncodedBitmap(bmp, jsonObject)) {

                        //for whiteboard let it know where to position
                        jsonObject.put(CHAT_MESSAGE_DATA_KEY_DOODLE_LEFT, 0);
                        jsonObject.put(CHAT_MESSAGE_DATA_KEY_DOODLE_TOP, 0);

                        jsonObject.put(CHAT_MESSAGE_DATA_KEY_DOODLE_AVAILABLE_WIDTH, viewWidth);
                        jsonObject.put(CHAT_MESSAGE_DATA_KEY_DOODLE_AVAILABLE_HEIGHT, viewHeight);

                        ChatMessageSend messageSend = new ChatMessageSend(chatId, WhiteboardUtils.CHAT_MESSAGE_TAG_PICTURE);
                        messageSend.data(jsonObject);
                        BBMEnterprise.getInstance().getBbmdsProtocol().send(messageSend);
                    }
                } catch (JSONException e) {
                    Logger.e(e);
                }
                return null;
            }
        }.execute();
    }

    private static boolean addEncodedBitmap(Bitmap bmp, JSONObject jsonObject) throws JSONException {
        //remember the size before any shrinking
        int startWidth = bmp.getWidth();
        int startHeight = bmp.getHeight();

        //base 64 encode the compressed image bytes to put in json
        int imgQuality = 100;

        ByteArrayOutputStream baos;
        byte[] dataBytes = null;
        String dataEnc = null;

        final int maxTries = 20;
        //the data must be under 70KB, otherwise it will fail, leave some space for other attributes in it
        final int maxSize = 68 * 1024;
        int shrinkCount = 0;
        boolean tooBig = true;
        do {
            if (shrinkCount == 1) {
                Logger.user("Image too large, shrinking to send...");
            }

            baos = new ByteArrayOutputStream();
            Bitmap.CompressFormat format;
            if (shrinkCount == 0) {
                format = Bitmap.CompressFormat.PNG;
            } else {
                format = Bitmap.CompressFormat.JPEG;
            }
            bmp.compress(format, imgQuality, baos);

            //before doing the base 64 first check the compressed bytes to avoid wasted effort creating larger base 64 copy
            if (baos.size() < maxSize) {
                dataBytes = baos.toByteArray();
                dataEnc = Base64.encodeToString(dataBytes, Base64.DEFAULT);
                tooBig = dataEnc.length() > (68 * 1024);

                Logger.d("tooBig="+tooBig+" shrinkCount=" + shrinkCount + " bmp: W=" + bmp.getWidth() + " H=" + bmp.getHeight() + " BC=" + bmp.getByteCount()
                        + " len=" + dataBytes.length
                        + " encoded len=" + dataEnc.length() + " Q=" + imgQuality + " format=" + format);
            } else {
                Logger.d("too big, skipped base 64. shrinkCount=" + shrinkCount + " bmp: W=" + bmp.getWidth() + " H=" + bmp.getHeight() + " BC=" + bmp.getByteCount()
                        + " len=" + baos.size() + " Q=" + imgQuality + " format=" + format);
            }

            ++shrinkCount;
            if (tooBig) {
                //if (shrinkQuality) {
                if (shrinkCount % 4 == 0) {
                    //tried decreasing quality enough, shrink resolution
                    int newWidth = (int) (bmp.getWidth() * 0.75);
                    int newHeight = (int) (bmp.getHeight() * 0.75);
                    //reset quality higher for this size
                    imgQuality = 80;
                    bmp = Bitmap.createScaledBitmap(bmp, newWidth, newHeight, false);
                } else {
                    imgQuality -= 20;
                }
            }
        } while (tooBig && shrinkCount < maxTries);

        if (tooBig) {
            Logger.user("Image could not be compressed enough to send!");
            return false;
        }

        jsonObject.put(CHAT_MESSAGE_DATA_KEY_DOODLE_PNG_BYTES, dataEnc);
        //send the width to allow UI to determine size quicker than loading image
        jsonObject.put(CHAT_MESSAGE_DATA_KEY_DOODLE_WIDTH, bmp.getWidth());
        jsonObject.put(CHAT_MESSAGE_DATA_KEY_DOODLE_HEIGHT, bmp.getHeight());
        if (bmp.getWidth() != startWidth || bmp.getHeight() != startHeight) {
            jsonObject.put(CHAT_MESSAGE_DATA_KEY_SCALE_TO_WIDTH, startWidth);
            jsonObject.put(CHAT_MESSAGE_DATA_KEY_SCALE_TO_HEIGHT, startHeight);
        }

        Logger.d("Done png.len="+dataBytes.length+" dataEnc.len="+dataEnc.length());
        return true;
    }

    public static void sendClearBackground(final String chatId, int color) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(CHAT_MESSAGE_DATA_KEY_BACKGROUND_COLOR, color);

            ChatMessageSend messageSend = new ChatMessageSend(chatId, WhiteboardUtils.CHAT_MESSAGE_TAG_CLEAR);
            messageSend.data(jsonObject);
            Logger.d("sendMessage: sending " + messageSend);
            BBMEnterprise.getInstance().getBbmdsProtocol().send(messageSend);
        } catch (JSONException e) {
            Logger.e(e);
        }
    }

    public static Bitmap createBitmap(ChatMessage chatMessage) {
        Bitmap bmp = null;
        String dataEnc = chatMessage.data.optString(WhiteboardUtils.CHAT_MESSAGE_DATA_KEY_DOODLE_PNG_BYTES);
        if (dataEnc != null) {
            byte[] dataBytes = Base64.decode(dataEnc, Base64.DEFAULT);
            bmp = BitmapFactory.decodeByteArray(dataBytes, 0, dataBytes.length);
            if (bmp == null) {
                Logger.d("failed to decode bytes len=" + dataBytes.length);
            } else {
                int displayWidth = chatMessage.data.optInt(CHAT_MESSAGE_DATA_KEY_SCALE_TO_WIDTH, -1);
                int displayHeight = chatMessage.data.optInt(CHAT_MESSAGE_DATA_KEY_SCALE_TO_HEIGHT, -1);
                Logger.d("dataEnc.len="+dataEnc.length()+" ByteCount="+bmp.getByteCount()+" W="+bmp.getWidth()+" H="+bmp.getHeight()+" DH="+displayHeight+" DW="+displayWidth);
                if (displayWidth > 0 && displayHeight > 0
                        && displayWidth != bmp.getWidth() && displayHeight != bmp.getHeight()) {
                    //scale the bitmap to this size. This is used when image needed to be shrunk,
                    //or would support a feature to allow user to pinch to scale image before sending
                    bmp = Bitmap.createScaledBitmap(bmp, displayWidth, displayHeight, false);
                }
            }
        } else {
            Logger.d("missing " + WhiteboardUtils.CHAT_MESSAGE_DATA_KEY_DOODLE_PNG_BYTES + " in chatMessage data " + chatMessage.data);
        }
        return bmp;
    }

    /**
     * Helper to create string for chat message.
     * Main purpose is to truncate the data if too long, useful since most messages have a few KB of data which floods logs.
     * Also skips some other values that aren't as useful for this app
     *
     * @param cm
     * @return
     */
    public static String toString(ChatMessage cm) {
        if (cm != null) {
            final StringBuilder sb = new StringBuilder("ChatMessage(part):{");
            sb.append("chatId=").append('"').append(cm.chatId).append('"');
            sb.append(", messageId=").append(cm.messageId);
            sb.append(", tag=").append('"').append(cm.tag).append('"');

            if (BBMEConfig.LOG_PRIVATE_DATA) sb.append(", content=").append('"').append(truncate(cm.content)).append('"');
            sb.append(", data=").append(truncate(String.valueOf(cm.data)));
            sb.append(", recall=").append(cm.recall);
            sb.append(", senderUri=").append('"').append(cm.senderUri).append('"');
            sb.append(", state=").append(cm.state);
            sb.append(", timestamp=").append(cm.timestamp);
            return sb.append("}").toString();
        } else {
            return "NULL";
        }
    }

    private static String truncate(String s) {
        if (s != null && s.length() > 300) {
            return s.substring(0, 300) + "("+s.length()+")...";
        }
        return s;
    }

    private static Toast mToast;

    private static Handler sMainHandler = new Handler(Looper.getMainLooper());

    public static void showDefaultToast(final Context context, final String text) {
        showDefaultToast(context, text, Toast.LENGTH_LONG);
    }

    public static void showDefaultToast(final Context context, final String text, final int duration) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            //Already UI thread, safe to call directly
            showDefaultToastOnUI(context, text, duration);
        } else {
            //can't do this from background thread, need to post to UI thread
            sMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    showDefaultToastOnUI(context, text, duration);
                }
            });
        }
    }

    private static void showDefaultToastOnUI(final Context context, final String text, final int duration) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(context, text, duration);
        mToast.show();
    }
}
