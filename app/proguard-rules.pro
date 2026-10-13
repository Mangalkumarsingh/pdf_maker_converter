# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# -------------------------------------------------------------
# Apache POI & XMLBeans
# -------------------------------------------------------------
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.etsi.uri.**
-dontwarn org.w3c.dom.**
-dontwarn java.awt.**
-dontwarn javax.xml.stream.**
-dontwarn net.sf.saxon.**
-dontwarn org.apache.batik.**
-dontwarn org.osgi.framework.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.commons.codec.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.math3.**

-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep interface org.apache.poi.** { *; }
-keep interface org.apache.xmlbeans.** { *; }

# -------------------------------------------------------------
# PDFBox Android
# -------------------------------------------------------------
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
-keep class com.tom_roush.pdfbox.** { *; }

# -------------------------------------------------------------
# ML Kit Text Recognition
# -------------------------------------------------------------
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# -------------------------------------------------------------
# Coil & uCrop
# -------------------------------------------------------------
-dontwarn coil.**
-keep class com.yalantis.ucrop.** { *; }

# -------------------------------------------------------------
# View Binding
# -------------------------------------------------------------
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
    public static * bind(...);
}

# -------------------------------------------------------------
# Models
# -------------------------------------------------------------
-keep class com.myPage.pdfGenerator.model.** { *; }