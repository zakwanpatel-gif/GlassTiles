# Glass Tiles Launcher (Android)

Your glass-tiles home screen as a real Android launcher:
- Your 9 wallpapers, rotating every 30 minutes with a crossfade
- Weather tile with time and date (live weather for Mbombela), namaaz times and the Hijri date — swipe the tile
- Search bar for apps and the web, with an **Ask** button that opens Claude
- Most used apps (learns from what you open)
- Dock with your phone's own Phone, Messages, Browser and Camera apps
- Page 2: all your apps sorted into Apple-style folders you can make bigger or smaller (tap **Edit**)
- Hold any app to move it to another folder, see App info, or uninstall
- Uses each app's real icon

---

## Step 1 — Turn the project into an APK (no coding needed)

**Option A: GitHub builds it for you (free, about 10 minutes)**
1. Go to github.com, sign in (or create a free account).
2. Click **+** (top right) → **New repository** → name it `glass-tiles` → **Private** → **Create repository**.
3. On the new page click **uploading an existing file**.
4. Unzip `glass-tiles-launcher.zip` on your computer, open the folder, select **everything inside it** and drag it into the GitHub page. Click **Commit changes**.
   - Make sure the `.github` folder uploads too (on Mac press Cmd+Shift+. to show hidden folders; on Windows tick View → Hidden items).
5. Click the **Actions** tab. A job called **Build APK** starts by itself. Wait for the green tick (about 5–8 minutes).
6. Click the finished job → scroll to **Artifacts** → download **GlassTiles-APK**. Unzip it to get `GlassTiles.apk`.

**Option B: Android Studio**
1. Install Android Studio (free) and open this folder.
2. Let it finish syncing, then **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. Click **locate** to find `app-debug.apk`.

## Step 2 — Install on your Samsung
1. Send the APK to your phone (WhatsApp it to yourself, Google Drive, or USB cable).
2. Tap the APK on the phone. If asked, allow **Install unknown apps** for that app (Files, Chrome or WhatsApp), then tap **Install**.

## Step 3 — Make it your home screen
1. Press the **Home** button. When asked which app to use, choose **Glass Tiles** → **Always**.
2. If it doesn't ask: **Settings → Apps → Choose default apps → Home app → Glass Tiles**.

To go back to Samsung's home screen any time: same setting, choose **One UI Home**.

---

## Good to know
- Weather comes from Open-Meteo (free, no account) for Mbombela and needs internet; it refreshes every 20 minutes.
- Namaaz times are calculated on the phone for Mbombela (Fajr 18°, Isha 17°). Asr is Hanafi by default — switch it on page 2. Compare once with your local masjid's timetable.
- **Ask** opens Claude with your question (the Claude app if installed, otherwise claude.ai).
- Notification pull-down, recent apps and gestures still work as normal — they belong to Android, not the launcher.
