# Keep Dhizuku
-keep class com.rosan.dhizuku.** { *; }
-keep class rikka.shizuku.** { *; }

# Keep AIDL
-keep class com.kovak.kamal.IRemoteService { *; }
-keep class com.kovak.kamal.IRemoteService$* { *; }
-keep class com.kovak.kamal.RemoteService { *; }
-keep class com.kovak.kamal.DhizukuAdmin { *; }

# Keep data classes
-keep class com.kovak.kamal.FileItem { *; }
