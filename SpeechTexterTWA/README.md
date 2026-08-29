# تایپ صوتی — SpeechTexter TWA

یک پوسته اندروید سبک برای اجرای `https://www.speechtexter.com/` با Trusted Web Activity و موتور مرورگر به‌روز دستگاه.

## مشخصات

- Android 8.0 به بالا (`minSdk 26`)
- `compileSdk/targetSdk 36`
- Android Browser Helper `2.7.3`
- فقط HTTPS؛ ترافیک Cleartext غیرفعال است
- بدون مجوز گسترده فایل و بدون دسترسی مستقیم میزبان به متن یا میکروفون
- میکروفون و دانلودها توسط مرورگر کاربر مدیریت می‌شوند
- پشتیبانی از تم روشن/تیره نوارهای سیستم و Splash کم‌حجم
- ساخت Release با R8 و حذف منابع بلااستفاده

## نکته مهم درباره TWA

SpeechTexter یک PWA با Web App Manifest است، اما مالک این پروژه به سرور آن دامنه دسترسی ندارد. برای حذف کامل نوار مرورگر، مالک `speechtexter.com` باید فایل Digital Asset Links را در مسیر زیر منتشر کند و اثرانگشت گواهی این APK را در آن قرار دهد:

`https://www.speechtexter.com/.well-known/assetlinks.json`

تا پیش از انجام این مرحله توسط مالک سایت، Android Browser Helper به‌طور امن به Custom Tab برمی‌گردد. این انتخاب عمدی است، چون موتور Web Speech در Chrome اجرا می‌شود و جایگزینی آن با WebView می‌تواند دکمه میکروفون را از کار بیندازد.

## ساخت محلی

```bash
./gradlew clean check lintRelease assembleDebug assembleRelease
```

APK دیباگ در مسیر زیر ساخته می‌شود:

`app/build/outputs/apk/debug/app-debug.apk`

نسخه Release ابتدا بدون امضا تولید می‌شود و باید با کلید مالک برنامه امضا شود.

## حریم خصوصی و مالکیت

این پروژه مستقل است و وابستگی یا نمایندگی رسمی از SpeechTexter ندارد. متن، صدا، کوکی‌ها، Local Storage، دانلودها و سیاست حریم خصوصی در محیط مرورگر و طبق قوانین خود وب‌سایت مدیریت می‌شوند. برای انتشار عمومی یا فروشگاهی باید اجازه استفاده از نام/محتوا و مالکیت یا اختیار دامنه بررسی شود.
