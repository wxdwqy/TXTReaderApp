# ProGuard rules for TXTReaderApp
-keepattributes JavascriptInterface
-keepclassmembers class com.yunshu.txtreader.FileBridge {
    @android.webkit.JavascriptInterface <methods>;
}
