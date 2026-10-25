# OkHttp 平台检测
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WorkManager / Room：Room 生成的 *._Impl 由反射实例化，不能裁
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# kotlinx.serialization：数据层（DTO/序列化器/信封解析）整体保留，避免泛型序列化器被裁
-keep class top.saltfish.ech0.data.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class top.saltfish.ech0.**$$serializer { *; }
-keepclassmembers class top.saltfish.ech0.** { *** Companion; }
-keepclasseswithmembers class top.saltfish.ech0.** { kotlinx.serialization.KSerializer serializer(...); }
