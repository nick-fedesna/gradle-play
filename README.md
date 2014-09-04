###Gradle Play
===
Gradle plugin for publishing Android applications using the Google Play Publisher API

```
buildscript {
    dependencies {
        classpath files('libraries/gradle-play-0.1.1-SNAPSHOT.jar')
        classpath 'com.google.apis:google-api-services-androidpublisher:v2-rev2-1.19.0'
        classpath 'com.google.api-client:google-api-client-gson:1.19.0'
        classpath 'com.google.oauth-client:google-oauth-client-java6:1.19.0'
        classpath 'com.google.guava:guava:15.0'
    }
}

apply plugin: 'play'

android {
    ...
}

play {
    applicationName "Company-AppName/1.0"
    publishVariant 'flavorOneRelease', 'flavorTwoRelease', 'flavorThreeRelease'
    publishTrack 'alpha' // can be [production, beta, alpha], defaults to 'alpha'
    serviceEmail '' // add service email
    serviceKey '' // add path to .p12 file
}
```