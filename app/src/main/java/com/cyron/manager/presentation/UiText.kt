package com.cyron.manager.presentation

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

internal val LocalAppLanguage = compositionLocalOf { "en" }

/**
 * Persian copy is written for a real product, not translated word-by-word:
 * short sentences, everyday words, and no filler. Technical names stay Latin.
 */
private val persianText = mapOf(
    "overview" to "نمای کلی", "downloads" to "دانلودها", "settings" to "تنظیمات",
    "download manager" to "مدیر دانلود", "personal download space" to "فضای دانلود شخصی",
    "your files,\nin good hands." to "دانلودها،\nمرتب و در دسترس.", "everything is where you left it." to "هر فایل، همان‌جایی که می‌خواهید.",
    "on device" to "روی گوشی", "at a glance" to "در یک نگاه", "transfer activity" to "فعالیت دانلود",
    "real downloaded data · last 7 days" to "حجم دانلود در 7 روز گذشته", "download history" to "تاریخچهٔ دانلود",
    "7 days" to "7 روز", "7d ago" to "7 روز پیش", "6d ago" to "6 روز پیش", "5d ago" to "5 روز پیش",
    "4d ago" to "4 روز پیش", "3d ago" to "3 روز پیش", "2d ago" to "2 روز پیش", "today" to "امروز",
    "recent downloads" to "دانلودهای اخیر", "your file history appears here" to "تاریخچهٔ فایل‌ها اینجا می‌آید",
    "your download list is ready" to "فهرست دانلود آماده است", "add a link to start your first real download." to "اولین لینک را اضافه کنید تا دانلود شروع شود.",
    "private by default · stored on your device" to "خصوصی، روی گوشی خودتان",
    "downloaded" to "دانلود شده", "files safely completed" to "فایل کامل‌شده", "in progress" to "در حال دانلود",
    "active transfers" to "دانلود فعال", "completed today" to "امروز کامل شده", "completed (24h)" to "تکمیل در 24 ساعت", "successful downloads" to "دانلود موفق",
    "total speed" to "سرعت کل", "across active transfers" to "جمع دانلودهای فعال",
    "your personal library" to "کتابخانهٔ شما", "files · stored on this device" to "فایل روی این گوشی",
    "search filenames, url…" to "جست‌وجوی نام یا لینک…", "all files" to "همهٔ فایل‌ها", "active" to "فعال",
    "completed" to "کامل‌شده", "paused" to "متوقف", "failed" to "ناموفق", "all types" to "همهٔ نوع‌ها",
    "archives" to "آرشیو", "video" to "ویدیو", "apps" to "برنامه", "documents" to "سند", "disk images" to "فایل ایمیج",
    "all your files" to "همهٔ فایل‌ها", "download" to "دانلود", "add a download" to "افزودن دانلود", "clear search & filters" to "پاک‌کردن جست‌وجو و فیلتر",
    "your library is empty" to "کتابخانه خالی است", "no matching downloads" to "دانلودی پیدا نشد",
    "add a url to download your first file." to "برای شروع، یک لینک وارد کنید.",
    "try changing the search or selected filters." to "جست‌وجو یا فیلتر را تغییر دهید.",
    "queued" to "در صف", "in queue" to "در صف", "checking" to "در حال بررسی", "checking server" to "در حال بررسی سرور",
    "done" to "تمام", "cancelled" to "لغو شد", "download failed" to "دانلود ناموفق", "downloading" to "در حال دانلود",
    "resume / retry" to "ادامه یا تلاش دوباره", "resume" to "ادامه", "retry" to "تلاش دوباره", "pause" to "توقف",
    "cancel" to "لغو", "delete" to "حذف", "open file" to "بازکردن فایل", "share file" to "اشتراک‌گذاری",
    "open" to "بازکردن", "share" to "اشتراک‌گذاری", "connecting…" to "در حال اتصال…", "of" to "از",
    "safe · verified http downloads" to "HTTP · دانلود امن و بررسی‌شده", "media · yt-dlp + ffmpeg" to "رسانه · یوتیوب و وب",
    "torrent · magnet / .torrent" to "تورنت · مگنت یا فایل", "http file" to "فایل HTTP", "video / audio" to "ویدیو و صدا", "torrent" to "تورنت",
    "youtube" to "یوتیوب", "youtube · video & shorts" to "یوتیوب · ویدیو و شورتس", "youtube link" to "لینک یوتیوب",
    "inspect video" to "بررسی ویدیو", "inspect formats" to "بررسی کیفیت‌ها", "inspecting…" to "در حال بررسی…",
    "best quality" to "بهترین کیفیت", "best available" to "بهترین کیفیت موجود", "best single file" to "بهترین فایل یکپارچه",
    "paste a youtube video, shorts, or youtu.be link. cyron downloads the video and audio streams and merges them on your device." to "لینک ویدیو، Shorts یا youtu.be را وارد کنید. Cyron ویدیو و صدا را جدا دانلود و روی گوشی ادغام می‌کند.",
    "quality is merged on-device with ffmpeg. some videos may be restricted by youtube." to "کیفیت روی گوشی با FFmpeg ساخته می‌شود. ممکن است یوتیوب بعضی ویدیوها را محدود کند.",
    "enter an http(s) direct-download link. cyron probes the server and verifies ranges before starting." to "لینک مستقیم فایل را وارد کنید؛ قبل از شروع، سرور بررسی می‌شود.",
    "direct download url" to "لینک مستقیم دانلود", "file name (optional)" to "نام فایل (اختیاری)", "detected from server" to "از سرور تشخیص داده می‌شود",
    "verified multi-connection" to "دانلود چنداتصالی", "single connection" to "یک اتصال", "expected sha-256 (optional)" to "SHA-256 (اختیاری)",
    "enter a media page to inspect available formats. site support varies; access restrictions and drm are not bypassed." to "لینک ویدیو یا صفحهٔ رسانه را وارد کنید تا کیفیت‌های موجود را ببینید. پشتیبانی سایت‌ها متفاوت است و DRM دور زده نمی‌شود.",
    "media page url" to "لینک صفحهٔ رسانه", "file title (optional)" to "عنوان فایل (اختیاری)", "detected from page" to "از صفحه تشخیص داده می‌شود",
    "select format" to "انتخاب کیفیت", "audio only" to "فقط صدا", "audio · mp3" to "صدا (MP3)",
    "yt-dlp formats and ffmpeg audio conversion. no browser cookies or drm circumvention." to "دانلود با yt-dlp و تبدیل صدا با FFmpeg؛ بدون کوکی مرورگر و بدون دور زدن DRM.",
    "inspecting formats…" to "در حال بررسی کیفیت‌ها…",
    "use a magnet link or select a local .torrent file. torrent data is private until complete, then exported to your selected folder." to "یک مگنت یا فایل تورنت انتخاب کنید. داده‌ها تا کامل‌شدن داخل برنامه می‌مانند و بعد به پوشهٔ مقصد منتقل می‌شوند.",
    "magnet link (optional)" to "لینک مگنت (اختیاری)", "choose a .torrent file" to "انتخاب فایل تورنت", "torrent networking can use mobile data; respect the rights attached to the content." to "تورنت ممکن است از اینترنت همراه استفاده کند؛ حقوق محتوا را رعایت کنید.",
    "destination folder selected" to "پوشهٔ مقصد انتخاب شد", "choose a destination folder" to "پوشهٔ مقصد را انتخاب کنید", "browse" to "انتخاب",
    "no account or cloud transfer. temporary data stays in app-private storage." to "بدون حساب کاربری و فضای ابری؛ فایل‌های موقت داخل خود برنامه می‌مانند.",
    "made for your device" to "مخصوص گوشی شما", "just how you want your downloads." to "دانلودها را همان‌طور که می‌خواهید تنظیم کنید.",
    "download behavior" to "رفتار دانلود", "simultaneous downloads" to "دانلود هم‌زمان", "maximum active files at once" to "چند فایل هم‌زمان دانلود شوند",
    "http connections per file" to "اتصال هم‌زمان هر فایل", "independent verified byte ranges" to "اتصال‌های موازی برای فایل‌های بزرگ",
    "storage & notifications" to "ذخیره‌سازی و اعلان", "save location" to "محل ذخیره", "not selected · choose a folder" to "پوشه‌ای انتخاب نشده",
    "selected folder · system-managed access" to "پوشهٔ انتخاب‌شده", "change" to "تغییر",
    "download notifications" to "اعلان دانلود", "a notification when a file finishes" to "وقتی دانلودی تمام شد خبرم کن",
    "appearance" to "ظاهر", "dark mode" to "حالت تاریک", "light mode" to "حالت روشن", "dark" to "تاریک", "light" to "روشن",
    "language" to "زبان", "english" to "انگلیسی", "persian" to "فارسی",
    "network & speed" to "شبکه و سرعت", "downloads use your device's normal network; cyron does not require a vpn. if a site only works through a vpn, the restriction may be from the network or the source." to "دانلودها از اینترنت معمولی گوشی استفاده می‌کنند؛ Cyron به VPN نیاز ندارد. اگر سایتی فقط با VPN باز می‌شود، محدودیت از شبکه یا خود سایت است.",
    "more download actions" to "کارهای بیشتر", "about cyron" to "دربارهٔ Cyron", "cyron manager" to "Cyron",
    "open source · local-first · private" to "متن‌باز، روی گوشی، خصوصی", "native kotlin + jetpack compose · okhttp · room · datastore · android storage access framework" to "Kotlin بومی · Jetpack Compose · OkHttp · Room · DataStore · چارچوب ذخیره‌سازی اندروید",
    "files and settings stay on your device. no accounts or cloud services." to "همه‌چیز روی گوشی شما می‌ماند؛ بدون حساب و فضای ابری.",
    "file details" to "جزئیات فایل", "transfer speed" to "سرعت دانلود", "time remaining" to "زمان باقی‌مانده", "connections" to "اتصال‌ها", "transfer information" to "اطلاعات دانلود",
    "source" to "منبع", "saved name" to "نام ذخیره‌شده", "content type" to "نوع فایل", "http resume" to "ادامهٔ دانلود", "calculated after completion" to "بعد از تکمیل محاسبه می‌شود",
    "system-selected local folder" to "پوشهٔ انتخاب‌شده", "not selected" to "انتخاب نشده", "added" to "اضافه شده",
    "http connections ·" to "اتصال HTTP ·", "verified ranges" to "بازه‌های تأییدشده", "requires server range + validator" to "نیازمند Range و اعتبارسنج سرور", "http connections · 4 (requires server range + validator)" to "4 اتصال HTTP (نیازمند Range و اعتبارسنج سرور)", "inspect link" to "بررسی لینک", "inspect media" to "بررسی رسانه", "start download" to "شروع دانلود",
    "download actions" to "عملیات دانلود", "search downloads" to "جست‌وجوی دانلودها", "download settings" to "تنظیمات دانلود",
    "completed on this device" to "کامل‌شده روی این گوشی", "single connection fallback" to "استفاده از یک اتصال",
    "downloaded today" to "دانلود امروز",
    "streaming…" to "در حال دریافت…",
    "remove from list" to "حذف از فهرست",
    "download complete" to "دانلود کامل شد",
    "ready for downloads" to "آماده برای دانلود",
    "preparing download…" to "در حال آماده‌سازی…",
    "downloading media" to "دانلود رسانه",
    "downloading torrent" to "دانلود تورنت",
    "enter a valid http or https link without embedded credentials." to "یک لینک معتبر وارد کنید؛ نام کاربری و رمز را داخل لینک نگذارید.",
    "only http and https links without embedded credentials are supported." to "فقط لینک‌های HTTP و HTTPS پشتیبانی می‌شوند.",
    "enter a valid http or https link." to "یک لینک معتبر وارد کنید.",
    "this download is already running." to "این دانلود همین حالا در حال انجام است.",
    "the remote file size changed since probing." to "حجم فایل روی سرور تغییر کرده است.",
    "range support changed since probing. probe this link again." to "پشتیبانی Range تغییر کرده؛ لینک را دوباره بررسی کنید.",
    "the remote file changed since the original request. unsafe partial data was discarded." to "فایل روی سرور عوض شده؛ بخش‌های ناقص پاک شدند.",
    "the sha-256 checksum must contain exactly 64 hexadecimal characters." to "کد SHA-256 باید 64 کاراکتر باشد.",
    "the file did not match its expected sha-256 checksum. incomplete data was discarded." to "چک‌سام فایل درست نبود؛ داده‌های ناقص پاک شدند.",
    "download cancelled." to "دانلود لغو شد.",
    "download paused; verified partial data is preserved." to "دانلود متوقف شد؛ بخش‌های سالم برای ادامه نگه داشته شدند.",
    "network error. verified partial data has been preserved." to "خطای شبکه بود؛ بخش‌های سالم نگه داشته شدند.",
    "storage access is unavailable. choose the destination folder again." to "به پوشهٔ مقصد دسترسی نداریم؛ دوباره انتخابش کنید.",
    "choose a destination folder using the system folder picker." to "از پنجرهٔ انتخاب پوشه، مقصد را انتخاب کنید.",
    "the server returned an empty response." to "سرور جوابی نداد.",
    "the server no longer honours range requests; partial files were discarded." to "سرور دیگر Range را نمی‌پذیرد؛ فایل‌های ناقص پاک شدند.",
    "the server did not honour the resume request. retry to safely restart this segment." to "سرور اجازهٔ ادامه نداد؛ برای شروع دوباره تلاش کنید.",
    "this server does not support resuming partial downloads. retry to start again safely." to "این سرور ادامهٔ دانلود را پشتیبانی نمی‌کند؛ از اول شروع کنید.",
    "a saved segment exceeds the expected range. the incomplete download was discarded." to "بخشی از دانلود از محدودهٔ مجاز بزرگ‌تر بود؛ دانلود ناقص پاک شد.",
    "the server sent data beyond the verified byte range." to "سرور داده‌ای بیشتر از محدودهٔ تأییدشده فرستاد.",
    "the server closed the connection before this byte range finished." to "سرور قبل از پایان این بخش اتصال را بست.",
    "the server did not report a usable content-length; downloads of unknown-size streams are intentionally declined." to "سرور حجم فایل را اعلام نکرد؛ این نوع دانلود پشتیبانی نمی‌شود.",
    "the server reported inconsistent resource lengths while probing." to "سرور حجم‌های متناقض گزارش کرد.",
    "the range-support probe returned unexpected bytes." to "بررسی Range پاسخ غیرمنتظره داد.",
    "the server returned an invalid content-range." to "سرور Content-Range نامعتبر فرستاد.",
    "the remote file size is unknown." to "حجم فایل روی سرور مشخص نیست.",
    "the remote file is empty or its size is unknown." to "فایل روی سرور خالی است یا حجمش مشخص نیست.",
    "the range probe has no response body." to "پاسخ بررسی Range بدنه نداشت.",
    "the range probe returned no file data." to "بررسی Range داده‌ای برنگرداند.",
    "the remote etag changed. previously downloaded segments were not merged." to "ETag سرور عوض شد؛ بخش‌ها ترکیب نشدند.",
    "the remote last-modified changed. previously downloaded segments were not merged." to "Last-Modified سرور عوض شد؛ بخش‌ها ترکیب نشدند.",
    "the server returned a missing or malformed content-range." to "Content-Range ناقص یا نامعتبر بود.",
    "the server changed the resource or returned a mismatched byte range. segments were not merged." to "سرور فایل را عوض کرد یا محدودهٔ ناسازگار فرستاد؛ بخش‌ها ترکیب نشدند.",
    "not enough free storage to merge and verify this download. free some space and retry; the verified segments are kept." to "فضای کافی برای تکمیل دانلود نیست؛ کمی فضا آزاد کنید. بخش‌های دانلودشده حفظ می‌شوند.",
    "download failed. check your connection and retry." to "دانلود انجام نشد. اینترنت را بررسی کنید و دوباره امتحان کنید.",
    "unable to inspect this download link." to "نتوانستیم این لینک را بررسی کنیم.",
    "inspect the link again before starting the download." to "قبل از شروع، لینک را دوباره بررسی کنید.",
    "this media page could not be inspected. the site may be unsupported or may restrict access." to "این صفحه باز نشد؛ شاید سایت پشتیبانی نمی‌شود یا دسترسی را بسته است.",
    "enter an http or https media page." to "یک لینک HTTP یا HTTPS وارد کنید.",
    "enter a valid http or https media page." to "یک لینک معتبر HTTP یا HTTPS وارد کنید.",
    "the media download did not complete. check the link and the site's access terms." to "دانلود ویدیو کامل نشد. لینک را بررسی کنید یا بعداً دوباره امتحان کنید.",
    "the media engine returned no completed output file." to "فایل خروجی ساخته نشد.",
    "the video downloaded, but its audio stream could not be downloaded. choose another quality and try again." to "ویدیو دانلود شد اما صدای آن دانلود نشد. کیفیت دیگری انتخاب کنید و دوباره تلاش کنید.",
    "the video and audio streams downloaded, but they could not be merged. try another quality." to "ویدیو و صدا دانلود شدند اما ادغام نشدند. کیفیت دیگری امتحان کنید.",
    "audio conversion failed. the downloaded source has been retained in private app storage." to "تبدیل صدا انجام نشد؛ فایل اصلی داخل برنامه نگه داشته شد.",
    "audio extraction failed. the downloaded source has been retained in private app storage." to "استخراج صدا انجام نشد؛ فایل اصلی داخل برنامه نگه داشته شد.",
    "could not prepare private media storage." to "نتوانستیم فضای موقت را آماده کنیم.",
    "select a destination folder in android's folder picker." to "از پنجرهٔ انتخاب پوشه، مقصد را انتخاب کنید.",
    "choose a destination folder before starting." to "اول پوشهٔ مقصد را انتخاب کنید.",
    "choose a writable folder using the android folder picker." to "یک پوشهٔ قابل‌نوشتن انتخاب کنید.",
    "youtube is blocking automated access from this network. try again later or on a different connection." to "یوتیوب دسترسی این شبکه را بسته است. کمی بعد یا با اینترنت دیگری امتحان کنید.",
    "youtube needs a javascript runtime that is not included in this build. try another link or use a direct media file." to "این نسخه موتور جاوااسکریپت یوتیوب را ندارد. لینک دیگری امتحان کنید یا فایل رسانهٔ مستقیم بدهید.",
    "the site could not be reached. check your connection or vpn and try again." to "به سایت دسترسی نداشتیم. اتصال یا فیلترشکن را بررسی کنید و دوباره امتحان کنید.",
    "youtube changed its player. try another link or update the app." to "یوتیوب پخش‌کننده‌اش را تغییر داده است. لینک دیگری امتحان کنید یا اپ را به‌روز کنید.",
    "the download was cancelled." to "دانلود لغو شد.",
    "verified byte-range requests" to "درخواست‌های بازهٔ بایتی تأییدشده",
    "destination" to "مقصد",
    "file type" to "نوع فایل",
    "status" to "وضعیت",
    "progress" to "پیشرفت",
    "speed" to "سرعت",
    "remaining" to "باقی‌مانده",
    "delete file" to "حذف فایل",
    "delete this download?" to "این دانلود حذف شود؟",
    "delete the downloaded file from the selected folder, or keep the file and remove only the record." to "فایل دانلودشده را از پوشهٔ مقصد حذف کنید، یا فایل را نگه دارید و فقط رکورد را بردارید.",
    "this removes the record and any partial data." to "این کار رکورد و هر دادهٔ ناقص را حذف می‌کند.",
    "change folder" to "تغییر پوشه",
    "change destination" to "تغییر مقصد",
    "destination folder changed." to "پوشهٔ مقصد تغییر کرد.",
    "pause the download before changing its destination." to "قبل از تغییر مقصد، دانلود را متوقف کنید.",
    "keep file" to "نگه‌داشتن فایل",
    "allow download notifications?" to "اجازهٔ اعلان دانلود می‌دهید؟",
    "notifications show live progress and tell you when a file finishes, even while cyron is in the background. you can change this later in android settings." to "اعلان‌ها پیشرفت زنده و پایان دانلود را حتی وقتی Cyron در پس‌زمینه است نشان می‌دهند. بعداً می‌توانید از تنظیمات اندروید تغییرش دهید.",
    "allow" to "اجازه می‌دهم",
    "not now" to "بعداً",
    "third-party licenses" to "مجوزهای شخص ثالث",
    "full third-party licenses" to "متن کامل مجوزهای شخص ثالث",
    "open full license texts" to "نمایش متن کامل مجوزها",
    "summary" to "فهرست مجوزها",
    "license notice is unavailable." to "متن مجوزها در دسترس نیست.",
    "close" to "بستن",
    "open notification settings" to "تنظیمات اعلان اندروید",
    "wifi only" to "فقط Wi-Fi",
    "wait for wi-fi before starting downloads" to "تا اتصال Wi-Fi دانلود شروع نشود",
    "wi-fi only is enabled. connect to wi-fi and tap resume to continue." to "حالت «فقط Wi-Fi» روشن است. به Wi-Fi وصل شوید و «ادامه» را بزنید.",
    "some downloaded files could not be deleted from the selected folder. check the folder permission and retry." to "بعضی فایل‌ها از پوشهٔ مقصد حذف نشدند. دسترسی پوشه را بررسی کنید و دوباره تلاش کنید.",
    "choose an output folder before starting." to "قبل از شروع، پوشهٔ مقصد را انتخاب کنید.",
    "select a writable folder using the android folder picker." to "از پنجرهٔ انتخاب پوشه، یک پوشهٔ قابل‌نوشتن انتخاب کنید.",
    "the server response size changed; incomplete data was discarded." to "حجم پاسخ سرور تغییر کرد؛ داده‌های ناقص پاک شدند.",
    "the server response size changed" to "حجم پاسخ سرور تغییر کرد",
    "select a writable folder" to "یک پوشهٔ قابل‌نوشتن انتخاب کنید",
    "delete download?" to "این دانلود حذف شود؟",
    "cancel download?" to "این دانلود لغو شود؟",
    "delete" to "حذف",
    "keep" to "نگه‌داشتن",
    "cancel transfer" to "لغو انتقال",
    "this removes the record and the published file from the selected folder." to "رکورد و فایل منتشرشده از پوشهٔ انتخابی هم حذف می‌شوند.",
    "the transfer will stop and its partial data will be deleted." to "انتقال متوقف می‌شود و داده‌های ناقص حذف می‌شوند.",
    "download best quality without inspecting" to "دانلود بهترین کیفیت بدون بررسی",
    "paste an http(s) media link first." to "ابتدا یک لینک رسانهٔ HTTP(S) وارد کنید.",
    "the media download timed out. check your connection or vpn and try again." to "دانلود رسانه زمان‌بندی شد؛ اتصال یا فیلترشکن را بررسی کنید و دوباره امتحان کنید.",
    "the media download stalled. check your connection or vpn and try again." to "دانلود رسانه متوقف ماند. اتصال یا فیلترشکن را بررسی کنید و دوباره امتحان کنید.",
    "this video is unavailable, private, or restricted." to "این ویدیو در دسترس نیست، خصوصی است یا محدود شده.",
    "this link is not supported by the media engine." to "این لینک توسط موتور رسانه پشتیبانی نمی‌شود.",
    "playlists are not supported. paste a single video link." to "پلی‌لیست پشتیبانی نمی‌شود؛ لینک یک ویدیوی تکی را وارد کنید.",
    "the site refused the media request (403). try again later." to "سایت درخواست را رد کرد (403). بعداً دوباره امتحان کنید.",
    "the selected quality is not available for this link. choose another format." to "این کیفیت برای این لینک موجود نیست؛ کیفیت دیگری انتخاب کنید.",
    "this is not a valid .torrent file." to "این فایل تورنت معتبر نیست.",
    "magnet link must include a valid bittorrent info-hash." to "لینک مگنت باید info-hash معتبر داشته باشد.",
    "this torrent is already in the download list." to "این تورنت از قبل در فهرست است.",
    "the torrent source cannot be changed after this task has started." to "بعد از شروع، منبع تورنت قابل تغییر نیست.",
    "torrent is not complete yet." to "تورنت هنوز کامل نشده.",
    "torrent metadata is unavailable." to "اطلاعات تورنت در دسترس نیست.",
    "torrent metadata is still being discovered." to "اطلاعات تورنت هنوز در حال دریافت است.",
    "torrent session was not found." to "نشست تورنت پیدا نشد.",
    "selected folder is no longer available." to "پوشهٔ مقصد دیگر در دسترس نیست.",
    "selected folder is no longer writable." to "پوشهٔ مقصد قابل‌نوشتن نیست.",
    "could not create a destination subfolder." to "ساخت زیرپوشه ممکن نشد.",
    "could not create a temporary file in the selected destination." to "ساخت فایل موقت در مقصد ممکن نشد.",
    "could not open a destination torrent file." to "بازکردن فایل تورنت ممکن نشد.",
    "the selected storage provider cannot safely publish the completed file." to "فضای ذخیره‌سازی نمی‌تواند فایل را با ایمنی ذخیره کند.",
    "unsafe file path in torrent metadata." to "مسیر فایل در تورنت ناایمن است.",
    "a torrent file is missing or outside private storage." to "یک فایل تورنت پیدا نشد یا بیرون از فضای برنامه است.",
    "torrent download failed." to "دانلود تورنت انجام نشد.",
    "could not prepare private torrent storage." to "آماده‌سازی فضای تورنت ممکن نشد.",
    "could not prepare private torrent data." to "آماده‌سازی داده‌های تورنت ممکن نشد.",
    "the selected system folder is no longer accessible." to "پوشهٔ انتخاب‌شده در دسترس نیست.",
    "the selected system folder is not writable." to "پوشهٔ انتخاب‌شده قابل‌نوشتن نیست.",
    "the selected folder refused a temporary download file." to "پوشهٔ مقصد فایل موقت را نپذیرفت.",
    "could not write to the selected destination." to "نوشتن در مقصد ممکن نشد.",
    "the provider cannot safely publish a verified file. your incomplete download remains in private storage." to "فایل تأییدشده ذخیره نشد؛ دانلود ناقص داخل برنامه ماند.",
    "unable to keep access to that folder. please select a writable folder again." to "دسترسی به پوشه حفظ نشد؛ یک پوشهٔ دیگر انتخاب کنید.",
    "enter a magnet link or choose a .torrent file." to "یک لینک مگنت وارد کنید یا فایل تورنت انتخاب کنید.",
    "choose a destination folder first." to "اول پوشهٔ مقصد را انتخاب کنید.",
    "this download could not be added." to "افزودن دانلود ممکن نشد.",
    "no compatible app could open this file." to "برنامه‌ای برای بازکردن این فایل پیدا نشد.",
    "the completed file is not available to share." to "فایل برای اشتراک‌گذاری در دسترس نیست.",
    "enter exactly 64 valid hexadecimal characters for sha-256." to "دقیقاً 64 کاراکتر هگزادسیمال وارد کنید.",
    "the server did not supply a valid file size." to "سرور حجم معتبری اعلام نکرد.",
    "could not inspect this media link." to "بررسی این لینک ممکن نشد.",
    "could not add this download." to "افزودن این دانلود ممکن نشد.",
    "choose a writable folder using the system folder picker." to "یک پوشهٔ قابل‌نوشتن انتخاب کنید.",
    "choose the destination folder again to resume securely." to "برای ادامه، پوشهٔ مقصد را دوباره انتخاب کنید.",
    "recovered after the app closed. your partial data is retained; media/torrent recovery is best-effort." to "بعد از بسته‌شدن برنامه بازیابی شد؛ بخش‌های ناقص حفظ شده‌اند.",
    "could not allocate an app-private download directory." to "ساخت پوشهٔ موقت دانلود ممکن نشد.",
    "could not safely discard a stale partial segment." to "پاک‌کردن بخش قدیمی دانلود ممکن نشد.",
    "could not allocate a private segment file." to "ساخت فایل موقت دانلود ممکن نشد.",
    "a previous unverified merge could not be safely removed." to "فایل ترکیبی تأییدنشدهٔ قبلی پاک نشد.",
    "an abandoned merge could not be safely replaced." to "فایل ترکیبی نیمه‌کارهٔ قبلی جایگزین نشد.",
    "a segment is missing or its size does not match the verified byte range; refusing to merge." to "یک بخش از دانلود پیدا نشد یا اندازه‌اش درست نیست؛ فایل ترکیب نشد.",
    "a segment exceeded its validated byte range." to "یک بخش از دانلود از محدودهٔ تأییدشده بزرگ‌تر بود.",
    "a segment ended before its validated byte range." to "یک بخش از دانلود زودتر از محدودهٔ تأییدشده تمام شد.",
    "the merged file size does not match the verified http resource length." to "حجم فایل ترکیب‌شده با حجم اعلام‌شدهٔ سرور یکی نیست.",
    "the download could not be written to storage. free some space and retry; verified partial data is preserved." to "نوشتن دانلود روی حافظه ممکن نشد. کمی فضا آزاد کنید و دوباره تلاش کنید؛ بخش‌های سالم نگه داشته می‌شوند.",
    "a downloaded segment could not be read from private storage." to "خواندن بخش دانلودشده از حافظهٔ برنامه ممکن نشد.",
    "could not securely remove a partial download." to "پاک‌کردن امن دانلود ناقص ممکن نشد.",
    "could not remove the download's temporary directory." to "پوشهٔ موقت دانلود پاک نشد.",
    "could not safely invalidate a mismatched file segment." to "پاک‌کردن بخش ناسازگار دانلود ممکن نشد.",
    "android stopped the background service after its daily limit. tap resume to continue." to "اندروید بعد از سقف روزانه، سرویس پس‌زمینه را متوقف کرد. برای ادامه، «ادامه» را بزنید.",
    "this torrent is already in the download list" to "این تورنت از قبل در فهرست است",
    "unlimited transfer speed" to "سرعت دانلود نامحدود",
    "downloads use all available network capacity" to "دانلود از تمام ظرفیت موجود شبکه استفاده می‌کند",
    "batch import" to "افزودن دسته‌ای", "add several links at once" to "افزودن چند لینک هم‌زمان",
    "paste links" to "چسباندن لینک‌ها", "clear links" to "پاک‌کردن لینک‌ها",
    "download links (one per line)" to "لینک‌های دانلود (هر خط یک لینک)",
    "media links (one per line)" to "لینک‌های رسانه (هر خط یک لینک)",
    "magnet links (one per line)" to "لینک‌های مگنت (هر خط یک لینک)",
    "concurrent fragments" to "بخش‌های هم‌زمان", "concurrent media fragments" to "بخش‌های هم‌زمان رسانه",
    "pause all" to "توقف همه", "resume all" to "ادامهٔ همه", "sort downloads" to "مرتب‌سازی دانلودها",
    "newest first" to "جدیدترین", "oldest first" to "قدیمی‌ترین", "name" to "نام", "size" to "اندازه", "priority" to "اولویت",
    "higher priority" to "افزایش اولویت", "lower priority" to "کاهش اولویت",
    "queue position" to "جایگاه صف", "saving" to "در حال ذخیره", "saving file" to "در حال ذخیرهٔ فایل",
    "saving the verified file to your selected folder…" to "در حال ذخیرهٔ فایل تأییدشده در پوشهٔ انتخابی…",
    "published files" to "فایل‌های خروجی", "output files" to "فایل خروجی", "full url" to "لینک کامل",
    "completion date" to "زمان تکمیل", "follow system theme" to "پیروی از تم سیستم", "switch automatically with android" to "تغییر خودکار با تنظیمات اندروید",
    "keep completed and failed history" to "نگه‌داری تاریخچهٔ موفق و ناموفق",
    "older records are removed automatically; downloaded files are kept." to "رکوردهای قدیمی خودکار حذف می‌شوند؛ فایل‌های دانلودشده باقی می‌مانند.",
    "days" to "روز", "1 year" to "یک سال", "clear history now" to "پاک‌کردن تاریخچه",
    "clear download history?" to "تاریخچهٔ دانلود پاک شود؟",
    "clear history" to "پاک‌کردن تاریخچه",
    "this removes completed, failed, and cancelled records. downloaded files are not deleted." to "رکوردهای کامل، ناموفق و لغوشده حذف می‌شوند. فایل‌های دانلودشده حذف نمی‌شوند.",
    "all links are already in the download list." to "همهٔ لینک‌ها از قبل در فهرست دانلود هستند.",
    "paste at least one valid download link." to "حداقل یک لینک معتبر وارد کنید.",
    "batch torrent import accepts magnet links." to "افزودن دسته‌ای تورنت فقط لینک مگنت می‌پذیرد.",
    "enter an http(s) media link without embedded credentials." to "لینک رسانهٔ HTTP(S) بدون نام کاربری و رمز وارد کنید.",
    "waiting for an internet connection." to "در انتظار اتصال به اینترنت.",
    "wi-fi only is enabled. waiting for an unmetered network." to "حالت «فقط Wi-Fi» روشن است؛ در انتظار شبکهٔ بدون مصرف اندازه‌گیری.",
    "removed history entries" to "رکورد تاریخچه حذف شد",
    "download progress" to "پیشرفت دانلود",
    "back" to "بازگشت",
    "refusing an https to http downgrade." to "انتقال ناامن از HTTPS به HTTP رد شد."
)

private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
private val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

/**
 * Numbers and counts stay Latin in the Persian UI, so any digit that arrives from
 * java.text (a Persian date, for example) is folded back to ASCII.
 */
internal fun latinDigits(text: String): String = text.map { char ->
    when {
        char in persianDigits -> '0' + persianDigits.indexOf(char)
        char in arabicDigits -> '0' + arabicDigits.indexOf(char)
        char == '٪' -> '%'
        else -> char
    }
}.joinToString("")

/**
 * Embeds a value such as "12 MB" as a left-to-right run. Inside an RTL paragraph the
 * bidi algorithm would otherwise reorder it into "MB 12".
 */
internal fun isolateLtr(text: String): String = "\u202A" + text + "\u202C"

internal fun normalizeSearchText(text: String): String = text.lowercase(Locale.ROOT)
    .replace('ي', 'ی')
    .replace('ك', 'ک')
    .replace('ۀ', 'ه')
    .replace('ة', 'ه')
    .replace("\u200c", " ")
    .replace(Regex("\\s+"), " ")
    .trim()

internal fun localizeUiText(text: String, language: String): String {
    if (language != "fa") return text
    val leading = text.takeWhile(Char::isWhitespace)
    val trailing = text.takeLastWhile(Char::isWhitespace)
    val core = text.trim()
    val direct = persianText[core.lowercase(Locale.ROOT)]
    if (direct != null) return leading + direct + trailing
    // Values that change at runtime (sizes, counts, HTTP status) are localized by shape.
    val shaped = when {
        core.startsWith("Added ", true) && core.endsWith(" to downloads.") ->
            "«${core.removePrefix("Added ").removeSuffix(" to downloads.").trim()}» به دانلودها اضافه شد."
        Regex("^Added \\d+ downloads\\.$").matches(core) ->
            core.removePrefix("Added ").removeSuffix(" downloads.") + " دانلود اضافه شد."
        Regex("^Added \\d+ downloads; \\d+ could not be added\\.$").matches(core) -> {
            val parts = core.removeSuffix(".").split("; ")
            val added = parts[0].removePrefix("Added ").removeSuffix(" downloads")
            val failed = parts[1].removeSuffix(" could not be added")
            "$added دانلود اضافه شد؛ $failed لینک افزوده نشد."
        }
        Regex("^Add \\d+ links$").matches(core) -> "افزودن " + core.removePrefix("Add ").removeSuffix(" links") + " لینک"
        Regex("^Removed \\d+ history entries\\.$").matches(core) ->
            core.removePrefix("Removed ").removeSuffix(" history entries.") + " رکورد تاریخچه حذف شد."
        core.startsWith("Server returned HTTP ", ignoreCase = true) ->
            "پاسخ سرور: HTTP " + core.substring("Server returned HTTP ".length).trimEnd('.')
        core.startsWith("Not enough free storage", ignoreCase = true) ->
            "فضای ذخیره‌سازی کافی نیست. کمی فضا آزاد کنید و دوباره امتحان کنید."
        core.startsWith("Torrent transfer error: ", ignoreCase = true) ->
            "خطای انتقال تورنت: " + core.substring("Torrent transfer error: ".length)
        core.startsWith("Could not add this torrent: ", ignoreCase = true) ->
            "افزودن این تورنت ممکن نشد: " + core.substring("Could not add this torrent: ".length)
        else -> null
    }
    return leading + (shaped ?: core) + trailing
}
