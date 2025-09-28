[app]
title = Dashman
version = 1.2.1
android.version_code = 10201
package.name = dashman
package.domain = com.kelev
source.dir = .
source.include_exts = py,kv,png,jpg,ttf,otf,json,md,txt
entrypoint = main.py
requirements = python3,kivy,plyer,pyjnius,android
services = hotword:service_hotword.py
android.permissions = INTERNET, RECORD_AUDIO, WAKE_LOCK, FOREGROUND_SERVICE, VIBRATE,RECEIVE_BOOT_COMPLETED, 
android.minapi = 27
android.add_manifest_xml = manifest_additions.xml
POST_NOTIFICATIONS
android.services = hotword:service_hotword.py
android.foreground_services = audio, mediaPlayback
orientation = portrait
arch = armeabi-v7a, arm64-v8a
icon = assets/icon.png
presplash = assets/splash.png

[buildozer]
log_level = 2
warn_on_root = 1