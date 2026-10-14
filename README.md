# Ezan Vakti (Android)

Kotlin + Jetpack Compose. Diyanet yöntemiyle (Aladhan API, method 13) 81 il için vakitler,
vakit girişinde bildirim / ezan sesi, kilit ekranında geri sayımlı kalıcı bildirim ve ana ekran widget'ı.

## APK derleme
**Android Studio:** klasörü açın -> Build > Build Bundle(s)/APK(s) > Build APK(s).
**GitHub Actions:** projeyi GitHub'a yükleyin; Actions > Build APK > çıktıdaki `ezan-vakti-apk`.
**Komut satırı:** Android SDK + Gradle 8.7 kuruluyken `gradle assembleDebug`
(APK: app/build/outputs/apk/debug/app-debug.apk).

## Ezan sesi
Telif nedeniyle ses dosyası pakete konmadı. İki yol:
1. Uygulamada Ayarlar > Ses seç ile telefonunuzdaki mp3'ü seçin.
2. `app/src/main/res/raw/ezan.mp3` olarak ekleyip yeniden derleyin.
