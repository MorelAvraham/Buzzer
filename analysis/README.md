# analysis

Stage 1 analysis in Python. It runs on a laptop after the session.

`shots_from_session.py` loads one recorded session, matches the sensor data and the video data by time stamp and writes one row per shot.

Status: the script runs on the synthetic sample in `sample/`. It has not been run on real recordings yet.

## Files

| File | What it does |
|---|---|
| `shots_from_session.py` | Session in, one row per shot out |
| `make_sample.py` | Writes the synthetic sample in `sample/` |
| `sample/synthetic_hand.csv` | Fake hand sensor data, 120 samples per second, 10.5 s |
| `sample/synthetic_frames.csv` | Fake per-frame video data, 30 frames per second, 3 shots |
| `requirements.txt` | Python packages |

The sample is made up. It is not a measurement. Its only job is to let the script run. The real file formats are in [data/README.md](../data/README.md).

## Setup

Python 3.10 or newer.

```bash
cd analysis
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

On Windows, activate with `.venv\Scripts\activate`.

## Run on the sample

```bash
python shots_from_session.py --sensor sample/synthetic_hand.csv --frames sample/synthetic_frames.csv --out out/synthetic_shots.csv
```

The output goes to `out/`, which Git ignores. To rebuild the sample, run `python make_sample.py`.

## Output columns

| Column | Meaning |
|---|---|
| `shot` | Shot number in the session |
| `release_frame`, `release_t_ms` | First frame in which the ball is out of the hand, and its time on the phone clock |
| `m_per_px` | Scale from the rim height: 3.05 m divided by the rim to floor distance in pixels |
| `release_height_m` | Height of the ball at the release frame |
| `apex_height_m` | Highest point of the ball in the shot |
| `release_angle_deg` | Angle of the ball path over the first frames after the release |
| `made` | 1 if the ball passed through the rim, else 0 |
| `sensor_samples` | Sensor samples in the window around the release |
| `gyro_peak_dps` | Peak turning speed of the hand in that window, a first estimate of the wrist snap |
| `gyro_peak_t_ms` | Time of that peak |
| `gyro_peak_minus_release_ms` | Peak time minus the video release time |

## Limits of this first version

- The shot finder only looks at the `ball_in_hand` column from the video. It does not use the sensor to find shots yet. That is the job of U2.
- The release angle uses a straight line over 3 frames. On the synthetic sample this reads lower than the angle the generator used (50.8 degrees against 53.4 for shot 1), because the path already curves.
- The scale assumes the shooter and the hoop are at a similar distance from the camera.

## Good first tasks

- Add a `pytest` test that runs the script on the sample and checks that it finds 3 shots with `made` equal to 1, 0, 1.
- Fit a parabola to the ball path after the release and use it for the release angle and the apex. Compare with the current numbers on the sample.
- Read the pose CSV from `vision/` and add the knee angle at the release frame to each shot row.
