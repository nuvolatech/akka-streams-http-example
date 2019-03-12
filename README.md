Akka Streams To HTTP Example
=================================

This project builds on this excellent blog post: 
  https://blog.colinbreck.com/backoff-and-retry-error-handling-for-akka-streams/

When integrating streaming data flows, the protocol of choice is often REST/HTTP, and
we'd like a fault-tolerant, back-pressuring way to stream data from akka streams through HTTP.

One common way HTTP supports back pressure is by replying with error code `429 Too Many Requests`, 
signaling the client to slow down. 
Less sophisticated systems may just fail with `500 Internal Server Error`, so we want the flexibility 
of managing the response: certain errors could be retried, but some others we may just let the stream fail.

When retrying, a common way to do it is with exponential backoff, that is, doubling time between retries.



