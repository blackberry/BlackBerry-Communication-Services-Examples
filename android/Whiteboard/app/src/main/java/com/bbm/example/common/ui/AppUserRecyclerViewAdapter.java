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

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bbm.example.whiteboard.R;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.AppUserFilter;
import com.bbm.sdk.support.reactive.ObserveConnector;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.reactive.Observer;

import java.util.Collection;
import java.util.HashSet;

/**
 * {@link RecyclerView.Adapter} that can display a {@link AppUser}
 */
public class AppUserRecyclerViewAdapter extends RecyclerView.Adapter<AppUserRecyclerViewAdapter.ViewHolder> {

    private final ObservableList<AppUser> mUsers;
    //if in multiselect mode this will have all the values that the user has tapped
    //otherwise this will be empty
    private final HashSet<AppUser> mSelectedValues = new HashSet<>();

    //Callback for when contacts(s) are selected
    private AppUserSelectedCallback mCallback;

    private boolean mMultiselectMode = false;

    private ObserveConnector mObserveConnector = new ObserveConnector();

    /**
     * Display list of users (not in multiselect mode).
     * In this mode the {@Link AppUsersSelectedCallback#selected(AppUser) } will be called when the
     * user taps a user.
     * @param users the users to allow picking from
     * @param callback the listener to call when a user is selected
     */
    public AppUserRecyclerViewAdapter(ObservableList<AppUser> users, AppUserSelectedCallback callback) {
        this(users);
        mCallback = callback;
    }

    /**
     * Display a list of users in multiselect mode.
     * In this mode the callback will not be called when the user taps a user, instead that user will be marked as
     * selected.  At any point the owner of this can call {@Link getSelectedUsers()} to get a collection of the
     * user that have been selected
     *
     * @param users the users to allow picking from
     * @param callback the listener to call when a user is selected
     * @param defaultSelectedFilter optional filter to specify which users should be selected by default.
     */
    public AppUserRecyclerViewAdapter(ObservableList<AppUser> users, AppUserSelectedCallback callback, AppUserFilter defaultSelectedFilter) {
        this(users, callback);
        mMultiselectMode = true;

        if (defaultSelectedFilter != null) {
            //by default select any specified users
            for (AppUser user : mUsers.get()) {
                if (defaultSelectedFilter.matches(user)) {
                    mSelectedValues.add(user);
                }
            }
        }
    }

    private AppUserRecyclerViewAdapter(ObservableList<AppUser> users) {
        mUsers = users;

        mObserveConnector.connect(mUsers, new Observer() {
            @Override
            public void changed() {
                Logger.d("changed: mUsers.size="+ mUsers.size());

                if (!mUsers.isPending() && mUsers.size() == 0) {
                    //A real app would probably display a message in the UI
                    Logger.user("There are not any other app users");
                } else {
                    notifyDataSetChanged();
                }
            }
        }, true);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        //remove any observers that might be observing data from another row
        holder.mObserveConnector.removeObservers();
        //get the user for this position, since we need the ObservableValue get the regid from it to get the wrapped OV
        holder.mItem = UserManager.getInstance().getUser(mUsers.get(position).getRegId());

        //listen for changes to the app user and update UI
        holder.mObserveConnector.connect(holder.mItem, new Observer() {
            @Override
            public void changed() {
                if (holder.mImageTask != null) {
                    //first cancel the task so it doesn't set the image for previous user on this row
                    holder.mImageTask.cancel(true);
                    holder.mImageTask = null;

                    //set to default image immediately otherwise if user scrolls quick they would see previous users avatar for a bit
                    holder.mAppUserAvatarView.setImageResource(R.drawable.default_avatar);
                }

                if (holder.mItem.get().getExists() == Existence.YES) {
                    AppUser user = holder.mItem.get();
                    holder.mAppUserNameView.setText(user.getName());
                    holder.mAppUserInfoView.setText(user.getEmail());

                    boolean selected = mSelectedValues.contains(user);
                    //the background used by contacts list item has color defined for when in activated state
                    holder.mView.setActivated(selected);

                    if (!TextUtils.isEmpty((user.getAvatarUrl()))) {
                        Logger.d("Will load avatar for "+user);
                        holder.mImageTask = ImageTask.load(user.getAvatarUrl(), holder.mAppUserAvatarView);
                    } else {
                        Logger.d("empty avatar URL for "+user);
                        holder.mAppUserAvatarView.setImageResource(R.drawable.default_avatar);
                    }
                } else {
                    Logger.d("User doesn't exist yet "+holder.mItem.get());
                    holder.mAppUserNameView.setText("");
                    holder.mAppUserInfoView.setText("");

                    holder.mAppUserAvatarView.setImageResource(R.drawable.default_avatar);
                }

                holder.mView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mMultiselectMode) {
                            boolean selected = mSelectedValues.contains(holder.mItem.get());
                            //the background used by appUsers list item has color defined for when in activated state
                            holder.mView.setActivated(!selected);
                            if (selected) {
                                mSelectedValues.remove(holder.mItem.get());
                            } else {
                                mSelectedValues.add(holder.mItem.get());
                            }
                        } else {
                            if (mCallback != null) {
                                // Notify the active callbacks interface (the activity, if the
                                // fragment is attached to one) that an item has been selected.
                                mCallback.selected(holder.mItem.get());
                            }
                        }
                    }
                });
            }
        }, true); //run changed() right away
    }

    @Override
    public int getItemCount() {
        return mUsers.size();
    }

    public Collection<AppUser> getSelectedUsers() {
        return mSelectedValues;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mAppUserNameView;
        public final TextView mAppUserInfoView;
        public final ImageView mAppUserAvatarView;
        public ObservableValue<AppUser> mItem;
        public AsyncTask<Void, Void, Bitmap> mImageTask;

        ObserveConnector mObserveConnector = new ObserveConnector();

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mAppUserNameView = (TextView) view.findViewById(R.id.contact_name);
            mAppUserInfoView = (TextView) view.findViewById(R.id.contact_info);
            mAppUserAvatarView = (ImageView) view.findViewById(R.id.contact_avatar);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mAppUserNameView.getText() + "'";
        }
    }
}
