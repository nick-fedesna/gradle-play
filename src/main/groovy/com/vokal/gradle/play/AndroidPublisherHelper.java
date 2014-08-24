package com.vokal.gradle.play;

/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.repackaged.com.google.common.base.Preconditions;
import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.api.services.androidpublisher.AndroidPublisher;

import static com.google.api.services.androidpublisher.AndroidPublisherScopes.ANDROIDPUBLISHER;

/**
 * Helper class to initialize the publisher APIs client library.
 * <p>
 * Before making any calls to the API through the client library you need to
 * call the {@link AndroidPublisherHelper#init(PublishConfig)} method.
 * This will run all precondition checks for and authorize this client against the API.
 * </p>
 */
public class AndroidPublisherHelper {

    static final         String             MIME_TYPE_APK = "application/vnd.android.package-archive";
    static final         Collection<String> PUBLISH_SCOPE = Collections.singleton(ANDROIDPUBLISHER);
    private static final JsonFactory        JSON_FACTORY  = GsonFactory.getDefaultInstance();
    private static HttpTransport HTTP_TRANSPORT;

    /**
     * Performs all necessary setup steps for running requests against the API.
     *
     * @param playConfig the publishing config {@link PublishConfig}
     *
     * @return the {@link AndroidPublisher} service
     * @throws GeneralSecurityException
     * @throws IOException
     */
    protected static AndroidPublisher init(PublishConfig playConfig) throws IOException, GeneralSecurityException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(playConfig.getApplicationName()),
                                    "Play config applicationName cannot be null or empty! " +
                                            "(suggested format 'Company-App/1.0'");

        // Authorization.
        Credential credential = null;
        String service = playConfig.getServiceEmail();
        if (service != null && !service.isEmpty()) {
            newTrustedTransport();
            credential = authorizeWithServiceAccount(service, playConfig.getServiceKey());
        } else {
            throw new IllegalStateException("Play configuration must specify service email!");
        }

        // Set up and return API client.
        return new AndroidPublisher.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(playConfig.getApplicationName())
                .build();
    }

    private static Credential authorizeWithServiceAccount(String serviceAccountEmail,
                                                          String serviceKeyFilePath)
            throws IOException, GeneralSecurityException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceKeyFilePath),
                                    "Play config serviceKey cannot be null or empty!");

        File key = new File(serviceKeyFilePath);
        if (!key.exists()) {
            throw new FileNotFoundException("Could not find key file: " + serviceKeyFilePath);
        }

        // Build service account credential.
        return new GoogleCredential.Builder()
                .setTransport(HTTP_TRANSPORT)
                .setJsonFactory(JSON_FACTORY)
                .setServiceAccountId(serviceAccountEmail)
                .setServiceAccountScopes(PUBLISH_SCOPE)
                .setServiceAccountPrivateKeyFromP12File(key)
                .build();
    }

    private static void newTrustedTransport() throws GeneralSecurityException, IOException {
        if (null == HTTP_TRANSPORT) {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        }
    }

}