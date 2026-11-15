# X Shot - Android Screenshot App 📱✨

Turn any X / Twitter post link into clean, high-resolution graphic screenshots (inspired by aesthetic headline cards with side-by-side media grids).

---

## ✨ Features
- **Clean Headline Style**: Renders bold text and media with rounded corners and clean margins (no clutter, no like/retweet counters).
- **Multiple Layout Options**:
  - **Headline Only (Minimal)**: Matches aesthetic news cards (exactly like your reference image).
  - **Full Post**: Includes user avatar, name, handle, and verified badge.
- **Smart Aspect Ratios**: 4:5 vertical (Instagram feed/story) and 1:1 (square).
- **Side-by-Side Media Grid**: Automatically lays out 2 photos side-by-side with rounded borders.
- **Android Share Sheet Integration**: Tap "Share" from inside the X / Twitter app and pick **X Shot** to generate instantly!
- **Save to Gallery**: Saves directly to `Pictures/XPostShot/` on your Android device.

---

## 🚀 How to Get Your APK

### Option 1: Automatic Cloud Build (Recommended — No Android Studio Needed)
We have included a pre-configured **GitHub Actions CI/CD workflow** in [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml).
1. Initialize git and push this folder to your GitHub:
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin https://github.com/YOUR_USER/x-shot.git
   git push -u origin main
   ```
2. Go to the **Actions** tab on your GitHub repository.
3. GitHub will compile the APK automatically in ~90 seconds.
4. Download the `XShot-Debug-APK` zip file, extract `app-debug.apk`, and install it on your phone!

### Option 2: Build Locally with Android Studio
1. Open Android Studio.
2. Choose **Open** and select the `x-post-shot-android` folder.
3. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Android Studio will output `app-debug.apk`.

### Option 3: Test Instantly in Browser / Mobile Web
You can run the web companion immediately:
```bash
python -m http.server 8080 --directory web
```
Open `http://localhost:8080` (or your PC's IP on your phone's browser) and tap **Add to Home screen** to install it as an Android app!
