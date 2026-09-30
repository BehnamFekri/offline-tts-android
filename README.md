# Offline Persian TTS vertical slice

This single-module Android app demonstrates an offline pipeline:

Persian text → Piper VITS model → sherpa-onnx → app-private WAV → Media3 playback.

## Models

Large model files are deliberately excluded from this repository and APK. Download a converted sherpa-onnx archive, extract it, then import its model folder from the app:

- [Amir medium](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-amir-medium.tar.bz2)
- [Ganji medium](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-ganji-medium.tar.bz2)
- [Gyro medium](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-gyro-medium.tar.bz2)

Select the extracted archive root containing the voice ONNX file, `tokens.txt`, and `espeak-ng-data/`. Import copies the selected model into the app's private files directory. Speech generation does not use a network connection.

Voice defaults are intentionally not finalized. Compare recordings on target ARM64 devices and obtain Persian speaker feedback before selecting a default.

## Model source and licensing

The model archive pages identify these as conversions of the corresponding [Piper Persian voice models](https://huggingface.co/rhasspy/piper-voices/tree/main/fa/fa_IR). Amir and Ganji model cards list Datacula datasets under CC0. Gyro's model card points to its dataset source and says to see that source for licensing, so its terms still need confirmation. The voice repository is labeled MIT, but retain per-model and dataset attribution/terms; sherpa-onnx's Apache-2.0 license does not grant rights to model datasets.

## Build

Open this directory in Android Studio and build the `app` debug variant. The build targets ARM64-v8a only. Run local unit tests with `gradlew.bat testDebugUnitTest`.
