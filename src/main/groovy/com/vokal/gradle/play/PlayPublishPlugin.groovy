package com.vokal.gradle.play

import com.android.builder.core.DefaultBuildType
import com.android.builder.core.DefaultProductFlavor
import com.android.builder.model.BuildType
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
        String name;
        String version;

        File output;
        def flavor;
    }

    Project       project;
    PublishConfig playConfig;

    HashMap<String, List<AppVariant>> appVariants = new HashMap<>()

    String getName() {
        return "io.vokal.gradle.play"
    }

    void apply(Project project) {
        this.project = project

        project.extensions.create("play", PublishConfig);

        project.task('playInfo') << {
            printInfo(null)
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

            playConfig.publishVariants.each { publishVariant ->
                String capVariant = publishVariant.capitalize()
                project.android['applicationVariants'].all { variant ->
                    if (variant.name == publishVariant || variant.name.endsWith(capVariant)) {
                        if (!variant.buildType.debuggable) {
                            configureVariant(variant)
                        }
                    }
                }
            }

            if (appVariants.size() > 0) {
                Task allTask = project.task('playPublish') << {
                    for (String appId : appVariants.keySet()) {
                        printInfo(appId)
                        publishApk(appId)
                    }
                }

                for (Map.Entry<String, List<AppVariant>> app : appVariants.entrySet()) {
                    String appId = app.key
                    List<AppVariant> variants = app.value
                    boolean single = variants.size() == 1
                    boolean multi = variants.size() > 1

                    List<String> dependsOnSuffixes = new ArrayList<>()
                    variants.each { v ->
                        dependsOnSuffixes.add(v.name.capitalize())
                    }

                    String taskSuffix = ""
                    if (single) {
                        taskSuffix = dependsOnSuffixes.get(0)
                    } else if (multi) {
                        app.key.split("\\.").each { part ->
                            taskSuffix += part.capitalize()
                        }
//                        TODO: find common parts and use those for suffix
//                        dependsOnSuffixes.each { d ->
//                            def p = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, d).split("_")
//                        }
                    }
                    if (!taskSuffix.empty) {
                        Task task = project.task("playPublish${taskSuffix}") << {
                            printInfo(appId)
                            publishApk(appId)
                        }

                        dependsOnSuffixes.each { variant ->
                            task.dependsOn('assemble' + variant)
                        }

                        allTask.dependsOn(task)
                    }
                }
            }
        }
    }

    void printInfo(String appId) {
        if (appVariants.size() > 0) {
            println "\nPlay publishing: ${playConfig.applicationName}"

            if (appId == null) {
                for (Map.Entry<String, List<AppVariant>> app : appVariants.entrySet()) {
                    printVariants(app.key, app.value)
                }
            } else {
                printVariants(appId, appVariants.get(appId))
            }
        } else {
            println "\nNo Play publishing configured.\n"
        }
    }

    void printVariants(String appId, List<AppVariant> variants) {
        println "\n Package: ${appId}"
        for (AppVariant variant : variants) {
            println "  (${variant.flavor.versionCode}) '${variant.version}' ${variant.name} -> ${variant.output.name}"
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
        BuildType buildType = variant.buildType
        DefaultProductFlavor flavor = variant.mergedFlavor
        String appId = flavor.applicationId
        if (buildType.applicationIdSuffix != null)
            appId += buildType.applicationIdSuffix

        AppVariant pub = new AppVariant();
        pub.name = variant.name
        pub.flavor = flavor
        pub.output = variant.outputs[0].outputFile
        pub.version = flavor.versionName
        if (buildType.versionNameSuffix != null)
            pub.version += buildType.versionNameSuffix

        if (appVariants.containsKey(appId)) {
            appVariants.get(appId).add(pub)
        } else {
            List<AppVariant> list = new ArrayList<AppVariant>()
            list.add(pub)
            appVariants.put(appId, list)
        }

    }

    void publishApk(String appId) {
        try {
            // Create the API service.
            AndroidPublisher service = AndroidPublisherHelper.init(playConfig)
            final Edits edits = service.edits();

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
        } catch (ex) {
            println "Error uploading apk to '${playConfig.publishTrack}' track: " + ex
            ex.printStackTrace()
        }
    }
}
