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

import static com.vokal.gradle.play.AndroidPublisherHelper.MIME_TYPE_APK

public class PublishConfig {
    String applicationName
    String[] publishVariants
    String publishTrack
    String serviceEmail
    String serviceKey
}

class PlayPublishPlugin implements Plugin<Project> {

    private static final String TRACK_ALPHA = "alpha";

    private static class AppVariant {
        DefaultProductFlavor flavor;
        File output;
    }

    Project       project;
    PublishConfig playConfig;

    HashMap<String, List<AppVariant>> appVariants = new HashMap<>()

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
                if (playConfig.publishVariants.contains(variant.name)) {
                    if (!variant.buildType.debuggable) {
                        configureVariant(variant)
                    } else {
                        println "Cannot publish debug variant: ${variant.name}"
                    }
                }
            }
        }
    }

    void printInfo() {
        if (appVariants.size() > 0) {
            println "\nPlay publishing: ${playConfig.applicationName}"

            for (Map.Entry<String, List<AppVariant>> app : appVariants.entrySet()) {
                println "\n Package: ${app.key}"
                for (AppVariant variant : app.value) {
                    println " Variant: [${variant.flavor.versionCode}]${variant.flavor.name} - ${variant.output.path}"
                }
            }
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

        if (playConfig.publishVariants == null) {
            println "ERROR: play.publishVariants not specified!"
        }

        return isConfigOK
    }

    void configureVariant(def variant) {
        DefaultProductFlavor flavor = variant.mergedFlavor
        String appId = flavor.applicationId

        AppVariant pub = new AppVariant();
        if (appVariants.containsKey(appId)) {
            appVariants.get(appId).add(pub)
        } else {
            List<AppVariant> list = new ArrayList<>()
            list.add(pub)
            appVariants.put(appId, list)
//           publishVariants.put(appId, Lists.asList(pub))
        }

        pub.flavor = variant.mergedFlavor
        pub.output = variant.outputFile

        String taskSuffix = variant.name.capitalize()
        Task task = project.task('playPublish' + taskSuffix) << {
            printInfo()
            publishApk()
        }
        task.dependsOn('assemble' + taskSuffix)
    }

    void publishApk() {
        try {
            // Create the API service.
            AndroidPublisher service = AndroidPublisherHelper.init(playConfig)
            final Edits edits = service.edits();

            for (String appId : appVariants.keySet()) {
                // Create a new edit to make changes to this application.
                Insert editRequest = edits.insert(appId, null);
                AppEdit edit = editRequest.execute();
                final String editId = edit.getId();
                println "App edit created for: ${playConfig.applicationName}"

                List<Integer> apkVersionCodes = new ArrayList<>();
                for (AppVariant variant : appVariants.get(appId)) {
                    // Upload new apk to developer console
                    final AbstractInputStreamContent apkFile = new FileContent(MIME_TYPE_APK, variant.output)
                    Upload uploadRequest = edits.apks().upload(appId, editId, apkFile)
                    Apk apk = uploadRequest.execute();
                    apkVersionCodes.add(apk.getVersionCode());
                    println "Version code ${apk.getVersionCode()} has been uploaded"
                }

                // Assign apk(s) to publish track.
                Update updateTrackRequest = edits.tracks()
                        .update(appId, editId, playConfig.publishTrack,
                                new Track().setVersionCodes(apkVersionCodes));
                Track updatedTrack = updateTrackRequest.execute();
                println "Track ${updatedTrack.getTrack()} has been updated."

                // Commit changes for edit.
                Commit commitRequest = edits.commit(appId, editId);
                AppEdit appEdit = commitRequest.execute();
                println "App edit committed for: ${playConfig.applicationName} (${appEdit.getId()})"
            }
        } catch (ex) {
            println "Exception was thrown while uploading apk to '${playConfig.publishTrack}' track: " + ex
            ex.printStackTrace()
        }
    }
}
