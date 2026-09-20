package com.velnox.core.data.model

import com.velnox.core.auth.model.VelnoxRole
import com.velnox.core.auth.model.VelnoxUserStatus
import com.velnox.core.common.domain.ProductStatus
import com.velnox.core.common.domain.SellerStatus
import com.velnox.core.common.domain.VerificationStatus
import com.velnox.core.common.util.VelnoxFormat
import com.velnox.core.data.dto.AdminCategoryDto
import com.velnox.core.data.dto.AdminSellerDto
import com.velnox.core.data.dto.AdminUserDto
import com.velnox.core.data.dto.AuditLogDto
import com.velnox.core.data.dto.DashboardCountsDto
import com.velnox.core.data.dto.EmployeeDto
import com.velnox.core.data.dto.ModerationProductDto
import com.velnox.core.data.dto.PermissionDto
import com.velnox.core.data.dto.PlatformSettingDto
import com.velnox.core.data.dto.SellerGoalDto
import com.velnox.core.data.dto.SellerIncomeDto
import com.velnox.core.data.dto.SellerProfileEnvelopeDto
import com.velnox.core.data.dto.SellerRecordDto
import com.velnox.core.data.dto.SellerStatusDto

/** Seller application state plus the reasons an application was refused. */
data class SellerStatusInfo(
    val status: SellerStatus,
    val rejectionReason: String?,
    val correctionReason: String?,
    val rejectionReasonCode: String?,
    val correctionReasonCode: String?,
    val verificationStatus: VerificationStatus,
) {
    /** `true` when the seller workspace may be entered. */
    val isApproved: Boolean get() = status.isApproved

    val canReapply: Boolean get() = status.canReapply
}

data class SellerRecord(
    val id: String,
    val name: String?,
    val status: SellerStatus,
    val verificationStatus: VerificationStatus,
    val rejectionReason: String?,
    val createdAtEpochMillis: Long?,
)

data class SellerProfile(
    val seller: SellerRecord?,
    val shops: List<Shop>,
) {
    val primaryShop: Shop? get() = shops.firstOrNull()
}

/**
 * Seller income report.
 *
 * Every value is a backend aggregate over real orders. Nothing here is computed on
 * device, because a client-side sum would silently disagree with Neon and the whole
 * point of this endpoint is to be the financial record.
 */
data class SellerIncome(
    val grossSales: Double,
    val commission: Double,
    val netEarnings: Double,
    val pendingPayout: Double,
    val orderCount: Int,
    val unitsSold: Int,
    val currency: String,
) {
    val grossSalesLabel: String get() = VelnoxFormat.baht(grossSales)
    val commissionLabel: String get() = VelnoxFormat.baht(commission)
    val netEarningsLabel: String get() = VelnoxFormat.baht(netEarnings)
    val pendingPayoutLabel: String get() = VelnoxFormat.baht(pendingPayout)

    /** Average order value, or `null` when there are no orders to average. */
    val averageOrderValue: Double? get() = if (orderCount > 0) grossSales / orderCount else null
}

data class SellerGoal(
    val id: String,
    val title: String,
    val metric: String,
    val targetValue: Double,
    val currentValue: Double,
    val period: String?,
    val status: String?,
    val dueAtEpochMillis: Long?,
) {
    /** Progress in `0f..1f`, or `null` when the target is not a positive number. */
    val progress: Float?
        get() = if (targetValue > 0.0) {
            (currentValue / targetValue).toFloat().coerceIn(0f, 1f)
        } else {
            null
        }
}

data class DashboardCounts(
    val pendingSellers: Int?,
    val pendingProducts: Int?,
    val totalUsers: Int?,
    val totalSellers: Int?,
    val totalOrders: Int?,
    val totalProducts: Int?,
    val openVerifications: Int?,
    val grossMerchandiseValue: Double?,
) {
    /**
     * A tile is rendered only when the backend actually reported it.
     * A missing count must not be shown as a confident zero.
     */
    val hasAnyMetric: Boolean
        get() = listOf(pendingSellers, pendingProducts, totalUsers, totalSellers, totalOrders, totalProducts, openVerifications, grossMerchandiseValue?.toInt())
            .any { it != null }
}

data class ManagedSeller(
    val id: String,
    val name: String?,
    val email: String?,
    val phone: String?,
    val status: SellerStatus,
    val verificationStatus: VerificationStatus,
    val rejectionReason: String?,
    val shopId: String?,
    val shopName: String?,
    val productCount: Int?,
    val createdAtEpochMillis: Long?,
)

data class ModerationItem(
    val id: String,
    val name: String,
    val status: ProductStatus,
    val shopName: String?,
    val price: Double,
    val currency: String,
    val categorySlug: String?,
    val imageUrl: String?,
    val submittedAtEpochMillis: Long?,
    val rejectionReason: String?,
) {
    val priceLabel: String get() = VelnoxFormat.baht(price)
}

data class ManagedUser(
    val id: String,
    val email: String,
    val name: String,
    val role: VelnoxRole,
    val status: VelnoxUserStatus,
    val department: String?,
    val avatarUrl: String?,
    val employeeId: String?,
    val createdAtEpochMillis: Long?,
)

data class EmployeeAccount(
    val userId: String,
    val employeeId: String?,
    val email: String,
    val name: String,
    val role: VelnoxRole,
    val department: String?,
    val status: VelnoxUserStatus,
    val mustChangePassword: Boolean,
    val permissions: Set<String>,
)

data class AuditEntry(
    val id: String,
    val action: String,
    val actorEmail: String?,
    val entityType: String?,
    val entityId: String?,
    val metadataJson: String?,
    val createdAtEpochMillis: Long?,
)

data class PlatformSetting(
    val key: String,
    val valueJson: String?,
    val description: String?,
)

data class ManagedCategory(
    val id: String,
    val slug: String,
    val name: String,
    val parentId: String?,
    val sortOrder: Int,
    val isActive: Boolean,
    val imageUrl: String?,
    val productCount: Int?,
)

data class PermissionOption(
    val code: String,
    val label: String,
    val group: String?,
)

// ─── Mappers ─────────────────────────────────────────────────────────────────

fun SellerStatusDto.toDomain(): SellerStatusInfo = SellerStatusInfo(
    status = SellerStatus.fromWire(status),
    rejectionReason = rejectionReason?.takeIf { it.isNotBlank() },
    correctionReason = correctionReason?.takeIf { it.isNotBlank() },
    rejectionReasonCode = rejectionReasonCode?.takeIf { it.isNotBlank() },
    correctionReasonCode = correctionReasonCode?.takeIf { it.isNotBlank() },
    verificationStatus = VerificationStatus.fromWire(verificationStatus),
)

fun SellerRecordDto.toDomain(): SellerRecord = SellerRecord(
    id = id,
    name = name,
    status = SellerStatus.fromWire(status),
    verificationStatus = VerificationStatus.fromWire(verificationStatus),
    rejectionReason = rejectionReason,
    createdAtEpochMillis = createdAt.takeIf { it > 0L },
)

fun SellerProfileEnvelopeDto.toDomain(): SellerProfile = SellerProfile(
    seller = seller?.toDomain(),
    shops = shops.map { it.toDomain() },
)

fun SellerIncomeDto.toDomain(): SellerIncome = SellerIncome(
    grossSales = grossSales,
    commission = commission,
    netEarnings = netEarnings,
    pendingPayout = pendingPayout,
    orderCount = orderCount,
    unitsSold = unitsSold,
    currency = currency,
)

fun SellerGoalDto.toDomain(): SellerGoal = SellerGoal(
    id = id,
    title = title,
    metric = metric,
    targetValue = targetValue,
    currentValue = currentValue,
    period = period,
    status = status,
    dueAtEpochMillis = dueAt,
)

fun DashboardCountsDto.toDomain(): DashboardCounts = DashboardCounts(
    pendingSellers = pendingSellers,
    pendingProducts = pendingProducts,
    totalUsers = totalUsers,
    totalSellers = totalSellers,
    totalOrders = totalOrders,
    totalProducts = totalProducts,
    openVerifications = openVerifications,
    grossMerchandiseValue = grossMerchandiseValue,
)

fun AdminSellerDto.toDomain(): ManagedSeller = ManagedSeller(
    id = id,
    name = name,
    email = email,
    phone = phone,
    status = SellerStatus.fromWire(status),
    verificationStatus = VerificationStatus.fromWire(verificationStatus),
    rejectionReason = rejectionReason,
    shopId = shopId,
    shopName = shopName,
    productCount = productCount,
    createdAtEpochMillis = createdAt.takeIf { it > 0L },
)

fun ModerationProductDto.toDomain(): ModerationItem = ModerationItem(
    id = id,
    name = name,
    status = ProductStatus.fromWire(status),
    shopName = shopName,
    price = price,
    currency = currency,
    categorySlug = categorySlug,
    imageUrl = primaryImageUrl?.takeIf { it.isNotBlank() },
    submittedAtEpochMillis = submittedAt.takeIf { it > 0L },
    rejectionReason = rejectionReason,
)

fun AdminUserDto.toDomain(): ManagedUser = ManagedUser(
    id = id,
    email = email,
    name = name,
    role = VelnoxRole.fromWire(role),
    status = VelnoxUserStatus.fromWire(status),
    department = department,
    avatarUrl = avatar,
    employeeId = employeeId,
    createdAtEpochMillis = createdAt.takeIf { it > 0L },
)

fun EmployeeDto.toDomain(): EmployeeAccount = EmployeeAccount(
    userId = userId,
    employeeId = employeeId,
    email = email,
    name = name,
    role = VelnoxRole.fromWire(role),
    department = department,
    status = VelnoxUserStatus.fromWire(status),
    mustChangePassword = mustChangePassword,
    permissions = permissions.toSet(),
)

fun AuditLogDto.toDomain(): AuditEntry = AuditEntry(
    id = id,
    action = action,
    actorEmail = actorEmail,
    entityType = entityType,
    entityId = entityId,
    metadataJson = metadata?.takeIf { it.toString() != "null" }?.toString(),
    createdAtEpochMillis = createdAt.takeIf { it > 0L },
)

fun PlatformSettingDto.toDomain(): PlatformSetting = PlatformSetting(
    key = key,
    valueJson = value?.takeIf { it.toString() != "null" }?.toString(),
    description = description,
)

fun AdminCategoryDto.toDomain(): ManagedCategory = ManagedCategory(
    id = id,
    slug = slug,
    name = name,
    parentId = parentId,
    sortOrder = sortOrder,
    isActive = isActive,
    imageUrl = imageUrl,
    productCount = productCount,
)

fun PermissionDto.toDomain(): PermissionOption = PermissionOption(
    code = code,
    label = label?.takeIf { it.isNotBlank() } ?: code,
    group = group,
)
