# Open questions U1 to U6

Each question has a quick test that should answer it fast, and a backup plan if the answer is no. Each one also has a GitHub issue with the label `experiment`. Write the result in the issue and update the status here.

| ID | Question | What we know | Quick test | Backup | Milestone | Owner | Status |
|---|---|---|---|---|---|---|---|
| U1 | Can we get release time, release height and made or missed from a phone video? | The rim is 3.05 m above the floor and can be used as a scale. A frame lasts about 33 ms at 30 fps. | Film 100 free throws and mark them by hand. Hold the ball at marked heights and compare. | Mark made or missed by hand in the app, and use the player's height as a second scale. | MVP demo (Jan 2027) | To be decided | Not started |
| U2 | Can one sensor find each shot and its release time? | The video gives a release frame that can be used as a label. | Record 300 shots with one sensor and the camera, train a model and compare with the release frame from the video. | Take the release time from the camera. | MVP demo (Jan 2027) | To be decided | Not started |
| U3 | Are the elbow and wrist angles from the sleeve accurate? | Angles from IMUs drift over time. The camera can check the elbow angle. | 50 slow shots, compare the elbow angle with the video and with a goniometer. | A fixed pose before each set to reset the angles, and correction from the camera. | Final exhibition (Jun 2027) | To be decided | Not started |
| U4 | Do three sensors on cables survive shooting? | Cables on a moving arm can break or come loose. | 500 shots with a first sleeve, count lost samples and cable faults. | A sleeve with two sensor points. | Final exhibition (Jun 2027) | To be decided | Not started |
| U5 | Can the analysis run on the phone fast enough? | The target delay from release to vibration is 0.5 s, with a minimum of 1 s. | Measure the processing time per shot on the phone, and the delay to the vibration with slow-motion video. | Give the feedback at the end of each set. | Final exhibition (Jun 2027) | To be decided | Not started |
| U6 | Does the vibration help the player? | Nothing yet. | One session with three shooters, with the vibration on and off. | Show the feedback on the phone screen, and report the result as it is. | Final exhibition (Jun 2027) | To be decided | Not started |

## How to run an experiment

1. Open the issue for the question and assign yourself.
2. Write the plan in the issue before you start: how many shots, who shoots, which files you will record.
3. Record the data as described in [data/README.md](../data/README.md).
4. Write the result in the issue with the real numbers, even if the answer is no.
5. Add a line to [decisions.md](decisions.md) if the result changes the plan.
