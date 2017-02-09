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

package org.akvo.flow.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.akvo.flow.R;
import org.akvo.flow.data.dao.SurveyDao;
import org.akvo.flow.data.database.SurveyDbAdapter;
import org.akvo.flow.domain.Survey;
import org.akvo.flow.util.ConstantUtil;
import org.akvo.flow.util.FileUtil;
import org.akvo.flow.util.FileUtil.FileType;
import org.akvo.flow.util.NotificationHelper;
import org.akvo.flow.util.StatusUtil;
import org.akvo.flow.util.ViewUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import timber.log.Timber;

/**
 * Service that will check a well-known location on the device's SD card for a
 * zip file that contains data that should be loaded on the device. The root of
 * the zip file can contain a file called dbinstructions.sql. If it does, the
 * sql statements contained therein will be executed in the order they appear.
 * The zip file can also contain any number of directories which can each
 * contain ONE survey (the survey xml and any help media). The name of the
 * directory must be the surveyID and the name of the survey file will be used
 * for the survey name. The system will iterate through each directory and
 * install the survey and help media contained therein. If the survey is already
 * present on the device, the survey in the ZIP file will overwrite the data
 * already on the device. If there are multiple zip files in the directory, this
 * utility will process them in lexicographical order by file name; Any files
 * with a name starting with . will be skipped (to prevent inadvertent
 * processing of MAC OSX metadata files).
 * 
 * @author Christopher Fagiani
 */
public class BootstrapService extends IntentService {

    private static final String TAG = "BOOTSTRAP_SERVICE";
    public volatile static boolean isProcessing = false;
    private SurveyDbAdapter databaseAdapter;
    private Handler mHandler;

    public BootstrapService() {
        super(TAG);
    }

    public void onHandleIntent(Intent intent) {
        isProcessing = true;
        checkAndInstall();
        isProcessing = false;
        sendBroadcastNotification();
    }

    /**
     * Checks the bootstrap directory for unprocessed zip files. If they are
     * found, they're processed one at a time. If an error occurs, all
     * processing stops (subsequent zips won't be processed if there are
     * multiple zips in the directory) just in case data in a later zip depends
     * on the previous one being there.
     */
    private void checkAndInstall() {
        try {
            ArrayList<File> zipFiles = getZipFiles();
            if (zipFiles.isEmpty()) {
                return;
            }

            String startMessage = getString(R.string.bootstrapstart);
            displayNotification(startMessage);
            databaseAdapter = new SurveyDbAdapter(this);
            databaseAdapter.open();
            try {
                for (File file : zipFiles) {
                    try {
                        processFile(file);
                    } catch (Exception e) {
                        // try to roll back any database changes (if the zip has a rollback file)
                        String newFilename = file.getAbsolutePath();
                        file.renameTo(new File(newFilename + ConstantUtil.PROCESSED_ERROR_SUFFIX));
                        throw (e);
                    }
                }
                String endMessage = getString(R.string.bootstrapcomplete);
                displayNotification(endMessage);
            } finally {
                if (databaseAdapter != null) {
                    databaseAdapter.close();
                }
            }
        } catch (Exception e) {
            String errorMessage = getString(R.string.bootstraperror);
            displayErrorNotification(errorMessage);

            Timber.e(e,"Bootstrap error");
        }
    }

    private void displayErrorNotification(String errorMessage) {
        //FIXME: why are we repeating the message in title and text?
        NotificationHelper.displayErrorNotification(errorMessage, errorMessage, this, ConstantUtil.NOTIFICATION_BOOTSTRAP);
    }

    private void displayNotification(String message) {
        //FIXME: why are we repeating the message in title and text?
        NotificationHelper.displayNotification(message, message, this, ConstantUtil.NOTIFICATION_BOOTSTRAP);
    }

    /**
     * processes a bootstrap zip file
     */
    private void processFile(File file) throws Exception {
        final ZipFile zipFile = new ZipFile(file);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            Timber.d("Processing entry: " + entry.getName());
            String parts[] = entry.getName().split("/");
            String filename = parts[parts.length - 1];
            String id = parts.length > 1 ? parts[parts.length - 2] : "";

            // Skip directories and hidden/unwanted files
            if (entry.isDirectory() || filename.startsWith(".") ||
                    ConstantUtil.BOOTSTRAP_ROLLBACK_FILE.equalsIgnoreCase(filename)) {
                continue;
            }

            if (filename.endsWith(ConstantUtil.CASCADE_RES_SUFFIX)) {
                // Cascade resource
                FileUtil.extract(new ZipInputStream(zipFile.getInputStream(entry)),
                        FileUtil.getFilesDir(FileType.RES));
            } else if (filename.endsWith(ConstantUtil.XML_SUFFIX)) {
                processSurveyFile(zipFile, entry, filename, id);
            } else {
                // Help media file
                File helpDir = new File(FileUtil.getFilesDir(FileType.FORMS), id);
                if (!helpDir.exists()) {
                    helpDir.mkdir();
                }
                FileUtil.copy(zipFile.getInputStream(entry),
                        new FileOutputStream(new File(helpDir, filename)));
                }
            }

        // now rename the zip file so we don't process it again
        file.renameTo(new File(file.getAbsolutePath() + ConstantUtil.PROCESSED_OK_SUFFIX));
    }

    private void processSurveyFile(@NonNull ZipFile zipFile, @NonNull ZipEntry entry, @NonNull String filename,
                                   @NonNull String id) throws IOException {
        String surveyName = filename;
        // we want to avoid duplicate survey names
        String surveyFolderName = generateSurveyFolder(entry);
        if (surveyName.contains(ConstantUtil.DOT_SEPARATOR)) {
            surveyName = surveyName.substring(0, surveyName.indexOf(ConstantUtil.DOT_SEPARATOR));
        }
        Survey survey = databaseAdapter.getSurvey(id);
        if (survey == null) {
            survey = new Survey();
            survey.setId(id);
            survey.setName(surveyName);
            survey.setHelpDownloaded(true);// Resources are always attached to the zip file
            survey.setType(ConstantUtil.SURVEY_TYPE);
        }
        survey.setLocation(ConstantUtil.FILE_LOCATION);
        String surveyFileName = generateSurveyFileName(filename, surveyFolderName);
        survey.setFileName(surveyFileName);

        // in both cases (new survey and existing), we need to update the xml
        File surveyFile = generateNewSurveyFile(filename, surveyFolderName);
        FileUtil.copy(zipFile.getInputStream(entry), new FileOutputStream(surveyFile));

        // now read the survey XML back into memory to see if there is a version
        Survey loadedSurvey = null;
        try {
            InputStream in = new FileInputStream(surveyFile);
            loadedSurvey = SurveyDao.loadSurvey(survey, in);
        } catch (FileNotFoundException e) {
            Timber.e("Could not load survey xml file");
        }
        if (loadedSurvey == null) {
            // Something went wrong, we cannot continue with this survey
            return;
        }

        verifyAppId(loadedSurvey);

        survey.setName(loadedSurvey.getName());
        survey.setSurveyGroup(loadedSurvey.getSurveyGroup());

        if (loadedSurvey.getVersion() > 0) {
            survey.setVersion(loadedSurvey.getVersion());
        } else {
            survey.setVersion(1d);
        }

        // Save the Survey, SurveyGroup, and languages.
        updateSurveyStorage(survey);
    }

    /**
     * Check form app id. Reject the form if it does not belong to the one set up
     * @param loadedSurvey survey to verify
     */
    private void verifyAppId(@NonNull Survey loadedSurvey) {
        final String app = StatusUtil.getApplicationId(this);
        final String formApp = loadedSurvey.getApp();
        if (!TextUtils.isEmpty(app) && !TextUtils.isEmpty(formApp) && !app.equals(formApp)) {
            ViewUtil.displayToastFromService(getString(R.string.bootstrap_invalid_app), mHandler,
                                             getApplicationContext());
            throw new IllegalArgumentException("Form belongs to a different instance." +
                                                   " Expected: " + app + ". Got: " + formApp);
        }
    }

    private void updateSurveyStorage(@NonNull Survey survey) {
        databaseAdapter.addSurveyGroup(survey.getSurveyGroup());
        databaseAdapter.saveSurvey(survey);
    }

    @NonNull
    private File generateNewSurveyFile(@NonNull String filename, @Nullable String surveyFolderName) {
        File filesDir = FileUtil.getFilesDir(FileType.FORMS);
        if (TextUtils.isEmpty(surveyFolderName)) {
            return new File(filesDir, filename);
        } else {
            File surveyFolder = new File(filesDir, surveyFolderName);
            if (!surveyFolder.exists()) {
                surveyFolder.mkdir();
            }
            return new File(surveyFolder, filename);
        }
    }

    @NonNull
    private String generateSurveyFileName(@NonNull String filename, @Nullable String surveyFolderName) {
        StringBuilder sb = new StringBuilder(20);
        if (!TextUtils.isEmpty(surveyFolderName)) {
            sb.append(surveyFolderName);
            sb.append(File.separator);
        }
        sb.append(filename);
        return sb.toString();
    }

    @NonNull
    private String generateSurveyFolder(@NonNull ZipEntry entry) {
        String entryName = entry.getName();
        String entryPaths[] = entryName == null ? new String[0] : entryName.split(File.separator);
        return entryPaths.length < 2 ? "" : entryPaths[entryPaths.length - 2];
    }

    /**
     * returns an ordered list of zip files that exist in the device's bootstrap
     * directory
     */
    private ArrayList<File> getZipFiles() {
        ArrayList<File> zipFiles = new ArrayList<>();
        // zip files can only be loaded on the SD card (not internal storage) so
        // we only need to look there
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            File dir = FileUtil.getFilesDir(FileType.INBOX);
            File[] fileList = dir.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    if (file.isFile() && file.getName().toLowerCase()
                                .endsWith(ConstantUtil.ARCHIVE_SUFFIX)) {
                        zipFiles.add(file);
                    }
                }
            }
            Collections.sort(zipFiles);
        }
        return zipFiles;
    }

    /**
     * sets up the uncaught exception handler for this thread so we can report
     * errors to the server.
     */
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler();
    }

    /**
     * Dispatch a Broadcast notification to notify of surveys synchronization.
     * This notification will be received in SurveyHomeActivity, in order to
     * refresh its data
     */
    private void sendBroadcastNotification() {
        Intent intentBroadcast = new Intent(getString(R.string.action_surveys_sync));
        sendBroadcast(intentBroadcast);
    }
}
