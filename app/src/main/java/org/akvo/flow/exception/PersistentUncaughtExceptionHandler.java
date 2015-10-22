/*
 *  Copyright (C) 2010-2015 Stichting Akvo (Akvo Foundation)
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

package org.akvo.flow.exception;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.content.Context;
import android.util.Log;

import com.joshdholtz.sentry.Sentry;
import com.joshdholtz.sentry.Sentry.SentryEventBuilder;

import org.akvo.flow.util.ConstantUtil;
import org.akvo.flow.util.FileUtil;
import org.akvo.flow.util.FileUtil.FileType;
import org.akvo.flow.util.PropertyUtil;
import org.json.JSONException;

/**
 * This exception handler will log all exceptions it handles to the filesystem
 * so they can be processed later. This sets the default uncaught exception
 * handler. It will delegate processing to the previously installed uncaught
 * exception handler (if there is one) to preserve normal system operation. This
 * class is a singleton to preserve the chain of exception handlers.
 * 
 * @author Christopher Fagiani
 */
public class PersistentUncaughtExceptionHandler implements
        UncaughtExceptionHandler {
    private static final String TAG = "EXCEPTION_HANDLER";
    private static PersistentUncaughtExceptionHandler instance;

    private UncaughtExceptionHandler oldHandler;

    public static final PersistentUncaughtExceptionHandler getInstance() {
        if (instance == null) {
            instance = new PersistentUncaughtExceptionHandler();
        }
        return instance;
    }

    /**
     * installs the old uncaught exception handler in a member variable so it
     * can be invoked later
     */
    private PersistentUncaughtExceptionHandler() {
        if (Thread.getDefaultUncaughtExceptionHandler() != null
                && !(Thread.getDefaultUncaughtExceptionHandler() instanceof PersistentUncaughtExceptionHandler)) {
            oldHandler = Thread.getDefaultUncaughtExceptionHandler();
        }

    }

    public static void setup(Context context) {
        final PropertyUtil props = new PropertyUtil(context.getResources());

        // Sets a listener to intercept the SentryEventBuilder before
        // each capture to set values that could change state
        Sentry.setCaptureListener(new Sentry.SentryEventCaptureListener() {

            @Override
            public SentryEventBuilder beforeCapture(SentryEventBuilder builder) {
                try {
                    builder.getTags().put("appId", props.getProperty(ConstantUtil.S3_BUCKET));
                } catch (JSONException e) {}

                return builder;
            }
        });

        Sentry.init(context, props.getProperty(ConstantUtil.SENTRY_URL),
                props.getProperty(ConstantUtil.SENTRY_DSN));
        Thread.setDefaultUncaughtExceptionHandler(getInstance());
    }


    /**
     * saves the exception to the filesystem. Processing will then be delegated
     * to the previously installed uncaught exception handler
     */
    @Override
    public void uncaughtException(Thread sourceThread, Throwable exception) {
        recordException(exception);

        // Still process the exception with the default handler so we don't
        // change system behavior
        if (oldHandler != null) {
            oldHandler.uncaughtException(sourceThread, exception);
        }
    }

    /**
     * checks against a white-list of exceptions we ignore (mainly communication
     * errors that can arise if we're offline).
     * 
     * @param exception
     * @return
     */
    private static boolean ignoreException(Throwable exception) {
        if (exception instanceof UnknownHostException) {
            return true;
        } else if (exception instanceof SocketException) {
            return true;
        } else if (exception instanceof IllegalStateException) {
            if (exception.getMessage() != null
                    && exception
                            .getMessage()
                            .toLowerCase()
                            .contains("sqlitedatabase created and never closed")) {
                return true;
            }
        }
        return false;
    }

    /**
     * saves the exception to the filesystem. this can be used to save otherwise
     * handled exceptions so they can be reported to the server.
     * 
     * @param exception
     */
    public static void recordException(Throwable exception) {
        if (!ignoreException(exception)) {
            // Record exception in Sentry
            Sentry.captureException(exception);

            // save the error
            final Writer result = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(result);
            exception.printStackTrace(printWriter);
            if (exception.getMessage() != null) {
                printWriter.print("\n" + exception.getMessage());
            }

            String filename = ConstantUtil.STACKTRACE_FILENAME + System.currentTimeMillis()
                    + ConstantUtil.STACKTRACE_SUFFIX;
            File file = new File(FileUtil.getFilesDir(FileType.STACKTRACE), filename);
            FileOutputStream out;
            try {
                out = new FileOutputStream(file);
                FileUtil.writeStringToFile(result.toString(), out);
            } catch (IOException e) {
                Log.e(TAG, "Couldn't save trace file", e);
            } finally {
                try {
                    result.close();
                } catch (IOException e) {
                    Log.w(TAG, "Can't close print writer object", e);
                }
            }
        }
    }
    
}
