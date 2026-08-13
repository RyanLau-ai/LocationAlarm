# 高德地图 SDK ProGuard 规则（release 混淆时需要）
-keep class com.amap.api.**{*;}
-keep class com.autonavi.**{*;}
-keep class com.loc.**{*;}
-keep class com.amap.api.location.AMapLocationClient { *; }
-keep class com.amap.api.location.AMapLocationClientOption { *; }
-keep class com.amap.api.location.AMapLocationListener { *; }
-keep class com.amap.api.location.AMapLocation { *; }

# 高德 SDK 依赖的 okhttp 等三方库
-dontwarn okhttp3.**
-dontwarn okio.**

# 保留实体类
-keep class com.example.locationalarm.data.** { *; }
