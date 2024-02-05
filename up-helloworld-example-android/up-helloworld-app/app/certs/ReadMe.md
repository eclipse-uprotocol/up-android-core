=== Signing Keys ===

This folder contains key stores. The Android make system utilizes pk8 and pem files to sign apps. When using Gradle, we need to use a Android Keystore instead of the key/certificate. This folder contains keystores that have the Key/Certificate embedded into them.

== Generated Key Stores ==

This folder contains pre-generated keystores.

- device-platform.keystore
 * Android Key: platform
 * Key Alias: platform
 * Key Password: android
 * Store Password: android

== Gradle Example ==

To add a key store to gradle you would do the following:"
```
android {
    ...

    signingConfigs {
        device {
            keyAlias 'platform'
            keyPassword 'android'
            storePassword 'android'
            storeFile file(<path to key store>)
        }
    }

    buildTypes {
        debug {
            signingConfig signingConfigs.device
        }
    }

    ...
}
```




== Creating a Key Store ==

NOTE: You will need to have the pk8 and x509.pem file. For development we use the AOSP defaults which are located at:
<project root>/build/target/product/security/



1. Convert the PK8 File to a PEM Key
 * openssl pkcs8 -inform DER -nocrypt -in "<path to pk8 file>" -out "<output path of PEM key>"

1. Bundle the CERT and PEM Key together
 * openssl pkcs12 -export -in "<path to the cert file>" -inkey "<path to generated PEM Key>" -out "<path to output p12>" -password pass:"<pass phrase>" -name "<alias>"

1. Print your cewrt
 * openssl x509 -noout -fingerprint -in "<path to cert file>"

1.  Import the P12 into a Keystore for Gradle
 * keytool -importkeystore -deststorepass "<pass phrase>" -destkeystore "<output path for keystore>" -srckeystore "<path to p12>" -srcstoretype PKCS12 -srcstorepass "<pass phrase>"

The above will generate a keystore you can use.






