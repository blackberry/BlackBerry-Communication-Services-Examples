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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.common.Equal;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.util.Logger;

import java.util.ArrayList;

public class WhiteboardView extends View implements Observer {

    private DoodleListener mDoodleListener;
    private Path mPath = new Path();
    private Paint mPaint = new Paint();

    //These are just used when the getDoodle is called
    private Canvas mDrawingCanvas;
    private Bitmap mDrawingBitmap;

    private float mLeftMostX;
    private float mRightMostX;
    private float mHighestY;
    private float mLowestY;
    private long mDoodleStart;

    private int mStrokeWidth = 8;

    private int mStrokeColor = Color.BLACK;

    private ObservableValue<Chat> mChat;

    /**
     * Optional list of chat messages.  To use this view just for drawing leave this null.
     * If set this will display the most recent doodles up to newest reset control message.
     */
    private ObservableList<ObservableValue<ChatMessage>> mChatMessageList;

    private ChatMessage mChatMessageListLastDisplayed;
    private Canvas mChatMessagesCanvas;
    private Bitmap mChatMessagesBitmap;
    /**
     * Just needed to know if the mChatMessagesBitmap needs to be created.
     * This is set when we don't get have a size, so it can be created when size is known.
     */
    private boolean mNeedToCreateCanvas;

    public WhiteboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WhiteboardView(Context context) {
        super(context);
        init();
    }

    private void init() {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokeWidth);
        mPaint.setColor(mStrokeColor);
    }

    private void moveData(final float x, final float y) {
        if (x < mLeftMostX) {
            mLeftMostX = x;
        }
        if (x > mRightMostX) {
            mRightMostX = x;
        }
        if (y < mHighestY) {
            mHighestY = y;
        }
        if (y > mLowestY) {
            mLowestY = y;
        }

        validate();
    }

    private void validate() {
        if (mLowestY > getHeight()) {
            mLowestY = getHeight();
        }

        if (mRightMostX > getWidth()) {
            mRightMostX = getWidth();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                mPath.lineTo(x, y);
                moveData(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_DOWN:
                mPath.moveTo(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                mPath.lineTo(x, y);
                moveData(x, y);
                invalidate();

                break;
        }

        return true;
    }


    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        Bitmap chatMessagesBitmap = mChatMessagesBitmap;
        if (chatMessagesBitmap != null) {
            canvas.drawBitmap(chatMessagesBitmap, 0, 0, null);
        }

        canvas.drawPath(mPath, mPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (mNeedToCreateCanvas && getWidth() > 0 && getHeight() > 0) {
            mNeedToCreateCanvas = false;
            changed();
        }
    }

    /**
     * Set optional list of chat messages to display doodles from.
     *
     * @param chatMessageList the chat messages, pass null to stop listening to current list
     */
    public void setChat(ObservableValue<Chat> chat, ObservableList<ObservableValue<ChatMessage>> chatMessageList) {
        mChat = chat;
        if (mChat != null) {
            mChat.addObserver(new Observer() {
                @Override
                public void changed() {
                    //reset last displayed
                    mChatMessageListLastDisplayed = null;
                }
            });
        }

        if (mChatMessageList != null) {
            //stop listenting to old one
            mChatMessageList.removeObserver(this);
        }
        mChatMessageList = chatMessageList;
        mChatMessageListLastDisplayed = null;
        mChatMessagesCanvas = null;
        mChatMessagesBitmap = null;
        if (mChatMessageList != null) {
            mChatMessageList.addObserver(this);
            int w = getWidth();
            int h = getHeight();
            if (w > 0 && h > 0) {
                mChatMessagesBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                mChatMessagesCanvas = new Canvas(mChatMessagesBitmap);
                //call changed to display right away
                changed();
            } else {
                mNeedToCreateCanvas = true;
                Logger.d("not ready to create canvas yet w="+w+" h="+h);
            }
        }
        invalidate();
    }


    @Override
    public void changed() {
        ObservableList<ObservableValue<ChatMessage>> chatMessageList = mChatMessageList;
        if (chatMessageList != null) {
            //build list to display
            ArrayList<ChatMessage> toDisplay = new ArrayList<>();
            int size = chatMessageList.size();
            boolean rememberLastDisplayed = true;
            int pendingChatMessages = 0;
            //start at last, go until find last displayed or control message
            for (int i=size - 1; i >= 0; --i) {
                ObservableValue<ChatMessage> observableChatMessage = chatMessageList.get(i);
                ChatMessage chatMessage = observableChatMessage.get();
                Logger.d("checking ["+i+"]: ID="+chatMessage.messageId+" tag="+chatMessage.tag+" exists="+chatMessage.exists);
                observableChatMessage.addObserver(this);
                if (chatMessage.exists == Existence.MAYBE) {
                    rememberLastDisplayed = false;
                    ++pendingChatMessages;
                    if (pendingChatMessages > 3) {
                        Logger.d("have pendingChatMessages="+pendingChatMessages+" will wait for them to load...");
                        //when the chat is first loaded all messages except the last one will be loading
                        //if we continue we would process all previous messages, causing them to all load which
                        //would display starting to draw recent messages, so stop after a reasonable amount to
                        //give the most recent ones a chance to load so we can look for the last reset
                        break;
                    }
                } else {
                    if (Equal.isEqual(chatMessage, mChatMessageListLastDisplayed)) {
                        //we already displayed this one, stop
                        break;
                    } else {
                        String tag = chatMessage.tag;
                        if (WhiteboardUtils.CHAT_MESSAGE_TAG_WHITEBOARD.equals(tag) || WhiteboardUtils.CHAT_MESSAGE_TAG_PICTURE.equals(tag)) {
                            toDisplay.add(chatMessage);
                        } else if (WhiteboardUtils.CHAT_MESSAGE_TAG_CLEAR.equals(tag)) {
                            toDisplay.add(chatMessage);
                            //last one
                            break;
                        }
                        //ignore others
                    }
                }
            }

            Logger.d("size="+size+" toDisplay.size()="+toDisplay.size()
                    +" mChatMessagesCanvas="+mChatMessagesCanvas+" mChatMessagesBitmap="+mChatMessagesBitmap
                    +" mChatMessageListLastDisplayed="
                    //just log useful stuff
                    +WhiteboardUtils.toString(mChatMessageListLastDisplayed));

            if (toDisplay.size() > 0) {
                int w = getWidth();
                int h = getHeight();
                if (mChatMessagesCanvas == null) {
                    if (w > 0 && h > 0) {
                        mChatMessagesBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        mChatMessagesCanvas = new Canvas(mChatMessagesBitmap);
                    } else {
                        Logger.d("not ready to create canvas yet w="+w+" h="+h);
                    }
                }
                Canvas canvas = mChatMessagesCanvas;

                if (canvas != null) {
                    for (int i = toDisplay.size() - 1; i >= 0; --i) {
                        ChatMessage chatMessage = toDisplay.get(i);
                        String tag = chatMessage.tag;
                        Logger.d("draw ["+i+"]: ID="+chatMessage.messageId+" tag="+tag+" ");
                        if (WhiteboardUtils.CHAT_MESSAGE_TAG_WHITEBOARD.equals(tag) || WhiteboardUtils.CHAT_MESSAGE_TAG_PICTURE.equals(tag)) {
                            if (chatMessage.data == null) {
                                Logger.w("missing data for ID="+chatMessage.messageId+" tag="+tag+" ");
                                continue;
                            }

                            Bitmap bmp = WhiteboardUtils.createBitmap(chatMessage);

                            if (bmp == null) {
                                Logger.e("Failed to create bitmap from "+chatMessage);
                                continue;
                            }

                            //figure out if need to scale it
                            int remoteWidth = chatMessage.data.optInt(WhiteboardUtils.CHAT_MESSAGE_DATA_KEY_DOODLE_AVAILABLE_WIDTH, -1);
                            int remoteHeight = chatMessage.data.optInt(WhiteboardUtils.CHAT_MESSAGE_DATA_KEY_DOODLE_AVAILABLE_HEIGHT, -1);

                            Rect toRect;
                            float scaleX = 1;
                            float scaleY = 1;
                            if (remoteWidth > 0 && remoteWidth != w) {
                                //remote is different size
                                scaleX = (float)w / (float)remoteWidth;
                            }
                            if (remoteHeight > 0 && remoteHeight != h) {
                                //remote is different size
                                scaleY = (float)h / (float)remoteHeight;
                            }

                            int left = chatMessage.data.optInt(WhiteboardUtils.CHAT_MESSAGE_DATA_KEY_DOODLE_LEFT, 0);
                            int top = chatMessage.data.optInt(WhiteboardUtils.CHAT_MESSAGE_DATA_KEY_DOODLE_TOP, 0);

                            int rectX = (int)(scaleX * left);
                            int rectY = (int)(scaleY * top);
                            toRect = new Rect(rectX, rectY, rectX + (int)(scaleX * bmp.getWidth()), rectY + (int)(scaleY * bmp.getHeight()));

                            Logger.d("scaleX="+scaleX+" B.W="+bmp.getWidth()+" RW="+remoteWidth
                                    +" scaleY="+scaleY+" B.H="+bmp.getHeight()+" RH="+remoteHeight
                                    +" W="+w+" H="+h
                                    +" left="+left+" top="+top
                                    +" toRect="+toRect
                                    +" ByteCount="+bmp.getByteCount());

                            canvas.drawBitmap(bmp, null, toRect, null);

                        } else if (WhiteboardUtils.CHAT_MESSAGE_TAG_CLEAR.equals(tag)) {
                            //clear canvas or bmp?
                            int color = Color.WHITE;
                            if (chatMessage.data != null) {
                                color = chatMessage.data.optInt(WhiteboardUtils.CHAT_MESSAGE_DATA_KEY_BACKGROUND_COLOR, color);
                            }

                            canvas.drawColor(color);
                        }

                        if (rememberLastDisplayed) {
                            mChatMessageListLastDisplayed = chatMessage;
                        }
                    }
                    invalidate();
                }
            }
        }
    }

    public Bitmap getDoodle() {
        if (mDrawingBitmap == null || mDrawingCanvas == null) {
            int w = getWidth();
            int h = getHeight();
            if (w > 0 && h > 0) {
                mDrawingBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                mDrawingCanvas = new Canvas(mDrawingBitmap);
            } else {
                Logger.d("not ready to create mDrawingCanvas yet w="+w+" h="+h);
                return null;
            }
        }

        //clear anything from before
        mDrawingBitmap.eraseColor(Color.TRANSPARENT);
        mDrawingCanvas.drawPath(mPath, mPaint);

        return mDrawingBitmap;
    }


    public void reset() {
        mPath.reset();
        invalidate();
    }

    public void setDoodleListener(DoodleListener listener) {
        mDoodleListener = listener;
    }

    /**
     * Owner can call this to force sending doodle event.
     * Used if there is a send button when in draw mode...
     */
    public void sendDoodleEvent() {
        if (mDoodleListener != null) {
            invalidate();
            long duration = System.currentTimeMillis() - mDoodleStart;
            mDoodleStart = System.currentTimeMillis();
            DoodleEvent event = new DoodleEvent(DoodleType.DOODLE, mLeftMostX, mRightMostX, mHighestY, mLowestY, duration);
            mDoodleListener.onNewDoodle(event);
        }
    }

    public int getStrokeWidth() {
        return mStrokeWidth;
    }

    public void setStrokeWidth(int strokeWidth) {
        this.mStrokeWidth = strokeWidth;
        mPaint.setStrokeWidth(mStrokeWidth);
        invalidate();
    }

    public int getStrokeColor() {
        return mStrokeColor;
    }

    public void setStrokeColor(int strokeColor) {
        this.mStrokeColor = strokeColor;
        mPaint.setColor(mStrokeColor);
        invalidate();
    }

    interface DoodleListener {
        void onNewDoodle(DoodleEvent event);
    }

    enum DoodleType {
        DOODLE
    }

    class DoodleEvent {
        final DoodleType type;
        float leftMostX;
        float rightMostX;
        float highestY;
        float lowestY;
        long duration;

        DoodleEvent(DoodleType type, float leftMostX, float rightMostX, float highestY, float lowestY, long duration) {
            this.type = type;
            this.leftMostX = leftMostX;
            this.rightMostX = rightMostX;
            this.highestY = highestY;
            this.lowestY = lowestY;
            this.duration = duration;
        }

        @Override
        public String toString() {
            return "DoodleEvent{" +
                    "type=" + type +
                    ", leftMostX=" + leftMostX +
                    ", rightMostX=" + rightMostX +
                    ", highestY=" + highestY +
                    ", lowestY=" + lowestY +
                    ", duration=" + duration +
                    '}';
        }
    }
}
