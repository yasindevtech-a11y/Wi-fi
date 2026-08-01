# VaultSync (MVP)

Telefon eve WiFi'ye bağlandığında, ZTE ZXHN H3600 modemin USB portundaki harici
diskle iki yönlü senkronize olan, kendi depo uygulaman.

## Nasıl çalışır?

- Telefonda `vault` klasörü var (uygulamanın private storage'ı içinde).
- Modemdeki USB diskte Samba paylaşımı içinde de bir `vault` klasörü olacak.
- Telefon **ev WiFi'sine** bağlandığında otomatik senkron tetiklenir.
- **Uygulama silinip tekrar kurulursa** (veya telefon değişirse): yerel
  senkron geçmişi boş olduğu için sistem bunu "ilk kurulum" sayar ve
  hiçbir şeyi silmeden USB'deki tüm dosyaları geri yükler. Yani USB
  disk her zaman "asıl kaynak" — telefon kaybolsa/sıfırlansa bile veri
  USB'de güvende kalır.

## 1. Router tarafında Samba'yı aç

1. Tarayıcıda `192.168.1.1` → admin paneline gir
2. **USB / Local Network → Samba Service** sayfasına git
3. Samba servisini **On** yap
4. "Auto On After Detecting USB Storage Plugged" seçeneğini aç
5. Bir Samba kullanıcı adı/şifresi belirle (bunu uygulamaya gireceksin)
6. Paylaşım adını not al (örn. `usb1_1`) — panelde göreceksin
7. USB diski modeme tak, panelde "algılandı" diye görünmesini bekle

> Diski ilk kez kullanıyorsan FAT32 veya exFAT formatında olması en
> sorunsuz seçenek (NTFS bazı ZTE firmware'lerinde tam desteklenmeyebilir).

## 2. Projeyi Android Studio'da aç

1. Android Studio (Koala veya üzeri) kur
2. Bu klasörü **Open an existing project** ile aç
3. Gradle senkron otomatik başlar, ilk seferde internet ister (bağımlılıkları indirir)
4. `app/src/main/java/com/senin/vaultsync/` altındaki paketi kendi adına göre
   değiştirmek istersen Android Studio'nun "Refactor > Rename Package" özelliğini kullan

## 3. Uygulamayı çalıştır

1. Telefonunu USB ile bağla, geliştirici modu + USB debugging açık olsun
2. Run (▶) tuşuna bas
3. Uygulama açılınca ayarlar ekranına:
   - Modem IP: `192.168.1.1`
   - Samba paylaşım adı: (panelde gördüğün ad)
   - Kullanıcı adı / şifre: Samba için belirlediğin
   - Ev WiFi adı: `SUPERONLINE_Wi-Fi_4591`
4. "Ayarları Kaydet" → "Şimdi Senkronize Et" ile ilk testi yap

## 4. Kendi APK'nı GitHub Actions ile derletmek istersen

Repo'ya şunun gibi bir workflow ekleyip her push'ta otomatik APK üretebilirsin:

```yaml
# .github/workflows/build.yml
name: Build APK
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - run: chmod +x gradlew || true
      - run: ./gradlew assembleDebug || gradle assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
```

(Gradle wrapper'ı projeye Android Studio ilk açılışta otomatik ekleyecek —
`gradlew` dosyası yoksa Android Studio'dan **File > Sync Project with Gradle Files**
sonrası oluşur, sonra commit'lersin.)

## Bilinmesi gerekenler / sonraki adımlar

- **Şifre güvenliği:** Şu an Samba şifresi düz DataStore'da tutuluyor. Prodüksiyon
  kalitesinde olması için `EncryptedSharedPreferences` veya Android Keystore'a
  taşınmalı — istersen bir sonraki adımda ekleyelim.
- **Çakışma yönetimi:** Aynı dosya hem telefonda hem USB'de aynı anda değiştirilirse,
  hiçbiri kaybolmaz — ikisi de saklanır (`dosya (telefon-çakışma-...).uzantı` olarak).
- **Büyük dosyalar:** Şu anki sürüm dosyayı tek seferde kopyalıyor; çok büyük
  dosyalarda (birkaç GB) kesintide kaldığı yerden devam etme (resume) özelliği
  henüz yok — istersen bunu bir sonraki iterasyonda ekleriz.
- **Dosya silme UI'da yok:** Şu an sadece ekleme var; kasadan dosya silme
  butonunu istersen ekleyelim.
