package com.companyb.companyapp.utils

/**
 * HTTP utility constants.
 */
object HTTP {
    /**
     * HTTP response status code constants, grouped by category.
     *
     * Reference: [MDN HTTP response status codes](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status)
     */
    object Response {
        /**
         * 1xx Informational — request received, process continuing.
         */
        object Informational {
            /** **100 Continue** — client should continue the request. Interim response. */
            const val CONTINUE = 100

            /** **101 Switching Protocols** — server is switching to the protocol specified in the Upgrade header. */
            const val SWITCHING_PROTOCOLS = 101

            /** **102 Processing** (WebDAV) — server has received and is processing the request, no response yet. */
            const val PROCESSING = 102

            /** **103 Early Hints** — used with the Link header to allow the browser to start preloading resources. */
            const val EARLY_HINTS = 103
        }

        /**
         * 2xx Successful — request was received, understood, and accepted.
         */
        object Successful {
            /** **200 OK** — request succeeded. Default success response. */
            const val OK = 200

            /** **201 Created** — request succeeded and a new resource was created. Typically returned after POST. */
            const val CREATED = 201

            /** **202 Accepted** — request accepted for processing, but processing is not yet complete. */
            const val ACCEPTED = 202

            /** **203 Non-Authoritative Information** — response metadata is from a third-party copy, not the origin server. */
            const val NON_AUTHORITATIVE_INFORMATION = 203

            /** **204 No Content** — request succeeded but there is no content to send. Often used for DELETE. */
            const val NO_CONTENT = 204

            /** **205 Reset Content** — request succeeded; client should reset the document view. */
            const val RESET_CONTENT = 205

            /** **206 Partial Content** — partial GET succeeded. Used when the client sends a Range header. */
            const val PARTIAL_CONTENT = 206

            /** **207 Multi-Status** (WebDAV) — body contains XML with statuses for multiple sub-requests. */
            const val MULTI_STATUS = 207

            /** **208 Already Reported** (WebDAV) — DAV bindings already enumerated in a previous 207 reply. */
            const val ALREADY_REPORTED = 208

            /** **226 IM Used** (HTTP Delta encoding) — server fulfilled a GET using instance manipulations. */
            const val IM_USED = 226
        }

        /**
         * 3xx Redirection — further action is needed to complete the request.
         */
        object Redirection {
            /** **300 Multiple Choices** — more than one possible response; user/agent should choose one. */
            const val MULTIPLE_CHOICES = 300

            /** **301 Moved Permanently** — URL has permanently changed. New URL given in the response. */
            const val MOVED_PERMANENTLY = 301

            /** **302 Found** — URI temporarily changed. Client should continue using the original URI. */
            const val FOUND = 302

            /** **303 See Other** — redirect to a GET request at another URI. Usually after a POST or PUT. */
            const val SEE_OTHER = 303

            /** **304 Not Modified** — response has not been modified; client can use its cached version. */
            const val NOT_MODIFIED = 304

            /**
             * **305 Use Proxy** — requested resource must be accessed through the proxy given in the Location header.
             *
             * @deprecated Deprecated in HTTP/1.1 due to security concerns around proxy configuration.
             */
            @Deprecated("Deprecated in HTTP/1.1 due to security concerns around proxy configuration.")
            const val USE_PROXY = 305

            /** **307 Temporary Redirect** — URI temporarily changed. Same method must be used for the next request. */
            const val TEMPORARY_REDIRECT = 307

            /** **308 Permanent Redirect** — URI permanently changed. Same method must be used. Unlike 301, the method is not allowed to change. */
            const val PERMANENT_REDIRECT = 308
        }

        /**
         * 4xx Client Errors — request contains bad syntax or cannot be fulfilled.
         */
        object ClientError {
            /** **400 Bad Request** — server cannot process the request due to a client error (malformed syntax, invalid framing, etc.). */
            const val BAD_REQUEST = 400

            /** **401 Unauthorized** — client must authenticate to get the requested response. */
            const val UNAUTHORIZED = 401

            /**
             * **402 Payment Required** — reserved for future use.
             *
             * @note Not in widespread standard use; some services use it for quota or billing errors.
             */
            const val PAYMENT_REQUIRED = 402

            /** **403 Forbidden** — client does not have access rights. Unlike 401, identity is known but access is denied. */
            const val FORBIDDEN = 403

            /** **404 Not Found** — server cannot find the requested resource. */
            const val NOT_FOUND = 404

            /** **405 Method Not Allowed** — request method is known but not supported by the target resource. */
            const val METHOD_NOT_ALLOWED = 405

            /** **406 Not Acceptable** — no content matching the criteria given by the Accept headers was found. */
            const val NOT_ACCEPTABLE = 406

            /** **407 Proxy Authentication Required** — authentication must be done by a proxy. */
            const val PROXY_AUTHENTICATION_REQUIRED = 407

            /** **408 Request Timeout** — server timed out waiting for the request. */
            const val REQUEST_TIMEOUT = 408

            /** **409 Conflict** — request conflicts with the current state of the server (e.g. edit conflict). */
            const val CONFLICT = 409

            /** **410 Gone** — resource has been permanently deleted with no forwarding address. */
            const val GONE = 410

            /** **411 Length Required** — Content-Length header field is required but was not defined. */
            const val LENGTH_REQUIRED = 411

            /** **412 Precondition Failed** — client's preconditions in its headers were not met by the server. */
            const val PRECONDITION_FAILED = 412

            /** **413 Content Too Large** — request body is larger than the limits defined by the server. */
            const val CONTENT_TOO_LARGE = 413

            /** **414 URI Too Long** — URI requested by the client is longer than the server is willing to interpret. */
            const val URI_TOO_LONG = 414

            /** **415 Unsupported Media Type** — media format of the request is not supported by the server. */
            const val UNSUPPORTED_MEDIA_TYPE = 415

            /** **416 Range Not Satisfiable** — range specified by the Range header cannot be fulfilled. */
            const val RANGE_NOT_SATISFIABLE = 416

            /** **417 Expectation Failed** — expectation indicated by the Expect header cannot be met by the server. */
            const val EXPECTATION_FAILED = 417

            /**
             * **418 I'm a Teapot** — server refuses to brew coffee because it is a teapot.
             *
             * Defined in RFC 2324 (Hyper Text Coffee Pot Control Protocol). Not expected to be implemented by real HTTP servers.
             */
            const val IM_A_TEAPOT = 418

            /** **421 Misdirected Request** — request was directed at a server unable to produce a response for the given URI. */
            const val MISDIRECTED_REQUEST = 421

            /** **422 Unprocessable Content** (WebDAV) — request was well-formed but contained semantic errors. */
            const val UNPROCESSABLE_CONTENT = 422

            /** **423 Locked** (WebDAV) — resource being accessed is locked. */
            const val LOCKED = 423

            /** **424 Failed Dependency** (WebDAV) — request failed because a previous request it depended on failed. */
            const val FAILED_DEPENDENCY = 424

            /** **425 Too Early** — server is unwilling to process a request that might be replayed. */
            const val TOO_EARLY = 425

            /** **426 Upgrade Required** — server refuses to perform the request using the current protocol. Must upgrade. */
            const val UPGRADE_REQUIRED = 426

            /** **428 Precondition Required** — server requires the request to be conditional, to prevent lost-update problems. */
            const val PRECONDITION_REQUIRED = 428

            /** **429 Too Many Requests** — user has sent too many requests in a given amount of time (rate limiting). */
            const val TOO_MANY_REQUESTS = 429

            /** **431 Request Header Fields Too Large** — server is unwilling to process the request because headers are too large. */
            const val REQUEST_HEADER_FIELDS_TOO_LARGE = 431

            /** **451 Unavailable For Legal Reasons** — resource cannot legally be provided (e.g. government censorship). */
            const val UNAVAILABLE_FOR_LEGAL_REASONS = 451
        }

        /**
         * 5xx Server Errors — server failed to fulfil a valid request.
         */
        object ServerError {
            /** **500 Internal Server Error** — server encountered an unexpected condition preventing it from fulfilling the request. */
            const val INTERNAL_SERVER_ERROR = 500

            /** **501 Not Implemented** — request method is not supported by the server and cannot be handled. */
            const val NOT_IMPLEMENTED = 501

            /** **502 Bad Gateway** — server, while acting as a gateway, got an invalid response from the upstream server. */
            const val BAD_GATEWAY = 502

            /** **503 Service Unavailable** — server is not ready to handle the request (overloaded or down for maintenance). */
            const val SERVICE_UNAVAILABLE = 503

            /** **504 Gateway Timeout** — server, while acting as a gateway, did not get a response in time from the upstream server. */
            const val GATEWAY_TIMEOUT = 504

            /** **505 HTTP Version Not Supported** — HTTP version used in the request is not supported by the server. */
            const val HTTP_VERSION_NOT_SUPPORTED = 505

            /** **506 Variant Also Negotiates** — server has a configuration error in transparent content negotiation. */
            const val VARIANT_ALSO_NEGOTIATES = 506

            /** **507 Insufficient Storage** (WebDAV) — server cannot store the representation needed to complete the request. */
            const val INSUFFICIENT_STORAGE = 507

            /** **508 Loop Detected** (WebDAV) — server detected an infinite loop while processing the request. */
            const val LOOP_DETECTED = 508

            /** **510 Not Extended** — further extensions to the request are required for the server to fulfil it. */
            const val NOT_EXTENDED = 510

            /** **511 Network Authentication Required** — client needs to authenticate to gain network access (e.g. captive portal). */
            const val NETWORK_AUTHENTICATION_REQUIRED = 511
        }
    }
}
