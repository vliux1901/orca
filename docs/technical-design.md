# Technical Design: Android Maintenance Document Assistant

## Purpose

This document designs the V1 Android app from `docs/prd.md`: a local-first assistant for organizing car maintenance and repair documents, extracting key facts, showing a maintenance timeline, and answering simple factual questions from the user's records.

The Android app structure is intentionally described at a high level. The important implementation detail is the document processing system: OCR, text layout reconstruction, fact extraction, and conversion into maintenance history.

## V1 Product Boundaries

V1 supports:

- Multiple vehicle profiles.
- Manual import of PDFs and images.
- Maintenance and repair documents only.
- Extraction of service date, mileage, vendor, service items, parts/fluids, total cost, and provider notes.
- Timeline entries marked unverified until the user edits or confirms them.
- Simple search and factual questions over extracted maintenance history.

V1 does not support:

- Email, cloud drive, dealer, manufacturer, or background sync imports.
- DMV, registration, insurance, title, loan, lease, warranty, or recall documents.
- Manufacturer interval recommendations.
- Fleet management.
- Cloud OCR or cloud LLM processing by default.

## Architecture Summary

The app remains a single Android application module for now.

Recommended layers:

- UI: Compose screens for vehicles, import, documents, timeline, edit, and ask/search.
- ViewModel: screen state and command handling.
- Repository: local DB plus app-private file storage.
- Processing: OCR, layout analysis, fact extraction, timeline building, and indexing.

The key backend-like flow is local:

```text
User imports PDF/image
  -> app copies original file to private storage
  -> Document row is created with status = queued
  -> worker extracts text from PDF or OCR
  -> text blocks are normalized into reading order and approximate rows
  -> maintenance facts are extracted with confidence and source location
  -> maintenance event is built from those facts
  -> search index is rebuilt for that document
  -> timeline shows unverified extracted result
  -> user corrections create verified replacement values
```

## Recommended Dependencies

Keep exact versions in `gradle/libs.versions.toml`.

Core Android dependencies are standard and not detailed here. The document-processing dependencies are:

```kotlin
// OCR for image files and scanned PDF pages. Use bundled model for V1.
implementation("com.google.mlkit:text-recognition:16.0.1")

// Background processing.
implementation("androidx.work:work-runtime-ktx:<version>")

// Local relational persistence.
implementation("androidx.room:room-runtime:<version>")
implementation("androidx.room:room-ktx:<version>")
ksp("androidx.room:room-compiler:<version>")

// Optional but useful for JSON geometry/debug payloads.
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<version>")
```

If remote AI extraction is enabled, add the chosen provider SDK or a small HTTP client behind an app-owned interface. Do not let provider request/response classes leak into repositories, UI, or database code.

PDF support:

- Use Android framework `PdfRenderer` to render scanned PDF pages into bitmaps for OCR.
- Choose one embedded text extraction library separately for digitally generated PDFs. `PdfRenderer` renders pages but does not extract embedded PDF text.

## Storage Model

Use local-only storage by default.

Files:

```text
files/documents/{vehicleId}/{documentId}/original.{ext}
files/documents/{vehicleId}/{documentId}/pages/page-001.png    // optional rendered PDF page/debug preview
```

Database stores:

- Vehicle metadata.
- Imported document metadata.
- OCR/PDF text blocks.
- Extracted and corrected maintenance facts.
- Maintenance events and service items.
- Lightweight search rows.

Do not log OCR text, VIN, license plate, filenames, service notes, or user questions.

## Key Database Tables

The schema focuses on V1 maintenance and repair records. It does not model future document classes like DMV/registration/insurance.

### `vehicles`

Required by the PRD.

| Field | Type | Notes |
|---|---|---|
| `id` | TEXT/UUID | Primary key. |
| `display_name` | TEXT | Required. |
| `make` | TEXT | Required. |
| `model` | TEXT | Required. |
| `year` | INTEGER | Required. |
| `license_plate` | TEXT | Required by PRD. |
| `vin` | TEXT nullable | Optional. |
| `current_mileage` | INTEGER nullable | Optional. |
| `created_at` | INTEGER | Epoch millis. |
| `updated_at` | INTEGER | Epoch millis. |

### `documents`

One row per imported source file.

| Field | Type | Notes |
|---|---|---|
| `id` | TEXT/UUID | Primary key. |
| `vehicle_id` | TEXT/UUID | FK to `vehicles`. |
| `original_file_name` | TEXT | Display/debug only. |
| `mime_type` | TEXT | `application/pdf`, `image/jpeg`, etc. |
| `local_path` | TEXT | App-private copied file path. |
| `sha256` | TEXT | Duplicate detection. |
| `subtype` | TEXT | `service_invoice`, `repair_order`, `maintenance_receipt`, `inspection_report`, `unknown`. |
| `processing_status` | TEXT | `queued`, `processing`, `processed`, `failed`. |
| `processing_error` | TEXT nullable | User-visible retry/debug reason. |
| `imported_at` | INTEGER | Epoch millis. |
| `processed_at` | INTEGER nullable | Epoch millis. |

### `document_text_blocks`

OCR/PDF text output. This table is the bridge from source files to extracted facts.

| Field | Type | Notes |
|---|---|---|
| `id` | TEXT/UUID | Primary key. |
| `document_id` | TEXT/UUID | FK to `documents`. |
| `page_number` | INTEGER nullable | 1-based page number for PDFs; `1` for images. |
| `block_index` | INTEGER | Stable order after layout normalization. |
| `text` | TEXT | OCR/PDF text for this block. |
| `left` | INTEGER nullable | Bounding box in source bitmap/page coordinates. |
| `top` | INTEGER nullable | Bounding box. |
| `right` | INTEGER nullable | Bounding box. |
| `bottom` | INTEGER nullable | Bounding box. |
| `confidence` | REAL nullable | OCR confidence if available. |
| `source` | TEXT | `pdf_text`, `mlkit_image`, `mlkit_pdf_page`. |

For V1, block-level storage is enough. If table extraction needs more precision, add `document_text_lines` later.

### `document_facts`

Atomic extracted or user-corrected facts.

| Field | Type | Notes |
|---|---|---|
| `id` | TEXT/UUID | Primary key. |
| `vehicle_id` | TEXT/UUID | FK to `vehicles`. |
| `document_id` | TEXT/UUID | FK to `documents`. |
| `fact_type` | TEXT | `service_date`, `mileage`, `vendor`, `service_item`, `part_or_fluid`, `total_cost`, `provider_note`, `document_subtype`. |
| `raw_value` | TEXT | Exact extracted/user-entered value. |
| `normalized_value` | TEXT nullable | ISO date, integer mileage, decimal cost, normalized category, etc. |
| `confidence` | REAL nullable | Extractor confidence from 0.0 to 1.0. |
| `verification_state` | TEXT | `extracted_unverified`, `user_corrected`, `user_verified`, `rejected`. |
| `source_text_block_id` | TEXT nullable | FK to text block where value came from. |
| `source_page_number` | INTEGER nullable | Convenience for opening source page. |
| `supersedes_fact_id` | TEXT nullable | Points to old extracted fact when user corrects it. |
| `created_at` | INTEGER | Epoch millis. |
| `updated_at` | INTEGER | Epoch millis. |

Do not overwrite wrong extraction values. User edits create a new corrected fact and supersede the old one.

### `maintenance_events`

Timeline rows. Usually one event per imported document in V1.

| Field | Type | Notes |
|---|---|---|
| `id` | TEXT/UUID | Primary key. |
| `vehicle_id` | TEXT/UUID | FK to `vehicles`. |
| `document_id` | TEXT/UUID | Main source document. |
| `service_date_fact_id` | TEXT nullable | FK to `document_facts`. |
| `mileage_fact_id` | TEXT nullable | FK to `document_facts`. |
| `vendor_fact_id` | TEXT nullable | FK to `document_facts`. |
| `total_cost_fact_id` | TEXT nullable | FK to `document_facts`. |
| `summary` | TEXT | Built from service item facts. |
| `verification_state` | TEXT | `extracted_unverified`, `user_corrected`, `user_verified`. |
| `created_at` | INTEGER | Epoch millis. |
| `updated_at` | INTEGER | Epoch millis. |

### `service_items`

Line items attached to timeline events.

| Field | Type | Notes |
|---|---|---|
| `id` | TEXT/UUID | Primary key. |
| `maintenance_event_id` | TEXT/UUID | FK to `maintenance_events`. |
| `fact_id` | TEXT | FK to `document_facts` with `fact_type = service_item`. |
| `label` | TEXT | User-facing item, e.g. `Synthetic oil change 5W-30`. |
| `category` | TEXT nullable | `oil_change`, `brake_service`, `tire_service`, `battery`, `filter`, `fluid`, `inspection`, `repair`, `other`. |
| `verification_state` | TEXT | Same pattern as facts. |

### `search_entries`

Derived table. Rebuildable from facts/events/text.

| Field | Type | Notes |
|---|---|---|
| `id` | TEXT/UUID | Primary key. |
| `vehicle_id` | TEXT/UUID | FK to `vehicles`. |
| `document_id` | TEXT/UUID | FK to source document. |
| `maintenance_event_id` | TEXT nullable | FK if this entry points to an event. |
| `fact_id` | TEXT nullable | FK if this entry points to a fact. |
| `text` | TEXT | Searchable text. |
| `kind` | TEXT | `document_text`, `service_item`, `vendor`, `note`, `event_summary`. |

## Document Import Flow

Android details are straightforward:

1. User selects a vehicle.
2. User imports one or more files with the system picker.
3. App copies each URI into app-private storage.
4. App creates a `documents` row with `processing_status = queued`.
5. App enqueues `DocumentProcessingWorker(documentId)`.

Accepted MIME types:

- `application/pdf`
- `image/jpeg`
- `image/png`
- `image/heic` if decoding works on the device
- `image/webp` if decoding works on the device

Pseudo-code:

```kotlin
suspend fun importDocument(vehicleId: String, sourceUri: Uri) {
    val metadata = contentResolver.readMetadata(sourceUri)
    require(metadata.mimeType in supportedMimeTypes)

    val documentId = uuid()
    val localFile = documentFileStore.copyIntoPrivateStorage(
        vehicleId = vehicleId,
        documentId = documentId,
        sourceUri = sourceUri,
    )

    val sha256 = localFile.sha256()
    documentsDao.insert(
        DocumentEntity(
            id = documentId,
            vehicleId = vehicleId,
            originalFileName = metadata.displayName,
            mimeType = metadata.mimeType,
            localPath = localFile.path,
            sha256 = sha256,
            subtype = "unknown",
            processingStatus = "queued",
            importedAt = nowMillis(),
        )
    )

    workManager.enqueue(DocumentProcessingWorker.request(documentId))
}
```

## Processing Worker

The worker owns the end-to-end conversion from source file to timeline knowledge.

```kotlin
class DocumentProcessingWorker(
    appContext: Context,
    params: WorkerParameters,
    private val documents: DocumentsRepository,
    private val textExtractor: TextExtractor,
    private val knowledgeExtractor: MaintenanceKnowledgeExtractor,
    private val eventBuilder: MaintenanceEventBuilder,
    private val searchIndexer: SearchIndexer,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentId = inputData.getString("documentId") ?: return Result.failure()
        documents.markProcessing(documentId)

        return try {
            val document = documents.getDocument(documentId)
            val textBlocks = textExtractor.extract(document)
            documents.replaceTextBlocks(documentId, textBlocks)

            val draft = knowledgeExtractor.extract(document, textBlocks)
            documents.replaceFacts(documentId, draft.facts)

            val event = eventBuilder.build(document, draft.facts)
            documents.replaceMaintenanceEvent(documentId, event)

            searchIndexer.rebuildForDocument(documentId)
            documents.markProcessed(documentId)
            Result.success()
        } catch (t: Throwable) {
            documents.markFailed(documentId, t.userSafeMessage())
            Result.retry()
        }
    }
}
```

## OCR And Text Extraction

### How ML Kit OCR Works

ML Kit Text Recognition receives an `InputImage` and returns a `Text` object. The useful parts are:

- `result.text`: all recognized text flattened into one string.
- `result.textBlocks`: large rectangular text regions.
- `block.lines`: recognized lines inside a block.
- `line.elements`: word-like pieces inside a line.
- `boundingBox` and `cornerPoints`: geometry for mapping text back to the source image/PDF page.
- `confidence`: available on some returned objects.

V1 should convert ML Kit objects into app-owned models immediately. Do not store ML Kit classes in repositories or DB code.

### OCR For Image Files

```kotlin
class MlKitOcrEngine(
    private val context: Context,
) : OcrEngine {

    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    override suspend fun recognizeImage(
        file: File,
        pageNumber: Int,
    ): List<TextBlockDraft> {
        val image = InputImage.fromFilePath(context, file.toUri())
        val result = recognizer.process(image).await()

        return result.textBlocks.mapIndexed { index, block ->
            TextBlockDraft(
                pageNumber = pageNumber,
                blockIndex = index,
                text = block.text,
                bounds = block.boundingBox,
                confidence = null,
                source = TextSource.MlKitImage,
                lines = block.lines.map { line ->
                    TextLineDraft(
                        text = line.text,
                        bounds = line.boundingBox,
                        elements = line.elements.map { element ->
                            TextElementDraft(
                                text = element.text,
                                bounds = element.boundingBox,
                            )
                        }
                    )
                }
            )
        }
    }
}
```

`await()` comes from the Play Services Kotlin coroutines adapter. If you do not want that dependency, wrap the `Task<Text>` manually with `suspendCancellableCoroutine`.

### OCR For Scanned PDFs

For scanned PDFs, render each page to a bitmap with `PdfRenderer`, then feed the bitmap to ML Kit.

```kotlin
class PdfPageRenderer {
    fun renderPage(file: File, pageIndex: Int, scale: Float = 2.0f): Bitmap {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(pageIndex).use { page ->
                    val width = (page.width * scale).roundToInt()
                    val height = (page.height * scale).roundToInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }
}
```

Then run OCR:

```kotlin
suspend fun recognizePdfPage(bitmap: Bitmap, pageNumber: Int): List<TextBlockDraft> {
    val image = InputImage.fromBitmap(bitmap, 0)
    val result = recognizer.process(image).await()
    return result.textBlocks.mapIndexed { index, block ->
        block.toTextBlockDraft(pageNumber = pageNumber, blockIndex = index)
    }
}
```

Recommended render scale:

- Start with `2.0f` for receipts/invoices.
- Increase only if OCR quality is poor because memory cost rises quickly.
- Process one page at a time and recycle bitmaps after OCR.

### Digitally Generated PDFs

Many service invoices are not scanned; they contain embedded text. OCR is unnecessary and less accurate for those.

The PDF extractor should do this:

```text
for each page:
  try embedded PDF text extraction
  if extracted text length is good enough:
      create text blocks with source = pdf_text
  else:
      render page with PdfRenderer
      run ML Kit OCR
```

Heuristic for "good enough":

```kotlin
fun hasEnoughEmbeddedText(pageText: String): Boolean {
    val lettersOrDigits = pageText.count { it.isLetterOrDigit() }
    return lettersOrDigits >= 80
}
```

Exact embedded PDF text library is still open. The architecture should hide it behind:

```kotlin
interface PdfTextExtractor {
    suspend fun extractText(file: File): List<PdfPageText>
}
```

### TextExtractor Interface

```kotlin
interface TextExtractor {
    suspend fun extract(document: DocumentEntity): List<TextBlockDraft>
}

class DefaultTextExtractor(
    private val pdfTextExtractor: PdfTextExtractor,
    private val pdfPageRenderer: PdfPageRenderer,
    private val ocrEngine: MlKitOcrEngine,
) : TextExtractor {

    override suspend fun extract(document: DocumentEntity): List<TextBlockDraft> {
        return when {
            document.mimeType == "application/pdf" -> extractPdf(document.localPath)
            document.mimeType.startsWith("image/") -> ocrEngine.recognizeImage(
                file = File(document.localPath),
                pageNumber = 1,
            )
            else -> emptyList()
        }
    }
}
```

## OCR-First AI vs Direct Vision AI

There are two viable ways to use AI after import:

1. Extract text first with embedded PDF text extraction or OCR, then send text to AI.
2. Send the original image/PDF page directly to a vision-capable AI model.

They solve different problems.

| Approach | Strengths | Weaknesses |
|---|---|---|
| OCR first, then AI parses text | Cheaper, easier to debug, can use local OCR, stores reusable text blocks, simpler source links, lower privacy exposure if OCR stays local. | Loses visual layout, table rows/columns can be flattened incorrectly, OCR mistakes become AI input mistakes, weak for checkboxes and dense inspection forms. |
| Direct image/PDF page to vision AI | Better at visual layout, tables, columns, checkboxes, headers, stamps, and messy receipt photos. | Higher cost, higher latency, sends more sensitive source content remotely, harder to inspect/debug, needs stricter privacy controls. |
| Hybrid | Best practical balance: local/simple path for normal docs and vision fallback for hard docs. | More code paths and more extraction-result reconciliation. |

Expected effectiveness for V1 maintenance documents:

| Document Type | OCR First + AI Text Parser | Direct Vision AI |
|---|---|---|
| Clean digital PDF with embedded text | High | High |
| Simple receipt photo | Medium-high | High |
| Scanned invoice with tables | Medium | High |
| Dense repair order | Medium | Medium-high |
| Inspection checklist with boxes | Low-medium | Medium-high |
| Blurry or skewed photo | Low | Medium, depending on image quality. |

Recommended V1-compatible design:

```text
1. Try embedded PDF text extraction.
2. For images/scanned PDFs, run local ML Kit OCR.
3. Run rule-based extraction and/or AI text extraction on recognized text.
4. If confidence is low, OCR quality is poor, or the document is table/checklist-heavy, call vision AI with page image(s).
5. Validate AI output locally.
6. Store extracted values as extracted_unverified.
7. Let the user correct or confirm.
```

Fallback triggers for vision AI:

- OCR extracted too little text.
- OCR confidence is low when available.
- Table-like layout is detected, for example many aligned rows with currency values.
- Extractors find conflicting dates, mileage, or totals.
- No service item is found but the document subtype appears maintenance-related.
- User taps `Improve extraction` or `Try AI extraction`.

Privacy guardrails:

- Remote AI must be opt-in if the product keeps the local-first privacy promise.
- Prefer sending selected page images or text snippets instead of the entire document.
- Do not send unrelated vehicle profile fields unless needed for disambiguation.
- Do not log remote prompts or responses with raw document content.
- Keep AI results unverified until user review.

Provider boundary:

```kotlin
interface AiMaintenanceExtractor {
    suspend fun extractFromText(request: AiTextExtractionRequest): AiExtractionResult
    suspend fun extractFromPageImages(request: AiVisionExtractionRequest): AiExtractionResult
}

data class AiTextExtractionRequest(
    val documentId: String,
    val vehicleHint: VehicleHint?,
    val pages: List<PageTextForAi>,
)

data class AiVisionExtractionRequest(
    val documentId: String,
    val vehicleHint: VehicleHint?,
    val pages: List<PageImageForAi>,
)
```

The AI response should be strict JSON, not prose. The app should reject malformed output and validate every field before saving.

Example expected AI JSON shape:

```json
{
  "document_subtype": {
    "value": "service_invoice",
    "confidence": 0.86,
    "evidence": "Invoice #1234"
  },
  "service_date": {
    "raw": "03/12/2025",
    "normalized": "2025-03-12",
    "confidence": 0.91,
    "evidence": "DATE: 03/12/2025"
  },
  "mileage": {
    "raw": "45,231",
    "normalized": 45231,
    "confidence": 0.88,
    "evidence": "ODOMETER: 45,231"
  },
  "service_items": [
    {
      "raw": "Synthetic oil change 5W-30",
      "category": "oil_change",
      "confidence": 0.84,
      "evidence": "Synthetic oil change 5W-30"
    }
  ],
  "total_cost": {
    "raw": "$89.42",
    "normalized": {
      "currency": "USD",
      "amount": 89.42
    },
    "confidence": 0.89,
    "evidence": "TOTAL $89.42"
  }
}
```

## Handling Tables And Line Items

Maintenance documents often contain tables. ML Kit gives text and geometry, but it does not return real table cells. The app needs a lightweight layout step.

Goal for V1:

- Recover enough row structure to identify service items, mileage, dates, and totals.
- Do not promise perfect itemized table extraction.
- Mark table-derived facts as unverified unless the user corrects/confirms them.

### Layout Normalization

Input: OCR/PDF blocks with bounding boxes.

Output: ordered lines and approximate rows.

```kotlin
data class NormalizedLine(
    val pageNumber: Int,
    val text: String,
    val bounds: Rect?,
    val sourceBlockId: String?,
)

data class NormalizedRow(
    val pageNumber: Int,
    val lines: List<NormalizedLine>,
    val text: String,
    val bounds: Rect?,
)
```

Simple row grouping algorithm:

```kotlin
fun groupLinesIntoRows(lines: List<NormalizedLine>): List<NormalizedRow> {
    val sorted = lines.sortedWith(
        compareBy<NormalizedLine> { it.pageNumber }
            .thenBy { it.bounds?.centerY() ?: Int.MAX_VALUE }
            .thenBy { it.bounds?.left ?: Int.MAX_VALUE }
    )

    val rows = mutableListOf<MutableList<NormalizedLine>>()
    for (line in sorted) {
        val lastRow = rows.lastOrNull()
        val sameRow = lastRow != null && yOverlaps(lastRow, line)
        if (sameRow) lastRow.add(line) else rows.add(mutableListOf(line))
    }

    return rows.map { rowLines ->
        val ordered = rowLines.sortedBy { it.bounds?.left ?: Int.MAX_VALUE }
        NormalizedRow(
            pageNumber = ordered.first().pageNumber,
            lines = ordered,
            text = ordered.joinToString("  ") { it.text },
            bounds = unionBounds(ordered.mapNotNull { it.bounds }),
        )
    }
}
```

This is intentionally simple. It handles common invoice rows like:

```text
Synthetic Oil Change        1      $89.42
Tire Rotation               1      $29.99
```

It will struggle with dense multi-column inspections, merged cells, checkboxes, and skewed photos. That is acceptable for V1 if the UI shows unverified results and lets the user edit.

## Text To Maintenance Knowledge

OCR produces text. The app needs maintenance knowledge:

```text
raw recognized text
  -> normalized lines/rows
  -> fact candidates
  -> resolved document facts
  -> maintenance event + service items
  -> search entries and question answers
```

### Fact Candidate Model

Fact candidates are temporary extraction results before DB persistence.

```kotlin
data class FactCandidate(
    val factType: FactType,
    val rawValue: String,
    val normalizedValue: String?,
    val confidence: Float,
    val sourceTextBlockId: String?,
    val sourcePageNumber: Int?,
    val extractionRule: String,
)

enum class FactType {
    ServiceDate,
    Mileage,
    Vendor,
    ServiceItem,
    PartOrFluid,
    TotalCost,
    ProviderNote,
    DocumentSubtype,
}
```

### Extractor Interface

```kotlin
interface MaintenanceKnowledgeExtractor {
    suspend fun extract(
        document: DocumentEntity,
        textBlocks: List<TextBlockDraft>,
    ): KnowledgeDraft
}

data class KnowledgeDraft(
    val facts: List<FactCandidate>,
    val warnings: List<String>,
)
```

Provide multiple implementations behind this interface:

```kotlin
class RuleBasedMaintenanceKnowledgeExtractor : MaintenanceKnowledgeExtractor
class AiTextMaintenanceKnowledgeExtractor : MaintenanceKnowledgeExtractor
class AiVisionMaintenanceKnowledgeExtractor : MaintenanceKnowledgeExtractor
class HybridMaintenanceKnowledgeExtractor : MaintenanceKnowledgeExtractor
```

Recommended behavior:

```text
Hybrid extractor
  -> run rule-based extraction
  -> if rule confidence is acceptable, use rule output
  -> otherwise call AI text extraction
  -> if AI text output is still weak and policy allows remote images, call AI vision extraction
  -> validate and normalize all AI output locally
  -> return final fact candidates
```

### Extraction Strategy

Use deterministic extractors first. They are predictable, testable, and work locally. AI extraction can improve messy documents, but its output still needs local validation.

Run these extractors:

1. `DocumentSubtypeExtractor`
2. `ServiceDateExtractor`
3. `MileageExtractor`
4. `VendorExtractor`
5. `ServiceItemExtractor`
6. `PartFluidExtractor`
7. `TotalCostExtractor`
8. `ProviderNoteExtractor`

Each extractor reads normalized lines/rows and emits candidates with confidence.

### Date Extraction Example

```kotlin
class ServiceDateExtractor {
    private val dateRegex = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\b""")
    private val strongLabels = listOf("service date", "date", "invoice date", "repair date", "closed")

    fun extract(lines: List<NormalizedLine>): List<FactCandidate> {
        return lines.flatMap { line ->
            dateRegex.findAll(line.text).map { match ->
                val labelBoost = if (strongLabels.any { line.text.lowercase().contains(it) }) 0.25f else 0f
                FactCandidate(
                    factType = FactType.ServiceDate,
                    rawValue = match.value,
                    normalizedValue = normalizeDate(match.value),
                    confidence = 0.60f + labelBoost,
                    sourceTextBlockId = line.sourceBlockId,
                    sourcePageNumber = line.pageNumber,
                    extractionRule = "date_regex_with_label_boost",
                )
            }
        }
    }
}
```

### Mileage Extraction Example

Mileage needs guardrails because invoices contain phone numbers, ZIP codes, invoice numbers, prices, and VIN fragments.

```kotlin
class MileageExtractor {
    private val mileageRegex = Regex("""\b(?:odometer|mileage|miles|mi)\D{0,12}(\d{1,3}(?:,\d{3}){0,2}|\d{4,6})\b""", RegexOption.IGNORE_CASE)

    fun extract(lines: List<NormalizedLine>): List<FactCandidate> {
        return lines.mapNotNull { line ->
            val match = mileageRegex.find(line.text) ?: return@mapNotNull null
            val rawNumber = match.groupValues[1]
            val miles = rawNumber.replace(",", "").toIntOrNull() ?: return@mapNotNull null
            if (miles !in 1..1_500_000) return@mapNotNull null

            FactCandidate(
                factType = FactType.Mileage,
                rawValue = rawNumber,
                normalizedValue = miles.toString(),
                confidence = 0.85f,
                sourceTextBlockId = line.sourceBlockId,
                sourcePageNumber = line.pageNumber,
                extractionRule = "mileage_labeled_regex",
            )
        }
    }
}
```

### Total Cost Extraction Example

Prefer explicit total labels over arbitrary currency values.

```kotlin
class TotalCostExtractor {
    private val totalRegex = Regex("""\b(total|amount due|paid|grand total)\b\D{0,20}\$?\s*(\d+[,.]\d{2})""", RegexOption.IGNORE_CASE)

    fun extract(rows: List<NormalizedRow>): List<FactCandidate> {
        return rows.mapNotNull { row ->
            val match = totalRegex.find(row.text) ?: return@mapNotNull null
            val amount = match.groupValues[2].replace(",", "")

            FactCandidate(
                factType = FactType.TotalCost,
                rawValue = match.value,
                normalizedValue = "USD $amount",
                confidence = 0.88f,
                sourceTextBlockId = row.lines.firstOrNull()?.sourceBlockId,
                sourcePageNumber = row.pageNumber,
                extractionRule = "total_cost_labeled_row",
            )
        }
    }
}
```

### Service Item Extraction Example

Use a controlled vocabulary plus row context. Preserve original text for display.

```kotlin
class ServiceItemExtractor {
    private val patterns = listOf(
        "oil change" to "oil_change",
        "synthetic oil" to "oil_change",
        "tire rotation" to "tire_service",
        "alignment" to "tire_service",
        "brake pad" to "brake_service",
        "brake rotor" to "brake_service",
        "battery" to "battery",
        "air filter" to "filter",
        "cabin filter" to "filter",
        "coolant" to "fluid",
        "transmission fluid" to "fluid",
        "inspection" to "inspection",
    )

    fun extract(rows: List<NormalizedRow>): List<FactCandidate> {
        return rows.flatMap { row ->
            val lower = row.text.lowercase()
            patterns.mapNotNull { (phrase, category) ->
                if (!lower.contains(phrase)) return@mapNotNull null
                FactCandidate(
                    factType = FactType.ServiceItem,
                    rawValue = row.text,
                    normalizedValue = category,
                    confidence = 0.80f,
                    sourceTextBlockId = row.lines.firstOrNull()?.sourceBlockId,
                    sourcePageNumber = row.pageNumber,
                    extractionRule = "service_item_vocabulary_row_match:$phrase",
                )
            }
        }
    }
}
```

### Vendor Extraction Example

Vendor usually appears near the top of page 1. V1 can use a heuristic:

```kotlin
class VendorExtractor {
    fun extract(lines: List<NormalizedLine>, vehicle: VehicleEntity): FactCandidate? {
        val headerLines = lines
            .filter { it.pageNumber == 1 }
            .take(8)
            .filterNot { line ->
                val t = line.text.lowercase()
                t.contains(vehicle.make.lowercase()) ||
                    t.contains(vehicle.model.lowercase()) ||
                    t.contains("invoice") ||
                    t.contains("repair order") ||
                    t.matches(Regex(""".*\d{3}[- .]\d{3}[- .]\d{4}.*"""))
            }

        val candidate = headerLines.maxByOrNull { businessNameScore(it.text) } ?: return null
        return FactCandidate(
            factType = FactType.Vendor,
            rawValue = candidate.text,
            normalizedValue = normalizeVendorName(candidate.text),
            confidence = 0.55f,
            sourceTextBlockId = candidate.sourceBlockId,
            sourcePageNumber = candidate.pageNumber,
            extractionRule = "page1_header_vendor_heuristic",
        )
    }
}
```

Vendor extraction will be imperfect. Keep confidence lower and make it easy to edit.

### Candidate Resolution

After all extractors run, choose facts for the event.

Rules:

- Pick the highest-confidence service date, mileage, vendor, and total cost.
- Keep all service items above a threshold.
- Keep all provider notes above a threshold.
- If two top candidates disagree and both are high confidence, keep both facts but mark the event as needing review.
- Never infer missing date, mileage, vendor, or cost.

```kotlin
class FactResolver {
    fun resolve(candidates: List<FactCandidate>): List<FactCandidate> {
        val singleValueTypes = setOf(
            FactType.ServiceDate,
            FactType.Mileage,
            FactType.Vendor,
            FactType.TotalCost,
            FactType.DocumentSubtype,
        )

        val chosenSingles = singleValueTypes.mapNotNull { type ->
            candidates
                .filter { it.factType == type && it.confidence >= 0.50f }
                .maxByOrNull { it.confidence }
        }

        val multiValueFacts = candidates.filter {
            it.factType in setOf(FactType.ServiceItem, FactType.PartOrFluid, FactType.ProviderNote) &&
                it.confidence >= 0.55f
        }

        return chosenSingles + multiValueFacts.distinctBy {
            Triple(it.factType, it.normalizedValue, it.rawValue.lowercase())
        }
    }
}
```

### Persisting Facts

```kotlin
suspend fun persistFacts(document: DocumentEntity, facts: List<FactCandidate>) {
    factsDao.replaceExtractedFactsForDocument(
        documentId = document.id,
        facts = facts.map { fact ->
            DocumentFactEntity(
                id = uuid(),
                vehicleId = document.vehicleId,
                documentId = document.id,
                factType = fact.factType.name,
                rawValue = fact.rawValue,
                normalizedValue = fact.normalizedValue,
                confidence = fact.confidence,
                verificationState = "extracted_unverified",
                sourceTextBlockId = fact.sourceTextBlockId,
                sourcePageNumber = fact.sourcePageNumber,
                supersedesFactId = null,
                createdAt = nowMillis(),
                updatedAt = nowMillis(),
            )
        }
    )
}
```

## Building The Timeline Event

V1 should normally create one `maintenance_events` row per document.

```kotlin
class MaintenanceEventBuilder {
    fun build(document: DocumentEntity, facts: List<DocumentFactEntity>): MaintenanceEventDraft {
        val serviceDate = facts.preferred(FactType.ServiceDate)
        val mileage = facts.preferred(FactType.Mileage)
        val vendor = facts.preferred(FactType.Vendor)
        val totalCost = facts.preferred(FactType.TotalCost)
        val serviceItems = facts.filter { it.factType == FactType.ServiceItem.name }

        return MaintenanceEventDraft(
            vehicleId = document.vehicleId,
            documentId = document.id,
            serviceDateFactId = serviceDate?.id,
            mileageFactId = mileage?.id,
            vendorFactId = vendor?.id,
            totalCostFactId = totalCost?.id,
            summary = summarizeServiceItems(serviceItems),
            verificationState = "extracted_unverified",
            serviceItems = serviceItems.map { fact ->
                ServiceItemDraft(
                    factId = fact.id,
                    label = fact.rawValue,
                    category = fact.normalizedValue,
                    verificationState = fact.verificationState,
                )
            }
        )
    }
}
```

`preferred()` should choose user-corrected/user-verified facts before extracted facts:

```kotlin
fun List<DocumentFactEntity>.preferred(type: FactType): DocumentFactEntity? {
    return filter { it.factType == type.name }
        .sortedWith(
            compareByDescending<DocumentFactEntity> { it.verificationState == "user_corrected" || it.verificationState == "user_verified" }
                .thenByDescending { it.confidence ?: 0f }
        )
        .firstOrNull()
}
```

## User Corrections

When the user edits a field, do not mutate the extracted fact in place. Insert a replacement fact.

Example: user corrects mileage from `45231` to `45281`.

```kotlin
suspend fun correctFact(
    oldFactId: String,
    correctedRawValue: String,
    correctedNormalizedValue: String,
) {
    val old = factsDao.get(oldFactId)
    val newFact = old.copy(
        id = uuid(),
        rawValue = correctedRawValue,
        normalizedValue = correctedNormalizedValue,
        confidence = 1.0f,
        verificationState = "user_corrected",
        supersedesFactId = old.id,
        sourceTextBlockId = old.sourceTextBlockId,
        updatedAt = nowMillis(),
    )
    factsDao.insert(newFact)
    timelineDao.rebuildEventForDocument(old.documentId)
    searchIndexer.rebuildForDocument(old.documentId)
}
```

This preserves auditability and lets the UI show that the timeline is based on a user-corrected value.

## Search And Simple Question Answering

V1 does not need an LLM to answer common maintenance questions. Use extracted facts and deterministic templates first.

Supported initial question types:

- "When was the oil last changed?"
- "Show brake work."
- "How much did the last service cost?"
- "Where did I get tires replaced?"
- "What mileage was the car at during the last service?"

Intent detection can be simple:

```kotlin
data class QuestionIntent(
    val kind: Kind,
    val serviceCategory: String?,
) {
    enum class Kind { LastServiceOfType, ServiceHistory, LastCost, VendorForService, MileageAtLastService, KeywordSearch }
}

fun classifyQuestion(question: String): QuestionIntent {
    val q = question.lowercase()
    val category = when {
        "oil" in q -> "oil_change"
        "brake" in q -> "brake_service"
        "tire" in q -> "tire_service"
        "battery" in q -> "battery"
        else -> null
    }

    return when {
        q.startsWith("when") && category != null -> QuestionIntent(QuestionIntent.Kind.LastServiceOfType, category)
        "show" in q && category != null -> QuestionIntent(QuestionIntent.Kind.ServiceHistory, category)
        "cost" in q || "how much" in q -> QuestionIntent(QuestionIntent.Kind.LastCost, category)
        "where" in q -> QuestionIntent(QuestionIntent.Kind.VendorForService, category)
        "mileage" in q || "odometer" in q -> QuestionIntent(QuestionIntent.Kind.MileageAtLastService, category)
        else -> QuestionIntent(QuestionIntent.Kind.KeywordSearch, category)
    }
}
```

Example answer flow:

```kotlin
suspend fun answerLastService(vehicleId: String, category: String): AnswerResult {
    val event = timelineDao.findMostRecentEventWithServiceCategory(vehicleId, category)
        ?: return AnswerResult(
            answer = "I could not find a recorded ${category.humanize()} in this vehicle's maintenance documents.",
            status = "unsupported",
            sourceDocumentId = null,
            sourcePageNumber = null,
        )

    return AnswerResult(
        answer = "I found ${category.humanize()} on ${event.serviceDateText()} at ${event.mileageTextOrUnknown()}.",
        status = if (event.verificationState == "extracted_unverified") "supported_unverified" else "supported_verified",
        sourceDocumentId = event.documentId,
        sourcePageNumber = event.sourcePageNumber,
    )
}
```

Answer rules:

- If no source document backs the answer, say unsupported.
- If the event is unverified, say it was extracted from a document and may need review.
- If date or mileage is missing, do not invent it.
- If multiple records conflict, show both and ask the user to verify.

## UI Surfaces

High-level only:

- Vehicle list/create/edit.
- Vehicle detail with import action, recent timeline, document count, and ask/search entry.
- Document list with processing status.
- Document detail with original preview and extracted facts.
- Timeline with unverified/verified markers.
- Timeline event detail with edit actions.
- Ask/search screen with answer and source document link.

Important UX rule: every extracted value shown in the timeline should be visibly `Unverified` until user-corrected or confirmed.

## Error Handling

Processing status should be visible per document.

Important failures:

- Unsupported MIME type: reject before creating a document row.
- File copy failure: show retry/import again.
- OCR failure: keep document row, status `failed`, retry available.
- No text found: keep document row and allow manual correction/manual event later if added.
- No facts found: show document but no timeline event, or create a minimal event only if product chooses manual review flow.

## Testing Strategy

Focus tests on extraction quality, not Android boilerplate.

Unit tests:

- Date parser.
- Mileage parser with false positives: phone, ZIP, invoice number, VIN fragment, price.
- Total cost parser with subtotal/tax/total rows.
- Service item vocabulary matching.
- Row grouping from bounding boxes.
- Fact resolver confidence behavior.
- Event builder preferred fact behavior.
- Question classifier and answer templates.

Fixture tests:

- Anonymized service invoice PDF.
- Anonymized repair order PDF.
- Receipt image.
- Inspection report image.
- Table-heavy invoice.
- Blurry/skewed image expected to produce low confidence or fewer facts.

Instrumented tests:

- Import sample file.
- Worker processes it.
- Document facts are written.
- Timeline entry appears as unverified.
- User correction updates the timeline.

## Milestones

### Milestone 1: Organizer

- Vehicle CRUD.
- Manual PDF/image import.
- Local file storage.
- Documents table/list.
- Processing status.

### Milestone 2: OCR And Timeline

- ML Kit OCR for images.
- PDF render-to-OCR fallback for scanned PDFs.
- Embedded PDF text extractor interface.
- Text block persistence.
- Deterministic fact extractors.
- Optional AI text extractor behind `MaintenanceKnowledgeExtractor`.
- Optional vision AI fallback behind `AiMaintenanceExtractor` for low-confidence/table-heavy documents if remote processing is approved.
- Maintenance event and service item generation.
- Timeline with unverified markers.
- Correction flow.

### Milestone 3: Search And Factual QA

- Search entries from facts/events/text.
- Intent-based question classifier.
- Answer templates with source document links.
- Unsupported/uncertain/conflict answer states.

## Risks

- OCR may recognize words but lose table structure. Mitigation: geometry-based row grouping, conservative confidence, user correction.
- OCR-first AI may inherit OCR mistakes. Mitigation: vision AI fallback for low-confidence, table-heavy, or checklist-like documents.
- Direct vision AI increases privacy exposure and cost. Mitigation: opt-in remote processing, selected-page upload, strict provider boundary, no raw-content logging.
- Vendor extraction will be noisy. Mitigation: low confidence and easy edit path.
- Dense inspection reports may not map cleanly to service events. Mitigation: extract notes/items but mark unverified.
- Embedded PDF text extraction library is still undecided. Mitigation: hide behind `PdfTextExtractor`.
- Local-only processing limits extraction quality. Mitigation: hybrid extractor with local rules first and opt-in remote AI fallback after privacy decision.

## Open Decisions

- Which embedded PDF text extraction library to use.
- Which remote AI provider/model to use if hybrid extraction is approved.
- Whether remote AI is opt-in globally, per document, or per retry action.
- Whether AI may receive full page images/PDF pages or only OCR text/snippets by default.
- Whether encrypted DB/files are required before beta.
- Whether users can manually create a maintenance event when extraction finds no facts.
- Whether questions/answers are persisted or ephemeral.
- Whether Android backup excludes all app data or only source documents.

## References

- Google ML Kit Text Recognition v2 for Android: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- Android `PdfRenderer`: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer
