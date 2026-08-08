# Parlor deliberately relies on generated serializers and direct Koin
# definitions, so release shrinking does not need blanket class retention.
# Dependency-specific consumer rules are merged automatically by AGP.
#
# Keep annotation and nesting metadata used by kotlinx.serialization and by
# crash symbolication without retaining application implementation classes.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault
-keepattributes InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
