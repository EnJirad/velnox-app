package com.velnox.core.auth.mapper

import com.velnox.core.auth.dto.UserDto
import com.velnox.core.auth.model.VelnoxRole
import com.velnox.core.auth.model.VelnoxUser
import com.velnox.core.auth.model.VelnoxUserStatus

/**
 * DTO → domain mapping for identity.
 *
 * Explicit rather than reflective so a backend field rename produces one obvious
 * compile-time-visible change instead of silently yielding `null` in the UI.
 *
 * Mapping decisions worth stating:
 *  * `createdAt`/`updatedAt` use `null` for the serializer's `0` sentinel: an
 *    absent timestamp must render as "—", not as 1 January 1970.
 *  * An unknown role is preserved as [VelnoxRole.Unknown] rather than coerced.
 *  * `permissions` is a `Set` because the backend sends a list and every check is
 *    a membership test.
 */
fun UserDto.toDomain(): VelnoxUser = VelnoxUser(
    id = id,
    email = email,
    name = name,
    avatarUrl = avatar?.takeIf { it.isNotBlank() },
    coverUrl = coverUrl?.takeIf { it.isNotBlank() },
    role = VelnoxRole.fromWire(role),
    status = VelnoxUserStatus.fromWire(status),
    department = department?.takeIf { it.isNotBlank() },
    mustChangePassword = mustChangePassword,
    permissions = permissions.toSet(),
    createdAtEpochMillis = createdAt.takeIf { it > 0L },
    updatedAtEpochMillis = updatedAt.takeIf { it > 0L },
)

/**
 * Login responses (`member-login`, `native/google` fallback) carry a flatter
 * identity than `/api/auth/me`, so they are mapped with the same defaults the
 * backend applies rather than left as partially-populated objects.
 */
fun partialIdentity(
    id: String,
    email: String,
    name: String,
    role: String,
    department: String? = null,
): VelnoxUser = VelnoxUser(
    id = id,
    email = email,
    name = name,
    avatarUrl = null,
    coverUrl = null,
    role = VelnoxRole.fromWire(role),
    status = VelnoxUserStatus.Active,
    department = department?.takeIf { it.isNotBlank() },
    mustChangePassword = false,
    permissions = emptySet(),
    createdAtEpochMillis = null,
    updatedAtEpochMillis = null,
)
