# Ubiquitous Language

## People & Roles

| Term             | Definition                                                                 | Aliases to avoid         |
| ---------------- | -------------------------------------------------------------------------- | ------------------------ |
| **Practitioner** | Person who performs physical therapy or wellness work on clients.          | Staff, therapist         |
| **Coordinator**  | Person who manages branch financials, attendance, and remittance.          | Admin, cashier           |
| **Manager**      | A role that assigns home branches and manages medical mission delegates.   | Supervisor               |
| **Owner**        | The business owner with global visibility but limited financial edit rights. | Boss                     |
| **Accountant**   | Read-only role for viewing sales and remittance records across all branches. | Auditor                  |
| **User**         | Any authenticated identity within the system.                              | Account, login           |

## Branches & Attendance

| Term              | Definition                                                                    | Aliases to avoid         |
| ----------------- | ----------------------------------------------------------------------------- | ------------------------ |
| **Branch**        | A physical clinic, provincial tour, or medical mission location.              | Site, office, event      |
| **Home Branch**   | A branch where a **User** is permanently assigned and holds a **Slot**.       | Primary branch           |
| **Attendance**    | The record of a **User**'s presence at a **Branch**, tracked via **Clock-In** and **Clock-Out**. | Check-in, sign-out, presence |
| **Clock-In**      | The act of recording the start of a **User**'s presence at a **Branch**.      | Check-in                 |
| **Clock-Out**     | The act of recording the end of a **User**'s presence at a **Branch**.        | Check-out, sign-out      |
| **Relief Duty**   | When a **User** **Clocks-In** to a **Branch** that is not one of their **Home Branches**. | Floating, visiting       |
| **Relief Access** | Temporary edit rights granted to a **Relief Duty** user by a checked-in peer.  | Temp access, daily grant |
| **Slot**          | A cosmetic ordering number (e.g., Slot 1) for practitioners within a **Branch**. | Rank, position           |

## Operations & Clinical

| Term            | Definition                                                                  | Aliases to avoid         |
| --------------- | --------------------------------------------------------------------------- | ------------------------ |
| **Client**      | A global record of a person receiving services across any **Branch**.       | Patient, customer        |
| **Session**     | A single visit or work performed for a **Client** at a **Branch**.          | Appointment, treatment   |
| **Session Type**| Auto-assigned pricing tier (Regular, Subsequent, etc.) based on history.    | Price level              |
| **Voiding**     | Marking a **Session** as invalid without hard-deleting the record.           | Deleting, cancelling     |
| **Concern**     | A structured checkbox item on the waiver describing a client's issue.       | Illness, problem         |

## Finance & Inventory

| Term            | Definition                                                                    | Aliases to avoid         |
| --------------- | ----------------------------------------------------------------------------- | ------------------------ |
| **Unit Price**  | The base price of a product, excluding **Commission**. This is the remittable revenue. | Price, base price        |
| **Retail Price**| The total amount paid by a **Client** for a product (**Unit Price** + **Commission**). | Final price, total price |
| **Remittance**  | The transfer of net income (Session) or **Unit Price** revenue (Product) to the office. | Payout, settlement       |
| **Commission**  | A flat bonus amount set per product. **Retail Price** = **Unit Price** + **Commission**. | Tip, bonus, incentive    |
| **Split**       | The high-precision division of the **Commission** pool among present staff.   | Share, cut               |
| **Compensation** | The daily pay assigned to a **User** for their work at a **Branch**.          | Salary, wage             |
| **Allowance**   | A transport payment provided to users on medical mission duty.                | Stipend, travel pay      |
| **Expense**     | A company cost (e.g., pantry, utilities) logged against a **Branch Day**.     | Spend, outgoing          |
| **Branch Day**  | A calendar day for a specific **Branch** with a lifecycle (Open, Past, Remitted). | Date record              |

## Relationships

- A **User** can have multiple **Home Branches**.
- A **Session** is performed by one or more **Practitioners**.
- A **Remittance** covers a date range of **Branch Days**.
- **Attendance** at a **Branch** determines eligibility for the **Commission Split**.
- A **Relief Duty** user must request **Relief Access** to perform edits.

## Example dialogue

> **Dev:** "Is the **Retail Price** what we remit to the office?"
> **Domain expert:** "No, only the **Unit Price** is remitted. The **Commission** part of the **Retail Price** goes into the daily **Commission** pool for the **Commission Split**."
> **Dev:** "So if a **Practitioner** is on **Relief Duty**, they still get a **Split**?"
> **Domain expert:** "Yes, as long as they **Clock-In**. But to log the sale, they need someone to **Grant Relief Access** first."
> **Dev:** "Does their **Role** give them that access automatically?"
> **Domain expert:** "No. **Roles** are just bundles. We only check if their **User** holds the specific **Capability** for that **Branch Day**."
