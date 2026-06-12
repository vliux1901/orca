# Product Requirements: Car Maintenance Document Assistant

## Context

Vehicle owners receive many records over the life of a car. The first version
focuses on maintenance and repair documents, such as service invoices, repair
orders, inspection notes, and receipts. These records are often scattered across
email, file systems, photos, cloud storage, and paper folders.

## Product Thesis

Users need a trusted assistant that organizes car maintenance documents,
extracts key facts, and answers questions using cited evidence from the
original records.

## Target User

The first target user is a private vehicle owner who wants a reliable
maintenance history for one or more cars. Typical needs:

- Keep car maintenance documents in one place.
- Understand maintenance and repair history.
- Get answers that cite the original source document.
- Know when an answer is uncertain or unsupported.

## Product Scope

V1 supports car maintenance and repair documents only.

Supported v1 document categories:

- Maintenance and repair records.
- Service invoices.
- Repair orders.
- Maintenance receipts.
- Inspection reports that describe maintenance or repair work.

Future versions may add other vehicle documents:

- DMV registration.
- Smog or emissions inspection.
- Insurance policy and proof of insurance.
- Title or ownership documents.
- Warranty documents.
- Recall notices.
- Loan or lease documents.

## Goals

- Let users create vehicle profiles.
- Let users manually add maintenance-related PDFs and photos.
- Extract key facts from maintenance records.
- Provide a chronological maintenance timeline.
- Support search and natural-language questions.
- Cite source documents for factual answers.
- Clearly communicate uncertainty.

## Non-Goals

- Do not support non-vehicle document categories in v1.
- Do not support non-maintenance vehicle documents in v1.
- Do not automatically ingest email in v1.
- Do not replace a mechanic, dealer, insurer, DMV, or manufacturer.
- Do not make unsupported recommendations.
- Do not guarantee extracted data is correct without user review.
- Do not optimize for business fleet management in v1.

## User Stories

- Create a vehicle profile and attach documents to the right car.
- Add maintenance PDFs or photos so important records are stored in one place.
- See maintenance and repair history as a timeline.
- Ask when a service was last performed and get a cited answer.
- Correct extracted facts when the system is wrong.

## MVP Requirements

### Vehicle Profiles

Required fields:

- Display name.
- Make.
- Model.
- Year.
- License plate.

Optional fields:

- VIN.
- Current mileage.

### Document Import

Supported v1 inputs:

- PDF files.
- Images or photos of paper documents.

Deferred inputs:

- Email import.
- Cloud drive import.
- Dealer or manufacturer account import.
- Automatic background sync.

### Document Processing

For each imported maintenance document, the system should attempt to extract:

- Service date.
- Vehicle mileage.
- Vendor or service provider.
- Service items.
- Parts or fluids mentioned.
- Total cost.
- Notes or recommendations from the provider.

Each extracted field should link back to the source document.

### Maintenance Timeline

Each item should show:

- Date.
- Mileage, if available.
- Vendor, if available.
- Summary of service performed.
- Cost, if available.
- Link to source document.

### Search And Questions

Answers should include:

- Concise answer.
- Source citations.
- Relevant dates and mileage.
- Uncertainty note when records are incomplete or ambiguous.

### User Correction

The user should be able to edit:

- Document subtype.
- Service date.
- Mileage.
- Vendor.
- Service items.
- Cost.

Corrected facts should be treated as user-verified data.

## Answer Quality Requirements

The product should:

- Cite the source document for factual answers.
- Distinguish document facts from inferred conclusions.
- Say when available records do not support an answer.
- Show conflicting records when documents disagree.
- Prefer a narrow supported answer over a broad unsupported answer.

## Data Model

Initial entities:

- `Vehicle`: A user's car.
- `Document`: A source file added by the user.
- `DocumentFact`: A fact extracted from a document.
- `MaintenanceEvent`: A dated service or repair event.
- `ServiceItem`: A specific maintenance or repair item.
- `OdometerReading`: Mileage associated with a document or event.
- `Vendor`: Service provider, repair shop, dealer, or mechanic.
- `Citation`: Link from a fact or answer back to a source document.

Important relationships:

- A `Vehicle` has many `Document` records.
- A `Document` may produce many `DocumentFact` records.
- A `Document` may produce one or more `MaintenanceEvent` records.
- A `MaintenanceEvent` may have service items, mileage, vendor, and cost.
- A factual answer should include one or more citations.

## Success Metrics

- Users who create at least one vehicle profile.
- Documents imported per vehicle.
- Maintenance fact extraction success rate.
- Timeline views per active user.
- Questions asked per active user.
- Percentage of answers with citations.
- Percentage of answers marked uncertain or unsupported when appropriate.
- User correction rate for extracted fields.
- User-reported answer helpfulness.
- Users who import more than one document.
- Users who return after first import.
- Users who use the product before or after a service visit.

## Key Risks

- Document formats vary widely.
- Extraction may be unreliable without user review.
- Users may not trust answers without clear citations.
- Maintenance terms vary by vendor.
- Records may be incomplete.
- Manufacturer service intervals may be hard to source and keep current.
- Users may expect automatic email or app import earlier than v1 supports.

## Open Questions

- Should v1 be mobile-only, web-only, or both?
    - Answer: Mobile-only (Android first).
- Should v1 support camera capture or only file upload?
    - Answer: Only file upload.
- Should users review extracted data before it appears in the timeline?
    - Answer: Not necessary. But in the timeline it should mark it as '
      unverified'.
- Should manufacturer maintenance intervals be deferred until extraction is
  reliable?
- Should documents be stored locally, in the cloud, or both?
    - Answer: For privacy concerns, ideally local only. But may depend on the
      technical difficulties.
- Should multiple vehicles be supported in v1?
  - Answer: Yes.
- What privacy guarantees are required?
- Which non-maintenance vehicle documents should be prioritized after v1?

## Suggested Milestones

1. Maintenance document organizer: create vehicle, import file or photo, store
   maintenance document under vehicle, and display document list.
2. Maintenance timeline: extract service date, mileage, vendor, service items,
   and cost; create editable timeline items linked to source documents.
3. Cited question answering: answer questions across one vehicle's records with
   citations and uncertainty handling.
4. Decision support: compare maintenance history against known service
   intervals with explicit service-interval sourcing.

## Recommendation

Build the first prototype around this workflow:

1. Create a vehicle.
2. Add several maintenance documents.
3. Extract maintenance facts.
4. Show a maintenance timeline.
5. Answer a factual question with citations.

This proves the core value before adding automatic import, broader document
categories, or advanced recommendation logic.
