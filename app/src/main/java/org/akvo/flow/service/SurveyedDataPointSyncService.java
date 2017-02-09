/*
 *  Copyright (C) 2013-2017 Stichting Akvo (Akvo Foundation)
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

package org.akvo.flow.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.Pair;

import org.akvo.flow.R;
import org.akvo.flow.api.FlowApi;
import org.akvo.flow.data.database.SurveyDbAdapter;
import org.akvo.flow.domain.SurveyGroup;
import org.akvo.flow.domain.SurveyInstance;
import org.akvo.flow.domain.SurveyedLocale;
import org.akvo.flow.exception.HttpException;
import org.akvo.flow.util.ConstantUtil;
import org.akvo.flow.util.NotificationHelper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

public class SurveyedDataPointSyncService extends IntentService {

    private static final String TAG = SurveyedDataPointSyncService.class.getSimpleName();

    public static final String SURVEY_GROUP = "survey_group";

    private final Handler mHandler = new Handler();

    public SurveyedDataPointSyncService() {
        super(TAG);
        // Tell the system to restart the service if it was unexpectedly stopped before completion
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final long surveyGroupId = intent.getLongExtra(SURVEY_GROUP, SurveyGroup.ID_NONE);
        int syncedRecords = 0;
        FlowApi api = new FlowApi(getApplicationContext());
        SurveyDbAdapter database = new SurveyDbAdapter(getApplicationContext()).open();
        boolean correctSync = true;
        NotificationHelper
                .displayNotificationWithProgress(this, getString(R.string.syncing_records),
                        getString(R.string.pleasewait), true, true,
                        ConstantUtil.NOTIFICATION_RECORD_SYNC);
        try {
            Set<String> batch, lastBatch = new HashSet<>();
            while (true) {
                Pair<Set<String>, Boolean> syncResult = sync(database, api, surveyGroupId);
                batch = syncResult.first;
                if (!syncResult.second) {
                    //at least one of the data points seems corrupted
                    correctSync = syncResult.second;
                }
                batch.removeAll(lastBatch);// Remove duplicates.
                if (batch.isEmpty()) {
                    break;
                }
                syncedRecords += batch.size();
                sendBroadcastNotification();// Keep the UI fresh!
                NotificationHelper
                        .displayNotificationWithProgress(this, getString(R.string.syncing_records),
                                String.format(getString(R.string.synced_records),
                                        syncedRecords), true, true,
                                ConstantUtil.NOTIFICATION_RECORD_SYNC);
                lastBatch = batch;
            }
            if (correctSync) {
                NotificationHelper
                        .displayNotificationWithProgress(this, getString(R.string.syncing_records),
                                String.format(getString(R.string.synced_records),
                                        syncedRecords), false, false,
                                ConstantUtil.NOTIFICATION_RECORD_SYNC);
            } else {
                NotificationHelper.displayErrorNotificationWithProgress(this,
                        getString(R.string.sync_error),
                        getString(R.string.syncing_corrupted_data_points_error), false, false,
                        ConstantUtil.NOTIFICATION_RECORD_SYNC);
            }
        } catch (HttpException e) {
            Timber.e(e, e.getMessage());
            String message = e.getMessage();
            switch (e.getStatus()) {
                case HttpURLConnection.HTTP_FORBIDDEN:
                    // A missing assignment might be the issue. Let's hint the user.
                    message = getString(R.string.error_assignment_text);
                    break;
            }
            displayToast(message);
            NotificationHelper
                    .displayErrorNotificationWithProgress(this, getString(R.string.sync_error),
                            message, false,
                            false, ConstantUtil.NOTIFICATION_RECORD_SYNC);
        } catch (IOException e) {
            Timber.e(e, e.getMessage());
            displayToast(getString(R.string.network_error));
            NotificationHelper
                    .displayErrorNotificationWithProgress(this, getString(R.string.sync_error),
                            getString(R.string.network_error), false, false,
                            ConstantUtil.NOTIFICATION_RECORD_SYNC);
        } finally {
            database.close();
        }

        sendBroadcastNotification();
    }

    /**
     * Sync a Record batch, and return the Set of Record IDs within the response
     */
    @NonNull
    private Pair<Set<String>, Boolean> sync(@NonNull SurveyDbAdapter database, @NonNull FlowApi api,
            long surveyGroupId)
            throws IOException {
        final String syncTime = database.getSyncTime(surveyGroupId);
        Set<String> records = new HashSet<>();
        Timber.d("sync() - SurveyGroup: " + surveyGroupId + ". SyncTime: " + syncTime);
        List<SurveyedLocale> locales = api
                .getSurveyedLocales(surveyGroupId, syncTime);
        boolean correctData = true;
        if (locales != null) {
            for (SurveyedLocale locale : locales) {
                List<SurveyInstance> surveyInstances = locale.getSurveyInstances();
                if (surveyInstances == null || surveyInstances.isEmpty()) {
                    correctData = false;
                }
                database.syncSurveyedLocale(locale);
                records.add(locale.getId());
            }
        }
        //Delete empty or corrupted data received from server
        database.deleteEmptyRecords();
        return new Pair<>(records, correctData);
    }

    private void displayToast(final String text) {
        mHandler.post(new ServiceToastRunnable(getApplicationContext(), text));
    }

    /**
     * Dispatch a Broadcast notification to notify of SurveyedLocales synchronization.
     * This notification will be received in {@link org.akvo.flow.ui.fragment.DatapointsFragment}, in order to
     * refresh its data
     */
    private void sendBroadcastNotification() {
        Intent intentBroadcast = new Intent(ConstantUtil.ACTION_LOCALE_SYNC);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intentBroadcast);
    }
}
