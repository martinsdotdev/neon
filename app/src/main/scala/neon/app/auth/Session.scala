package neon.app.auth

import neon.common.UserId

import java.time.Instant
import java.util.UUID

case class Session(
    id: UUID,
    tokenHash: String,
    userId: UserId,
    expiresAt: Instant,
    createdAt: Instant,
    ipAddress: Option[String],
    userAgent: Option[String]
)
