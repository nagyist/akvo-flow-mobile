/*
 *  Copyright (C) 2015-2016 Stichting Akvo (Akvo Foundation)
 *
 *  This file is part of Akvo FLOW.
 *
 *  Akvo FLOW is free software: you can redistribute it and modify it under the terms of
 *  the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 *  either version 3 of the License or any later version.
 *
 *  Akvo FLOW is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Affero General Public License included below for more details.
 *
 *  The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.akvo.flow.data.loader;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;

import org.akvo.flow.app.FlowApp;
import org.akvo.flow.data.database.SurveyDbAdapter;
import org.akvo.flow.data.database.UserColumns;
import org.akvo.flow.data.loader.base.AsyncLoader;
import org.akvo.flow.domain.User;

import java.util.ArrayList;
import java.util.List;

public class UserLoader extends AsyncLoader<List<User>> {

    public UserLoader(Context context) {
        super(context);
    }

    @NonNull
    @Override
    public List<User> loadInBackground() {
        List<User> users = new ArrayList<>();
        SurveyDbAdapter database = new SurveyDbAdapter(getContext());
        database.open();
        Cursor cursor = database.getUsers();
        if (cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(UserColumns._ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(UserColumns.NAME));
                User user = new User(id, name);
                // Skip selected user
                if (!user.equals(FlowApp.getApp().getUser())) { //TODO: get user from preferences
                    users.add(new User(id, name));
                }
            } while (cursor.moveToNext());
            cursor.close();
        }
        database.close();
        return users;
    }
}
