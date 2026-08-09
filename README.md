# FTP Manager (Android)

Basit bir Android FTP istemcisi: bağlan, listele, yükle, indir, sil, yeniden adlandır.

## GitHub'da nasıl APK'ya çeviririm?

1. GitHub'da yeni, **boş** bir repo oluştur (README eklemeden).
2. Bu klasördeki tüm dosyaları (gizli `.github` klasörü dahil) o repoya yükle:
   - Ya GitHub web arayüzünden "Add file > Upload files" ile tüm klasörü sürükle bırak,
   - Ya da terminalden:
     ```
     git init
     git add .
     git commit -m "ilk yükleme"
     git branch -M main
     git remote add origin <REPO_URL>
     git push -u origin main
     ```
3. Repo sayfasında **Actions** sekmesine git. "Build APK" workflow'u otomatik başlayacak (push sonrası).
4. Workflow bitince (yeşil tik), workflow çalıştırmasına tıkla, en altta **Artifacts** bölümünde
   `ftp-manager-debug-apk` dosyasını indir. İçinden `app-debug.apk` çıkacak.
5. Bu APK'yı telefonuna kopyala, "bilinmeyen kaynaklardan yükleme"ye izin ver, kur.

## Uygulama nasıl kullanılır?

- Sunucu/IP, port (genelde 21), kullanıcı adı ve şifreyi gir.
- "Bağlan ve Listele" ile modem/USB depolamadaki dosyaları gör (yol kutusuna `/` veya
  ilgili klasör adını yaz).
- Listeden bir dosyaya dokunarak seç, sonra İndir / Sil / Ad Değiştir butonlarını kullan.
- "Yükle" ile telefonundan bir dosya seçip mevcut yol altına gönder.

## Otomatik senkronizasyon (yeni)

- **"Yerel Klasör Seç"**: telefonda dosyaların tutulacağı bir klasör seçersin (örn. Downloads içinde yeni bir klasör oluşturup onu seç).
- **"Şimdi Senkronize Et"**: sadece modemdeki yeni/değişmiş dosyaları indirir, telefonda olup modemde olmayan yeni dosyaları da yukarı yükler.
  Boyutu aynı olan dosyalar tekrar indirilmez — internet/veri harcanmaz.
- **"Otomatik senkronizasyon" kutusu**: işaretlenirse yaklaşık her 6 saatte bir, **yalnızca Wi-Fi'deyken** arka planda otomatik senkronize eder. Mobil veri asla kullanılmaz.
- Telefonu sıfırlarsan (format atarsan), klasörü tekrar seçip "Şimdi Senkronize Et" dediğinde
  (veya Wi-Fi'ye bağlanınca otomatik olarak) modemdeki USB'de ne varsa telefona yeniden iner —
  yerel kopya böylece kendini tazeler.

## Not

Modemin USB depolamasında FTP servisinin ve bir kullanıcı/şifresinin, modemin
yönetim panelinden (genelde 192.168.1.1) aktif edilmiş olması gerekir. Etiketteki
seri numarası/MAC adresleri FTP giriş bilgisi değildir.
