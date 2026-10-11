# Keep line numbers so crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The math engine is pure Kotlin and reflection-free, so default rules are enough.
# If minification is enabled later, keep the engine entry points safe:
-keep class com.vikashtechnology.mcalculator.engine.** { *; }
