package org.vliux.orca.extraction

sealed class ExtractionPipelineException(
    open val errorCode: String,
    open val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class RetryableExtractionException(
    override val errorCode: String,
    message: String,
    cause: Throwable? = null,
) : ExtractionPipelineException(
    errorCode = errorCode,
    retryable = true,
    message = message,
    cause = cause,
)

open class TerminalExtractionException(
    override val errorCode: String,
    message: String,
    cause: Throwable? = null,
) : ExtractionPipelineException(
    errorCode = errorCode,
    retryable = false,
    message = message,
    cause = cause,
)

class MalformedProviderPayloadException(
    message: String,
    cause: Throwable? = null,
) : TerminalExtractionException(
    errorCode = "MALFORMED_PROVIDER_RESPONSE",
    message = message,
    cause = cause,
)
