# Audio sourcing + conversion

> How to find royalty-free nature recordings, convert them into seamless
> 10-minute OGG Vorbis loops at 48 kHz, and drop them into the app so
> `SoundCatalog` picks them up instead of the procedural fallback.

## Which sounds we want

Per [`audio/SoundId.kt`](../../app/src/main/java/com/sleepsound/audio/SoundId.kt),
these are the nature sounds where procedural synthesis falls short and
real recordings are needed:

| File name to drop in | What to look for |
|---|---|
| `rain.ogg` | Steady rainfall on a soft surface (leaves, ground) — avoid hard pavement / metal which sound harsh at night. 10+ minute source ideal. No thunder, no wind, no traffic. |
| `ocean.ogg` | Continuous wave-on-beach loop. Pick a recording with regular wave timing (every 6-15 s). Avoid recordings with seagulls, voices, or sudden wave crashes. |
| `thunderstorm.ogg` | Distant thunder + rain. Want a long mix where thunder is ambient, not jump-scare. |
| `fireplace.ogg` | Crackling fire, indoor recording, no music or voices. Lots of variety in pop frequency is good. |

The seven other sounds (the four noise colors, TV static, Dryer, Fan)
stay procedural — math captures them well, no sample needed.

---

## Where to source

Best-to-worst by license clarity for a paid Play Store app:

### 1. Freesound.org (CC0 filter)
The community archive with the best variety. **Always filter to
`license:cc0`** in the URL or sidebar — anything else needs attribution
or is non-commercial-only.

- Rain: [https://freesound.org/search/?q=rain+ambience&f=license:%22Creative+Commons+0%22](https://freesound.org/search/?q=rain+ambience&f=license:%22Creative+Commons+0%22)
- Ocean: [https://freesound.org/search/?q=ocean+waves+beach&f=license:%22Creative+Commons+0%22](https://freesound.org/search/?q=ocean+waves+beach&f=license:%22Creative+Commons+0%22)
- Thunderstorm: [https://freesound.org/search/?q=thunderstorm+ambience&f=license:%22Creative+Commons+0%22](https://freesound.org/search/?q=thunderstorm+ambience&f=license:%22Creative+Commons+0%22)
- Fireplace: [https://freesound.org/search/?q=fireplace+crackle&f=license:%22Creative+Commons+0%22](https://freesound.org/search/?q=fireplace+crackle&f=license:%22Creative+Commons+0%22)

You need a free account to download. Prefer source files marked
"Field recording" with **48 kHz**, **stereo**, and at least 5 minutes
length (we'll loop / crossfade to get to 10).

### 2. Pixabay sounds
All uploads are royalty-free for commercial use, no attribution
required. Smaller catalog than Freesound. https://pixabay.com/sound-effects/

### 3. Looperman / freemusicarchive
Mostly music, not nature ambience. Skip.

### 4. BBC Sound Effects archive
Public archive at https://sound-effects.bbcrewind.co.uk/. Personal-use
free; commercial release requires their separate license. Save for
later — Freesound CC0 covers v1.

---

## Format target

Drop the final files at `app/src/main/assets/sounds/<id>.ogg`:

```
app/src/main/assets/sounds/
  rain.ogg
  ocean.ogg
  thunderstorm.ogg
  fireplace.ogg
```

`SoundCatalog.create()` looks for these names (lower-cased `SoundId`
name + extension) and prefers them over the procedural generator.
`.ogg`, `.opus`, `.mp3`, `.flac`, `.wav` all work — OGG Vorbis q4 is
the recommended target (smallest size for good ambient quality).

**Target spec:**

| Property | Value |
|---|---|
| Format | OGG Vorbis |
| Quality | `-q:a 4` (~96 kbps stereo, ideal for noise) |
| Channels | 2 (stereo) |
| Sample rate | 48 000 Hz (matches the AudioTrack pipeline; avoids resampling at runtime) |
| Duration | ~10 minutes (~7 MB per file) |
| Seamless | Last sample must crossfade smoothly into the first |

---

## Conversion recipe

You need [ffmpeg](https://ffmpeg.org/) installed. On the Mac:
`brew install ffmpeg`.

### Simple case: a single long, seamless source

If you have a clean 10+ minute recording that already loops well (e.g.
a curated "10 hour rain" recording with no markers), just transcode:

```bash
ffmpeg -i source.wav \
  -ar 48000 -ac 2 \
  -c:a libvorbis -q:a 4 \
  -t 600 \
  rain.ogg
```

The `-t 600` trims to exactly 10 minutes.

### Common case: 30-second to 5-minute source, needs loop bridging

Most Freesound uploads are 30 seconds to a few minutes. To turn a short
clip into a 10-minute loop, **concatenate copies and crossfade the
seams**:

```bash
# Step 1: trim source to exactly N seconds (e.g. 30s) at a zero-crossing
ffmpeg -i source.wav -t 30 -ar 48000 -ac 2 base.wav

# Step 2: concatenate 20 copies with 2-second crossfades at each seam
# (Build the filter chain programmatically — 20 copies of base.wav
# crossfaded gives ~10 min, accounting for the crossfade overlap.)
ffmpeg \
  -i base.wav -i base.wav -i base.wav -i base.wav -i base.wav \
  -i base.wav -i base.wav -i base.wav -i base.wav -i base.wav \
  -i base.wav -i base.wav -i base.wav -i base.wav -i base.wav \
  -i base.wav -i base.wav -i base.wav -i base.wav -i base.wav \
  -filter_complex \
  "[0][1]acrossfade=d=2:c1=tri:c2=tri[x1];\
   [x1][2]acrossfade=d=2:c1=tri:c2=tri[x2];\
   [x2][3]acrossfade=d=2:c1=tri:c2=tri[x3];\
   [x3][4]acrossfade=d=2:c1=tri:c2=tri[x4];\
   [x4][5]acrossfade=d=2:c1=tri:c2=tri[x5];\
   [x5][6]acrossfade=d=2:c1=tri:c2=tri[x6];\
   [x6][7]acrossfade=d=2:c1=tri:c2=tri[x7];\
   [x7][8]acrossfade=d=2:c1=tri:c2=tri[x8];\
   [x8][9]acrossfade=d=2:c1=tri:c2=tri[x9];\
   [x9][10]acrossfade=d=2:c1=tri:c2=tri[x10];\
   [x10][11]acrossfade=d=2:c1=tri:c2=tri[x11];\
   [x11][12]acrossfade=d=2:c1=tri:c2=tri[x12];\
   [x12][13]acrossfade=d=2:c1=tri:c2=tri[x13];\
   [x13][14]acrossfade=d=2:c1=tri:c2=tri[x14];\
   [x14][15]acrossfade=d=2:c1=tri:c2=tri[x15];\
   [x15][16]acrossfade=d=2:c1=tri:c2=tri[x16];\
   [x16][17]acrossfade=d=2:c1=tri:c2=tri[x17];\
   [x17][18]acrossfade=d=2:c1=tri:c2=tri[x18];\
   [x18][19]acrossfade=d=2:c1=tri:c2=tri[out]" \
  -map "[out]" -c:a libvorbis -q:a 4 \
  rain.ogg
```

### Better case: long source where we want to bridge the file's own end-to-start seam

If you have a 10-minute recording and want to make it seamless when it
loops in-app, take the first 2 seconds and crossfade them onto the end:

```bash
# total length 600s, crossfade last 2s with the start
ffmpeg -i source.wav -filter_complex \
  "[0:a]atrim=0:598,asetpts=PTS-STARTPTS[main];\
   [0:a]atrim=0:2,asetpts=PTS-STARTPTS[head];\
   [0:a]atrim=598:600,asetpts=PTS-STARTPTS[tail];\
   [tail][head]acrossfade=d=2:c1=tri:c2=tri[bridge];\
   [main][bridge]concat=n=2:v=0:a=1[out]" \
  -map "[out]" -ar 48000 -ac 2 -c:a libvorbis -q:a 4 \
  rain.ogg
```

---

## Drop-in and test

```bash
# From the Android project root
cp /path/to/rain.ogg app/src/main/assets/sounds/rain.ogg
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.sleepsound.debug/com.sleepsound.MainActivity
```

Tap Rain on the phone — `SoundCatalog.create` will pick up
`assets/sounds/rain.ogg` instead of `procedural.Rain` automatically.
Verify in the debug log:

```bash
adb logcat -s SampleSource:* -d
```

You should see no errors. If `MediaCodec` can't decode the file, check
that the OGG was encoded properly (`ffprobe rain.ogg` should show
`vorbis, 48000 Hz, stereo`).

---

## Quality check

Before committing the file:

1. **Listen end-to-start on loop** — open in an audio player set to
   repeat the file. You shouldn't be able to identify the seam.
2. **Check file size** — `ls -l app/src/main/assets/sounds/`. Target
   under 10 MB per file.
3. **Check for clipping** — open in Audacity, scan the waveform peaks.
   Should stay below ±0.95 of full scale.
4. **Test on the phone** — full session in the actual app, both with
   headphones and through the phone speaker.

---

## License attribution

For each file shipped, record the source license + author in
`store/AUDIO_CREDITS.md` (create the file if it doesn't exist):

```markdown
- rain.ogg — derived from "Heavy rain on garden plants" by user `klankbeeld`
  on Freesound (CC0). https://freesound.org/people/klankbeeld/sounds/123456/
```

CC0 doesn't require attribution but recording it keeps the trail honest
if Google Play ever asks. For CC-BY tracks, attribution is mandatory —
include the credit string in the in-app About section *and* the Play
Store listing.
