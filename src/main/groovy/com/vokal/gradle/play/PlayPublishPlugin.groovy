package com.vokal.gradle.play

import com.android.builder.core.DefaultProductFlavor
import com.google.api.client.http.AbstractInputStreamContent
import com.google.api.client.http.FileContent
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisher.Edits
import com.google.api.services.androidpublisher.AndroidPublisher.Edits.Apks.Upload
import com.google.api.services.androidpublisher.AndroidPublisher.Edits.Commit
import com.google.api.services.androidpublisher.AndroidPublisher.Edits.Insert
import com.google.api.services.androidpublisher.AndroidPublisher.Edits.Tracks.Update
import com.google.api.services.androidpublisher.model.Apk
import com.google.api.services.androidpublisher.model.AppEdit
import com.google.api.services.androidpublisher.model.Track
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

public class PublishConfig {
    String applicationName
    String publishVariant
    String publishTrack
    String serviceEmail
    String serviceKey
}

class PlayPublishPlugin implements Plugin<Project> {

    private static final String TRACK_ALPHA = "alpha";

    Project project;
    DefaultProductFlavor flavor;
    PublishConfig playConfig;
    File output;

    String getName() {
        return "play"
    }

    void apply(Project project) {
        this.project = project

        project.extensions.create("play", PublishConfig);

        project.task('playInfo') << {
            printInfo()
        }

        def hasApp = project.hasProperty('android')
        def hasPlayConfig = project.hasProperty('play')
        if (!hasApp || !hasPlayConfig) {
            return
        }

        playConfig = project.play

        project.afterEvaluate {
            if (!checkConfig()) {
                return
            }

            project.android['applicationVariants'].all { variant ->
                if (!variant.buildType.debuggable && variant.name == playConfig.publishVariant) {
                    configureVariant(variant)
                }
            }
        }
    }

    void printInfo() {
        if (flavor != null) {
            println "\nPlay publishing config:\n"
            println "App Name: ${playConfig.applicationName}"
            println " Package: ${flavor.applicationId}"
            println "  Output: ${output}\n"
        } else {
            println "\nNo Play publishing configured.\n"
        }
    }

    boolean checkConfig() {
        boolean isConfigOK = true

        println "${playConfig.applicationName}"

        String name = playConfig.applicationName
        if (name == null || name.isEmpty()) {
            println "ERROR: play.applicationName required! Suggested format: 'Company-AppName/1.0'"
            isConfigOK = false
        }

        String track = playConfig.publishTrack
        if (track != null && !track.isEmpty()) {
            if (!"production".equals(track) && !"beta".equals(track) && !"alpha".equals(track)) {
                println 'ERROR: play.publishTrack must be one of [production, beta, alpha] (default: alpha)'
                isConfigOK = false
            }
        } else {
            playConfig.publishTrack = TRACK_ALPHA
        }

        String email = playConfig.serviceEmail
        String key = playConfig.serviceKey
        if (email == null || email.isEmpty()) {
            println 'ERROR: play.serviceEmail required!'
            isConfigOK = false
        }
        if (key == null || key.isEmpty()) {
            println 'ERROR: play.serviceKey required!'
            isConfigOK = false
        } else {
            File keyFile = new File(key);
            if (!keyFile.exists()) {
                println 'ERROR: Could not find key file: ' + key
                isConfigOK = false
            }
        }

        return isConfigOK
    }

    void configureVariant(def variant) {
        if (variant.name == playConfig.publishVariant) {
            flavor = variant.mergedFlavor
            output = variant.outputFile

            if (playConfig.applicationName == null) {
                playConfig.applicationName = "${flavor.applicationId}/${flavor.versionName}"
            }

            String taskSuffix = variant.name.capitalize()
            Task task = project.task('playPublish' + taskSuffix) << {
                printInfo()
                publishApk()
            }
            task.dependsOn('assemble' + taskSuffix)
        }
    }

    void publishApk() {
        try {
            // Create the API service.
            AndroidPublisher service = AndroidPublisherHelper.init(playConfig)
            final Edits edits = service.edits();

            // Create a new edit to make changes to your listing.
            Insert editRequest = edits.insert(flavor.applicationId, null);
            AppEdit edit = editRequest.execute();
            final String editId = edit.getId();
            println "App edit created for: ${playConfig.applicationName}"

            // Upload new apk to developer console
            final AbstractInputStreamContent apkFile = new FileContent(AndroidPublisherHelper.MIME_TYPE_APK, output)
            Upload uploadRequest = edits.apks().upload(flavor.applicationId, editId, apkFile)
            Apk apk = uploadRequest.execute();
            println "Version code ${apk.getVersionCode()} has been uploaded"

            // Assign apk to alpha track.
            List<Integer> apkVersionCodes = new ArrayList<>();
            apkVersionCodes.add(apk.getVersionCode());
            Update updateTrackRequest = edits.tracks()
                    .update(flavor.applicationId, editId, playConfig.publishTrack,
                    new Track().setVersionCodes(apkVersionCodes));
            Track updatedTrack = updateTrackRequest.execute();
            println "Track ${updatedTrack.getTrack()} has been updated."

            // Commit changes for edit.
            Commit commitRequest = edits.commit(flavor.applicationId, editId);
            AppEdit appEdit = commitRequest.execute();
            println "App edit committed for: ${playConfig.applicationName} (${appEdit.getId()})"

        } catch (ex) {
            println "Exception was thrown while uploading apk to alpha track: " + ex
            ex.printStackTrace()
        }
    }
}
