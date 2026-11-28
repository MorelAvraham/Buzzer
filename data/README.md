# data

This folder holds only this README. Recordings are never committed to Git. Git ignores everything else in this folder.

## Data policy

- **Written consent.** Every volunteer signs a consent form before we record them. The form says what we record (video of the body and sensor data), what we use it for, where we keep it, and that they can ask us to delete it at any time.
- **Ask first.** Before the first recording with volunteers outside the team, ask the course staff if the tests need approval.
- **No names in file names.** Each player gets a code, `P01`, `P02` and so on. The list that links codes to names is kept by the team separately, not in Git and not in the shared data folder.
- **No videos or raw recordings in Git.** The repository is public. Only the small synthetic sample in `analysis/sample/` is in Git.
- **Where the data lives.** In one shared folder that only the team and the mentor can open. The place is to be decided. Write the link here when it is chosen.
- **Deleting.** If a volunteer asks, delete all their files from the shared folder and from your laptop.

## File names

One recording session gives several files with the same start:

```
<date>_<player>_<session>_<kind>.<ext>
```

For example `20261201_P01_S01_video.mp4`.

| Kind | File | Made by |
|---|---|---|
| `video` | `.mp4` | The app |
| `frametimes` | `.csv`, the phone clock time of every video frame | The app |
| `hand`, `forearm`, `upper` | `.csv`, one per sensor point. Stage 1 has `hand` only | The app |
| `meta` | `.json`, details of the session | The app, or by hand |
| `frames` | `.csv`, ball and hoop per frame | `vision/`, planned |
| `pose` | `.csv`, knee and back angles per frame | `vision/pose_to_csv.py` |
| `shots` | `.csv`, one row per shot | `analysis/shots_from_session.py` |
| `labels` | `.csv`, hand marks, for example made or missed | By hand |

## CSV columns for one recording

All times are in milliseconds on the phone clock (`t_ms`), so the files can be matched by time stamp.

**Sensor file** (`hand.csv`, and in stage 2 also `forearm.csv` and `upper.csv`):

| Column | Unit | Meaning |
|---|---|---|
| `t_ms` | ms | Time of the sample on the phone clock |
| `ax`, `ay`, `az` | m/s² | Acceleration |
| `gx`, `gy`, `gz` | degrees per second | Turning speed |
| `board_us` | microseconds | Time of the sample on the board clock, as sent by the sleeve. Kept to check the clock conversion |

**Frame times file** (`frametimes.csv`):

| Column | Meaning |
|---|---|
| `frame` | Frame number in the video, from 0 |
| `t_ms` | Time of the frame on the phone clock |
| `sensor_ts_ns` | Camera sensor time stamp of the frame, in ns. Kept to check the clock conversion |
| `matched` | 1 if a camera report matched this video frame, 0 if the time comes from the video file alone |

There is one row for every frame in the video file. If the camera timestamp source is `UNKNOWN` (see #17), `t_ms` uses an estimated offset that can be late by a few ms.

**Per-frame video file** (`frames.csv`), read by `analysis/`:

| Column | Meaning |
|---|---|
| `frame`, `t_ms` | Frame number and time on the phone clock |
| `ball_x_px`, `ball_y_px` | Centre of the ball in pixels, empty if not found |
| `ball_in_hand` | 1 if the ball is in the shooter's hand, else 0 |
| `ball_in_rim` | 1 if the ball passes down through the rim in this frame, else 0 |
| `rim_x_px`, `rim_y_px` | Centre of the rim in pixels |
| `floor_y_px` | Image row of the floor under the hoop |

**Session details** (`meta.json`): phone model, frame rate, sensor sample rate, firmware version, sleeve version, camera distance and side, shot type, number of shots, notes.

## Good first tasks

- Write the consent form for volunteers in Hebrew and English, and put the empty form in `docs/`.
- Write an example `meta.json` with every field, and a short script that checks a session folder has all the files with the right names.
