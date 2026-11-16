"""Write a tiny synthetic session to analysis/sample/.

Nothing here is a measurement. The numbers are made up so that
shots_from_session.py has something to run on. The file formats follow
data/README.md.

The fake session has three free throws. Shots 1 and 3 go in, shot 2 is short.

Run:
    python make_sample.py
"""

from pathlib import Path

import numpy as np
import pandas as pd

SEED = 0
FPS = 30
SENSOR_HZ = 120
DURATION_MS = 10500
START_MS = 1000  # phone clock time of the first frame and the first sample

# Image geometry in pixels (image y grows downwards).
FLOOR_Y_PX = 1000.0
RIM_Y_PX = 300.0
RIM_HEIGHT_M = 3.05
M_PER_PX = RIM_HEIGHT_M / (FLOOR_Y_PX - RIM_Y_PX)
SHOOTER_X_PX = 500.0
RIM_DISTANCE_M = 4.2
RIM_X_PX = SHOOTER_X_PX + RIM_DISTANCE_M / M_PER_PX
RIM_RADIUS_M = 0.23
G = 9.81

# Made-up shots: release time on the phone clock, release height,
# where the ball crosses rim height, flight time to that point,
# and the peak turning speed of the hand.
SHOTS = [
    {"release_ms": 3000, "h0_m": 2.30, "target_m": 4.20, "flight_s": 1.00, "snap_dps": 850.0},
    {"release_ms": 6000, "h0_m": 2.35, "target_m": 3.80, "flight_s": 0.95, "snap_dps": 700.0},
    {"release_ms": 9000, "h0_m": 2.25, "target_m": 4.20, "flight_s": 1.05, "snap_dps": 900.0},
]
HOLD_MS = 1000  # the ball is in the hand this long before each release
SNAP_DELAY_MS = 15  # in this fake data the gyro peak comes 15 ms after release
SNAP_SIGMA_MS = 40


def ball_position(t_ms, shot):
    """Return (x_px, y_px, in_hand, in_rim) for one shot at time t_ms, or None."""
    rel = shot["release_ms"]
    dt = (t_ms - rel) / 1000.0
    if -HOLD_MS / 1000.0 <= dt < 0:
        # Ball in the hand, rising from 1.2 m to the release height.
        frac = 1.0 + dt / (HOLD_MS / 1000.0)
        h = 1.2 + (shot["h0_m"] - 1.2) * frac
        x_m = 0.0
        return x_m, h, 1, 0
    if 0 <= dt <= shot["flight_s"] + 0.3:
        T = shot["flight_s"]
        vx = shot["target_m"] / T
        vy = (RIM_HEIGHT_M - shot["h0_m"] + 0.5 * G * T * T) / T
        x_m = vx * dt
        h = shot["h0_m"] + vy * dt - 0.5 * G * dt * dt
        if h < 0:
            return None
        descending = vy - G * dt < 0
        near_rim = abs(x_m - RIM_DISTANCE_M) < RIM_RADIUS_M and abs(h - RIM_HEIGHT_M) < 0.15
        return x_m, h, 0, int(descending and near_rim)
    return None


def make_frames(rng):
    rows = []
    n_frames = int(DURATION_MS * FPS / 1000)
    for i in range(n_frames):
        t_ms = START_MS + round(i * 1000 / FPS)
        row = {
            "frame": i,
            "t_ms": t_ms,
            "ball_x_px": np.nan,
            "ball_y_px": np.nan,
            "ball_in_hand": 0,
            "ball_in_rim": 0,
            "rim_x_px": round(RIM_X_PX + rng.normal(0, 1), 1),
            "rim_y_px": round(RIM_Y_PX + rng.normal(0, 1), 1),
            "floor_y_px": round(FLOOR_Y_PX + rng.normal(0, 1), 1),
        }
        for shot in SHOTS:
            pos = ball_position(t_ms, shot)
            if pos is None:
                continue
            x_m, h_m, in_hand, in_rim = pos
            row["ball_x_px"] = round(SHOOTER_X_PX + x_m / M_PER_PX + rng.normal(0, 2), 1)
            row["ball_y_px"] = round(FLOOR_Y_PX - h_m / M_PER_PX + rng.normal(0, 2), 1)
            row["ball_in_hand"] = in_hand
            row["ball_in_rim"] = in_rim
        rows.append(row)
    return pd.DataFrame(rows)


def make_sensor(rng):
    n = int(DURATION_MS * SENSOR_HZ / 1000)
    t_ms = START_MS + np.round(np.arange(n) * 1000 / SENSOR_HZ).astype(int)
    gx = rng.normal(0, 5, n)
    gy = rng.normal(0, 5, n)
    gz = rng.normal(0, 5, n)
    ax = rng.normal(0, 0.2, n)
    ay = rng.normal(0, 0.2, n)
    az = 9.81 + rng.normal(0, 0.2, n)
    for shot in SHOTS:
        centre = shot["release_ms"] + SNAP_DELAY_MS
        pulse = np.exp(-0.5 * ((t_ms - centre) / SNAP_SIGMA_MS) ** 2)
        gz += shot["snap_dps"] * pulse
        gx += 0.2 * shot["snap_dps"] * pulse
        ax += 15.0 * pulse
    return pd.DataFrame(
        {
            "t_ms": t_ms,
            "ax": ax.round(3),
            "ay": ay.round(3),
            "az": az.round(3),
            "gx": gx.round(2),
            "gy": gy.round(2),
            "gz": gz.round(2),
        }
    )


def main():
    rng = np.random.default_rng(SEED)
    out_dir = Path(__file__).parent / "sample"
    out_dir.mkdir(exist_ok=True)
    frames = make_frames(rng)
    sensor = make_sensor(rng)
    frames.to_csv(out_dir / "synthetic_frames.csv", index=False)
    sensor.to_csv(out_dir / "synthetic_hand.csv", index=False)
    print(f"wrote {len(frames)} frames and {len(sensor)} sensor samples to {out_dir}")


if __name__ == "__main__":
    main()
