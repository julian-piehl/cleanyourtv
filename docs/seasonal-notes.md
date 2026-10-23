# Seasonal themes: notes for later

Working notes for the `seasonal` branch. Remove this file before opening a pull request upstream.

## Open points

- **Wallpaper picker in Launcher settings** still lists pictures through `WallpaperRotation.images()`, which stops
  after 100 files. With a seasonal collection over 100 pictures, only the first 100 can be picked by hand. Rotation
  is not affected. Decided: leave it for now.
- **Reserve pictures**: once all four seasons have their own pictures, reduce the general (digit-prefixed) pictures
  in `themes/seasonal` to a single one.
- **Season pictures**: `themes/seasonal` has none yet, so the scenes landscapes show all year as the reserve. Needs
  four sets: spring, summer, autumn, winter.
- **Countdown** under the clock ("3 nights to Halloween"), modelled on the race-weekend panel. Wanted later, for
  Halloween and Christmas.
- **Build-up towards the day** and **day/night variants** for events: not now, maybe later.
- **Wallpaper generators** (`halloween_wallpapers.py`, `render_wallpapers.py`) are not in the repo yet; adding them
  to `tools/` needs rows in the README's tools table.
