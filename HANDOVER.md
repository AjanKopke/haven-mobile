# Haven Mobile — Handover

_Başlangıç: 2026-11-15_

Haven Client'ın Android sürümü. Zalith Launcher 2'nin (GPL-3, PojavLauncher tabanlı) çatalı. Java Edition'ı telefonda çalıştıran kısım (JVM, LWJGL, renderer'lar, dokunmatik kontroller) Zalith'ten geliyor; Haven'a özel her şey `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/haven/` altında.

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
| `HavenPerformance.kt` | FPS paketi: Fabric profilinin ilk açılışında Modrinth'ten Sodium, Lithium, FerriteCore, ImmediatelyFast, Entity Culling, ModernFix (+ zorunlu bağımlılıklar). Profil başına bir kez (`.haven-perf`). Ayar: Renderer ayarları → "Haven FPS paketi". |
- Microsoft girişi: Live istemcisi `00000000402b5328` (masaüstüyle aynı), login.live.com cihaz kodu. Zalith'in Azure istemci kimliği gerekmiyor.
- Varsayılan çözünürlük %80 (`RendererSettings.resolutionRatio`).
- Uygulama içi güncelleme: açılışta (saatte en fazla bir) `info/latest_version_md.json` okunur; `code` > kurulu `versionCode` ise pencere çıkar, APK tarayıcıyla iner, Android kurulumu sorar. Dosyayı her `v*` etiketinde CI yazar (`[skip ci]` commit'i). Zalith'in Çin aynası kapatıldı (Zalith sürümlerini gösteriyordu).
- 1.0.1: Zalith logosu → Haven, bağış penceresi/destekçiler kaldırıldı; Hakkında'da GPL 7(b) gereği "Zalith Launcher 2 © MovTery" satırı duruyor (silinmemeli).

## Derleme ve sürüm
- **Yerelde Gradle çalıştırma** (PC yük altında kapanıyor). Her şey GitHub Actions'ta: `.github/workflows/haven.yml`.
- `main`'e push → arm64 Debug APK artifact. `vX.Y.Z` etiketi → imzalı arm64 Release APK + GitHub release.
- Sürüm: `ZalithLauncher/gradle.properties` `launcher_version_code` (+1) ve `launcher_version_name`.
- İmza anahtarı `havenhub\mobile-imza\haven-mobile.jks` + `keystore.properties` (depo dışında, kaybetme: aynı anahtar olmadan güncelleme kurulmaz). Secret'lar: `HAVEN_KEYSTORE_B64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `CURSEFORGE_API_KEY`.
- Push: `git -c credential.helper= push https://x-access-token:<github.txt>@github.com/AjanKopke/haven-mobile.git main vX.Y.Z`
- Site: `mcl/website/download.php?os=android` bu deponun son release'indeki `.apk`'ya yönlendirir; Android tarayıcıda ana buton "Android için indir" olur.

## Test edilmeyenler
- Gerçek telefonda hiçbir şey denenmedi (cihaz/emülatör yok). Doğrulanan: CI derlemesi, WebSocket tünel protokolü.
- Denenmesi gerekenler: Haven Play ile masaüstü dünyasına katılma (doğrudan + tünel), Google girişi, Haven mod'un Android'de açılması, FPS paketiyle hangi renderer'ın sorunsuz olduğu (Sodium bazı GL4ES sürümlerinde sorun çıkarabilir).
- Telefondan dünya paylaşma (ev sahibi olma) yok; Zalith'in kendi "Terracotta" (EasyTier) çok oyunculusu duruyor.
