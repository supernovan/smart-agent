# Architectural Rules (Spring Boot)

1.  **Strict Layering:**
  *   **Controllers (`@RestController`):** Responsible ONLY for HTTP routing, request validation, and mapping to/from `requests`/`responses`. Never contain business logic.
  *   **Services (`@Service`):** Contain all core business logic. They process incoming data from Controllers, interact with Repositories, and map internal `models` to response objects.
  *   **Repositories (`@Repository`):** Spring Data JPA interfaces for database interaction.

2. **Module Independence:**
  *   The project is highly modular (e.g., `invoices`, `salary`, `bookkeeping`).
  *   A module should primarily interact with its own models and repositories. If it needs data from another module, it should go through that module's Service layer, not access its Repository directly.

3.  **Dependency Injection:**
  *   Use constructor injection for all Spring components. Do not use `@Autowired` on fields.

4.  **Database Entities:**
  *   JPA entities belong in the `models` package.
  *   Use Kotlin `data class` with `@Entity`, but ensure the `@NoArgConstructor` (or similar compiler plugin functionality) is present for JPA compatibility.

5.  **General Coding Philosophy:**
  * Solve the problem structurally, not symptomatically.** If you need to return empty periods, change the data structure you are returning, do not break the strictness of the existing data structures.
  * Prioritize explicit data structures.** Don't use generic `Map<String, Any>` or alter existing classes just to cram new data in. Create well-named, dedicated DTOs for new aggregations.
