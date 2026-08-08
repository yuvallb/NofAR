# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <methods>;
}
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# WorkManager + Hilt workers
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# Keep native methods and Parcelables used by location/sensors
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
