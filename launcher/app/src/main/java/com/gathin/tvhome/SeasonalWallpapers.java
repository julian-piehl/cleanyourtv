package com.gathin.tvhome;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Picks the part of a theme's {@code wallpapers/} collection that belongs to the day, by filename prefix: the part
 * before the first hyphen, {@code halloween-01.jpg} for the event {@code halloween} or {@code winter-03.jpg} for the
 * season. In order, the day shows
 * <ol>
 *   <li>its event's pictures, if the event has any;</li>
 *   <li>otherwise its season's pictures, if the season has any;</li>
 *   <li>otherwise the general pictures, the reserve: every one without a hyphen or with only digits before it
 *       ({@code 01-dusk-peaks.jpg} from the scenes theme is one);</li>
 *   <li>and if there are none of those either, the whole collection, so a seasonal theme never shows a bare
 *       background.</li>
 * </ol>
 * A picture with a letter in its prefix always belongs to a season or an event and only shows in its own time: when
 * that season or event is switched off, or no event of that name exists ({@code christmas-01.jpg} before there is a
 * Christmas event), it never shows.
 * <p>
 * The collection is listed here rather than with {@link WallpaperRotation#images}, which stops after 100 pictures in
 * all; a seasonal theme may hold up to {@link #MAX_PER_GROUP} for each season and event. Which files count as
 * pictures follows WallpaperRotation's rules exactly (SeasonalWallpapersTest compares the two).
 */
final class SeasonalWallpapers {

    /** Most pictures one season, event or the general group may rotate through, as WallpaperRotation allows in all. */
    static final int MAX_PER_GROUP = 100;
    /** WallpaperRotation's limit for one picture. */
    private static final long MAX_BYTES = 10 * 1024 * 1024;

    private SeasonalWallpapers() {
    }

    /** The pictures in {@code folder} for {@code day}. */
    static List<File> select(File folder, SeasonalCalendar calendar, SeasonalCalendar.Day day) {
        return choose(list(folder), calendar, day);
    }

    /** Every picture in {@code folder}, sorted by name, with WallpaperRotation's rules but no overall limit. */
    static List<File> list(File folder) {
        List<File> result = new ArrayList<>();
        File[] entries = folder == null ? null : folder.listFiles();
        if (entries == null) return result;
        Arrays.sort(entries, Comparator.comparing(File::getName));
        for (File entry : entries) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (entry.isFile() && entry.length() > 0 && entry.length() <= MAX_BYTES
                    && (name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".png") || name.endsWith(".webp"))) {
                result.add(entry);
            }
        }
        return result;
    }

    /** The day's group of {@code images}, by the order in the class comment. */
    static List<File> choose(List<File> images, SeasonalCalendar calendar, SeasonalCalendar.Day day) {
        if (day.event != null) {
            List<File> g = group(images, day.event);
            if (!g.isEmpty()) return g;
        }
        if (day.season != null) {
            List<File> g = group(images, day.season.id);
            if (!g.isEmpty()) return g;
        }
        List<File> general = new ArrayList<>();
        for (File f : images) {
            if (general(f.getName()) && general.size() < MAX_PER_GROUP) general.add(f);
        }
        if (!general.isEmpty()) return general;
        return new ArrayList<>(images.subList(0, Math.min(images.size(), MAX_PER_GROUP)));
    }

    private static List<File> group(List<File> images, String id) {
        List<File> out = new ArrayList<>();
        for (File f : images) {
            if (id.equals(prefix(f.getName())) && out.size() < MAX_PER_GROUP) out.add(f);
        }
        return out;
    }

    /** Whether a picture is a general one: no hyphen, or only digits before the first one. */
    static boolean general(String filename) {
        String p = prefix(filename);
        if (p == null) return true;
        for (int i = 0; i < p.length(); i++) {
            if (p.charAt(i) < '0' || p.charAt(i) > '9') return false;
        }
        return true;
    }

    /** The lower-case part before the first hyphen, or null without one ({@code winter.jpg} is general). */
    static String prefix(String filename) {
        int dash = filename.indexOf('-');
        return dash <= 0 ? null : filename.substring(0, dash).toLowerCase(Locale.ROOT);
    }
}
