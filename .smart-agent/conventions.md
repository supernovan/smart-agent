# Naming and Package Conventions

Every major domain module (e.g., `invoices`, `salary`, `merchants`) follows a strict internal structure. You MUST place files in their correct sub-packages and use the correct suffixes.

1.  **`models` (Internal Domain & Entities)**
  *   **Content:** JPA Entities, Enums, and internal domain logic.
  *   **Naming:** Use clean, singular nouns.
  *   *Example:* `Customer`, `Invoice`, `SalaryRun`, `AttachmentType`.
  *   **Rule:** Internal models must NEVER be exposed directly in Controller endpoints.

2.  **`requests` (Incoming Payloads)**
  *   **Content:** DTOs (Data Transfer Objects) used as `@RequestBody` or `@ModelAttribute` in Controllers.
  *   **Naming:** Must always end with the suffix `Request`. Prefix with the action being performed if applicable.
  *   *Example:* `CreateCustomerRequest`, `UpdateInvoiceRequest`, `ProcessSalaryRequest`.
  *   **Rule:** These should contain validation annotations (e.g., `@NotBlank`, `@Min`).

3.  **`responses` (Outgoing Payloads)**
  *   **Content:** DTOs returned by Controllers to the client.
  *   **Naming:** Must always end with the suffix `Response`. Prefix with the action or entity.
  *   *Example:* `GetCustomerResponse`, `InvoiceListResponse`, `SalaryDetailsResponse`.
  *   **Rule:** Controllers must map internal `models` to these `Response` objects before returning.

4.  **Component Naming:**
  *   Controllers end with `Controller` (e.g., `InvoiceController`).
  *   Services end with `Service` (e.g., `InvoiceService`).
  *   Repositories end with `Repository` (e.g., `InvoiceRepository`).

5.   **Naming Conventions**
  *   GET Responses:** Any DTO returned from a GET endpoint MUST be prefixed with `Get`. For example, use `GetSalaryPeriodResponse` instead of `SalaryPeriodResponse`.

6.  **Testing Requirements**
   *  Always Test:** Whenever you create a new feature, modify existing business logic in a Service, or add a new endpoint, you MUST explicitly include a step in your plan to write or update the corresponding Unit Tests (e.g., in `*Test.kt` files).
   * Do not consider a task complete until the tests are implemented.
