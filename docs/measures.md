# Who measures what

| Measure | Camera | Sleeve |
|---|---|---|
| Made or missed | Yes | No |
| Release height | Yes, in metres | No |
| Arc of the ball | Yes | No |
| Legs and back posture | Yes, from a body pose model | No |
| Release time | Yes, one frame every 33 ms | Yes, expected to be more exact |
| Elbow angle | Check only | Yes |
| Wrist snap | No | Yes |
| Vibration feedback | No | Yes, stage 2 only |

## How each measure is found

These are the planned methods. None of them is built or tested yet.

- **Made or missed.** The video analysis follows the ball and checks if it passes through the rim.
- **Release height.** The rim is 3.05 m above the floor. The distance in pixels between the rim and the floor gives the number of metres per pixel. The height of the ball at the release frame is then turned into metres. This assumes the shooter and the hoop are at a similar distance from the camera. U1 checks this.
- **Arc of the ball.** The path of the ball after the release, in metres, from the same scale.
- **Legs and back posture.** MediaPipe Pose gives the body points per frame. From them we compute the knee angles and the angle of the back. See [vision/README.md](../vision/README.md).
- **Release time, camera.** The first frame in which the ball is out of the hand. At 30 frames per second a frame lasts about 33 ms, so this time is only known to about 33 ms.
- **Release time, sleeve.** Found from the hand sensor. The sensor samples much faster than the camera, so we expect it to be more exact. U2 checks this.
- **Elbow angle.** From the upper-arm and forearm sensors (stage 2). The camera can only check it, because the arm is often hidden or seen at an angle.
- **Wrist snap.** From the forearm and hand sensors (stage 2). In stage 1 there is only the hand sensor, so we use the peak turning speed of the hand as a first estimate.

## Camera values as labels

The camera values are also the labels used to train and test the sleeve models. For example, the release frame from the video is the label for the sleeve release time model in U2.
