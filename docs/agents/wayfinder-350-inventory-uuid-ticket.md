## Question

How should inventory movement UUID retries classify ownership? Repository transaction must require immutable Branch, Product, Branch Day, creator, movement reason, quantity, notes, and UUID request identity for idempotent return; foreign or altered retries must produce deterministic conflict without repeating stock mutation or audit.
