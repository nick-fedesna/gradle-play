#Gradle Play

Gradle plugin for publishing Android applications using the Google Play Publisher API

## Building

- clone repository
- execute `./gradlew assemble`
- copy __build/libs/play-0.1.2-SNAPSHOT.jar__ *(eg. libraries/ subfolder below)*

## Usage

### build.gradle
````
buildscript {
    dependencies {
        classpath 'com.android.tools.build:gradle:1.0.0'
        ...
        
        classpath files('libraries/gradle-play-0.1.2-SNAPSHOT.jar')
        classpath 'com.google.apis:google-api-services-androidpublisher:v2-rev2-1.19.0'
        classpath 'com.google.api-client:google-api-client-gson:1.19.0'
        classpath 'com.google.oauth-client:google-oauth-client-java6:1.19.0'
        classpath 'com.google.guava:guava:15.0'
    }
}

apply plugin: 'com.android.application'
apply plugin: 'io.vokal.gradle.play'

android {
    ...
}

play {
    applicationName "Company-AppName/1.0"
    publishVariants 'freeRelease', 'paidRelease'
    publishTrack 'alpha' // can be [production, beta, alpha], defaults to 'alpha'
    serviceEmail '' // add service email
    serviceKey '' // add path to .p12 file
}
````

You can also specify just the suffix, which will build all flavors for that build type:

````
play {
    ...
    publishVariants 'release'
    ...
}
````
(eg. might build _freeX86Release, freeArmRelease, paidX86Release, paidArmRelease_ variants)

### gradle tasks
````
playInfo - displays publishing info grouped by application
playPublish - publishes all configured applications
````
The plugin will group apps by package and upload all apks for that app together.
If there is only one APK for an application, a variant specific task will be created.