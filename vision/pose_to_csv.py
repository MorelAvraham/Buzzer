"""Run MediaPipe Pose on a video file and write knee and back angles per frame.

Output columns:
  frame           frame number, from 0
  t_ms            time of the frame in ms (video time plus --t0-ms)
  pose_found      1 if a body was found in the frame, else 0
  left_knee_deg   angle at the left knee between hip and ankle, 180 = straight leg
  right_knee_deg  same for the right knee
  back_deg        angle of the hip-to-shoulder line from vertical, 0 = upright
  min_visibility  lowest MediaPipe visibility score of the points used

Angles are computed in pixels, not in the 0 to 1 image coordinates, so they
are not bent by the shape of the frame. They are 2D angles in the image
plane. With the camera to the side of the shooter this is close to the side
view of the body, but it is not a 3D angle.

Run:
    python pose_to_csv.py VIDEO --out out/pose.csv
"""

import argparse
import csv
import math
from pathlib import Path

import cv2
import mediapipe as mp
from mediapipe.tasks.python import BaseOptions
from mediapipe.tasks.python import vision

DEFAULT_MODEL = Path(__file__).parent / "pose_landmarker_lite.task"

# MediaPipe Pose landmark numbers.
L_SHOULDER, R_SHOULDER = 11, 12
L_HIP, R_HIP = 23, 24
L_KNEE, R_KNEE = 25, 26
L_ANKLE, R_ANKLE = 27, 28
USED = [L_SHOULDER, R_SHOULDER, L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE]

COLUMNS = ["frame", "t_ms", "pose_found", "left_knee_deg", "right_knee_deg", "back_deg", "min_visibility"]


def angle_at(a, b, c):
    """Angle ABC in degrees, from 2D points in pixels."""
    v1 = (a[0] - b[0], a[1] - b[1])
    v2 = (c[0] - b[0], c[1] - b[1])
    n1 = math.hypot(*v1)
    n2 = math.hypot(*v2)
    if n1 == 0 or n2 == 0:
        return float("nan")
    cos = (v1[0] * v2[0] + v1[1] * v2[1]) / (n1 * n2)
    return math.degrees(math.acos(max(-1.0, min(1.0, cos))))


def back_from_vertical(hip, shoulder):
    """Angle of the hip-to-shoulder line from vertical, in degrees."""
    dx = shoulder[0] - hip[0]
    dy_up = hip[1] - shoulder[1]  # image y grows downwards
    return math.degrees(math.atan2(abs(dx), dy_up))


def midpoint(p, q):
    return ((p[0] + q[0]) / 2, (p[1] + q[1]) / 2)


def angles_from_landmarks(landmarks, width, height):
    px = {i: (landmarks[i].x * width, landmarks[i].y * height) for i in USED}
    vis = min(landmarks[i].visibility for i in USED)
    return {
        "left_knee_deg": round(angle_at(px[L_HIP], px[L_KNEE], px[L_ANKLE]), 1),
        "right_knee_deg": round(angle_at(px[R_HIP], px[R_KNEE], px[R_ANKLE]), 1),
        "back_deg": round(
            back_from_vertical(midpoint(px[L_HIP], px[R_HIP]), midpoint(px[L_SHOULDER], px[R_SHOULDER])), 1
        ),
        "min_visibility": round(vis, 3),
    }


def run(video_path, model_path, out_path, t0_ms):
    if not Path(model_path).exists():
        raise SystemExit(f"model not found: {model_path}. See vision/README.md for the download command.")
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise SystemExit(f"cannot open video: {video_path}")
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0

    options = vision.PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=str(model_path)),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
    )
    out_path = Path(out_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    n_frames = 0
    n_found = 0
    with vision.PoseLandmarker.create_from_options(options) as landmarker, open(out_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        while True:
            ok, frame_bgr = cap.read()
            if not ok:
                break
            video_ms = round(n_frames * 1000 / fps)
            rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
            image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            result = landmarker.detect_for_video(image, video_ms)
            row = {"frame": n_frames, "t_ms": video_ms + t0_ms, "pose_found": 0}
            if result.pose_landmarks:
                h, w = frame_bgr.shape[:2]
                row["pose_found"] = 1
                row.update(angles_from_landmarks(result.pose_landmarks[0], w, h))
                n_found += 1
            writer.writerow(row)
            n_frames += 1
    cap.release()
    print(f"{n_frames} frames, body found in {n_found}, wrote {out_path}")


def main():
    p = argparse.ArgumentParser(description="Knee and back angles per frame from a video, with MediaPipe Pose.")
    p.add_argument("video", help="video file")
    p.add_argument("--out", required=True, help="output CSV")
    p.add_argument("--model", default=str(DEFAULT_MODEL), help="MediaPipe pose landmarker .task file")
    p.add_argument("--t0-ms", type=int, default=0, help="phone clock time of the first frame, added to t_ms")
    args = p.parse_args()
    run(args.video, args.model, args.out, args.t0_ms)


if __name__ == "__main__":
    main()
