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

Two more clusters sit at the service-module tier, above Branch Day:

```
┌──────────────────────────────┐ ┌──────────────────────────────┐
│  RELIEF CLUSTER              │ │  EXPORT MODULE               │
│  service/ReliefAccessService │ │  service/export/             │
│  ReliefInviteService         │ │                              │
│                              │ │ exportDaily()                │
│ requestReliefAccess()        │ │ exportRange()                │
│ grantAccess() denyAccess()   │ │ exportMonthly()              │
│ cancelRequest()              │ │ exportAllTime()              │
│ createInvite() acceptInvite()│ │ exportByBranchType(          │
│ declineInvite() retractInvite│ │   …, format)                 │
│                              │ │                              │
│ Hides:                       │ │ Hides:                       │
│ • broadcast flood rule       │ │ • CsvExporter / PdfExporter  │
│ • grant-time presence/status │ │   adapter selection          │
│   rechecks inside the        │ │ • on-demand rendering        │
│   command transaction        │ │   (results never stored)     │
│ • notifications written in   │ │ • per-window summary         │
│   the same transaction as    │ │   queries                    │
│   the change causing them    │ │                              │
│ • expiry (04:05) and invite  │ │                              │
│   reminder (07:00) jobs      │ │                              │
└──────────────────────────────┘ └──────────────────────────────┘
```

**Relief cluster** covers the two paths to a Relief Duty's edit access: the outsider-initiated **Relief Request** (broadcast to the whole branch, granted/denied/cancelled by any active branch member) and the branch-initiated **Relief Invite** (invitee accepts or declines). Both converge on the day-state semantics owned by Branch Day; link there rather than restating them. Delegate grants for medical missions ride the same seam (`MedicalMissionDelegateService`). Follow-up: accepted-invite revocation (#363, in grilling) may extend this interface once owner rules land.

### Where depth lives

**BranchDayModule** is the deepest single module. Every financial or operational write calls `resolveOrCreate` and `assertEditable` first. All day-state logic lives here and nowhere else.

**FinanceModule** is the second deepest. The commission engine, serializable remittance submission, snapshot write, and BigDecimal arithmetic are all hidden behind four method calls.

**CapabilityMiddleware** is narrow by design — one boolean return — but hides the view query, time-window filtering, deny list check, and priority resolution.

**AuditModule** looks trivial from the outside (`log(event)`) but hides old/new value diffing, flagging rules, and JSONB serialization.

**Repository layer** is intentionally shallow. Interface complexity roughly matches implementation complexity. Repos are not candidates for deepening.

The remaining service-layer code is pass-through by design, not deepening candidates: `dashboard/` aggregates read-only views for the dashboard screen; the remaining top-level services (users, clients, products, expenses, allowances, compensation, notifications, appointment scheduling) are single-aggregate CRUD-style services whose interface matches their implementation.
