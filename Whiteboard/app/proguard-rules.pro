-target 1.7
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

#-dump bin/class_files.txt
#-printseeds bin/seeds.txt
#-printusage bin/unused.txt
#-printmapping bin/mapping.txt

# These are the top level attributes that are kept when proguard runs.
# SourceFile - keeps the file to help create obfuscated stack traces
# LineNumberTable - keeps the line numbers for the obfuscated stack traces
# InnerClasses - used by compiler to find classes referenced in a complied lib via reflection
# EnclosingMethod - Specifies the method in which the class was defined, used for compile.
# Signature - Keep the generic signature of a class, field or method.
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature

#Duplicate library definition notes:
-dontnote android.net.http.*
-dontnote org.apache.commons.codec.**
-dontnote org.apache.http.**
-dontnote com.google.android.gms.**
-dontnote com.google.common.cache.**
-dontnote com.google.appengine.api.**
-dontnote com.google.apphosting.api.**
-dontnote com.google.firebase.**

-dontwarn com.google.common.**
-dontnote com.google.common.util.concurrent.**
-dontwarn com.google.errorprone.**