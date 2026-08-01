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

## 1. Router tarafında FTP'yi kullan (Samba yok, bu modelde FTP var)

1. Tarayıcıda `192.168.1.1` → admin paneline gir
2. **Yerel Ağ → FTP** sayfasına git
3. Sunucu: **Açık**
4. Kullanıcı Adı / Parola'yı not al (uygulamaya aynen gireceksin)
5. USB diski modeme tak

> Diski ilk kez kullanıyorsan FAT32 veya exFAT formatında olması en
> sorunsuz seçenek (NTFS bazı ZTE firmware'lerinde tam desteklenmeyebilir).

> Not: FTP şifresiz (parolayı düz metin olarak) çalışır; sadece ev ağın
> içinde kullanıldığı sürece risk düşüktür ama bu portu asla dışarıya
> (internete) port forwarding ile açma.

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
   - FTP kullanıcı adı / parolası: router panelindeki FTP sayfasında girdiğin bilgiler
   - Ev WiFi adı: `SUPERONLINE_Wi-Fi_4591`
4. "Ayarları Kaydet" → "Şimdi Senkronize Et" ile ilk testi yap

## 4. GitHub Actions ile otomatik APK derleme

Bu pakette `.github/workflows/build.yml` zaten hazır — repo'na push ettiğinde
otomatik çalışır ve derlenen APK'yı "Actions" sekmesinden, ilgili çalışmanın
altındaki **Artifacts** bölümünden indirebilirsin.

Yapman gerekenler:
1. Repo'da **Settings → Pages** kısmında otomatik açılmış olabilecek GitHub
   Pages'i kapat (bu proje bir web sitesi değil, alakasız bir workflow tetikler)
2. Bu klasörün tamamını (README, `.github` dahil) repo'ya push et
3. **Actions** sekmesinde "Build APK" workflow'unun yeşil tik almasını bekle
4. Tamamlanan çalışmanın sayfasında **Artifacts** kısmından `vaultsync-debug-apk` dosyasını indir, zip'i aç, içindeki `.apk` dosyasını telefonuna kur

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
