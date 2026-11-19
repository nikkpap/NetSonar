# NetSonar 📡

**Modern IP & Wi-Fi scanner for Android** — by **Nikolaos K. Paridis (aka nikkpap)** · ALU DEV TEAM

English / Ελληνικά · Dark / Light · OTA updates from GitHub Releases

## Features
- **Network**: SSID, band (2.4 / 5 / 6 GHz), channel, frequency, signal gauge, link speed, Wi-Fi standard (Wi-Fi 4/5/6/7),
  local IP, gateway, subnet mask, DNS, IPv6, phone MAC, router MAC (BSSID), **public IP (WAN)**, ISP, approximate location.
  Tap any tile to copy.
- **Devices**: animated radar scan of the whole subnet (ICMP + 16 TCP ports), name (reverse DNS / NetBIOS),
  MAC (NetBIOS / ARP), manufacturer (macvendors.com, cached), device type icon, response time, open ports,
  open in browser, share. Filters **All / 2.4 GHz / 5 GHz / 6 GHz / LAN** with live counts, plus search.
- **Wi-Fi**: nearby access points with band filter, channel map (spectrum chart), **best channel advice**,
  security (WPA3/WPA2/Open), channel width.
- **More**: language, theme, OTA update check, help.

> Android 10+ does not allow apps to read other devices' MAC addresses from the ARP table.
> NetSonar works around it where possible (NetBIOS answers from Windows PCs, NAS and many smart devices).

---

## 🇬🇧 Step by step

### 1. Open & run
1. Unzip, open the **NetSonar** folder in **Android Studio** (File → Open).
2. Wait for Gradle sync (first time downloads everything automatically).
3. Plug your phone (USB debugging on) → press ▶ **Run**.

### 2. Signing key (one time — needed for OTA)
1. From the separate **NetSonar_KEYSTORE_PRIVATE** zip copy `keystore.properties` and the `keystore` folder
   into the project root (next to `settings.gradle.kts`). They are git-ignored.
2. *Build → Generate App Bundles or APKs → Build APK(s)* with the **release** variant, or run `gradlew assembleRelease`.
   Output: `app/build/outputs/apk/release/NetSonar_v1.0.0.apk`.

### 3. GitHub + OTA
1. Create a **public** repo `nikkpap/NetSonar` and push the project (keystore is NOT uploaded thanks to `.gitignore`;
   if you upload by drag-and-drop in the browser, do it **before** copying the keystore in).
2. Add the 4 secrets from `GITHUB_SECRETS.txt` (Settings → Secrets and variables → Actions).
3. Every push to `main`: if the `versionName` has no Release yet, GitHub Actions builds the signed APK and publishes
   Release `vX.Y.Z` with `RELEASE_NOTES.md` as text. The app finds it automatically.

### 4. New version
1. In `app/build.gradle.kts`: raise `versionCode` (+1) and `versionName` (e.g. `1.0.1`).
2. Write the changes in `RELEASE_NOTES.md`.
3. Commit & push → done. Phones get the "Version 1.0.1 is available!" dialog.

---

## 🇬🇷 Βήμα-βήμα

### 1. Άνοιγμα & εκτέλεση
1. Αποσυμπίεσε και άνοιξε τον φάκελο **NetSonar** στο **Android Studio** (File → Open).
2. Περίμενε το Gradle sync (την πρώτη φορά κατεβάζει μόνο του ό,τι χρειάζεται).
3. Σύνδεσε το κινητό (USB debugging) → πάτα ▶ **Run**.

### 2. Κλειδί υπογραφής (μία φορά — απαραίτητο για OTA)
1. Από το ξεχωριστό zip **NetSonar_KEYSTORE_PRIVATE** αντέγραψε το `keystore.properties` και τον φάκελο `keystore`
   στη ρίζα του project (δίπλα στο `settings.gradle.kts`). Αγνοούνται από το git.
2. *Build → Build APK(s)* σε **release**, ή `gradlew assembleRelease` →
   `app/build/outputs/apk/release/NetSonar_v1.0.0.apk`.

### 3. GitHub + OTA
1. Φτιάξε **public** repo `nikkpap/NetSonar` και ανέβασε το project (το keystore ΔΕΝ ανεβαίνει λόγω `.gitignore`·
   αν ανεβάζεις με drag-and-drop από τον browser, κάν' το **πριν** βάλεις μέσα το keystore).
2. Πρόσθεσε τα 4 secrets από το `GITHUB_SECRETS.txt` (Settings → Secrets and variables → Actions).
3. Σε κάθε push στο `main`: αν η `versionName` δεν έχει ακόμα Release, το GitHub Actions φτιάχνει το υπογεγραμμένο APK
   και δημοσιεύει Release `vX.Y.Z` με κείμενο το `RELEASE_NOTES.md`. Η εφαρμογή το βρίσκει μόνη της.

### 4. Νέα έκδοση
1. Στο `app/build.gradle.kts`: αύξησε `versionCode` (+1) και `versionName` (π.χ. `1.0.1`).
2. Γράψε τις αλλαγές στο `RELEASE_NOTES.md`.
3. Commit & push → τέλος. Τα κινητά βλέπουν «Διαθέσιμη η έκδοση 1.0.1!».

---
Online services used: ipinfo.io (public IP), api.macvendors.com (manufacturers), api.github.com (updates).
