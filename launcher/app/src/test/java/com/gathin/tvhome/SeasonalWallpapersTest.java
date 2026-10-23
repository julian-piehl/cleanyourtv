package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class SeasonalWallpapersTest {

    private static final File DIR = new File("wallpapers");
    private static final SeasonalCalendar HALLOWEEN_AND_SEASONS =
            SeasonalConfig.build(null, null, Collections.singletonList("halloween"), null);

    private static List<File> files(String... names) {
        List<File> out = new ArrayList<>();
        for (String n : names) out.add(new File(DIR, n));
        return out;
    }

    private static List<String> names(List<File> files) {
        List<String> out = new ArrayList<>();
        for (File f : files) out.add(f.getName());
        return out;
    }

    private static List<String> on(List<File> images, String date) {
        SeasonalCalendar.Day day = HALLOWEEN_AND_SEASONS.on(LocalDate.parse(date));
        return names(SeasonalWallpapers.choose(images, HALLOWEEN_AND_SEASONS, day));
    }

    private static final List<File> COLLECTION = files("01-dusk-peaks.jpg", "02-golden-coast.jpg",
            "autumn-01.jpg", "autumn-02.jpg", "halloween-01.jpg", "halloween-02.jpg", "winter-01.jpg",
            "christmas-01.jpg");

    @Test
    public void theEventsPicturesWinItsDays() {
        assertEquals(Arrays.asList("halloween-01.jpg", "halloween-02.jpg"), on(COLLECTION, "2026-10-31"));
    }

    @Test
    public void otherwiseTheSeasonsPictures() {
        assertEquals(Arrays.asList("autumn-01.jpg", "autumn-02.jpg"), on(COLLECTION, "2026-10-10"));
        assertEquals(Collections.singletonList("winter-01.jpg"), on(COLLECTION, "2027-01-10"));
    }

    @Test
    public void otherwiseTheGeneralPicturesWithDigitsOrNoPrefix() {
        List<File> images = new ArrayList<>(COLLECTION);
        images.add(new File(DIR, "plain.jpg"));
        assertEquals(Arrays.asList("01-dusk-peaks.jpg", "02-golden-coast.jpg", "plain.jpg"), on(images, "2026-04-10"));
    }

    @Test
    public void aLetterPrefixWithoutItsEventNeverShows() {
        // No event called christmas exists here, so its picture belongs to nobody's days.
        for (String date : new String[] {"2026-04-10", "2026-10-10", "2026-10-31", "2026-12-24"}) {
            assertFalse(date, on(COLLECTION, date).contains("christmas-01.jpg"));
        }
    }

    @Test
    public void generalMeansNoHyphenOrOnlyDigitsBeforeIt() {
        assertTrue(SeasonalWallpapers.general("01-dusk-peaks.jpg"));
        assertTrue(SeasonalWallpapers.general("plain.jpg"));
        assertFalse(SeasonalWallpapers.general("christmas-01.jpg"));
        assertFalse(SeasonalWallpapers.general("2b-mixed.jpg"));
    }

    @Test
    public void seasonAndEventPicturesStayOutOfTheGeneralGroup() {
        List<String> spring = on(COLLECTION, "2026-04-10");
        assertFalse(spring.contains("halloween-01.jpg"));
        assertFalse(spring.contains("winter-01.jpg"));
        assertFalse(spring.contains("christmas-01.jpg"));
    }

    @Test
    public void withNothingElseTheWholeCollection() {
        List<File> onlySeasonal = files("halloween-01.jpg", "winter-01.jpg");
        assertEquals(Arrays.asList("halloween-01.jpg", "winter-01.jpg"), on(onlySeasonal, "2026-04-10"));
        assertEquals(Collections.<String>emptyList(), on(Collections.<File>emptyList(), "2026-04-10"));
    }

    @Test
    public void eachGroupHoldsUpToTheLimitNotTheWholeCollection() {
        List<File> many = new ArrayList<>();
        for (int i = 0; i < 150; i++) many.add(new File(DIR, String.format("autumn-%03d.jpg", i)));
        for (int i = 0; i < 150; i++) many.add(new File(DIR, String.format("halloween-%03d.jpg", i)));
        List<String> halloween = on(many, "2026-10-31");
        assertEquals(SeasonalWallpapers.MAX_PER_GROUP, halloween.size());
        assertEquals("halloween-000.jpg", halloween.get(0));
        assertEquals(SeasonalWallpapers.MAX_PER_GROUP, on(many, "2026-10-10").size());
    }

    @Test
    public void prefixIsThePartBeforeTheFirstHyphen() {
        assertEquals("halloween", SeasonalWallpapers.prefix("Halloween-01-pumpkins.jpg"));
        assertNull(SeasonalWallpapers.prefix("winter.jpg"));
        assertNull(SeasonalWallpapers.prefix("-01.jpg"));
    }

    @Test
    public void listingFollowsWallpaperRotationsRules() throws IOException {
        File dir = Files.createTempDirectory("seasonal").toFile();
        try {
            write(dir, "b.jpg", 10);
            write(dir, "a.JPEG", 10);
            write(dir, "c.png", 10);
            write(dir, "d.webp", 10);
            write(dir, "empty.jpg", 0);
            write(dir, "notes.txt", 10);
            write(dir, "captions.txt", 10);
            assertTrue(new File(dir, "sub.jpg").mkdir());
            assertEquals(names(WallpaperRotation.images(dir)), names(SeasonalWallpapers.list(dir)));
            assertEquals(Arrays.asList("a.JPEG", "b.jpg", "c.png", "d.webp"), names(SeasonalWallpapers.list(dir)));
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
    }

    @Test
    public void listingHasNoOverallLimit() throws IOException {
        File dir = Files.createTempDirectory("seasonal").toFile();
        try {
            for (int i = 0; i < 120; i++) write(dir, String.format("autumn-%03d.jpg", i), 1);
            assertEquals(100, WallpaperRotation.images(dir).size());
            assertEquals(120, SeasonalWallpapers.list(dir).size());
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
    }

    @Test
    public void aSnapshotGoesStaleWhenTheDayLooksDifferent() {
        SeasonalCalendar.Snapshot s = SeasonalCalendar.Snapshot.of(HALLOWEEN_AND_SEASONS, LocalDate.parse("2026-10-23"));
        assertFalse(s.staleOn(LocalDate.parse("2026-10-23")));
        assertTrue(s.staleOn(LocalDate.parse("2026-10-24")));    // Halloween begins
        SeasonalCalendar.Snapshot h = SeasonalCalendar.Snapshot.of(HALLOWEEN_AND_SEASONS, LocalDate.parse("2026-10-25"));
        assertFalse(h.staleOn(LocalDate.parse("2026-10-31")));   // same event, no rebuild
        assertTrue(h.staleOn(LocalDate.parse("2026-11-02")));    // back to autumn
        assertFalse(SeasonalCalendar.Snapshot.OFF.staleOn(LocalDate.parse("2026-10-24")));
    }

    private static void write(File dir, String name, int bytes) throws IOException {
        Files.write(new File(dir, name).toPath(), new byte[bytes]);
    }
}
