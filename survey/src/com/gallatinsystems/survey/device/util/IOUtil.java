/*
 *  Copyright (C) 2013 Stichting Akvo (Akvo Foundation)
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

package com.gallatinsystems.survey.device.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.gallatinsystems.survey.device.util.Swift.UploadListener;

import android.util.Log;

public class IOUtil {
    private static final String TAG = IOUtil.class.getSimpleName();

    private static final int IO_BUFFER_SIZE = 8192;

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        copyStream(in, out, 0, null);
    }

    public static void copyStream(InputStream in, OutputStream out, long totalBytes,
            UploadListener listener) throws IOException {
        long bytesWritten = 0;// Only useful in the presence of a listener
        byte[] b = new byte[IO_BUFFER_SIZE];
        
        int read;
        while ((read = in.read(b)) != -1) {
            out.write(b, 0, read);
            bytesWritten += read;
            if (listener != null) {
                listener.uploadProgress(bytesWritten, totalBytes);
            }
        }
    }

    public static void closeSilently(Closeable closable) {
        if (closable != null) {
            try {
                closable.close();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }
}
