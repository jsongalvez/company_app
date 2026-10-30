# CompanyApp word list

CompanyApp is the internal app of a physical therapy practice with several branches. Practitioners log sessions, coordinators handle money and remittances, and owners manage people and branches.

This list is the shared language between the owner, the developers and the app. The **App word** is exactly what users see on screen. The **Code name** is what the backend and database call it. If you add or rename a word, update this file in the same change.

Rules for app wording:

- Use the app word on screen. Never show a code name (`EDIT_PAST_DAY`, `PENDING`, `UUID`), a status code, or an issue number.
- Error messages say what happened and what to do next, for example "This day is closed. Ask a coordinator to edit it."
- Money is shown in pesos with two decimals and no symbol in tables (`2,500.00`).

## Places and days

| App word | What it means | Code name |
|---|---|---|
| Branch | A place where sessions happen: a clinic, a provincial tour, or a medical mission. Each has its own stock, sessions and money. | `branch` |
| Clinic | A permanent branch. | `CLINIC` |
| Provincial tour | A temporary trip to a province, run as its own branch. | `PROVINCIAL_TOUR` |
| Medical mission | A free event, run as its own branch. Every session there costs 0 and doesn't count toward a client's visit history. | `MEDICAL_MISSION` |
| Day | One business day at one branch. A day ends at **4 AM** Manila time, not midnight, so late work still counts for the same day. | branch day, operational date |
| Open | Today. Anyone on duty can add and change things. | `OPEN` |
| Closed | A past day. Only a coordinator can change it. | `PAST` |
| Remitted | A day whose money was sent in a remittance. Only a coordinator can change it, and must give a reason. | `REMITTED` |

## People and access

| App word | What it means | Code name |
|---|---|---|
| User | Anyone who signs in. | `app_user` |
| Practitioner | Treats clients and logs sessions. | `PRACTITIONER` |
| Coordinator | Handles expenses, pay and remittances for their branches. The only one who can change closed days. | `COORDINATOR` |
| Manager | A coordinator who can also manage users and mission helpers. | `MANAGER` |
| Owner | Runs the business: users, branches and products. Sees every branch, look-only; works like a member only where they are one. Pays staff at every branch, but never sees anyone's commission outside their own branch. | `OWNER` |
| Accountant | Can see every branch's numbers but can't change anything, and is never a branch member. Sees no commission. | `ACCOUNTANT` |
| Superuser | Full access, for setup and support. The developer's role; only a superuser can "View as" someone. Works at every branch without being a member. Shown as Practitioner to anyone else who manages users. | `SUPERUSER` |
| View as | A superuser seeing the app as another person, with that person's permissions. Look-only unless "Allow changes" is on. | `X-View-As` header |
| New (no access yet) | Someone who joined with an invite code but hasn't been given a role. They can sign in but see nothing. | `ONBOARDING` |
| Role | A job title that decides what someone can do. A user can have several. | `role` |
| Access | What a user is allowed to do where. The app hides what you can't do. The code calls each single permission a "capability". | capability, `user_capability` |
| Home branch | A branch a user is assigned to. | branch assignment |
| Order | Where a person appears in staff lists at a branch (1 is first). | slot |
| Invite code | A one-time code a manager gives a new user so they can set their password. | invite |
| Deactivate / Reactivate | Block someone from signing in, or let them back in. Nothing they did is deleted. | `INACTIVE` / `ACTIVE` |
| Mission helper | Someone added under Helpers on the Branches page. Works the branch like relief, with no end day, and shares its commission until removed. | delegate, `ASSIGN_DELEGATE` |

## Duty

| App word | What it means | Code name |
|---|---|---|
| Clock in / Clock out | Start and end your shift at a branch. | attendance |
| On duty | Clocked in now. | active shift |
| Relief duty | Working a shift at a branch that isn't your home branch. You can look at first; to make changes the branch must approve you. | relief |
| Ask for relief | Ask a branch to let you work there on a day. Anyone at that branch can approve or decline. | relief request |
| Relief invite | A branch asks someone to come work there on a day. That person accepts or declines. | relief invite |
| Withdraw / Take back / Cancel | The asker cancels their request, the branch takes back an invite before it's answered, or the branch cancels an accepted invite before the shift starts. | cancel / retract / revoke |

## Clients and sessions

| App word | What it means | Code name |
|---|---|---|
| Client | A person who comes for treatment. One record is shared by all branches. | `client` |
| Erase personal details | Remove a client's name, phone, address and health notes for good. Only gender and age stay, for reports. | anonymize |
| Session | One visit by one client at one branch. | `session` |
| Walk-in | A session that wasn't booked. It can't be marked no-show or cancelled. | `isWalkIn` |
| Waiting | The session is booked or the client is here, not finished yet. A client can only have one waiting session. | `PENDING` |
| Done | The session happened. Only done sessions count as income. | `COMPLETED` |
| No-show | The client didn't come. | `NO_SHOW` |
| Cancelled | The session was called off. | `CANCELLED` |
| First visit | The client's first paid visit anywhere, at a clinic. | `REGULAR` |
| First visit (tour) | The client's first paid visit anywhere, on a provincial tour. | `PROVINCIAL_FIRST` |
| Second visit | The client's second paid visit. | `SECOND_SESSION` |
| Return visit | The third paid visit or later. | `SUBSEQUENT` |
| Mission visit | A free visit at a medical mission. | `MEDICAL_MISSION` |
| Visits before the app | How many times a client came before we started using the app. Staff enter it on the client, and it counts toward the visit type, so a returning client isn't treated as new. | `earlierVisits` |
| Price list | What each kind of visit costs at a branch. A new session starts at this price; it can be changed. | base rates |
| Concern | What the client came in for, e.g. back pain. | `concern` |
| Void / Restore | Take a session out of the money totals but keep it on record, with a reason. Restore puts it back. | void / unvoid |

## Products and stock

| App word | What it means | Code name |
|---|---|---|
| Product | Something the practice sells. The product list is shared by all branches. | `product` |
| Category | A group of products. | `product_category` |
| Stock | How many of each product a branch has. | inventory |
| Restock | Add units that arrived. | restock |
| Tester / Sample / Missing | Units taken out of stock for a reason other than a sale. | movement reasons |
| Count fix | Correct the stock count after a recount. | `ADJUSTMENT` |
| Product sale | Selling products to a client or a walk-in. | `product_sale` |
| Commission | The fixed amount staff earn for each product sold. | commission |
| Commission share | A day's commission, split equally among everyone on duty when the sale happened. Only the branch's members, its relief that day and its helpers see it; sales reports never show it. | commission split, `VIEW_COMMISSION` |

## Money

| App word | What it means | Code name |
|---|---|---|
| Income | Money from done sessions. | gross income |
| Pay | What a staff member earns for a day. | compensation |
| Allowance | Extra money such as transport. | allowance |
| Expense | Money spent from the branch's cash, e.g. supplies. | expense |
| Net | Income minus pay and expenses. | net income |
| Remittance | Sending a branch's money to the business. There are two kinds: **Sessions** (net session income) and **Product sales**. | remittance |
| Draft | A remittance still being put together. It can be changed freely. | `DRAFT` |
| Sent | A submitted remittance. Its totals are frozen, and its days become Remitted. | `SUBMITTED`, snapshot |
| Undo | Bring a sent remittance back to draft, within 48 hours, with a reason. | undo |
| Changed since sent | The difference between a sent remittance's frozen totals and today's numbers, caused by later edits. | drift |
| Bank transfer / Handed to accountant | How the money was sent. | `BANK_TRANSFER` / `HANDED_TO_ACCOUNTANT` |

## Records

| App word | What it means | Code name |
|---|---|---|
| Alerts | Messages for you, e.g. a relief request or an upcoming session. | notifications |
| Change history | Who changed what and when, with old and new values. Nothing in it can be edited. | audit log |
| Needs review | A change to a closed or remitted day, flagged for the owner to look at. | flagged entry |
| Mark reviewed | Confirm you've looked at a flagged change. | acknowledge |
