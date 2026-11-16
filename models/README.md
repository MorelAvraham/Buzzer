# models

Trained models for Buzzer. For example: the ball detector for the video, and the sleeve model that finds each shot and its release time.

Status: no models trained yet.

## Where the model files go

Model files are not stored in Git. Git ignores `.tflite`, `.onnx`, `.pt`, `.pth`, `.ckpt` and `.task` files. Put the files in the shared data folder (see [data/README.md](../data/README.md)), in a `models/` folder there.

In Git, this folder keeps one short text file per model, so we always know what each file is. Name it `<model>_<date>.md`, for example `release_time_20261215.md`, and write in it:

- what the model does
- the file name in the shared folder
- the code and the commit that trained it
- the data it was trained and tested on (session names, number of shots, number of players)
- the results on the test data, with the number of shots, marked as measured
- known problems

## Stage 1 and stage 2

- **Stage 1.** Models are trained and run on a laptop in Python.
- **Stage 2.** The models that run in real time are exported to LiteRT (the new name of TensorFlow Lite, `.tflite` files) and run on the phone. Before a model goes to the phone, check that its output on the phone matches its output on the laptop for the same input.

## Good first tasks

- Write the text file template described above as `TEMPLATE.md` in this folder.
- Find out how to export a small PyTorch or Keras model to LiteRT, try it on a tiny model, and write the exact steps in this README.
