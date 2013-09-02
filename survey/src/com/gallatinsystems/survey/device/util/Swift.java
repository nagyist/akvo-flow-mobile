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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.http.HttpStatus;

import android.text.TextUtils;
import android.util.Log;

import com.turbomanage.httpclient.HttpResponse;
import com.turbomanage.httpclient.android.AndroidHttpClient;

/**
 * OpenStack/Swift uploader. This version uses Swift v1.0, with Token
 * based authentication.
 * <br>
 * TODO: 
 * <ul>
 * <li>Cache the token and storage url</li>
 * <li>Upgrade API version depending on the backend</li>
 * <li>Add MIME type to objects</li>
 * <li>Discuss container security options (public/private)</li>
 * </ul>
 *
 */
public class Swift {

    /**
     * Interface that can be used to be notified of upload progress
     */
    public interface UploadListener {
        public void uploadProgress(long bytesUploaded, long totalBytes);
    }
    
    private static final String TAG = Swift.class.getSimpleName();
    private static final int BUFFER_SIZE = 8192;

    private String mApiUrl;
    private String mUsername;
    private String mApiKey;
    private String mStorageUrl;
    private String mToken;
    
    public Swift(String apiUrl, String username, String apiKey) {
        mApiUrl = apiUrl;
        mUsername = username;
        mApiKey = apiKey;
    }
    
    public boolean uploadFile(String container, String name, File file, 
            UploadListener listener) {
        try {
            boolean reauthenticate = true;
            if (TextUtils.isEmpty(mToken)) {
                reauthenticate = false;
                authenticate();
            }
            
            return uploadFile(container, name, file, reauthenticate, listener);
        } catch (ApiException e) {
            Log.e(TAG, e.getMessage());
            return false;
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }
    
    private boolean uploadFile(String container, String name, File file, boolean reauthenticate, 
            UploadListener listener) throws ApiException, IOException {
        Log.i(TAG, "uploading file: " + name);
        try {
            return put(container, name, file, listener);
        } catch (UnauthorizedException e) {
            if (reauthenticate) {
                authenticate();
                return uploadFile(container, name, file, false, listener);
            }
            return false;
        }
    }
    
    private boolean put(String container, String name, File file, UploadListener listener) 
            throws UnauthorizedException, IOException {
        InputStream in = null;
        OutputStream out = null;
        HttpURLConnection conn = null; 
        
        try {
            URL url = new URL(mStorageUrl + "/" + container + "/" + name);
            conn = (HttpURLConnection)url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("PUT");
            conn.setRequestProperty(Header.AUTH_TOKEN, mToken);
            conn.setRequestProperty(Header.ETAG, FileUtil.getMD5Checksum(file));
            
            in = new BufferedInputStream(new FileInputStream(file));
            out = new BufferedOutputStream(conn.getOutputStream());
            
            final long totalBytes = file.length();
            long bytesWritten = 0;
            
            byte[] b = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(b)) != -1) {
                out.write(b, 0, read);
                bytesWritten += read;
                if (listener != null) {
                    listener.uploadProgress(bytesWritten, totalBytes);
                }
            }
            out.flush();
            
            int status = 0;
            try {
                status = conn.getResponseCode();
            } catch (IOException e) {
                // HttpUrlConnection will throw an IOException if any 4XX
                // response is sent. If we request the status again, this
                // time the internal status will be properly set, and we'll be
                // able to retrieve it. What mastermind designed this?
                status = conn.getResponseCode();
            }
            if (status == HttpStatus.SC_UNAUTHORIZED) {
                throw new UnauthorizedException("401 - Unauthorized");
            } else if (status != HttpStatus.SC_CREATED) {
                throw new ApiException("Status Code: " + status + 
                        ". Expected: 201 - Created");
            }
            
            return true;
        } finally {
            if (conn != null) conn.disconnect();
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {}
            }
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {}
            }
        }
    }
    
    /**
     * Token based authentication.
     * TODO: cache the token & storage url
     * 
     * @throws ApiException
     */
    private void authenticate() throws ApiException {
        AndroidHttpClient httpClient = new AndroidHttpClient(mApiUrl);
        httpClient.addHeader(Header.AUTH_USER, mUsername);
        httpClient.addHeader(Header.AUTH_KEY, mApiKey);
        
        HttpResponse response = httpClient.get(Api.AUTH, null);
        
        if (response != null && response.getStatus() == HttpStatus.SC_OK) {
            mToken = getHeader(response.getHeaders(), Header.AUTH_TOKEN);
            mStorageUrl = getHeader(response.getHeaders(), Header.STORAGE_URL);
        } else {
            StringBuilder message = new StringBuilder("Could not authenticate with Swift. ");
            if (response != null) {
                message.append("Status Code: ").append(response.getStatus());
            }
            else {
                message.append("HttpResponse is null");
            }
            throw new ApiException(message.toString());
        }
    }
    
    private String getHeader(Map<String, List<String>> headers, String name) {
        if (headers != null) {
            List<String> headerValues = headers.get(name);
            if (headerValues == null) {
                // Issue #13 -
                // https://github.com/akvo/akvo-flow-mobile/issues/13
                // Prior to Gingerbread, HttpUrlConnection converted
                // all response headers to lower case. This is a workaround
                // to ensure we cover those situations as well
                final String lowercaseName = name.toLowerCase(Locale.ENGLISH);
                headerValues = headers.get(lowercaseName);
            }

            if (headerValues != null && headerValues.size() > 0) {
                return headerValues.get(0);
            }
        }

        return null;
    }
    
    @SuppressWarnings("serial")
    class ApiException extends RuntimeException {
        public ApiException(String message) {
            super(message);
        }
    }
    
    @SuppressWarnings("serial")
    class UnauthorizedException extends ApiException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
    
    interface Api {
        static final String AUTH = "/auth/v1.0";
    }
    
    interface Header {
        static final String AUTH_USER   = "X-Auth-User";
        static final String AUTH_KEY    = "X-Auth-Key";
        static final String AUTH_TOKEN  = "X-Auth-Token";
        static final String STORAGE_URL = "X-Storage-Url";
        static final String ETAG        = "ETag";
    }
}
