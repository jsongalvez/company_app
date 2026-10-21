---------------------------- MODULE Outbox ----------------------------
(*
  Model of the phone's offline save queue and the #/today list.
  Code it describes (web/src, commit recorded in README.md):
    lib/outbox.svelte.ts : enqueue(), flush(), alreadyApplied(), keepRecent(), heldSessions()
    api/client.ts        : resilientFetch (mustQueue / canQueue)
    lib/cache.svelte.ts  : fetchCached seq guard, Query.refresh on SENT
    pages/Today.svelte   : `sessions` = server list + held creates + recent creates
  Server rules come from backend SessionService.updateStatus, SessionRoutes.handleCreateSession,
  ProductSaleService.sell and BranchDayService.assertEditableBranchDay.

  One person, one phone, one session `s`, one product sale. Item kinds: create, mark, sale.
  Statuses: "P" = PENDING (Waiting), "D" = COMPLETED (Done).
  Switches (CONSTANTS) turn each proposed fix on; all FALSE = the code as it is today.
*)
EXTENDS Naturals, Sequences, FiniteSets

CONSTANTS
    DropNoopMark,     \* fix: a mark that undoes the waiting mark and equals the shown status queues nothing
    SkipRemoved,      \* fix: flush skips items the person has since replaced (not yet sent)
    TriedKept,        \* fix: a mark that was already sent once is never replaced; the new mark queues behind it
    BlockBehindFailed, \* fix: a mark waits while an earlier mark for the session sits in "couldn't send"
    ChainVersions,    \* fix: after a mark lands, later waiting marks of the same base version move to the new version
    RecentUntilSeen,  \* fix: "just sent" rows persist over a reload and stay until the server list shows them
    ReplayReason,     \* fix: a replay sent on a later day than it was made carries a reason
    UserCanEditPast,  \* the signed-in person holds EDIT_PAST_DAY
    MaxMarks, MaxReloads

None == 0
Codes == {0, 400, 403, 404, 409}

VARIABLES net, today, srv, sale, queue, sending, todo, flight, sent,
          recent, cache, dashReqs, dseq,
          intent, nid, nmarks, ncreate, nsale, nreload

vars == <<net, today, srv, sale, queue, sending, todo, flight, sent,
          recent, cache, dashReqs, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

Item == [id : Nat, kind : {"create", "mark", "sale"}, st : {"P", "D", "-"}, ver : Nat,
         day : 0..1, qday : 0..1, failed : Codes, tried : BOOLEAN]

TypeOK ==
    /\ net \in {"up", "down"}
    /\ today \in 0..1
    /\ srv \in [ex : BOOLEAN, st : {"P", "D"}, ver : 0..4, day : 0..1]
    /\ sale \in [ex : BOOLEAN, day : 0..1]
    /\ queue \in Seq(Item)
    /\ sending \in BOOLEAN
    /\ flight.phase \in {"none", "req", "res"}

Waiting(q) == SelectSeq(q, LAMBDA i : i.failed = 0)
Ids(q) == {q[k].id : k \in 1..Len(q)}
HasKind(q, k, onlyWaiting) == \E j \in 1..Len(q) : q[j].kind = k /\ (~onlyWaiting \/ q[j].failed = 0)
Remove(q, id) == SelectSeq(q, LAMBDA i : i.id # id)
SetFailed(q, id, c) == [j \in 1..Len(q) |-> IF q[j].id = id THEN [q[j] EXCEPT !.failed = c] ELSE q[j]]

\* heldMark(): the last waiting mark's status, else none
HeldMark(q) ==
    LET m == SelectSeq(Waiting(q), LAMBDA i : i.kind = "mark")
    IN IF Len(m) = 0 THEN "-" ELSE m[Len(m)].st

\* what the Today row shows for the session (Today.svelte `sessions`)
Shown ==
    \/ cache.has
    \/ HasKind(queue, "create", TRUE)
    \/ recent

Displayed ==
    IF HeldMark(queue) # "-" THEN HeldMark(queue)
    ELSE IF cache.has THEN cache.st ELSE "P"

SrvView == [has |-> srv.ex, st |-> srv.st, ver |-> srv.ver]

Init ==
    /\ net = "up" /\ today = 0
    /\ srv = [ex |-> FALSE, st |-> "P", ver |-> 0, day |-> 0]
    /\ sale = [ex |-> FALSE, day |-> 0]
    /\ queue = <<>> /\ sending = FALSE /\ todo = <<>> /\ sent = 0
    /\ flight = [phase |-> "none", item |-> [id |-> 0, kind |-> "sale", st |-> "-", ver |-> 0, day |-> 0, qday |-> 0, failed |-> 0, tried |-> FALSE], code |-> 0]
    /\ recent = FALSE
    /\ cache = [has |-> FALSE, st |-> "P", ver |-> 0, seq |-> 0]
    /\ dashReqs = {} /\ dseq = 0
    /\ intent = "-" /\ nid = 1 /\ nmarks = 0 /\ ncreate = 0 /\ nsale = 0 /\ nreload = 0

(* ---------------- the person ---------------- *)

UserCreate ==
    /\ ncreate = 0
    /\ (net = "down" \/ Waiting(queue) # <<>>)   \* otherwise the save goes straight to the server and never waits
    /\ queue' = Append(queue, [id |-> nid, kind |-> "create", st |-> "-", ver |-> 0, day |-> today, qday |-> today, failed |-> 0, tried |-> FALSE])
    /\ nid' = nid + 1 /\ ncreate' = 1
    /\ UNCHANGED <<net, today, srv, sale, sending, todo, flight, sent, recent, cache, dashReqs, dseq, intent, nmarks, nsale, nreload>>

\* The row's button: Waiting -> Done, Done -> Undo. Marks queue when offline or behind other saves (mustQueue).
UserMark(to) ==
    /\ nmarks < MaxMarks
    /\ Shown
    /\ (net = "down" \/ Waiting(queue) # <<>>)
    /\ to # Displayed
    /\ ~(srv.ex /\ srv.day < today)   \* a mark on an earlier day's session asks for a reason on screen: not an offline save
    /\ LET ver  == IF cache.has THEN cache.ver ELSE 0
           \* enqueue(): the same session's waiting marks are replaced by this one
           kept == SelectSeq(queue, LAMBDA i : ~(i.kind = "mark" /\ i.failed = 0 /\ ~(TriedKept /\ i.tried)))
           tr   == SelectSeq(kept, LAMBDA i : i.kind = "mark" /\ i.failed = 0 /\ i.tried)
           base == IF Len(tr) > 0 THEN tr[Len(tr)].st ELSE IF cache.has THEN cache.st ELSE "P"
           new  == [id |-> nid, kind |-> "mark", st |-> to, ver |-> ver, day |-> today, qday |-> today, failed |-> 0, tried |-> FALSE]
       IN /\ queue' = IF DropNoopMark /\ to = base THEN kept ELSE Append(kept, new)
          /\ todo' = IF SkipRemoved THEN SelectSeq(todo, LAMBDA i : i.kind # "mark") ELSE todo
    /\ nid' = nid + 1 /\ nmarks' = nmarks + 1 /\ intent' = to
    /\ UNCHANGED <<net, today, srv, sale, sending, flight, sent, recent, cache, dashReqs, dseq, ncreate, nsale, nreload>>

UserSale ==
    /\ nsale = 0
    /\ (net = "down" \/ Waiting(queue) # <<>>)
    /\ queue' = Append(queue, [id |-> nid, kind |-> "sale", st |-> "-", ver |-> 0, day |-> today, qday |-> today, failed |-> 0, tried |-> FALSE])
    /\ nid' = nid + 1 /\ nsale' = 1
    /\ UNCHANGED <<net, today, srv, sale, sending, todo, flight, sent, recent, cache, dashReqs, dseq, intent, nmarks, ncreate, nreload>>

Rollover ==
    /\ today = 0 /\ today' = 1
    /\ UNCHANGED <<net, srv, sale, queue, sending, todo, flight, sent, recent, cache, dashReqs, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

NetFlip ==
    /\ net' = IF net = "up" THEN "down" ELSE "up"
    /\ UNCHANGED <<today, srv, sale, queue, sending, todo, flight, sent, recent, cache, dashReqs, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

\* Page reload: memory goes (recent unless persisted, requests, flush loop); localStorage stays (queue, cache).
Reload ==
    /\ nreload < MaxReloads /\ nreload' = nreload + 1
    /\ recent' = IF RecentUntilSeen THEN recent ELSE FALSE
    /\ dashReqs' = {} /\ dseq' = 0
    /\ cache' = [cache EXCEPT !.seq = 0]
    /\ sending' = FALSE /\ todo' = <<>> /\ sent' = 0
    /\ flight' = [flight EXCEPT !.phase = "none"]
    /\ UNCHANGED <<net, today, srv, sale, queue, intent, nid, nmarks, ncreate, nsale>>

\* RECENT_MS (120 s) is long after the refresh that SENT starts has answered or failed.
RecentExpire ==
    /\ recent
    /\ ~sending /\ dashReqs = {}
    /\ (~RecentUntilSeen \/ cache.has)
    /\ recent' = FALSE
    /\ UNCHANGED <<net, today, srv, sale, queue, sending, todo, flight, sent, cache, dashReqs, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

(* ---------------- the dashboard list cache ---------------- *)

DashStart ==
    /\ dseq < 2
    /\ dseq' = dseq + 1
    /\ dashReqs' = dashReqs \cup {[seq |-> dseq + 1, snap |-> SrvView]}
    /\ UNCHANGED <<net, today, srv, sale, queue, sending, todo, flight, sent, recent, cache, intent, nid, nmarks, ncreate, nsale, nreload>>

DashArrive ==
    /\ net = "up"
    /\ \E r \in dashReqs :
        /\ dashReqs' = dashReqs \ {r}
        /\ cache' = IF cache.seq > r.seq THEN cache
                    ELSE [has |-> r.snap.has, st |-> r.snap.st, ver |-> r.snap.ver, seq |-> r.seq]
    /\ UNCHANGED <<net, today, srv, sale, queue, sending, todo, flight, sent, recent, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

DashFail ==
    /\ net = "down"
    /\ \E r \in dashReqs : dashReqs' = dashReqs \ {r}
    /\ UNCHANGED <<net, today, srv, sale, queue, sending, todo, flight, sent, recent, cache, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

(* ---------------- flush() ---------------- *)

\* `for (const i of waiting())`: the loop walks a snapshot taken when it starts.
FlushStart ==
    /\ ~sending /\ Waiting(queue) # <<>>
    /\ sending' = TRUE /\ sent' = 0
    /\ todo' = Waiting(queue)
    /\ UNCHANGED <<net, today, srv, sale, queue, flight, recent, cache, dashReqs, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

\* Live check, as in the loop: a mark whose earlier mark was refused is left where it is.
Blocked(it) == BlockBehindFailed /\ it.kind = "mark" /\ \E j \in 1..Len(queue) : queue[j].kind = "mark" /\ queue[j].failed # 0

SkipBlocked ==
    /\ sending /\ flight.phase = "none" /\ todo # <<>> /\ Blocked(Head(todo))
    /\ todo' = Tail(todo)
    /\ UNCHANGED <<net, today, srv, sale, queue, sending, flight, sent, recent, cache, dashReqs, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

FlushNext ==
    /\ sending /\ flight.phase = "none" /\ todo # <<>> /\ ~Blocked(Head(todo))
    /\ flight' = [phase |-> "req", item |-> Head(todo), code |-> 0]
    /\ todo' = Tail(todo)
    /\ queue' = [j \in 1..Len(queue) |-> IF queue[j].id = Head(todo).id THEN [queue[j] EXCEPT !.tried = TRUE] ELSE queue[j]]
    /\ UNCHANGED <<net, today, srv, sale, sending, sent, recent, cache, dashReqs, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

\* `finally { sending = false; if (sent) dispatch(SENT) }`; SENT refreshes the visible list.
FlushEnd ==
    /\ sending /\ flight.phase = "none" /\ todo = <<>>
    /\ sending' = FALSE /\ sent' = 0
    /\ IF sent > 0 /\ dseq < 2
         THEN /\ dseq' = dseq + 1
              /\ dashReqs' = dashReqs \cup {[seq |-> dseq + 1, snap |-> SrvView]}
         ELSE UNCHANGED <<dseq, dashReqs>>
    /\ UNCHANGED <<net, today, srv, sale, queue, todo, flight, recent, cache, intent, nid, nmarks, ncreate, nsale, nreload>>

\* Server side: what the routes answer for the item in flight.
PastGate(gateDay, it) ==
    IF gateDay >= today THEN 0
    ELSE IF ~UserCanEditPast THEN 403
    ELSE IF ReplayReason /\ it.qday < today THEN 0
    ELSE 400

Legal(from, to) == from # to      \* PENDING->PENDING is neither a mark, an undo nor a correction

Process ==
    /\ flight.phase = "req" /\ net = "up"
    /\ LET it == flight.item IN
       CASE it.kind = "create" ->
              IF srv.ex THEN /\ flight' = [flight EXCEPT !.phase = "res", !.code = 200] /\ UNCHANGED <<srv, sale>>
              ELSE /\ srv' = [ex |-> TRUE, st |-> "P", ver |-> 0, day |-> today]
                   /\ flight' = [flight EXCEPT !.phase = "res", !.code = 200] /\ UNCHANGED sale
         [] it.kind = "mark" ->
              IF ~srv.ex THEN /\ flight' = [flight EXCEPT !.phase = "res", !.code = 404] /\ UNCHANGED <<srv, sale>>
              ELSE IF srv.ver # it.ver THEN /\ flight' = [flight EXCEPT !.phase = "res", !.code = 409] /\ UNCHANGED <<srv, sale>>
              ELSE IF ~Legal(srv.st, it.st) THEN /\ flight' = [flight EXCEPT !.phase = "res", !.code = 400] /\ UNCHANGED <<srv, sale>>
              ELSE IF PastGate(srv.day, it) # 0
                   THEN /\ flight' = [flight EXCEPT !.phase = "res", !.code = PastGate(srv.day, it)] /\ UNCHANGED <<srv, sale>>
                   ELSE /\ srv' = [srv EXCEPT !.st = it.st, !.ver = srv.ver + 1]
                        /\ flight' = [flight EXCEPT !.phase = "res", !.code = 200] /\ UNCHANGED sale
         [] it.kind = "sale" ->
              \* findSameDaySale only recognises a retry on the same day; any other day goes through the gate first
              IF sale.ex /\ sale.day = today THEN /\ flight' = [flight EXCEPT !.phase = "res", !.code = 200] /\ UNCHANGED <<srv, sale>>
              ELSE IF PastGate(it.day, it) # 0
                   THEN /\ flight' = [flight EXCEPT !.phase = "res", !.code = PastGate(it.day, it)] /\ UNCHANGED <<srv, sale>>
                   ELSE /\ sale' = [ex |-> TRUE, day |-> it.day]
                        /\ flight' = [flight EXCEPT !.phase = "res", !.code = 200] /\ UNCHANGED srv
    /\ UNCHANGED <<net, today, queue, sending, todo, sent, recent, cache, dashReqs, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

\* Request never got through: flush returns (reportNetFailure).
ReqLost ==
    /\ flight' = [flight EXCEPT !.phase = "none"]
    /\ flight.phase = "req" /\ net = "down"
    /\ todo' = <<>>
    /\ UNCHANGED <<net, today, srv, sale, queue, sending, sent, recent, cache, dashReqs, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

\* The server answered but the answer never arrived.
ResLost ==
    /\ flight.phase = "res" /\ net = "down"
    /\ flight' = [flight EXCEPT !.phase = "none"]
    /\ todo' = <<>>
    /\ UNCHANGED <<net, today, srv, sale, queue, sending, sent, recent, cache, dashReqs, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

\* Marks queued behind `it` were made from the version `it` was made from; once `it` is on the server they move up.
Bump(q, it) == [j \in 1..Len(q) |-> IF q[j].kind = "mark" /\ q[j].ver = it.ver /\ q[j].id # it.id
                                    THEN [q[j] EXCEPT !.ver = it.ver + 1] ELSE q[j]]

Deliver ==
    /\ flight.phase = "res" /\ net = "up"
    /\ LET it == flight.item  c == flight.code
           gone == Remove(queue, it.id)
           lands == c = 200 \/ (c = 409 /\ it.kind = "mark" /\ srv.st = it.st)   \* 409 -> alreadyApplied(): GET the session, same status
       IN
       /\ flight' = [flight EXCEPT !.phase = "none"]
       /\ IF lands
            THEN /\ sent' = sent + 1
                 /\ recent' = IF it.kind = "create" /\ c = 200 THEN TRUE ELSE recent
                 /\ IF ChainVersions /\ it.kind = "mark"
                      THEN /\ queue' = Bump(gone, it) /\ todo' = Bump(todo, it)
                      ELSE /\ queue' = gone /\ UNCHANGED todo
            ELSE /\ queue' = SetFailed(queue, it.id, c)
                 /\ UNCHANGED <<recent, todo, sent>>
    /\ UNCHANGED <<net, today, srv, sale, sending, cache, dashReqs, dseq, intent, nid, nmarks, ncreate, nsale, nreload>>

Next ==
    \/ UserCreate \/ \E to \in {"P", "D"} : UserMark(to) \/ UserSale
    \/ Rollover \/ NetFlip \/ Reload \/ RecentExpire
    \/ DashStart \/ DashArrive \/ DashFail
    \/ FlushStart \/ FlushNext \/ SkipBlocked \/ FlushEnd \/ Process \/ ReqLost \/ ResLost \/ Deliver

Spec == Init /\ [][Next]_vars

(* ---------------- properties ---------------- *)

\* P1. A session of today that exists (on the server, or waiting on the phone) is always on the list.
NoVanish ==
    (   (srv.ex /\ srv.day = today)
     \/ HasKind(queue, "create", TRUE)
     \/ (flight.phase # "none" /\ flight.item.kind = "create"))
    => Shown

\* P2. Saves the person made in a sensible order are never refused for a clash or a bad request.
NoSelfRefusal ==
    \A j \in 1..Len(queue) : queue[j].failed \notin {400, 409}

\* P3. When everything has been sent, the server holds what the person last chose.
ConvergesToIntent ==
    ((~HasKind(queue, "mark", FALSE)) /\ ~sending /\ flight.phase = "none" /\ intent # "-" /\ srv.ex)
        => srv.st = intent
=======================================================================
