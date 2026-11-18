# Store assets

Everything here is generated from files in this repository or captured from the real app, so the
listing cannot drift from what ships.

## Icons

| File | Source | Size |
|---|---|---|
| `play-icon-512.png` | `icon.svg` | 512 × 512, 24-bit |
| `feature-graphic-1024x500.png` | `feature-graphic.svg` | 1024 × 500, 24-bit |

The SVG uses the same geometry as `app/src/main/res/drawable/ic_launcher_foreground.xml`, so
regenerating the launcher icon means re-rendering these too. Render with headless Chrome:

```
"C:\Program Files\Google\Chrome\Application\chrome.exe" --headless=new --disable-gpu ^
  --hide-scrollbars --force-device-scale-factor=1 --window-size=512,512 ^
  --screenshot=play-icon-512.png file:///D:/VEGA/BrainStorm/docs/store/icon.svg
```

## Phone screenshots

`screenshots/01-dashboard.png` … `04-settings.png`, all 1080 × 2160.

Play requires an aspect ratio of at most 2:1, and the emulator is 1080 × 2340 (2.17:1), so
cropping would have cut the navigation labels off. Instead the display is resized and the app is
captured at its native layout:

```
adb -s emulator-5554 shell wm size 1080x2160     # 2:1, no cropping
```

then, for each tab, `adb shell screencap -p` and:

```
magick cap.png -alpha off -depth 8 PNG24:screenshots/01-dashboard.png
```

24-bit PNG is deliberate: Play rejects alpha for screenshots. After capturing, restore the
emulator with `adb shell wm size reset`.

The captures come from the **consumer release build** with the real frozen policy (3 apps,
1 h budget, 24 h lock) and real usage from that day, so the numbers in the listing are the numbers
the app shows.
