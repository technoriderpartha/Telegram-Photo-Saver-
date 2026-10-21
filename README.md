# Telegram Photo Saver

An Android app that logs in with **your own Telegram account** and downloads photos from the channels you
follow into your gallery (`Pictures/TelegramPhotos/<channel>/`). The APK is built entirely by GitHub
Actions, so no Android Studio is needed.

It uses **TDLib**, Telegram's official client library. The workflow compiles TDLib from Telegram's own
source code (`github.com/tdlib/td`) using Telegram's Docker recipe, so no third-party binaries are involved.

## Get the APK

1. Upload this repository to GitHub (keep the folder structure, including the hidden `.github` folder).
2. Open the **Actions** tab. The **Build APK** workflow starts automatically (or use **Run workflow**).
3. **The first run takes 30 to 60+ minutes** because it compiles TDLib for four CPU types. The result is
   cached, so later runs take only a few minutes.
4. Download the **TelegramPhotoSaver-debug-apk** artifact from the finished run, unzip it, and install
   `app-debug.apk` on your phone (allow "install unknown apps" when asked).

## Before first use: get a Telegram API ID

Telegram requires every app to have its own API ID and hash (free, one minute):

1. Open https://my.telegram.org and log in with your phone number.
2. Choose **API development tools** and create an app (any name).
3. Copy the **api_id** (a number) and **api_hash** (a long string).

## Using the app

1. First launch: enter the API ID and API hash, then your phone number, the login code Telegram sends
   to your Telegram app (or SMS), and your two-step verification password if you have one.
   The login stays saved on the phone.
2. Pick a channel from the list of channels you follow (use the search box to filter).
3. Photos load newest first. Tap **Load older** for more. Tap photos to select them, or select none and
   tap **Download all loaded**.
4. Photos land in your gallery. Photos saved earlier are skipped.

Keep the app open while downloading.

## Notes and limits

- Only photos are downloaded (not videos or files). Photos are the full-size versions Telegram stores.
- Only channels are listed, not groups or private chats.
- Your API ID/hash and login session are stored only inside the app on your phone. Log out from the
  channel list screen to remove the session. Do not share the APK build artifacts' data folders.
- Using a user account through the official API is allowed, but Telegram can limit accounts that
  download at extreme volume. Download in moderate batches.

## Project layout

- `.github/workflows/build-apk.yml` builds TDLib (cached), copies it into the app, and builds the APK.
- `app/src/main/java/com/example/tgphotosaver/`:
  `Td.kt` (TDLib wrapper: login, channel list, photo history, file download),
  `LoginActivity`, `ChannelsActivity`, `PhotosActivity`, `PhotoAdapter`, `PhotoSaver` (gallery export).
- TDLib's `Client.java`, `TdApi.java` and `libtdjni.so` are generated during the build and are not
  stored in the repository.
- Requires Android 10 (API 29) or newer. No storage permission is needed.
