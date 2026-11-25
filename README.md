[فارسی](#persian) · [English](#english)

<a id="persian"></a>

<div dir="rtl">

# دانلود منیجر Cyron

یک دانلود‌منیجر بومی اندروید که با Kotlin، Jetpack Compose، Room، DataStore، OkHttp و کوروتین‌ها ساخته شده است. دانلودها با درخواست‌های واقعی HTTP(S) انجام می‌شوند؛ این برنامه یک نمونهٔ نمایشی یا پوشش WebView نیست. یادداشت‌های نسخهٔ جاری در [CHANGELOG.md](CHANGELOG.md) آمده است.

## امکانات

- داشبورد بومی، کتابخانهٔ دانلودها، جستجو و فیلتر، جزئیات هر کار و تنظیمات.
- بررسی HTTP(S)، دنبال‌کردن تغییر مسیر، انتقال تأییدشدهٔ بازه‌ای (byte-range)، تعداد اتصال قابل تنظیم بدون هیچ سقف سرعت تحمیلی از سوی برنامه، توقف/لغو و بررسی checksum.
- بررسی یوتیوب و صفحه‌های عمومی رسانه با ماژول پایتونی yt-dlp همراه برنامه، با انتخاب فرمت، ادغام محلی با FFmpeg و تبدیل اختیاری به MP3.
- دانلود مگنت و فایل `.torrent` محلی با یک نشست محلی libtorrent؛ فایل‌های کامل‌شده به پوشهٔ SAF انتخاب‌شدهٔ کاربر صادر می‌شوند.
- ادامهٔ دانلود فقط زمانی انجام می‌شود که نمایش راه‌دور با ETag یا Last-Modified قابل اعتبارسنجی باشد. هر پاسخ بازه‌ای پیش از ادغام بخش‌های خصوصی بررسی می‌شود. اگر این بررسی‌های ایمنی شکست بخورند، Cyron از ادامه یا ادغام موازیِ ناایمن خودداری می‌کند.
- سرویس پیش‌زمینهٔ `dataSync`، اعلان پیشرفت، wake lock هنگام انتقال فعال و تاریخچهٔ محلی در Room.
- انتقال‌های قطع‌شده با backoff نمایی محدود دوباره تلاش می‌کنند و هنگام جابه‌جایی بین Wi-Fi و دادهٔ همراه منتظر شبکه می‌مانند تا کل فایل شکست نخورد. انتقال HTTP، رسانه و تورنت هیچ سقف سرعت قابل تنظیمی از سوی کاربر ندارند؛ سرعت واقعی به منبع، شبکه، دستگاه و تعداد اتصال انتخاب‌شده بستگی دارد.
- عنوان رسانه و فهرست فرمت‌ها از فرادادهٔ واقعی yt-dlp می‌آیند، بنابراین فایل ذخیره‌شده از عنوان صفحه و نوع MIME نگاشت‌شده استفاده می‌کند. جریان‌های فقط‌ویدیو و فقط‌صدا جداگانه دانلود و روی دستگاه با FFmpegKit ادغام می‌شوند، چون نسخهٔ رایگان yt-dlp فایل اجرایی ffmpeg ندارد.
- دسترسی به پوشهٔ مقصد از طریق Storage Access Framework اندروید (SAF)؛ فایل‌های ناقص تا زمان تأیید در حافظهٔ خصوصی برنامه نگه داشته می‌شوند.
- رابط کاربری فارسی با قلم Vazirmatn (SIL OFL 1.1) و متن‌های طبیعی و محصول‌محور. تنظیمات به‌صورت محلی با DataStore ذخیره می‌شوند. هیچ حساب، سرور، تحلیل‌گر یا سرویس ابری لازم نیست.
- حداقل API اندروید ۲۶ (Android 8). Target API ۳۵؛ Compile SDK ۳۶. بیلدهای بومی فعلاً فقط `arm64-v8a` و `x86_64` را شامل می‌شوند.

## ساخت و تست

پیش‌نیازها: JDK 17، Node.js نسخهٔ ۲۰ یا بالاتر برای بررسی سورس عمومی، Android SDK Platform 36 و Android Build Tools، و دسترسی به Google Maven و Maven Central. از Gradle Wrapper موجود در مخزن (9.6) استفاده کن: `./gradlew`. ابتدا `ANDROID_HOME` یا `ANDROID_SDK_ROOT` را تنظیم کن و مجوزهای Android SDK را بپذیر.

```shell
node scripts/check-public.mjs
sh ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

نصب بیلد دیباگ روی دستگاه متصل:

```shell
./gradlew installDebug
```

اجرای تست instrumentation مربوط به Room روی شبیه‌ساز یا دستگاه متصل:

```shell
./gradlew connectedDebugAndroidTest
```

بیلد بر اساس ABI تفکیک می‌شود: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (گوشی‌ها)، `app-x86_64-debug.apk` (شبیه‌سازها) و `app-universal-debug.apk` (هر دو، برای راحتی). APK دیباگ را به‌عنوان نسخهٔ انتشار توزیع نکن. فایل `app/build.gradle.kts` گزینه‌های R8، کوچک‌سازی منابع و تفکیک ABI را فعال می‌کند. امضای Release یا از فایل `keystore.properties` (که در Git نادیده گرفته می‌شود) استفاده می‌کند یا از متغیرهای محیطی `CYRON_KEYSTORE_*` که در [راهنمای انتشار](docs/RELEASE.md) توضیح داده شده‌اند. پیکربندی ناقص امضا باعث شکست بیلد می‌شود؛ اگر هیچ پیکربندی امضایی وجود نداشته باشد، APKهای Release بدون امضا باقی می‌مانند.

## دانلود

1. Cyron Manager را اجرا کن و در صورت تمایل اجازهٔ اعلان بده.
2. از **تنظیمات → تغییر** برای دادن دسترسی پایدار به یک پوشهٔ قابل‌نوشتن با انتخاب‌گر پوشهٔ سیستم اندروید استفاده کن.
3. روی **افزودن** بزن، یک URL عمومی HTTP(S) وارد کن و آن را بررسی کن. Cyron به `Content-Length` مثبت و مشخص نیاز دارد و پاسخ بازه‌ای سرور را پیش از باز کردن اتصال‌های مستقل تأیید می‌کند.
4. به‌صورت اختیاری digest مورد انتظار SHA-256 (۶۴ رقم) را وارد کن. نام فایل و تعداد اتصال را انتخاب کن و انتقال را شروع کن.
5. از منوی عملیات هر کار یا اعلان پیش‌زمینه، انتقال را متوقف، ادامه، لغو یا حذف کن. فقط فایل‌های کامل‌شده را باز و به اشتراک بگذار. لغو از اعلان پیش از پاک‌شدن داده‌های ناقص، یک ضربهٔ دوم می‌خواهد.

سرورهایی که ورود می‌خواهند، جریان با طول نامشخص برمی‌گردانند، درخواست‌های لازم را ممنوع می‌کنند یا فایل را بدون validator پایدار تغییر می‌دهند، ممکن است مناسب نباشند. افزایش سرعت با چند اتصال به سرور مبدأ و شبکه بستگی دارد؛ بعضی سرورها از بازهٔ بایتی پشتیبانی نمی‌کنند. لغو، داده‌های ناقص HTTP را حذف می‌کند؛ توقف یا بازیابی بخش‌های خصوصی را نگه می‌دارد و هنگام ادامه دوباره با validatorهای HTTP اعتبارسنجی می‌کند. دانلود رسانه به ادامه‌دادن yt-dlp وابسته است. جریان توقف/ادامه/حذف رسانهٔ مستقیم روی شبیه‌ساز با یک سرور رسانهٔ محلی آزموده شده است؛ سازگاری یوتیوب با سایت زنده هنوز به تست گسترده‌تر روی دستگاه نیاز دارد. دادهٔ تورنت هنگام انتقال در حافظهٔ خصوصی برنامه نگه داشته و فقط پس از تکمیل صادر می‌شود، اما بازیابی/ادامه پس از مرگ فرآیند هنوز اعتبارسنجی نشده است. قطع‌شدن انتقال با فایل نهاییِ تأییدشده یکی نیست.

## رسانه و تورنت

برای ویدیو، Shorts یا لینک `youtu.be` از حالت **یوتیوب** و برای صفحهٔ عمومی دیگر که نسخهٔ همراه yt-dlp پشتیبانی می‌کند از حالت **رسانه** استفاده کن. ابتدا فرمت‌های موجود را بررسی کن، سپس کیفیت یا گزینهٔ صوتی را انتخاب کن. کیفیت‌های بالا به‌صورت جریان‌های جداگانهٔ ویدیو و صدا دانلود و به‌صورت محلی با FFmpegKit ادغام می‌شوند؛ اگر این ادغام شکست بخورد، Cyron به یک فرمت تک‌فایلی progressive برمی‌گردد. گزینهٔ **صدا · MP3** بیلد همراه FFmpeg را برای تبدیل محلی فراخوانی می‌کند. از این برنامه برای دور زدن DRM، paywall، نیاز به ورود یا کنترل‌های دسترسی سایت استفاده نکن؛ حقوق و شرایط هر منبع را رعایت کن. یکپارچگی فعلی هیچ کوکی مرورگر یا توکن احراز هویتی اضافه نمی‌کند. سازگاری با سایت‌های رسانه ممکن است با تغییر سایت‌ها عوض شود و این قابلیت هنوز روی دستگاه فیزیکی آزموده نشده است.

برای URI مگنت یا فایل `.torrent` انتخاب‌شده از انتخاب‌گر اسناد اندروید از حالت **تورنت** استفاده کن. در دسترس بودن BitTorrent به peerها، سیاست شبکه و منبع بستگی دارد؛ برنامه فعلاً ذخیره‌سازی دادهٔ ادامهٔ مخصوص تورنت یا کنترل‌های seed کامل‌شده ندارد. پوشهٔ خروجی انتخاب‌شده فقط پس از اعلام تکمیل توسط تورنت نوشته می‌شود. فقط تورنت‌هایی را دانلود و به اشتراک بگذار که مجاز به این کار هستی.

وابستگی‌های بومی شخص ثالث حجم APK را قابل‌توجه افزایش می‌دهند و مجوزهای خودشان را دارند. پیش از توزیع مجدد APK، [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)، [SBOM.json](SBOM.json) و [اطلاعات منبع و پیوند مجدد شخص ثالث](docs/THIRD_PARTY_SOURCES.md) را بررسی کن. FFmpegKit تحت LGPLv3 توزیع می‌شود؛ اطلاعیه‌ها، متن مجوزها و مسیر منبع/جایگزینی لازم بخشی از چک‌لیست انتشار هستند. پشتیبانی ABI فعلاً به arm64-v8a و x86_64 محدود است.

## معماری

برای مرزهای ماژول، چرخهٔ عمر انتقال، بازیابی و محدودیت‌های عمدی [راهنمای معماری](docs/ARCHITECTURE.md) را ببین. پیش از تغییر مجوزهای manifest، [مجوزها و دادهٔ محلی](docs/PERMISSIONS.md) را بخوان.

صفحهٔ ایستای `docs/design-mockup/` فقط یک نمونهٔ طراحی است؛ دانلودها را در مرورگر شبیه‌سازی می‌کند و به اپلیکیشن اندروید متصل نیست.

پوشهٔ `domain/` شامل مدل‌های کار/وضعیت/مقدار و قراردادهای مخزن است. `data/` پیاده‌سازی Room و ترجیحات DataStore را دارد. `download/` موتور OkHttp، بررسی، اعتبارسنجی بازه و هماهنگی انتقال را در خود دارد. `storage/` مالک فایل‌های بخش خصوصی و انتشار اتمیک در SAF است. `background/` میزبان سرویس پیش‌زمینهٔ اندروید است. `presentation/` صفحه‌های Compose و ViewModel را دارد. Hilt وابستگی‌های سطح برنامه را متصل می‌کند.

## امنیت و مجوزها

برنامه مجوزهای اینترنت، وضعیت شبکه، سرویس پیش‌زمینه/data-sync، اعلان (اندروید ۱۳ به بعد)، wake-lock و تکمیل بوت را درخواست می‌کند. تکمیل بوت فقط برای حفظ ثبت کار دوره‌ای بازیابی صف استفاده می‌شود و سرویس همیشه‌درحال‌اجرا راه نمی‌اندازد. برنامه دسترسی گسترده به حافظه درخواست نمی‌کند. HTTP برای احترام به لینک‌های واردشده فعال است اما رمزنگاری یا احراز هویت ندارد؛ HTTPS را ترجیح بده. کاربر مقصد را از طریق SAF انتخاب می‌کند. بخش‌های ناقص در پوشهٔ خصوصی و بدون پشتیبان‌گیری برنامه می‌مانند و هرگز پیش از تأیید کامل فایل و digest اختیاری با نام درخواستی منتشر نمی‌شوند. رمزنگاری sandbox اپلیکیشن اندروید، در صورت وجود، توسط دستگاه/سیستم‌عامل ارائه می‌شود؛ Cyron خودش آن فایل‌های موقت را رمزنگاری نمی‌کند. فقط URLهایی را استفاده کن که به آن‌ها اعتماد داری؛ HTTP نمی‌تواند انتقال راه‌دور را احراز هویت یا رمزنگاری کند.

## تست‌ها

پیش از مشارکت، بررسی‌های مورد انتظار را اجرا کن؛ [CONTRIBUTING.md](CONTRIBUTING.md) را ببین. بررسی‌های دستگاه اندروید و چرخهٔ عمر ارائه‌دهندهٔ SAF هنوز پیش از انتشار لازم‌اند.

`app/src/test` شامل تست‌های واحد JVM محلی برای ایمنی نام فایل و ناورداهای بازهٔ بایتی به‌همراه بررسی‌های پروتکل HTTP مبتنی بر MockWebServer است. فایل `.github/workflows/android.yml` ایمنی سورس عمومی را بررسی می‌کند، تغییرات وابستگی را در pull requestها بازبینی می‌کند، تست‌های واحد و Android Lint را اجرا می‌کند، تست instrumentation مربوط به Room را روی شبیه‌ساز Android 35 اجرا می‌کند و یک APK دیباگ آپلود می‌کند. تگ‌های نسخه با استفاده از Secretهای امضای محافظت‌شده، APKهای تأییدشده می‌سازند و یک GitHub Release پیش‌نویس ایجاد می‌کنند. بررسی‌های اضافی ارائه‌دهندهٔ SAF، اعلان، بازیابی پس از کرش و چرخهٔ عمر دستگاه فیزیکی هنوز پیش از انتشار نسخه لازم‌اند.

## امنیت

آسیب‌پذیری‌ها را به‌صورت خصوصی از طریق GitHub Security Advisories و مطابق [SECURITY.md](SECURITY.md) گزارش کن. اعتبارنامه‌ها، URLهای خصوصی، کلیدهای امضا یا فایل‌های شخصی را در issueهای عمومی قرار نده.

## مجوز

کد منبع Cyron Manager تحت Apache-2.0 است؛ [LICENSE](LICENSE) را ببین. وابستگی‌های همراه، شرایط خودشان را حفظ می‌کنند. پیش از توزیع مجدد یک بیلد، [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)، [SBOM.json](SBOM.json) و [docs/RELEASE.md](docs/RELEASE.md) را ببین.

</div>

---

<a id="english"></a>

# English

A native Android download manager built with Kotlin, Jetpack Compose, Room, DataStore, OkHttp and coroutines. Downloads use real HTTP(S) requests; this is not a mock or a WebView wrapper. See [CHANGELOG.md](CHANGELOG.md) for the current release notes.

## Highlights

- Native dashboard, download library, search and filters, task details and settings.
- HTTP(S) probing, redirects, verified byte-range transfers, configurable connection count with no app-imposed speed cap, pause/cancel, and checksum verification.
- YouTube and general media-page inspection through the bundled yt-dlp Python module, with format selection, local FFmpeg merging and optional MP3 conversion.
- Magnet and local `.torrent` downloads through a local libtorrent session; completed files are exported into the user-selected SAF folder.
- Resume only when the remote representation can be validated with an ETag or Last-Modified value. Each ranged response is checked before private segments are merged. If those safety checks fail, Cyron declines unsafe resume/parallel merging.
- Foreground `dataSync` service, progress notification, wake lock while actively transferring, and local Room history.
- Interrupted transfers retry with capped exponential backoff and wait for a network instead of failing a whole file on a Wi-Fi/mobile handover. HTTP, media and torrent transfers have no user-configurable speed cap; actual throughput depends on the source, network, device and selected connection count.
- Media titles and format lists come from real yt-dlp metadata, so saved files use the page title and a mapped MIME type. Video-only and audio-only streams are downloaded separately and merged on-device with FFmpegKit because the free yt-dlp build has no ffmpeg executable.
- Destination-folder access through Android's Storage Access Framework (SAF); partial files are kept in app-private storage until verified.
- Persian UI with the Vazirmatn typeface (SIL OFL 1.1) and natural, product-focused copy. Settings are stored locally with DataStore. No accounts, server, analytics, or cloud services are required.
- Minimum Android API 26 (Android 8). Target API 35; compile SDK 36. Native builds currently include `arm64-v8a` and `x86_64` only.

## Build and test

Requirements: JDK 17, Node.js 20 or newer for the public-source check, Android SDK Platform 36 and Android Build Tools, and access to Google Maven and Maven Central. Use the checked-in Gradle wrapper (9.6): `./gradlew`. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` and accept the Android SDK licences first.

```shell
node scripts/check-public.mjs
sh ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Install a local debug build on a connected device:

```shell
./gradlew installDebug
```

To run the Room instrumentation test on a connected emulator or device:

```shell
./gradlew connectedDebugAndroidTest
```

The build splits by ABI: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (phones), `app-x86_64-debug.apk` (emulators) and `app-universal-debug.apk` (both, for convenience). Do not distribute a debug APK as a release. `app/build.gradle.kts` enables R8, resource shrinking and ABI splits. Release signing uses either the git-ignored `keystore.properties` file or the `CYRON_KEYSTORE_*` environment variables described in [the release guide](docs/RELEASE.md). Incomplete signing configuration fails the build; without any signing configuration, release APKs remain unsigned.

## Downloading

1. Launch Cyron Manager and allow notifications if desired.
2. Choose **Settings → Change** to grant the app durable access to a writable folder using Android's system folder picker.
3. Tap **Add**, enter a public HTTP(S) URL, and inspect it. Cyron needs a known positive `Content-Length` and verifies the server's byte-range response before opening independent connections.
4. Optionally enter the file's expected 64-digit SHA-256 digest. Select a filename and connection count, then start the transfer.
5. Pause, resume, cancel, or delete transfers from their actions menu or the foreground notification. Open and share only completed files. Cancelling from the notification asks for a second tap before partial data is removed.

Servers that require a login flow, return unknown-length streams, forbid the required requests, or change the file without stable validators may not be suitable. A multi-connection speed-up depends on the origin server and network; some servers do not support byte ranges. Cancellation deletes HTTP partial data; pausing/recovery keeps private segments which are revalidated against HTTP validators on resume. Media downloads rely on yt-dlp's continuation. The direct-media pause/resume/delete flow has been exercised on an emulator with a local media server; live-site YouTube compatibility still needs broader device testing. Torrent data is kept in app-private storage during transfer and exported only after completion, but process-death recovery/resume has not been validated. An interruption is not the same as a successfully verified final file.

## Media and torrents

Use the **YouTube** mode for a video, Shorts or `youtu.be` link, or the **Media** mode for another public page supported by the bundled yt-dlp release. Inspect the available formats first, then choose a quality or audio option. High qualities are downloaded as separate video and audio streams and merged locally with FFmpegKit; if that merge fails, Cyron falls back to a progressive single-file format. **Audio · MP3** invokes the bundled FFmpeg build for a local conversion. Do not use this app to bypass DRM, paywalls, login requirements, or a site's access controls; respect the rights and terms for each source. The current integration does not add browser cookies or authentication tokens. Media-site compatibility can change as sites change, and this feature has not yet been exercised on a physical device.

Use the **Torrent** mode for a magnet URI or a `.torrent` file selected from Android's document picker. BitTorrent availability depends on peers, network policy and the source; the app currently has no torrent-specific resume-data persistence or completed-seed controls. The selected output directory is written only after the torrent reports completion. Use only torrents you are authorized to download and share.

Third-party native dependencies add significant APK size and retain their own licenses. Review [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [SBOM.json](SBOM.json) and [third-party source and relinking information](docs/THIRD_PARTY_SOURCES.md) before redistributing an APK. FFmpegKit is distributed under LGPLv3; the required notices, license texts and source/replacement path are part of the release checklist. ABI support is currently limited to arm64-v8a and x86_64.

## Architecture

See [the architecture guide](docs/ARCHITECTURE.md) for module boundaries, transfer lifecycle, recovery and intentional limits. See [permissions and local data](docs/PERMISSIONS.md) before changing manifest permissions.

The static `docs/design-mockup/` page is a design prototype only; it simulates downloads in a browser and is not connected to the Android app.

`domain/` contains task/status/value models and repository contracts. `data/` implements Room persistence and DataStore preferences. `download/` contains the OkHttp engine, probing, range validation and transfer orchestration. `storage/` owns private segment files and atomic SAF publication. `background/` hosts the Android foreground service. `presentation/` contains Compose screens and the ViewModel. Hilt wires application-scoped dependencies.

## Security and permissions

The app requests Internet, network-state, foreground-service/data-sync, notification (Android 13+), wake-lock, and boot-completion permissions. Boot completion is used only to keep the periodic queue-recovery job registered; it does not start an always-running service. It does not request broad storage access. HTTP is enabled to honor entered links but is not encrypted or authenticated; prefer HTTPS. Users choose the destination through SAF. Partial segments stay in the app's private no-backup directory and are never published as the requested filename before the full file and optional digest are verified. Android application sandbox encryption, when available, is provided by the device/OS; Cyron does not independently encrypt those temporary files. Only use URLs you trust; HTTP cannot authenticate or encrypt the remote transfer.

## Tests

Run the expected checks before contributing; see [CONTRIBUTING.md](CONTRIBUTING.md). Android device and SAF-provider lifecycle checks are still required before a release.

`app/src/test` contains local JVM unit tests for filename safety and byte-range invariants plus MockWebServer-based HTTP protocol checks. `.github/workflows/android.yml` checks public-source safety, reviews dependency changes on pull requests, runs unit tests and Android Lint, runs the Room instrumentation test on an Android 35 emulator, and uploads a debug APK. Version tags use protected signing secrets to build verified APKs and create a draft GitHub Release. Additional SAF-provider, notification, crash-recovery, and physical-device lifecycle checks are still required before publishing a release.

## Security

Report vulnerabilities privately through GitHub Security Advisories as described in [SECURITY.md](SECURITY.md). Do not include credentials, private URLs, signing keys or personal files in public issues.

## License

Cyron Manager source code is Apache-2.0; see [LICENSE](LICENSE). Bundled dependencies retain their own terms. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [SBOM.json](SBOM.json) and [docs/RELEASE.md](docs/RELEASE.md) before redistributing a build.
