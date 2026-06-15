# Prototype Plan: V0.1 OCR And Deterministic Extraction

## Purpose

Build a quick Android prototype to validate two technical risks from `docs/technical-design.md`:

1. How well on-device OCR performs on different Android phones, especially for table-heavy maintenance documents.
2. How well deterministic fact extractors perform on OCR output.

This prototype intentionally excludes the full product experience. It does not need vehicle profiles, database storage, timeline persistence, account handling, or polished UI.

## Scope

V0.1 supports:

- Import one or multiple photos from the gallery.
- Run OCR on each selected image using on-device ML Kit Text Recognition.
- Show OCR output to the user.
- Run deterministic fact extractors on recognized text.
- Show extracted facts, confidence, source text, and simple warnings.
- Capture enough debug information to compare OCR quality across devices.

V0.1 does not support:

- PDFs.
- Camera capture.
- Database persistence.
- Remote AI extraction.
- Document upload.
- Timeline creation.
- Vehicle profiles.
- Fancy UI or production navigation.

V0.2 can add direct original-document AI extraction after V0.1 establishes OCR and rule-based baselines.

## Success Criteria

The prototype is useful if we can answer:

- Does ML Kit OCR recognize text accurately on real maintenance document photos?
- How often does OCR preserve enough row/table structure for deterministic extraction?
- Which phones perform poorly due to camera image quality, memory, OCR latency, or rendering differences?
- Which fact types are reliable with deterministic extractors?
- Which document layouts require AI fallback in V0.2?

Minimum output per imported image:

- Original image preview.
- OCR full text.
- OCR text blocks or rows in reading order.
- Extracted facts grouped by type.
- Confidence per fact.
- Source line or row per fact.
- OCR/extraction duration.
- Device/debug metadata.

## User Flow

```text
Open prototype screen
  -> tap Import Photos
  -> select one or multiple images from gallery
  -> app shows selected image list
  -> app runs OCR for each image
  -> app displays OCR text and normalized rows
  -> user accepts OCR results
  -> app runs deterministic extractors
  -> app displays extracted facts and warnings
```

## UI Requirements

Keep UI simple and functional.

Single screen is acceptable:

- `Import Photos` button.
- List of imported images.
- Per-image expandable result card.

Each image result card should show:

- Thumbnail or full-width preview.
- Processing status: `queued`, `ocr_running`, `extracting`, `done`, `failed`.
- OCR elapsed time.
- Extractor elapsed time.
- OCR full text in a selectable text area.
- Normalized rows section for table debugging.
- Extracted facts table.
- Warnings/errors section.

Extracted facts table columns:

| Column | Meaning |
|---|---|
| `Type` | `service_date`, `mileage`, `vendor`, `service_item`, `part_or_fluid`, `total_cost`, `provider_note`, `document_subtype`. |
| `Raw` | Original extracted value. |
| `Normalized` | Parsed value if available. |
| `Confidence` | 0.0 to 1.0. |
| `Source` | Source row or line text. |
| `Rule` | Extractor rule name. |

## Implementation Work Items

### 1. Add Prototype Entry Point

Create a simple prototype screen reachable from `MainActivity`.

For speed, this can temporarily replace the default app content.

Work items:

- Add `PrototypeScreen` composable.
- Add state holder/ViewModel if useful, but a simple ViewModel is enough.
- Add import button using Android photo picker.
- Render a list of selected image processing results.

Acceptance criteria:

- App launches into prototype screen.
- User can tap a button and select multiple gallery images.
- Selected images appear in the UI.

### 2. Multi-Photo Gallery Import

Use Android's photo picker for one or multiple images.

Preferred API:

```kotlin
rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(),
) { uris: List<Uri> ->
    viewModel.onImagesSelected(uris)
}
```

For devices where the system photo picker is unavailable, use the AndroidX fallback behavior provided by the activity result contract.

Work items:

- Add required Activity Compose usage if not already present.
- Accept multiple image URIs.
- Create an in-memory `PrototypeImageJob` per URI.
- Do not copy files into permanent storage for V0.1 unless needed for debugging.

Acceptance criteria:

- User can select one image.
- User can select multiple images.
- Each image is processed independently.
- Failures for one image do not block others.

### 3. OCR With ML Kit Text Recognition

Use bundled ML Kit Text Recognition Latin model for V0.1.

Dependency:

```kotlin
implementation("com.google.mlkit:text-recognition:16.0.1")
```

OCR flow:

```text
Uri
  -> InputImage.fromFilePath(context, uri)
  -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  -> recognizer.process(image)
  -> map Text result to app-owned OCR models
```

App-owned OCR models:

```kotlin
data class OcrResultDraft(
    val fullText: String,
    val blocks: List<OcrBlockDraft>,
    val elapsedMs: Long,
)

data class OcrBlockDraft(
    val text: String,
    val bounds: Rect?,
    val lines: List<OcrLineDraft>,
)

data class OcrLineDraft(
    val text: String,
    val bounds: Rect?,
    val elements: List<OcrElementDraft>,
)

data class OcrElementDraft(
    val text: String,
    val bounds: Rect?,
)
```

Work items:

- Add `MlKitOcrEngine` prototype class.
- Convert ML Kit `Text` output into app-owned models.
- Measure OCR elapsed time per image.
- Surface OCR failures in UI.

Acceptance criteria:

- OCR runs on imported photos.
- Full recognized text is visible.
- Blocks/lines are available to layout normalization.
- OCR duration is visible.

### 4. Layout Normalization For Table Debugging

Build normalized lines and approximate rows from OCR geometry.

Models:

```kotlin
data class NormalizedLine(
    val text: String,
    val bounds: Rect?,
    val sourceBlockIndex: Int,
    val sourceLineIndex: Int,
)

data class NormalizedRow(
    val text: String,
    val bounds: Rect?,
    val lines: List<NormalizedLine>,
)
```

Algorithm:

```text
1. Flatten OCR blocks into lines.
2. Sort by vertical center, then left coordinate.
3. Group lines into same row when Y ranges overlap enough.
4. Within each row, sort left to right.
5. Join row text with visible spacing.
```

Work items:

- Implement `LayoutNormalizer`.
- Expose normalized rows in UI.
- Show both full OCR text and row-normalized text so table failures are obvious.

Acceptance criteria:

- Table-like documents show row-based output.
- Non-table receipts still show readable row output.
- We can visually compare OCR full text vs normalized rows.

### 5. Deterministic Fact Extractors

Implement the V0.1 deterministic extractors from `technical-design.md`.

Extractors:

- `DocumentSubtypeExtractor`
- `ServiceDateExtractor`
- `MileageExtractor`
- `VendorExtractor`
- `ServiceItemExtractor`
- `PartFluidExtractor`
- `TotalCostExtractor`
- `ProviderNoteExtractor`

Candidate model:

```kotlin
data class FactCandidate(
    val factType: FactType,
    val rawValue: String,
    val normalizedValue: String?,
    val confidence: Float,
    val sourceText: String,
    val extractionRule: String,
)
```

V0.1 fact types:

```kotlin
enum class FactType {
    DocumentSubtype,
    ServiceDate,
    Mileage,
    Vendor,
    ServiceItem,
    PartOrFluid,
    TotalCost,
    ProviderNote,
}
```

Work items:

- Implement regex and vocabulary-based extractors.
- Use normalized rows for service items, total cost, parts/fluids, and table-like data.
- Use normalized lines for dates, mileage, vendor, and notes.
- Add a simple `FactResolver` to remove duplicates and keep highest-confidence single-value facts.
- Measure extraction elapsed time.

Acceptance criteria:

- Extractors run automatically after OCR.
- Facts are displayed by type.
- Every fact shows raw value, normalized value, confidence, source text, and rule name.
- No DB is required.

### 6. Debug Metadata

Capture per-image metadata to compare phones and documents.

Metadata:

- Device manufacturer.
- Device model.
- Android SDK version.
- Image URI display name if available.
- Image dimensions if available.
- OCR elapsed time.
- Extraction elapsed time.
- Number of OCR blocks.
- Number of OCR lines.
- Number of normalized rows.
- Number of extracted facts by type.
- Error message if failed.

Work items:

- Add `PrototypeDebugInfo` model.
- Render debug info in each result card.
- Optionally add `Copy Debug Report` button to copy text to clipboard.

Acceptance criteria:

- Tester can compare results from multiple phones manually.
- Debug output is visible without ADB/logcat.

### 7. Manual Test Set

Create or collect a small anonymized local test set outside app code.

Recommended test documents:

- Clean maintenance receipt photo.
- Blurry receipt photo.
- Skewed receipt photo.
- Table-heavy service invoice photo.
- Dense repair order photo.
- Inspection checklist photo.
- Low-light photo.

For each device, manually record:

- OCR readability.
- Row preservation quality.
- Extracted service date correctness.
- Extracted mileage correctness.
- Extracted vendor correctness.
- Extracted service items correctness.
- Extracted total cost correctness.
- Notes on failures.

## Suggested File Layout

Keep prototype code isolated so it can be deleted or evolved later.

```text
app/src/main/java/org/vliux/orca/prototype/
  PrototypeScreen.kt
  PrototypeViewModel.kt
  PrototypeImageJob.kt
  ocr/
    MlKitOcrEngine.kt
    OcrModels.kt
  layout/
    LayoutNormalizer.kt
    LayoutModels.kt
  extraction/
    FactCandidate.kt
    FactType.kt
    MaintenanceFactExtractor.kt
    DocumentSubtypeExtractor.kt
    ServiceDateExtractor.kt
    MileageExtractor.kt
    VendorExtractor.kt
    ServiceItemExtractor.kt
    PartFluidExtractor.kt
    TotalCostExtractor.kt
    ProviderNoteExtractor.kt
    FactResolver.kt
  debug/
    PrototypeDebugInfo.kt
    DebugReportFormatter.kt
```

## Prototype State Model

```kotlin
data class PrototypeImageJob(
    val id: String,
    val uri: Uri,
    val status: PrototypeStatus,
    val ocrResult: OcrResultDraft? = null,
    val normalizedRows: List<NormalizedRow> = emptyList(),
    val facts: List<FactCandidate> = emptyList(),
    val debugInfo: PrototypeDebugInfo? = null,
    val errorMessage: String? = null,
)

enum class PrototypeStatus {
    Queued,
    OcrRunning,
    Extracting,
    Done,
    Failed,
}
```

No Room database is needed. Store jobs in ViewModel memory only.

## Evaluation Rubric

Use this rubric after testing each image on each phone.

OCR quality:

| Score | Meaning |
|---|---|
| 5 | Text almost perfect, table rows mostly preserved. |
| 4 | Minor text errors, facts still easy to extract. |
| 3 | Readable but table structure or some fields degraded. |
| 2 | Many OCR errors, only some facts extractable. |
| 1 | Mostly unusable. |

Fact extraction quality:

| Score | Meaning |
|---|---|
| 5 | Date, mileage, vendor, service items, and total cost correct. |
| 4 | One minor field wrong or missing. |
| 3 | Core service item/date found, but important fields missing. |
| 2 | Only weak partial extraction. |
| 1 | No useful facts extracted. |

Table handling quality:

| Score | Meaning |
|---|---|
| 5 | Rows and values remain associated. |
| 4 | Rows mostly correct, minor ordering problems. |
| 3 | Text readable but some row/cost associations unclear. |
| 2 | Columns flattened or mixed. |
| 1 | Table structure unusable. |

## V0.1 Deliverables

- Prototype screen with multi-photo import.
- ML Kit OCR integration.
- OCR result display.
- Normalized row display.
- Deterministic fact extraction.
- Fact result display.
- Debug metadata display.
- Manual testing rubric.

## V0.2 Preview

V0.2 should compare V0.1 output against remote AI extraction from original docs.

Possible V0.2 modes:

- Send OCR text to AI and compare with deterministic extractors.
- Send original image/page to vision AI and compare with OCR-first output.
- Show side-by-side results: deterministic vs AI text vs AI vision.

Do not implement V0.2 until V0.1 establishes baseline OCR and deterministic extraction behavior.
