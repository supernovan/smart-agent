Ge gärna förklaringar och kontext i brödtexten, men håll själva kodblocken helt fria från kommentarer såvida det inte är absolut nödvändigt för att förstå en komplex algoritm.

# Kotlin Code Style & Best Practices

1.  **Named Arguments:**
  *   Whenever instantiating a class, calling a function with more than one parameter, or creating a data class, you MUST use explicit named arguments.
  *   *Bad:* `SalaryRun("run-1", merchant.id, companyId, payoutDate)`
  *   *Good:* `SalaryRun(id = "run-1", merchantId = merchant.id, companyId = companyId, payoutDate = payoutDate)`

2.  **Test Variable Definitions (Avoid Magic Strings/Hardcoding):**
  *   Do NOT hardcode values (magic strings, raw numbers) directly inside mock setups or assertions in Unit Tests.
  *   Define variables with clear names before using them.
  *   *Bad:* `every { repository.findById("merchant-1") } returns ...`
  *   *Good:*
      ```kotlin
      val testMerchantId = "merchant-1"
      every { repository.findById(testMerchantId) } returns ...
      ```
