# NutriQuest local AI setup

## Food Scout

Add these files to `app/src/debug/assets` and rebuild the Android debug app.

- `food_detector.tflite` is a licensed YOLO detection export with one float32 RGB input (NHWC or NCHW) and one float32 output shaped `[1, 4 + classes, boxes]` or `[1, boxes, 4 + classes]`. Use a fixed input size, XYWH boxes, class probabilities, and an export with NMS disabled.
- `food_labels.txt` contains one class label per line in model order. Catalog names, IDs and aliases map recognised foods to a reference nutrition entry. Unmapped suggestions offer manual nutrition entry.

For a local smoke test, the [OpenCV YOLOv8n demo export](https://huggingface.co/opencv/opencv_contribution/tree/refs%2Fpr%2F14/yolov8n) can be paired with the ordered names from the [COCO label file](https://github.com/ultralytics/ultralytics/blob/v8.3.0/ultralytics/cfg/datasets/coco.yaml). Its weights are AGPL-3.0 and its food coverage is limited to a few COCO categories. Food Scout filters out COCO’s non-food classes. The debug assets are local and ignored by Git.

## NutriRoast on Android

NutriRoast uses a small Qwen language model directly on Android through LiteRT-LM. The app uses the same local model for individual meal roasts and daily recaps.

1. Download the [Qwen3-0.6B LiteRT model](https://huggingface.co/litert-community/Qwen3-0.6B/resolve/e6bb3e4a1eb3f663153f0c1c12b735d64be657a7/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm?download=true) once (about 329 MiB). NutriQuest checks the checksum of this exact build before installing it.
2. Put the file in Downloads on your phone or emulator. To transfer it from your computer, run `adb push <path-to-model.litertlm> /sdcard/Download/`.
3. Open NutriQuest and go to **Profile → Local chef → Import roast model**. Select the file and wait for **Qwen3-0.6B is ready for offline roasts**. The app keeps a private copy, so you can remove the original download afterward.
4. Log a meal, open **Diary**, then tap **Roast this meal** or **Roast my day**. This works in airplane mode with the model installed.

The model uses the CPU and requires a compatible 64-bit Android device with enough free storage and memory. Initial loading may take several seconds. The meal and comment history use the current session's diary storage. The model remains installed between sessions.

## Profile and photo library

Open **Profile** to enter a name, diet goal and personal goal notes, daily calorie and macro targets, roast tone, chef character, and roast language. Tap **Save profile**. Preferences are saved on this device and update Home, Diary, and future roasts. Diet goals are user-entered targets.

Every tone aims to motivate the user to continue their chosen diet plan. **寸嘴 · Savage** plays a cocky accountability rival, **Extra spicy** uses theatrical tough love, **Cheeky** uses lighter sarcasm, and **Gentle** stays supportive. The joke targets excuses or an evidenced gap between the saved goal and logged choices. It does not criticise cooking quality or body size. A treat alone does not establish failure. Choose **Weight management** or **Fewer sweet snacks**, or enter a personal goal. The selected tone applies to meals and daily recaps in the chosen language and role.

Each roast includes a goal reminder derived from the saved targets and the day's log, alongside the local model's generated comment. Adding a meal refreshes the context for earlier meal comments. **Another nudge** generates a new comment. The log may represent only part of the day, and targets are user-entered rather than medical recommendations.

Open **Camera → Choose from album**, or use **Choose from album** inside the meal editor. Select a photo in Android's picker. The app creates its own normalized copy and opens the meal draft with Food Scout suggestions. Confirm foods and servings before saving.

The catalog now has 18 entries, including all ten COCO food labels and manually selectable macarons. A pizza suggestion uses a regular-crust cheese-pizza reference; it does not infer toppings, recipe or weight. Add it, then tap **Portion** to enter grams per serving. **Edit nutrition** lets you replace the estimate with values from your label. **Enter a food / nutrition label** supports other foods. Search accepts English and catalog Chinese names. The original eight demo entries remain labelled as demo servings. See [nutrition references](NUTRITION_SOURCES.md) for the new entries' sources and rounding.

The emulator's **Pictures/NutriQuest** album contains sample fruit, pizza, and chicken-and-rice photographs. The sample sources and licenses are in the companion workspace's NutriQuest-test-photos/Sources.md.
