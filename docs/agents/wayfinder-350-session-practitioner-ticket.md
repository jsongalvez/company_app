## Question

How should concurrent session-practitioner mutations publish version conflicts? Keep child mutation and session version change atomic, translate a lost optimistic update into domain conflict rather than generic 500, and cover concurrent remarks/update/remove outcomes without weakening audit atomicity.
