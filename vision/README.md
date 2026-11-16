# vision

Video analysis in Python. In stage 1 it runs on a laptop after the session. In stage 2 the same steps move to the phone.

There are two parts:

1. **Body pose.** `pose_to_csv.py` runs MediaPipe Pose on a video and writes the knee and back angles per frame. This script exists.
2. **Ball and hoop detector.** Finds the hoop, follows the ball and writes the per-frame CSV that `analysis/` reads. This is planned and not built yet. See below.

## Setup

Python 3.10 or newer.

```bash
cd vision
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Download the MediaPipe pose model (about 6 MB) into this folder. Git ignores `.task` files.

```bash
curl -L -o pose_landmarker_lite.task https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task
```

On a Linux machine without a desktop you may get `libEGL.so.1: cannot open shared object file`. Install the missing libraries with `sudo apt-get install libegl1 libgles2`.

We ran the script with mediapipe 1.2.3, opencv-python 5.0.0.93 and Python 3.13. Older mediapipe versions may work but we have not tried them.

## Run the pose script

```bash
python pose_to_csv.py my_video.mp4 --out out/my_video_pose.csv
```

Use `--t0-ms` to add the phone clock time of the first frame, so the times match the sensor CSV. The output goes to `out/`, which Git ignores. Do not commit videos. See [data/README.md](../data/README.md).

Output columns:

| Column | Meaning |
|---|---|
| `frame` | Frame number, from 0 |
| `t_ms` | Time of the frame in ms, video time plus `--t0-ms` |
| `pose_found` | 1 if a body was found, else 0 |
| `left_knee_deg`, `right_knee_deg` | Angle at the knee between hip and ankle, 180 is a straight leg |
| `back_deg` | Angle of the hip-to-shoulder line from vertical, 0 is upright |
| `min_visibility` | Lowest MediaPipe visibility score of the points used |

The angles are 2D angles in the image, computed in pixels. With the camera to the side of the shooter they are close to the side view of the body, but they are not 3D angles.

What we checked so far: the script runs on a short video made from one still photo of a person in a lunge, and on a video with no person. The angles on the photo look right by eye. We have not compared them with a measured angle and we have not run the script on basketball video yet.

## Planned: ball and hoop detector

Not built yet. This is the plan, open to change.

**Goal.** For every frame, write one row of the per-frame CSV that `analysis/shots_from_session.py` reads:

| Column | Meaning |
|---|---|
| `frame`, `t_ms` | Frame number and phone clock time |
| `ball_x_px`, `ball_y_px` | Centre of the ball, empty if not found |
| `ball_in_hand` | 1 if the ball is in the shooter's hand |
| `ball_in_rim` | 1 if the ball passes down through the rim in this frame |
| `rim_x_px`, `rim_y_px` | Centre of the rim |
| `floor_y_px` | Image row of the floor under the hoop |

**Steps.**

1. **Hoop and floor.** The tripod does not move during a session, so the rim and the floor can be found once per session. The first version can be a click on the first frame. A detector can replace the click later.
2. **Ball.** A small object detector, for example a YOLO model, fine-tuned on our own side-view frames of free throws. The model choice is not decided yet.
3. **Tracking.** Join the ball detections from frame to frame, and fill short gaps.
4. **Ball in hand.** The ball is in the hand when it is close to the wrist point from the pose model. The distance limit is to be found from data.
5. **Ball in rim.** The ball centre moves down through the rim line between the two edges of the rim.
6. **Scale.** The rim is 3.05 m above the floor. The rim to floor distance in pixels gives metres per pixel.

U1 in [docs/experiments.md](../docs/experiments.md) checks whether this gives good enough release time, release height and made or missed.

## Good first tasks

- Add a `--draw` option to `pose_to_csv.py` that writes a copy of the video with the body points and the angles drawn on it, so we can check the angles by eye.
- Write a small tool that shows the first frame of a video, lets you click the rim centre and the floor, and saves `rim_x_px`, `rim_y_px` and `floor_y_px` to a JSON file.
- Film 20 seconds of a teammate shooting from the side (with their consent) and run `pose_to_csv.py` on it. Plot the knee angle over time and write in an issue what you see.
