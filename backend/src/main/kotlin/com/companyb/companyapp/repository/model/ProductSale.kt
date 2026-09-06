package com.companyb.companyapp.repository.model
import com.companyb.companyapp.branchday.BranchDayTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class ProductSale(
    val id: UUID,
    val branchDayId: UUID,
    val sessionId: UUID?,
    val clientId: UUID?,
    val isWalkIn: Boolean,
    val productId: UUID,
    val productName: String,
    val handledBy: UUID,
    val quantity: Int,
    val unitPriceAtTime: BigDecimal,
    val totalAmountAtTime: BigDecimal,
    val commissionAmountAtTime: BigDecimal,
    val soldAt: OffsetDateTime,
)

private const val PRICE_PRECISION = 10
private const val PRICE_SCALE = 2
private const val COMMISSION_PRECISION = 15
private const val COMMISSION_SCALE = 4

object ProductSaleTable : Table("product_sale") {
    val id = javaUUID("id")
    val branchDayId = javaUUID("branch_day_id").references(BranchDayTable.id)
    val sessionId = javaUUID("session_id").references(SessionTable.id).nullable()
    val clientId = javaUUID("client_id").references(ClientTable.id).nullable()
    val isWalkIn = bool("is_walk_in").default(false)
    val productId = javaUUID("product_id").references(ProductTable.id)
    val productName = text("product_name")
    val handledBy = javaUUID("handled_by").references(AppUserTable.id)
    val quantity = integer("quantity")
    val unitPriceAtTime = decimal("unit_price_at_time", PRICE_PRECISION, PRICE_SCALE)
    val totalAmountAtTime = decimal("total_amount_at_time", PRICE_PRECISION, PRICE_SCALE)
    val commissionAmountAtTime = decimal("commission_amount_at_time", COMMISSION_PRECISION, COMMISSION_SCALE)
    val soldAt = timestampWithTimeZone("sold_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: ProductSale): Map<String, String?> =
        mapOf(
            "id" to entity.id.toString(),
            "branchDayId" to entity.branchDayId.toString(),
            "sessionId" to entity.sessionId?.toString(),
            "clientId" to entity.clientId?.toString(),
            "isWalkIn" to entity.isWalkIn.toString(),
            "productId" to entity.productId.toString(),
            "productName" to entity.productName,
            "handledBy" to entity.handledBy.toString(),
            "quantity" to entity.quantity.toString(),
            "unitPriceAtTime" to entity.unitPriceAtTime.toPlainString(),
            "totalAmountAtTime" to entity.totalAmountAtTime.toPlainString(),
            "commissionAmountAtTime" to entity.commissionAmountAtTime.toPlainString(),
            "soldAt" to entity.soldAt.toString(),
        )
}
