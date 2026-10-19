CITY DINER - Android app project (builds a real APK online, no Android Studio needed)

A) Firebase: already filled in (project citydiner-cefcb). Make sure Authentication users and Firestore rules (firestore.rules) are set up in the Firebase console.

B) Get the APK
1. Create a free GitHub account, make a new repository, upload ALL files of this folder (keep .github folder).
2. Open the Actions tab > "Build APK" > it runs by itself (about 4-5 minutes).
3. Open the repository > Releases > "CityDiner APK (latest)" > download CityDiner.apk.
4. Send CityDiner.apk to any Android phone (WhatsApp/Drive) > open > allow "Install unknown apps" > Install.

The app has no Firebase code inside the APK: login and sync run through the web part, so changing the menu or
Firebase config only needs a new build (push a change and Actions rebuilds).
