"""Load one recorded session and write one row per shot.

Inputs (formats in data/README.md):
  - a hand sensor CSV: t_ms, ax, ay, az, gx, gy, gz
  - a per-frame video CSV: frame, t_ms, ball_x_px, ball_y_px, ball_in_hand,
    ball_in_rim, rim_x_px, rim_y_px, floor_y_px

Both files use the phone clock in milliseconds, so the script can match
them by time stamp.

For each shot the script finds, from the video:
  - the release frame: the first frame in which the ball is out of the hand
  - the release height and the highest point of the ball, in metres,
    using the rim height (3.05 m above the floor) as the scale
  - the release angle, from the first frames after the release
  - made or missed: did the ball pass through the rim
and, from the hand sensor, in a window around the video release:
  - the peak turning speed of the hand (a first estimate of the wrist snap)
  - the time of that peak, and how far it is from the video release

This is a first version for stage 1. It is a baseline, not a trained model.

Run:
    python shots_from_session.py --sensor sample/synthetic_hand.csv \\
        --frames sample/synthetic_frames.csv --out out/synthetic_shots.csv
"""

import argparse
import math
from pathlib import Path

import numpy as np
import pandas as pd

RIM_HEIGHT_M = 3.05

SENSOR_COLUMNS = ["t_ms", "ax", "ay", "az", "gx", "gy", "gz"]
FRAME_COLUMNS = [
    "frame",
    "t_ms",
    "ball_x_px",
    "ball_y_px",
    "ball_in_hand",
    "ball_in_rim",
    "rim_x_px",
    "rim_y_px",
    "floor_y_px",
]


def load_csv(path, columns):
    df = pd.read_csv(path)
    missing = [c for c in columns if c not in df.columns]
    if missing:
        raise SystemExit(f"{path}: missing columns {missing}")
    return df.sort_values("t_ms").reset_index(drop=True)


def find_releases(frames, min_hold_frames, min_free_frames):
    """Return the index of each release frame.

    A release is a frame where the ball is out of the hand, after at least
    min_hold_frames in the hand, and the ball stays out of the hand for at
    least min_free_frames.
    """
    in_hand = frames["ball_in_hand"].fillna(0).astype(int).to_numpy()
    releases = []
    run = 0
    for i, v in enumerate(in_hand):
        if v == 1:
            run += 1
            continue
        if run >= min_hold_frames and in_hand[i : i + min_free_frames].sum() == 0:
            releases.append(i)
        run = 0
    return releases


def video_measures(frames, start, end, n_angle_frames):
    """Measures from the video for one shot. start is the release frame index."""
    shot = frames.iloc[start:end]
    rim_y = shot["rim_y_px"].median()
    floor_y = shot["floor_y_px"].median()
    if not floor_y > rim_y:
        return {}
    m_per_px = RIM_HEIGHT_M / (floor_y - rim_y)

    def height_m(y_px):
        return (floor_y - y_px) * m_per_px

    rel = frames.iloc[start]
    out = {
        "release_frame": int(rel["frame"]),
        "release_t_ms": int(rel["t_ms"]),
        "m_per_px": round(m_per_px, 5),
        "release_height_m": round(height_m(rel["ball_y_px"]), 3),
        "apex_height_m": round(height_m(shot["ball_y_px"].min()), 3),
        "made": int(shot["ball_in_rim"].fillna(0).max() > 0),
    }

    # Release angle from the release frame to a few frames later.
    after = shot.dropna(subset=["ball_x_px", "ball_y_px"]).head(n_angle_frames + 1)
    if len(after) >= 2:
        dx = abs(after["ball_x_px"].iloc[-1] - after["ball_x_px"].iloc[0])
        dy_up = after["ball_y_px"].iloc[0] - after["ball_y_px"].iloc[-1]
        out["release_angle_deg"] = round(math.degrees(math.atan2(dy_up, dx)), 1)
    else:
        out["release_angle_deg"] = np.nan
    return out


def sensor_measures(sensor, release_t_ms, before_ms, after_ms):
    """Measures from the hand sensor in a window around the video release."""
    lo = release_t_ms - before_ms
    hi = release_t_ms + after_ms
    win = sensor[(sensor["t_ms"] >= lo) & (sensor["t_ms"] <= hi)]
    if win.empty:
        return {
            "sensor_samples": 0,
            "gyro_peak_dps": np.nan,
            "gyro_peak_t_ms": np.nan,
            "gyro_peak_minus_release_ms": np.nan,
        }
    gyro = np.sqrt(win["gx"] ** 2 + win["gy"] ** 2 + win["gz"] ** 2)
    i = int(gyro.to_numpy().argmax())
    peak_t = int(win["t_ms"].iloc[i])
    return {
        "sensor_samples": len(win),
        "gyro_peak_dps": round(float(gyro.iloc[i]), 1),
        "gyro_peak_t_ms": peak_t,
        "gyro_peak_minus_release_ms": peak_t - release_t_ms,
    }


def shots_from_session(sensor, frames, args):
    sensor = sensor.copy()
    sensor["t_ms"] = sensor["t_ms"] + args.sensor_offset_ms
    releases = find_releases(frames, args.min_hold_frames, args.min_free_frames)
    rows = []
    for n, start in enumerate(releases):
        nxt = releases[n + 1] if n + 1 < len(releases) else len(frames)
        t0 = frames["t_ms"].iloc[start]
        end = start
        while end < nxt and frames["t_ms"].iloc[end] - t0 <= args.max_flight_ms:
            end += 1
        row = {"shot": n + 1}
        row.update(video_measures(frames, start, end, args.angle_frames))
        if "release_t_ms" in row:
            row.update(sensor_measures(sensor, row["release_t_ms"], args.before_ms, args.after_ms))
        rows.append(row)
    return pd.DataFrame(rows)


def parse_args():
    p = argparse.ArgumentParser(description="Write one row per shot for one recorded session.")
    p.add_argument("--sensor", required=True, help="hand sensor CSV")
    p.add_argument("--frames", required=True, help="per-frame video CSV")
    p.add_argument("--out", required=True, help="output CSV, one row per shot")
    p.add_argument("--sensor-offset-ms", type=int, default=0,
                   help="added to the sensor time stamps if the two clocks are not aligned")
    p.add_argument("--before-ms", type=int, default=300, help="sensor window before the video release")
    p.add_argument("--after-ms", type=int, default=200, help="sensor window after the video release")
    p.add_argument("--min-hold-frames", type=int, default=5, help="frames in the hand before a release")
    p.add_argument("--min-free-frames", type=int, default=3, help="frames out of the hand after a release")
    p.add_argument("--max-flight-ms", type=int, default=2000, help="how long to follow the ball after release")
    p.add_argument("--angle-frames", type=int, default=3, help="frames used for the release angle")
    return p.parse_args()


def main():
    args = parse_args()
    sensor = load_csv(args.sensor, SENSOR_COLUMNS)
    frames = load_csv(args.frames, FRAME_COLUMNS)
    shots = shots_from_session(sensor, frames, args)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    shots.to_csv(out, index=False)
    print(f"found {len(shots)} shots, wrote {out}")


if __name__ == "__main__":
    main()
