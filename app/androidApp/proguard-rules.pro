# R8 rules for Squadify's (clubs) release build (TECHNICAL_DECISIONS.md §11.7).
#
# The libraries the app uses ship their own consumer rules (kotlinx.serialization for the @Serializable
# models and the Navigation 3 screens, Ktor, Koin, Firebase, Play Billing, Compose), so nothing of ours
# needs keeping by name. Add a rule here only for something R8 removed that a test build showed missing.

# Readable crash reports: Crashlytics maps the obfuscated names back with the mapping file the
# Crashlytics Gradle plugin uploads; it still needs the line numbers and source file to point at.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
