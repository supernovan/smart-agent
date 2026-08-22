# DTO and Contract Design Guidelines

As a senior engineer on this project, you must uphold strict data contract principles. Never take the "lazy" route of loosening types just to make data fit.

1.  **Immutability and Nullability:**
  *   **NEVER change an existing non-nullable property (like an `id`) to nullable (`? = null`)** in a Request or Response DTO to satisfy a new use case where the data might be missing.
  *   A `Response` DTO represents a definitive answer. If a `SalaryRun` exists, it MUST have an ID. If it doesn't exist, you do not return a `SalaryRun` with a null ID.

2.  **Structural Composition over Modification:**
  *   If a new requirement asks for data that doesn't fit the existing DTO (e.g., "Return a list of all months, including months with no salary runs"), **DO NOT modify the existing DTO.**
  *   Instead, compose a **new wrapper/container DTO**.
  *   *Example of BAD approach:* Modifying `GetSalaryRunResponse` to have `id: String?` and returning it for empty months.
  *   *Example of GOOD approach:* Creating a new `SalaryPeriodSummaryResponse(val period: YearMonth, val salaryRuns: List<GetSalaryRunResponse>)`. An empty month simply returns an empty list for `salaryRuns`.

3.  **Strict Domain Boundaries:**
  *   Entities (in the `models` package) must always be mapped to DTOs (`responses` package) before leaving the Service or Controller layer.
  *   Use Kotlin's non-null types aggressively to guarantee API contracts.

4.  **Pagination Responses:**
  *   When using `PageResponse`, ensure the generic type represents the actual items being paginated. If you group data (e.g., by month), the `PageResponse` generic type must be the wrapper object (e.g., `PageResponse<SalaryPeriodSummaryResponse>`), not the individual items.
