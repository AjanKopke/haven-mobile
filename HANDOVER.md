# Haven Mobile — Handover

_Başlangıç: 2026-11-15_

Haven Client'ın Android sürümü. Zalith Launcher 2'nin (GPL-3, PojavLauncher tabanlı) çatalı. Java Edition'ı telefonda çalıştıran kısım (JVM, LWJGL, renderer'lar, dokunmatik kontroller) Zalith'ten geliyor; Haven'a özel her şey `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/haven/` altında.

- **1.0.7 (2026-11-17):** Haven Play katılımında bir mod indirmesi bozulursa (tünel koptu ya da ev sahibinin oyunu jar'ı değiştirdi, Essential gibi) telefon ev sahibinin listesini yeniden alıp 3 kereye kadar dener; her mod önce `cache/haven-play-mod.part`'a iner, sonra pakete yazılır. Önce ilk hatada "Mod dosyası eksik geldi" deyip duruyordu (bir oyuncu "fabric-language-kotlin eksik" gördü). Sadece derlendi, gerçek katılımla denenmedi.
- **Test emülatörü:** `Haven_Test_API_30` (Android 11, ARM çevirili): yayındaki arm64 APK kuruluyor.
## Depo ve lisans
- Kaynak: `havenhub\mobile`, GitHub `AjanKopke/haven-mobile` (**public**). GPL-3, APK dağıtıldığı için kaynağın açık olmasını şart koşuyor. Masaüstü kaynağı (`nova-launcher-src`) özel kalıyor, bu depoya ondan kod kopyalanmadı (yalnızca davet kodu biçimi ve Gate protokolü Kotlin'e yeniden yazıldı).
- `upstream` remote'u = Zalith. Güncelleme almak için: `git fetch upstream` + merge (geçmiş orphan commit ile başladı, merge'de `--allow-unrelated-histories` gerekir).
- Paket adı `net.havenclient.mobile`; Kotlin `namespace` değişmedi (`com.movtery.zalithlauncher`), 975 dosyanın `R` importları buna bağlı.

## Haven özellikleri
| Dosya | Ne yapar |
|---|---|
| `HavenMod.kt` | `assets/haven-mod/haven-client-mod-<mc>.jar` (masaüstü `mcl/mod` ile aynı jar'lar) → `filesDir/.hv/hc-<sha1>.jar`, `-Dfabric.addMods`. Yalnız Fabric profilleri, tam sürüm eşleşmesi. Yeni mod sürümünde jar'ları buraya yeniden kopyala. |
| `HavenInvite.kt` | `parseInvite` / `decodeDirect` (haven-play.js) Kotlin karşılığı. |
| `HavenPlay.kt` | Davet kodu → doğrudan adres (3 sn) ya da tünel → `HAVENPLAY1 manifest` → ev sahibinin jar'ları `file <sha1>` ile → `.mrpack` (overrides/mods + overrides/havenplay.json) → Zalith'in normal modpack içe aktarması. Aynı kurulum (`key`) için ikinci katılmada profil yeniden kullanılır. Oyun başlarken (`:game` süreci) `havenplay.json` `join:true` ise adres yeniden bulunur ve `--quickPlayMultiplayer` eklenir; bayrak sonra kapanır. |
| `CfBridge.kt` | `cloudflared access tcp` yerine: yerel port → `wss://<ad>.trycloudflare.com` WebSocket, ham TCP ikili çerçevelerle (2026-11-15 yerel hızlı tünelle doğrulandı). |
| `HavenPlayCard.kt` | Çok oyunculu ekranının başında Haven Play kartı (kod, Yapıştır, Katıl). |
| `HavenAccount.kt` | Google = Haven hesabı: `havenclient.net/client/auth/google.php` relay'i (PKCE, `claim`, `setname`), sitedeki oyun adıyla çevrimdışı hesap, `/lol/api/companion/v1/auth/google` token'ı `.haven-token`. Giriş menüsünde "Google (Haven hesabı)". |
| `HavenPerformance.kt` | FPS paketi: Fabric profilinin ilk açılışında Modrinth'ten Lithium, FerriteCore, Entity Culling, ModernFix (+ zorunlu bağımlılıklar). Sodium ve ImmediatelyFast 1.0.2'de çıkarıldı (GL4ES/MobileGlues'ta çökme riski; gerçek telefonda denenince geri alınabilir). Profil başına bir kez (`.haven-perf`). Ayar: Renderer ayarları → "Haven FPS paketi". |
| `HavenSimpleHome.kt` | Basit ana sayfa (`MiscSettings.havenSimpleUi`, varsayılan açık; Ayarlar → Başlatıcı → "Basit arayüz"): 1) Hesap 2) Minecraft, sabit OYNA, sağda Haven Play kartı. `HavenQuickStart`: paketteki Haven mod sürümlerinden 1.21.x'in en yenisi + Fabric (meta.fabricmc.net) + Fabric API (Modrinth) → `.mrpack` → Zalith'in modpack içe aktarması. `LauncherScreen` basit modda bunu çizer ve Zalith'in tanıtım turunu başlatmaz. |
| `HavenCrash.kt` | `ErrorActivity` açılınca (başlatıcı ya da oyun çökmesi) hata kaydını `havenclient.net/lol/api/public/crash`'e yollar (hesapsız; aynı çökme günde bir, saatte en çok 30). Haven Rapor'da "Çökme: …" olarak görünür. |
- Microsoft girişi: Live istemcisi `00000000402b5328` (masaüstüyle aynı), login.live.com cihaz kodu. Zalith'in Azure istemci kimliği gerekmiyor.
- Varsayılan çözünürlük %80 (`RendererSettings.resolutionRatio`).
- Uygulama içi güncelleme: açılışta (saatte en fazla bir) `info/latest_version_md.json` okunur; `code` > kurulu `versionCode` ise pencere çıkar, APK tarayıcıyla iner, Android kurulumu sorar. Dosyayı her `v*` etiketinde CI yazar (`[skip ci]` commit'i). Zalith'in Çin aynası kapatıldı (Zalith sürümlerini gösteriyordu).
- 1.0.1: Zalith logosu → Haven, bağış penceresi/destekçiler kaldırıldı; Hakkında'da GPL 7(b) gereği "Zalith Launcher 2 © MovTery" satırı duruyor (silinmemeli).

## Derleme ve sürüm
- **Yerel derleme (2026-11-16, kullanıcı izniyle):** oyun açık değilken. Android Studio'nun JDK'sı + `-x :PetLib:cargoBuild` (bilgisayarda Rust/MSVC yok; `libpetlib.so` CI APK'sından `PetLib/build/petlib-jniLibs/<abi>/` içine kopyalanmış durumda):
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" CURSEFORGE_API_KEY=$(cat mcl/curseforge.txt) ./gradlew ZalithLauncher:assembleDebug -Darch=x86_64 -x :PetLib:cargoBuild`
  İlk derleme ~8 dk, sonrakiler ~30 sn. `local.properties` (sdk.dir) yerelde, depoda değil.
- **Emülatör:** AVD `Medium_Phone_API_37.0` (Android 17, x86_64). arm64 APK'yı Zalith reddediyor ("wrong version"), emülatör için `-Darch=x86_64`. CI'da da elle: workflow_dispatch `arch=x86_64`. Uygulama dili: `adb shell cmd locale set-app-locales net.havenclient.mobile.debug --locales tr-TR`. Oyunun kendisini emülatörde açma (ARM çevirisi + yazılım GPU, PC'yi yorar).
- Sürüm derlemeleri GitHub Actions'ta: `.github/workflows/haven.yml`.
- `main`'e push → arm64 Debug APK artifact. `vX.Y.Z` etiketi → imzalı arm64 ve armeabi-v7a (32-bit) Release APK'ları + tek GitHub release (ayrı `release` işi). `info/latest_version_md.json` ikisini de listeler, uygulama içi güncelleme cihazın mimarisine göre seçer. Site: `download.php?os=android` → arm64, `?os=android-arm` → 32-bit. 32-bit'te Java 25 yok, 26.x sürümleri açılmaz.
- Sürüm: `ZalithLauncher/gradle.properties` `launcher_version_code` (+1) ve `launcher_version_name`.
- İmza anahtarı `havenhub\mobile-imza\haven-mobile.jks` + `keystore.properties` (depo dışında, kaybetme: aynı anahtar olmadan güncelleme kurulmaz). Secret'lar: `HAVEN_KEYSTORE_B64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `CURSEFORGE_API_KEY`.
- Push: `git -c credential.helper= push https://x-access-token:<github.txt>@github.com/AjanKopke/haven-mobile.git main vX.Y.Z`
- Site: `mcl/website/download.php?os=android` bu deponun son release'indeki `.apk`'ya yönlendirir; Android tarayıcıda ana buton "Android için indir" olur.

## Emülatörde denenenler (2026-11-16, 1.0.2)
Başlatıcı açılışı, Basit ana sayfa, tek dokunuşla Minecraft 1.21.11 + Fabric kurulumu, çevrimdışı hesap, Mod ekle (Modrinth), sahte davet koduyla Katıl (Türkçe hata, çökme yok), Basit/Detaylı geçişi. Oyunun kendisi açılmadı.

## Çocuklar için hâlâ zor olanlar (sıradaki iş)
- İlk açılışta Zalith'in İngilizce/hukuki "Welcome" + sözleşme kutusu ve "bileşenleri kur" ekranı. Emülatörde "LWJGL 3.4.1 — Unable to create target directory" hatası bir kez çıktı, "Retry" ile geçti.
- Modpack içe aktarmada "sürüm adını gir" penceresi (Haven hızlı kurulumda sorulmadan geçilmeli).
- Ayarlar İşleyici sekmesinde açılıyor (çok teknik).
- Telefondan dünya paylaşma (ev sahibi olma) yok; davet gönderen hep bilgisayar.

## Test edilmeyenler
- Gerçek telefonda hiçbir şey denenmedi (cihaz/emülatör yok). Doğrulanan: CI derlemesi, WebSocket tünel protokolü.
- Denenmesi gerekenler: Haven Play ile masaüstü dünyasına katılma (doğrudan + tünel), Google girişi, Haven mod'un Android'de açılması, FPS paketiyle hangi renderer'ın sorunsuz olduğu (Sodium bazı GL4ES sürümlerinde sorun çıkarabilir).
- Telefondan dünya paylaşma (ev sahibi olma) yok; Zalith'in kendi "Terracotta" (EasyTier) çok oyunculusu duruyor.

## 1.0.5 / 1.0.6 (2026-11-16)
- **1.0.5 `HavenCrash.game`:** günlüğün sonu yalnız Minecraft çökme raporunun sistem bölümünü taşıyordu (ör. `processor: ~~ERROR~~ … LinuxOperatingSystemFFM` asıl neden değil; OSHI Android'de çalışmıyor). Artık günlükteki `Crash report saved to: …txt` ya da `hs_err_pid*.log` yolu açılıp ilk 60 satırı ("--- Çökme raporu ---") eklenir; başlık oradaki ilk gerçek hata satırı. `~~ERROR~~` satırları başlık olmaz.
- **1.0.6 `_Download.Single.kt`:** mod indirirken sürüm seçme listesinde, dosyanın `platformGameVersion()` listesinde olmayan Minecraft sürümlü profiller soluk ve seçilemez ("Bu sürüme uygun değil"); açık profil uygun değilse önceden seçilmez; hiçbiri uygun değilse uyarı (`haven_mod_no_fitting_version`). Yalnız `PlatformClasses.MOD`; veri eksikse engellemez. Haven'ın kendi kurulumları (`HavenSimpleHome`, `HavenPerformance`) zaten sürüme göre arıyordu.
- Açık: elle kopyalanan mod jar'ları ve önceden kurulmuş uyumsuz modlar (ör. bir oyuncunun 26.2 profilinde 1.21.11 `betterpingdisplay`) kontrol edilmiyor. Fikir: Fabric'in `HARD_DEP_INCOMPATIBLE_PRESELECTED <modid> … depends minecraft` satırı görülünce o modu `.disabled` yapıp söylemek (masaüstündeki `checkMods` benzeri).
- 16 Kasım çökmelerinin çoğu kullanıcı içeriği: yanlış sürüm modu, Forge 1.20.1 modpack, 26.4 snapshot, modpack'te Mixin hatası, iki yerel JVM çökmesi (hs_err), iki "Could not create the Java Virtual Machine". Başlatıcıda bir Compose `IllegalStateException` (Samsung, Android 17) R8 yüzünden okunamadı.
- `strings.xml`'de kesme işareti ters eğik çizgiyle kaçırılmalı; düz `can't` CI'da resource derlemesini düşürdü.
- CI (public depo) çalışıyor; v1.0.5 ve v1.0.6 arm64 + armeabi-v7a yayında.
