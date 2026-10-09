# kotlinx.serialization: keep generated serializers for the API models
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.bigshelf.app.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.bigshelf.app.data.**$$serializer { *; }
