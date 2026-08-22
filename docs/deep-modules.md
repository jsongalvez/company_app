# Deep Module Map

Modules are packages with a narrow public interface hiding significant implementation complexity. Use this as the reference when deciding where new logic belongs.

```
┌─────────────────────────────────────────────────────────────┐
│                         ROUTES LAYER                        │
│   (thin — parse request, call one service method, return)   │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      CAPABILITY MIDDLEWARE                   │
│  hasCapability(userId, code, contextType, contextId): Bool  │
│  • queries active_user_capabilities view                    │
│  • checks JWT deny list                                     │
│  • one call — 403 or pass                                   │
└────────────────────────────┬────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
┌────────▼────────┐ ┌────────▼────────┐ ┌────────▼────────┐
│  SESSION MODULE │ │ INVENTORY MODULE│ │  FINANCE MODULE │
│                 │ │                 │ │                 │
│ create()        │ │ recordMovement()│ │ assignCompensation()│
│ updateStatus()  │ │ getStock()      │ │ recalcCommission()  │
│ void()          │ │ getAlerts()     │ │ createDraft()       │
│ unvoid()        │ │                 │ │ submitRemittance()  │
│ addPractitioner │ │ Hides:          │ │ logExpense()        │
│ promoteConcern()│ │ • optimistic    │ │                     │
│                 │ │   lock on       │ │ Hides:              │
│ Hides:          │ │   branch_inv    │ │ • commission engine │
│ • session type  │ │ • sign/notes    │ │ • SERIALIZABLE      │
│   computation   │ │   constraints   │ │   submit tx         │
│ • concurrent    │ │ • branch_day    │ │ • snapshot write    │
│   session guard │ │   state check   │ │ • day transition    │
│ • base rate     │ │ • movement log  │ │ • BigDecimal        │
│   snapshot      │ │                 │ │   arithmetic        │
│ • void view     │ │                 │ │                     │
│   (never raw    │ │                 │ │                     │
│   session_void) │ │                 │ │                     │
└────────┬────────┘ └────────┬────────┘ └────────┬────────┘
         │                   │                   │
┌────────▼───────────────────▼───────────────────▼────────┐
│                     BRANCH DAY MODULE                    │
│             resolveOrCreate(branchId, date): BranchDay  │
│             assertEditable(branchDayId, userId)         │
│                                                         │
│  Hides: UPSERT branch_day, day-state evaluation,        │
│  OPEN/PAST/REMITTED logic, flagged audit enforcement    │
└──────────────────────────────┬──────────────────────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
┌────────▼────────┐  ┌─────────▼────────┐  ┌────────▼────────┐
│ ATTENDANCE MOD  │  │   AUTH MODULE    │  │  AUDIT MODULE   │
│                 │  │                  │  │                 │
│ clockIn()       │  │ login()          │  │ log(event)      │
│ clockOut()      │  │ issueToken()     │  │ flag(id, reason)│
│ markPresent()   │  │ validateToken()  │  │ acknowledge()   │
│                 │  │ deactivateUser() │  │                 │
│ Hides:          │  │                  │  │ Hides:          │
│ • branch_day_   │  │ Hides:           │  │ • old/new value │
│   assignment    │  │ • deny list      │  │   serialization │
│   creation      │  │ • bcrypt compare │  │ • flagging rule │
│ • is_relief     │  │ • JWT claims     │  │   (REMITTED     │
│   computation   │  │ • capability     │  │   days auto-    │
│ • multiple-     │  │   seeding        │  │   flag)         │
│   shift index   │  │                  │  │                 │
└────────┬────────┘  └────────┬─────────┘  └────────┬────────┘
         │                    │                      │
         └────────────────────┼──────────────────────┘
                              │
┌─────────────────────────────▼────────────────────────────┐
│                     REPOSITORY LAYER                     │
│   (one repo per aggregate — no cross-repo calls here)    │
│                                                          │
│  SessionRepo    InventoryRepo    RemittanceRepo          │
│  ClientRepo     CompensationRepo AttendanceRepo          │
│  BranchDayRepo  UserRepo         AuditRepo               │
└──────────────────────────────────────────────────────────┘
```

### Where depth lives

**BranchDayModule** is the deepest single module. Every financial or operational write calls `resolveOrCreate` and `assertEditable` first. All day-state logic lives here and nowhere else.

**FinanceModule** is the second deepest. The commission engine, serializable remittance submission, snapshot write, and BigDecimal arithmetic are all hidden behind four method calls.

**CapabilityMiddleware** is narrow by design — one boolean return — but hides the view query, time-window filtering, deny list check, and priority resolution.

**AuditModule** looks trivial from the outside (`log(event)`) but hides old/new value diffing, flagging rules, and JSONB serialization.

**Repository layer** is intentionally shallow. Interface complexity roughly matches implementation complexity. Repos are not candidates for deepening.
