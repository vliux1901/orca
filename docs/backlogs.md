# Backlogs

## TODO

### OCR Orientation Handling

Problem:

- Rotated photos can produce OCR text in the wrong reading order.
- Example: a document photographed or stored at 90 degrees may still be recognized left-to-right relative to the rotated pixels, not the intended document orientation.

Planned solution for prototype:

1. Honor EXIF orientation before OCR.
2. Add a per-image `Rotate 90°` action and rerun OCR.
3. Later, consider auto-trying 0, 90, 180, and 270 degrees and selecting the best OCR result using a heuristic score.

Implementation notes:

- Prefer feeding ML Kit an upright bitmap via `InputImage.fromBitmap(bitmap, 0)` instead of relying only on `InputImage.fromFilePath(context, uri)`.
- Use `ExifInterface` to detect metadata rotation when available.
- Keep manual rotation available because some images have incorrect or missing EXIF metadata.
