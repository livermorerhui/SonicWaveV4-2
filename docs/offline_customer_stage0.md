## Offline Customer - Stage 0

### AppMode
- `ONLINE`: Normal networked mode; customers load from server.
- `OFFLINE`: Offline mode (future work will support local customers).

### CustomerSource
- `ONLINE`: Customers originate from server.
- `OFFLINE`: Customers created locally while offline.

### `UserViewModel.selectedCustomer`
- `null`: Not in any customer detail context.
- Non-null: Currently in a specific customer's detail context.

### Critical Design Decision (non-goal for Stage 0)
- Contextual UI (e.g., "custom preset" visibility) depends only on `selectedCustomer != null`.
- It must **not** depend on `AppMode == OFFLINE`, keeping behavior consistent for online/offline customers.

### Stage 0 Scope
- No runtime behavior changes; types/docs/TODO hooks only.
