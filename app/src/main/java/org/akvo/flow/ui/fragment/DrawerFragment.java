/*
 *  Copyright (C) 2010-2017 Stichting Akvo (Akvo Foundation)
 *
 *  This file is part of Akvo Flow.
 *
 *  Akvo Flow is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Akvo Flow is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Akvo Flow.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.akvo.flow.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.BaseExpandableListAdapter;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.akvo.flow.R;
import org.akvo.flow.activity.SettingsActivity;
import org.akvo.flow.app.FlowApp;
import org.akvo.flow.data.database.SurveyDbAdapter;
import org.akvo.flow.data.loader.SurveyGroupLoader;
import org.akvo.flow.data.loader.UserLoader;
import org.akvo.flow.domain.SurveyGroup;
import org.akvo.flow.domain.User;
import org.akvo.flow.util.PlatformUtil;
import org.akvo.flow.util.ViewUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DrawerFragment extends Fragment implements ExpandableListView.OnChildClickListener,
        ExpandableListView.OnGroupClickListener {

    private static final float ITEM_TEXT_SIZE = 14;

    // Context menu IDs
    private static final int ID_DELETE_SURVEY = 0;
    private static final int ID_EDIT_USER = 0;
    private static final int ID_DELETE_USER = 1;

    // ExpandableList's group IDs
    private static final int GROUP_USERS = 0;
    private static final int GROUP_SURVEYS = 1;
    private static final int GROUP_SETTINGS = 2;

    // Loader IDs
    private static final int LOADER_SURVEYS = 0;
    private static final int LOADER_USERS = 1;

    private ExpandableListView mListView;
    private DrawerAdapter mAdapter;

    private SurveyDbAdapter mDatabase;
    private DrawerListener mListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.navigation_drawer, container, false);
        mListView = (ExpandableListView) v.findViewById(R.id.list);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mDatabase == null) {
            mDatabase = new SurveyDbAdapter(getActivity());
            mDatabase.open();
        }
        if (mAdapter == null) {
            mAdapter = new DrawerAdapter(getActivity());
            mListView.setAdapter(mAdapter);
            mListView.expandGroup(GROUP_SURVEYS);
            mListView.setOnGroupClickListener(this);
            mListView.setOnChildClickListener(this);
            registerForContextMenu(mListView);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDatabase.close();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mListener = (DrawerListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement surveys and users listeners");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
        if (mAdapter != null) {
            mAdapter.notifyDataSetInvalidated();
        }
    }

    public void load() {
        if (!isResumed()) {
            return;
        }
        LoaderManager loaderManager = getLoaderManager();
        loaderManager.restartLoader(LOADER_SURVEYS, null, new SurveyGroupLoaderCallback(this));
        loaderManager.restartLoader(LOADER_USERS, null, new UserLoaderCallback(this));
    }

    public void onDrawerClosed() {
        mListView.collapseGroup(GROUP_USERS);
    }

    private void addUser() {
        editUser(null);
    }

    private void editUser(final User user) {
        final boolean newUser = user == null;
        final EditText et = new EditText(getActivity());
        et.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        et.setSingleLine();
        if (!newUser) {
            et.append(user.getName());
        }

        int titleRes = user != null ? R.string.edit_user : R.string.add_user;

        ViewUtil.ShowTextInputDialog(getActivity(), titleRes, R.string.username, et,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = et.getText().toString();// TODO: Validate name
                        if (TextUtils.isEmpty(name)) {
                            // Disallow blank usernames
                            Toast.makeText(getActivity(), R.string.empty_user_warning,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Long uid = newUser ? null : user.getId();
                        uid = mDatabase.createOrUpdateUser(uid, name);

                        User loggedUser = FlowApp.getApp().getUser();
                        if (newUser) {
                            // Automatically log in new users
                            mListener.onUserSelected(new User(uid, name));
                        } else if (user.equals(loggedUser)) {
                            loggedUser.setName(name);
                        }
                        load();
                    }
                });
    }

    private void deleteUser(final User user) {
        final long uid = user.getId();
        ViewUtil.showConfirmDialog(R.string.delete_user, R.string.delete_user_confirmation,
                getActivity(),
                true, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mDatabase.deleteUser(uid);
                        if (user.equals(FlowApp.getApp().getUser())) {
                            FlowApp.getApp().setUser(null);
                        }
                        load();
                    }
                });
    }

    private SurveyGroup getSurveyForContextMenu(int type, int group, int child) {
        List<SurveyGroup> surveyGroups =
                mAdapter == null ? Collections.EMPTY_LIST : mAdapter.getSurveyGroups();
        if (group == GROUP_SURVEYS && type == ExpandableListView.PACKED_POSITION_TYPE_CHILD
                && child < surveyGroups.size()) {
            return surveyGroups.get(child);
        }
        return null;
    }

    private User getUserForContextMenu(int type, int group, int child) {
        List<User> users = mAdapter == null ? Collections.EMPTY_LIST : mAdapter.getUsers();
        if (group == GROUP_USERS) {
            if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD && child < users.size()) {
                return users.get(child);
            } else if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
                return FlowApp.getApp().getUser();
            }
        }
        return null;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuInfo;
        int type = ExpandableListView.getPackedPositionType(info.packedPosition);
        int group = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        int child = ExpandableListView.getPackedPositionChild(info.packedPosition);

        switch (group) {
            case GROUP_SURVEYS:
                SurveyGroup sg = getSurveyForContextMenu(type, group, child);
                if (sg != null) {
                    menu.setHeaderTitle(sg.getName());
                    menu.add(0, ID_DELETE_SURVEY, ID_DELETE_SURVEY, R.string.delete);
                }
                break;
            case GROUP_USERS:
                User user = getUserForContextMenu(type, group, child);
                if (user != null) {
                    menu.setHeaderTitle(user.getName());
                    menu.add(0, ID_EDIT_USER, ID_EDIT_USER, R.string.edit_user);
                    menu.add(0, ID_DELETE_USER, ID_DELETE_USER, R.string.delete_user);
                }
                break;
        }

    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) item.getMenuInfo();
        int type = ExpandableListView.getPackedPositionType(info.packedPosition);
        int group = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        int child = ExpandableListView.getPackedPositionChild(info.packedPosition);

        switch (group) {
            case GROUP_SURVEYS:
                return onSurveyContextItemSelected(type, group, child, item.getItemId());
            case GROUP_USERS:
                return onUserContextItemSelected(type, group, child, item.getItemId());
        }

        return super.onContextItemSelected(item);
    }

    private boolean onSurveyContextItemSelected(int type, int group, int child, int itemID) {
        SurveyGroup sg = getSurveyForContextMenu(type, group, child);
        if (sg != null && itemID == ID_DELETE_SURVEY) {
            final long surveyGroupId = sg.getId();
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.delete_project_text)
                    .setCancelable(true)
                    .setPositiveButton(R.string.okbutton,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    mDatabase.deleteSurveyGroup(surveyGroupId);
                                    if (FlowApp.getApp().getSurveyGroupId() == surveyGroupId) {
                                        mListener.onSurveySelected(null);
                                    }
                                    load();
                                }
                            })
                    .setNegativeButton(R.string.cancelbutton,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });
            builder.show();
            return true;
        }
        return false;
    }

    private boolean onUserContextItemSelected(int type, int group, int child, int itemID) {
        User user = getUserForContextMenu(type, group, child);
        if (user != null) {
            switch (itemID) {
                case ID_EDIT_USER:
                    editUser(user);
                    return true;
                case ID_DELETE_USER:
                    deleteUser(user);
                    return true;
            }
        }
        return false;
    }

    private void updateUsers(List<User> users) {
        if (mAdapter != null) {
            mAdapter.updateUsers(users);
        }
    }

    private void updateSurveys(List<SurveyGroup> surveyGroups) {
        if (mAdapter != null) {
            mAdapter.updateSurveys(surveyGroups);
        }
    }

    @Override
    public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
        switch (groupPosition) {
            case GROUP_SURVEYS:
                return true; // This way the expander cannot be collapsed
            case GROUP_SETTINGS:
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
            int childPosition, long id) {
        switch (groupPosition) {
            case GROUP_USERS:
                User user = (User) v.getTag();
                if (user.getId() == -1) {
                    addUser();
                } else {
                    mListener.onUserSelected(user);
                }
                return true;
            case GROUP_SURVEYS:
                SurveyGroup sg = (SurveyGroup) v.getTag();
                mListener.onSurveySelected(sg);
                return true;
        }
        return false;
    }

    static class DrawerAdapter extends BaseExpandableListAdapter {

        private static final int LEFT_PADDING_DP = 30;
        private final LayoutInflater mInflater;
        private final List<User> mUsers;
        private final List<SurveyGroup> mSurveys;
        private final int leftPadding;
        private final int selectedSurveyBackgroundColor;

        @ColorInt
        private final int mHighlightColor;

        DrawerAdapter(Context context) {
            this.mInflater = LayoutInflater.from(context);
            this.mUsers = new ArrayList<>();
            this.mSurveys = new ArrayList<>();
            this.leftPadding = (int) PlatformUtil.dp2Pixel(context, LEFT_PADDING_DP);
            this.mHighlightColor = ContextCompat.getColor(context, R.color.orange_main);
            this.selectedSurveyBackgroundColor = ContextCompat
                    .getColor(context, R.color.background_alternate);
        }

        @Override
        public int getGroupCount() {
            return 3;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            switch (groupPosition) {
                case GROUP_USERS:
                    return mUsers.size() + 1;
                case GROUP_SURVEYS:
                    return mSurveys.size();
                default:
                    return GROUP_USERS;
            }
        }

        @Override
        public Object getGroup(int groupPosition) {
            return null;
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return null;
        }

        @Override
        public long getGroupId(int groupPosition) {
            return 0;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public int getGroupType(int groupPosition) {
            return groupPosition;
        }

        @Override
        public int getGroupTypeCount() {
            return 3;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                ViewGroup parent) {
            View v = convertView;
            switch (groupPosition) {
                case GROUP_USERS:
                    if (v == null) {
                        v = mInflater.inflate(R.layout.drawer_item_users, parent, false);
                    }
                    TextView userNameTv = (TextView) v.findViewById(R.id.item_txt);
                    ImageView dropdown = (ImageView) v.findViewById(R.id.dropdown);

                    User u = FlowApp.getApp().getUser();
                    String username = u != null ?
                            u.getName() :
                            userNameTv.getResources().getString(R.string.select_user);
                    userNameTv.setText(username);
                    dropdown.setImageResource(isExpanded ?
                            R.drawable.ic_action_collapse :
                            R.drawable.ic_action_expand);
                    break;
                case GROUP_SURVEYS:
                    if (v == null) {
                        v = mInflater.inflate(R.layout.drawer_item_surveys, parent, false);
                    }
                    break;
                case GROUP_SETTINGS:
                    if (v == null) {
                        v = mInflater.inflate(R.layout.drawer_item_settings, parent, false);
                    }
                    break;
            }

            return v;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = mInflater.inflate(android.R.layout.simple_list_item_1, null);
            }
            TextView tv = (TextView) v.findViewById(android.R.id.text1);
            v.setPadding(leftPadding, 0, 0, 0);

            tv.setTextSize(ITEM_TEXT_SIZE);
            tv.setTextColor(Color.BLACK);
            v.setBackgroundColor(Color.TRANSPARENT);

            switch (groupPosition) {
                case GROUP_USERS:
                    User user = isLastChild ?
                            new User(-1, tv.getResources().getString(R.string.new_user)) :
                            mUsers.get(childPosition);
                    tv.setText(user.getName());
                    v.setTag(user);
                    break;
                case GROUP_SURVEYS:
                    SurveyGroup sg = mSurveys.get(childPosition);
                    tv.setText(sg.getName());
                    if (sg.getId() == FlowApp.getApp().getSurveyGroupId()) {
                        tv.setTextColor(mHighlightColor);
                        v.setBackgroundColor(selectedSurveyBackgroundColor);
                    }
                    v.setTag(sg);
                    break;
            }

            return v;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return groupPosition == GROUP_USERS || groupPosition == GROUP_SURVEYS;
        }

        public void updateUsers(@NonNull List<User> users) {
            mUsers.clear();
            mUsers.addAll(users);
            notifyDataSetChanged();
        }

        public List<SurveyGroup> getSurveyGroups() {
            return mSurveys;
        }

        public List<User> getUsers() {
            return mUsers;
        }

        public void updateSurveys(@NonNull List<SurveyGroup> surveyGroups) {
            mSurveys.clear();
            mSurveys.addAll(surveyGroups);
            notifyDataSetChanged();
        }
    }

    private static class UserLoaderCallback implements LoaderManager.LoaderCallbacks<List<User>> {

        private final WeakReference<DrawerFragment> fragmentWeakReference;

        private UserLoaderCallback(DrawerFragment drawerFragment) {
            this.fragmentWeakReference = new WeakReference<>(drawerFragment);
        }

        @Override
        public Loader<List<User>> onCreateLoader(int id, Bundle args) {
            DrawerFragment drawerFragment = fragmentWeakReference.get();
            Context context = drawerFragment == null ? null : drawerFragment.getActivity();
            if (context != null) {
                return new UserLoader(context);
            } else {
                //context is null so activity is probably being destroyed
                return null;
            }

        }

        @Override
        public void onLoadFinished(Loader<List<User>> loader, List<User> data) {
            DrawerFragment drawerFragment = fragmentWeakReference.get();
            if (drawerFragment != null) {
                drawerFragment.updateUsers(data);
            }
        }

        @Override
        public void onLoaderReset(Loader<List<User>> loader) {
            // EMPTY
        }
    }

    private static class SurveyGroupLoaderCallback
            implements LoaderManager.LoaderCallbacks<List<SurveyGroup>> {

        private final WeakReference<DrawerFragment> fragmentWeakReference;

        private SurveyGroupLoaderCallback(DrawerFragment drawerFragment) {
            this.fragmentWeakReference = new WeakReference<>(drawerFragment);
        }

        @Override
        public Loader<List<SurveyGroup>> onCreateLoader(int id, Bundle args) {
            DrawerFragment drawerFragment = fragmentWeakReference.get();
            Context context = drawerFragment == null ? null : drawerFragment.getActivity();
            if (context != null) {
                return new SurveyGroupLoader(context);
            } else {
                //context is null so activity is probably being destroyed
                return null;
            }
        }

        @Override
        public void onLoadFinished(Loader<List<SurveyGroup>> loader, List<SurveyGroup> data) {
            DrawerFragment drawerFragment = fragmentWeakReference.get();
            if (drawerFragment != null) {
                drawerFragment.updateSurveys(data);
            }
        }

        @Override
        public void onLoaderReset(Loader<List<SurveyGroup>> loader) {
            // EMPTY
        }
    }

    public interface DrawerListener {

        void onSurveySelected(SurveyGroup surveyGroup);

        void onUserSelected(User user);
    }

}
