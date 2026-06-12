# Product Requirements: Personal Vehicle Document Assistant

## Context

People constantly receive paper and digital documents: receipts, invoices,
service reports, inspection reports, warranty documents, insurance policies,
DMV notices, and emails. Over time those documents become scattered across
mailboxes, file systems, cloud drives, apps, and physical storage.

This makes it difficult to answer practical questions later:

- Which documents belong to the same subject, such as one car or one house?
- What information exists across all documents for that subject?
- What maintenance, repairs, payments, renewals, or claims have happened over
  time?
- Can an answer be produced from the user's own records with enough evidence
  to trust it?

The initial product focus is vehicle ownership records, starting with car
maintenance documents. This is a strong first use case because records are
recurring, semi-structured, personally valuable, and often needed at decision
time.

Example user question:

> The dealer says I should replace the brake fluid. Based on my records, when
> was it replaced last, at what mileage, and is there enough evidence to decide?

Tesla owners can access some maintenance records from the Tesla app, but those
records may be PDFs or summaries that are still hard to search, aggregate, and
reason over. The broader problem applies across many vehicle brands and service
providers.

## Product Thesis

Users need a trusted personal document assistant that organizes vehicle records
and answers questions using cited evidence from their own documents.

The product should not simply store files. It should turn messy documents into
a searchable, structured ownership history while keeping every important answer
traceable to source documents.

## Target User

The first target user is a private car owner who wants a reliable history of
maintenance and repairs across the life of a vehicle.

Typical characteristics:

- Owns or leases one or more vehicles.
- Receives service invoices, inspection reports, PDFs, photos, and emails.
- Does not consistently organize these records manually.
- Wants to make better maintenance decisions.
- May need records for resale, warranty claims, insurance, or tax purposes.

## Initial Use Case

The initial use case is car maintenance records.

The product should help the user answer questions such as:

- What maintenance has been done on this car?
- When was a specific service last performed?
- What was the mileage at the time of service?
- Which shop performed the work?
- How much did I spend on maintenance and repairs?
- Which source document supports this answer?
- Is the answer complete, uncertain, or unsupported by my records?

## Goals

- Let users add vehicle-related documents.
- Extract important maintenance facts from those documents.
- Group documents and extracted facts under a specific vehicle.
- Provide a chronological maintenance timeline.
- Support natural-language questions over the user's vehicle records.
- Always cite source documents for factual answers.
- Clearly communicate uncertainty when records are incomplete or ambiguous.

## Non-Goals

- Do not support every personal document category in v1.
- Do not automatically ingest email in the first version.
- Do not make unsupported recommendations without evidence.
- Do not replace a mechanic, dealer, or manufacturer service schedule.
- Do not guarantee that extracted data is correct without user review.
- Do not optimize for business fleet management in the first version.

## User Stories

- As a car owner, I can create a vehicle profile so records are grouped under
  the right car.
- As a car owner, I can add a maintenance invoice or service report so the app
  remembers what work was performed.
- As a car owner, I can see a timeline of maintenance events for one vehicle.
- As a car owner, I can ask when a specific service was last performed and get
  an answer with citations to the original document.
- As a car owner, I can see date, mileage, vendor, service items, and cost
  extracted from a document.
- As a car owner, I can correct extracted facts when the system is wrong.
- As a car owner, I can tell when an answer is uncertain or unsupported by my
  available records.

## MVP Requirements

### Vehicle Profiles

The user can create and manage a vehicle profile.

Required fields:

- Display name.
- Make.
- Model.
- Year.

Optional fields:

- VIN.
- License plate.
- Current mileage.

### Document Import

The user can manually add documents to a vehicle.

Supported v1 document inputs:

- PDF files.
- Images or photos of paper documents.

Deferred inputs:

- Email import.
- Cloud drive import.
- Dealer or manufacturer account import.
- Automatic background sync.

### Document Processing

For each imported document, the system should attempt to extract:

- Document type.
- Service date.
- Vehicle mileage.
- Vendor or service provider.
- Service items.
- Parts or fluids mentioned.
- Total cost.
- Notes or recommendations from the provider.

Each extracted field should retain a link back to the source document.

### Maintenance Timeline

The user can view a chronological timeline for one vehicle.

Each timeline item should show:

- Date.
- Mileage, if available.
- Vendor, if available.
- Summary of service performed.
- Cost, if available.
- Link to the source document.

### Search And Questions

The user can search and ask natural-language questions across one vehicle's
documents.

Example questions:

- When was the brake fluid last replaced?
- What repairs were done after 50,000 miles?
- How much did I spend on tires?
- Show me all oil changes.
- Did any document mention a battery replacement?

Answers should include:

- A concise answer.
- Source citations.
- Relevant dates and mileage.
- Confidence or uncertainty notes.
- A statement when the records do not contain enough information.

### User Correction

The user can review and correct extracted facts.

At minimum, the user should be able to edit:

- Service date.
- Mileage.
- Vendor.
- Service items.
- Cost.

Corrected facts should be treated as user-verified data.

## Answer Quality Requirements

Trust is the core product requirement.

The product should:

- Cite the source document for every factual answer.
- Distinguish between facts found in user documents and inferred conclusions.
- Avoid pretending the record set is complete.
- Say when the answer is not supported by available documents.
- Prefer a limited, well-supported answer over a broad unsupported answer.
- Show conflicting records when two documents disagree.

Example answer style:

> Your records show brake fluid replacement on March 12, 2023 at 42,118 miles,
> based on the invoice from Example Auto. I do not see a newer brake fluid
> service in the documents currently added.

## Data Model

Initial entities:

- `Vehicle`: A user's car.
- `Document`: A source file added by the user.
- `MaintenanceEvent`: A dated service or repair event.
- `ServiceItem`: A specific maintenance or repair item performed during an
  event.
- `OdometerReading`: Mileage associated with a document or event.
- `Vendor`: Service provider, shop, dealer, or mechanic.
- `Citation`: Link from an extracted fact or answer back to a source document.

Important relationships:

- A `Vehicle` has many `Document` records.
- A `Document` may produce zero or more `MaintenanceEvent` records.
- A `MaintenanceEvent` has one or more `ServiceItem` records.
- A `MaintenanceEvent` may have one `OdometerReading`.
- A factual answer should include one or more `Citation` records.

## Success Metrics

Product usage:

- Number of users who create at least one vehicle profile.
- Number of documents imported per vehicle.
- Percentage of imported documents that produce useful extracted facts.
- Number of timeline views per active user.
- Number of questions asked per active user.

Answer quality:

- Percentage of answers with citations.
- Percentage of answers marked uncertain or unsupported when appropriate.
- User correction rate for extracted fields.
- User-reported answer helpfulness.

Retention and value:

- Percentage of users who import more than one document.
- Percentage of users who return after first import.
- Percentage of users who use the product before or after a service visit.

## Key Risks

- Extraction may be unreliable across varied invoice formats.
- Users may not trust answers without clear citations.
- Maintenance terms can vary by vendor, such as "brake fluid flush" versus
  "hydraulic fluid service."
- Records may be incomplete, causing the system to miss prior service events.
- Manufacturer service intervals may be hard to source and keep up to date.
- Users may expect automatic email or app import earlier than the product can
  support it.

## Open Questions

- Should v1 be mobile-only, web-only, or both?
- Should the first version support only manual import, or also photo capture
  from the camera?
- Should users be required to review extracted data before it appears in the
  timeline?
- Should manufacturer maintenance interval recommendations be part of v1, or
  deferred until records extraction is reliable?
- What level of privacy and local-device processing is required?
- Should documents be stored in the cloud, locally on device, or both?
- Should the product support multiple vehicles in v1?
- What source of truth should be used for manufacturer service schedules?

## Suggested Milestones

### Milestone 1: Manual Record Organizer

- Create vehicle profile.
- Import PDF or image.
- Store document under vehicle.
- Display document list.

### Milestone 2: Structured Maintenance Timeline

- Extract service date, mileage, vendor, service items, and cost.
- Create editable maintenance timeline.
- Link each timeline item to source document.

### Milestone 3: Cited Question Answering

- Ask questions across one vehicle's records.
- Return concise answers with citations.
- Show uncertainty when records are incomplete.

### Milestone 4: Maintenance Decision Support

- Compare extracted maintenance history against known service intervals.
- Explain whether a recommendation is supported by records.
- Keep manufacturer interval sourcing explicit and reviewable.

## Recommendation

Build the first prototype around a single high-value workflow:

1. Create a vehicle.
2. Add one or more service documents.
3. Extract maintenance facts.
4. Show a timeline.
5. Ask "when was this service last done?" and answer with citations.

This sequence proves the hardest and most valuable product loop before adding
broader document categories, automatic import, or service interval
recommendations.
