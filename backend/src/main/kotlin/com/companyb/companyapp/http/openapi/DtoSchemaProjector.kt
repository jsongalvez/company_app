package com.companyb.companyapp.http.openapi

import com.companyb.companyapp.contracts.audit.AuditLogBrowseResponse
import com.companyb.companyapp.contracts.audit.AuditLogEntryResponse
import com.companyb.companyapp.contracts.audit.AuditLogTableResponse
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.branch.CreateBranchRequest
import com.companyb.companyapp.contracts.branch.MeBranchResponse
import com.companyb.companyapp.contracts.branchday.BranchDayTodayResponse
import com.companyb.companyapp.contracts.branchday.BranchDayUserResponse
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.client.CreateClientRequest
import com.companyb.companyapp.contracts.client.UpdateClientRequest
import com.companyb.companyapp.contracts.commerce.AddInventoryCardRequest
import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse
import com.companyb.companyapp.contracts.commerce.CreateProductCategoryRequest
import com.companyb.companyapp.contracts.commerce.CreateProductRequest
import com.companyb.companyapp.contracts.commerce.CreateProductSaleRequest
import com.companyb.companyapp.contracts.commerce.InventoryMovementRequest
import com.companyb.companyapp.contracts.commerce.InventoryMovementResponse
import com.companyb.companyapp.contracts.commerce.ProductCategoryResponse
import com.companyb.companyapp.contracts.commerce.ProductResponse
import com.companyb.companyapp.contracts.commerce.ProductSaleResponse
import com.companyb.companyapp.contracts.commerce.RestockRequest
import com.companyb.companyapp.contracts.commerce.UpdateProductRequest
import com.companyb.companyapp.contracts.commission.CommissionInclusionResponse
import com.companyb.companyapp.contracts.commission.CommissionSplitResponse
import com.companyb.companyapp.contracts.commission.CreateCommissionInclusionRequest
import com.companyb.companyapp.contracts.finance.AllowanceResponse
import com.companyb.companyapp.contracts.finance.CompensationResponse
import com.companyb.companyapp.contracts.finance.CreateAllowanceRequest
import com.companyb.companyapp.contracts.finance.CreateCompensationRequest
import com.companyb.companyapp.contracts.finance.CreateExpenseRequest
import com.companyb.companyapp.contracts.finance.DeleteExpenseRequest
import com.companyb.companyapp.contracts.finance.ExpenseResponse
import com.companyb.companyapp.contracts.finance.RestoreExpenseRequest
import com.companyb.companyapp.contracts.finance.UpdateCompensationRequest
import com.companyb.companyapp.contracts.finance.UpdateExpenseRequest
import com.companyb.companyapp.contracts.identity.AcceptInviteRequest
import com.companyb.companyapp.contracts.identity.ForgotPasswordRequest
import com.companyb.companyapp.contracts.identity.InviteMintRequest
import com.companyb.companyapp.contracts.identity.InviteMintResponse
import com.companyb.companyapp.contracts.identity.LoginRequest
import com.companyb.companyapp.contracts.identity.LoginResponse
import com.companyb.companyapp.contracts.identity.MeResponse
import com.companyb.companyapp.contracts.identity.ResetPasswordRequest
import com.companyb.companyapp.contracts.identity.RoleResponse
import com.companyb.companyapp.contracts.identity.UserAssignmentResponse
import com.companyb.companyapp.contracts.identity.UserRoleReplaceRequest
import com.companyb.companyapp.contracts.identity.UserSummaryResponse
import com.companyb.companyapp.contracts.incident.FeedbackRequest
import com.companyb.companyapp.contracts.incident.FeedbackResponse
import com.companyb.companyapp.contracts.incident.IncidentPacket
import com.companyb.companyapp.contracts.incident.PoolSnapshot
import com.companyb.companyapp.contracts.notification.NotificationHistoryResponse
import com.companyb.companyapp.contracts.notification.NotificationMarkAllReadResponse
import com.companyb.companyapp.contracts.notification.NotificationResponse
import com.companyb.companyapp.contracts.notification.NotificationUnreadCountResponse
import com.companyb.companyapp.contracts.remittance.AddDayBreakdownRequest
import com.companyb.companyapp.contracts.remittance.CreateRemittanceDraftRequest
import com.companyb.companyapp.contracts.remittance.CreateRemittanceLineRequest
import com.companyb.companyapp.contracts.remittance.RemittanceDayBreakdownResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDayPickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDetailResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDriftResponse
import com.companyb.companyapp.contracts.remittance.RemittanceFinancialSnapshotResponse
import com.companyb.companyapp.contracts.remittance.RemittanceLineResponse
import com.companyb.companyapp.contracts.remittance.RemittanceProductSalePickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceResponse
import com.companyb.companyapp.contracts.remittance.RemittanceSessionPickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceSubmitResponse
import com.companyb.companyapp.contracts.remittance.SubmitRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UndoRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UpdateRemittanceHeaderRequest
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryBrowseResponse
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryResponse
import com.companyb.companyapp.contracts.reporting.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.contracts.session.AddPractitionerRequest
import com.companyb.companyapp.contracts.session.AddSessionConcernRequest
import com.companyb.companyapp.contracts.session.ConcernResponse
import com.companyb.companyapp.contracts.session.CreateSessionRequest
import com.companyb.companyapp.contracts.session.DashboardCommissionResponse
import com.companyb.companyapp.contracts.session.DashboardPractitionerResponse
import com.companyb.companyapp.contracts.session.DashboardResponse
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.contracts.session.PromoteConcernRequest
import com.companyb.companyapp.contracts.session.RateResponse
import com.companyb.companyapp.contracts.session.RemovePractitionerRequest
import com.companyb.companyapp.contracts.session.RemoveSessionConcernRequest
import com.companyb.companyapp.contracts.session.SessionPractitionerResponse
import com.companyb.companyapp.contracts.session.SessionPreviewResponse
import com.companyb.companyapp.contracts.session.SessionResponse
import com.companyb.companyapp.contracts.session.SessionVoidResponse
import com.companyb.companyapp.contracts.session.SetRateRequest
import com.companyb.companyapp.contracts.session.UnvoidSessionRequest
import com.companyb.companyapp.contracts.session.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.contracts.session.UpdateSessionFinalPriceRequest
import com.companyb.companyapp.contracts.session.UpdateSessionStatusRequest
import com.companyb.companyapp.contracts.session.VoidSessionRequest
import com.companyb.companyapp.contracts.workforce.ActiveAttendanceResponse
import com.companyb.companyapp.contracts.workforce.ActiveShiftResponse
import com.companyb.companyapp.contracts.workforce.AssignDelegateRequest
import com.companyb.companyapp.contracts.workforce.AssignmentResponse
import com.companyb.companyapp.contracts.workforce.AttendanceMarkResponse
import com.companyb.companyapp.contracts.workforce.BranchMemberResponse
import com.companyb.companyapp.contracts.workforce.ClockInRequest
import com.companyb.companyapp.contracts.workforce.ClockInResponse
import com.companyb.companyapp.contracts.workforce.ClockOutRequest
import com.companyb.companyapp.contracts.workforce.ClockOutResponse
import com.companyb.companyapp.contracts.workforce.CreateAssignmentRequest
import com.companyb.companyapp.contracts.workforce.CreateReliefInviteRequest
import com.companyb.companyapp.contracts.workforce.DelegateResponse
import com.companyb.companyapp.contracts.workforce.DenyReliefAccessRequest
import com.companyb.companyapp.contracts.workforce.GrantReliefAccessRequest
import com.companyb.companyapp.contracts.workforce.MarkAttendanceRequest
import com.companyb.companyapp.contracts.workforce.MemberAttendanceResponse
import com.companyb.companyapp.contracts.workforce.ReliefAccessRequest
import com.companyb.companyapp.contracts.workforce.ReliefAccessResponse
import com.companyb.companyapp.contracts.workforce.ReliefBranchOptionResponse
import com.companyb.companyapp.contracts.workforce.ReliefCandidateResponse
import com.companyb.companyapp.contracts.workforce.ReliefInviteResponse
import com.companyb.companyapp.contracts.workforce.SwapSlotsRequest
import com.companyb.companyapp.contracts.workforce.UpdateSlotRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

/**
 * #495 — backend-local serializer-descriptor projection for shared DTO component schemas.
 * One finite catalog maps generated component names to actual serializers; nested
 * objects stay references, enums/primitives/lists inline. Unknown kinds fail closed.
 */
internal object DtoSchemaProjector {
    private val mapper = ObjectMapper()

    // Finite catalog: generated component name -> actual shared DTO serializer. #495
    val catalog: Map<String, KSerializer<*>> =
        mapOf(
            "AcceptInviteRequest" to AcceptInviteRequest.serializer(),
            "ActiveAttendanceResponse" to ActiveAttendanceResponse.serializer(),
            "ActiveShiftResponse" to ActiveShiftResponse.serializer(),
            "AddDayBreakdownRequest" to AddDayBreakdownRequest.serializer(),
            "AddInventoryCardRequest" to AddInventoryCardRequest.serializer(),
            "AddPractitionerRequest" to AddPractitionerRequest.serializer(),
            "AddSessionConcernRequest" to AddSessionConcernRequest.serializer(),
            "AllowanceResponse" to AllowanceResponse.serializer(),
            "AssignDelegateRequest" to AssignDelegateRequest.serializer(),
            "AssignmentResponse" to AssignmentResponse.serializer(),
            "AttendanceMarkResponse" to AttendanceMarkResponse.serializer(),
            "AuditLogBrowseResponse" to AuditLogBrowseResponse.serializer(),
            "AuditLogEntryResponse" to AuditLogEntryResponse.serializer(),
            "AuditLogTableResponse" to AuditLogTableResponse.serializer(),
            "BranchDayTodayResponse" to BranchDayTodayResponse.serializer(),
            "BranchDayUserResponse" to BranchDayUserResponse.serializer(),
            "BranchInventoryResponse" to BranchInventoryResponse.serializer(),
            "BranchMemberResponse" to BranchMemberResponse.serializer(),
            "BranchResponse" to BranchResponse.serializer(),
            "ClientResponse" to ClientResponse.serializer(),
            "ClockInRequest" to ClockInRequest.serializer(),
            "ClockInResponse" to ClockInResponse.serializer(),
            "ClockOutRequest" to ClockOutRequest.serializer(),
            "ClockOutResponse" to ClockOutResponse.serializer(),
            "CommissionInclusionResponse" to CommissionInclusionResponse.serializer(),
            "CommissionSplitResponse" to CommissionSplitResponse.serializer(),
            "CompensationResponse" to CompensationResponse.serializer(),
            "ConcernResponse" to ConcernResponse.serializer(),
            "CreateAllowanceRequest" to CreateAllowanceRequest.serializer(),
            "CreateAssignmentRequest" to CreateAssignmentRequest.serializer(),
            "CreateBranchRequest" to CreateBranchRequest.serializer(),
            "CreateClientRequest" to CreateClientRequest.serializer(),
            "CreateCommissionInclusionRequest" to CreateCommissionInclusionRequest.serializer(),
            "CreateCompensationRequest" to CreateCompensationRequest.serializer(),
            "CreateExpenseRequest" to CreateExpenseRequest.serializer(),
            "CreateProductCategoryRequest" to CreateProductCategoryRequest.serializer(),
            "CreateProductRequest" to CreateProductRequest.serializer(),
            "CreateProductSaleRequest" to CreateProductSaleRequest.serializer(),
            "CreateReliefInviteRequest" to CreateReliefInviteRequest.serializer(),
            "CreateRemittanceDraftRequest" to CreateRemittanceDraftRequest.serializer(),
            "CreateRemittanceLineRequest" to CreateRemittanceLineRequest.serializer(),
            "CreateSessionRequest" to CreateSessionRequest.serializer(),
            "DailySalesSummaryBrowseResponse" to DailySalesSummaryBrowseResponse.serializer(),
            "DailySalesSummaryResponse" to DailySalesSummaryResponse.serializer(),
            "DashboardCommissionResponse" to DashboardCommissionResponse.serializer(),
            "DashboardPractitionerResponse" to DashboardPractitionerResponse.serializer(),
            "DashboardResponse" to DashboardResponse.serializer(),
            "DashboardSessionResponse" to DashboardSessionResponse.serializer(),
            "DelegateResponse" to DelegateResponse.serializer(),
            "DeleteExpenseRequest" to DeleteExpenseRequest.serializer(),
            "DenyReliefAccessRequest" to DenyReliefAccessRequest.serializer(),
            "ErrorResponse" to ErrorResponse.serializer(),
            "ExpenseResponse" to ExpenseResponse.serializer(),
            "FeedbackRequest" to FeedbackRequest.serializer(),
            "FeedbackResponse" to FeedbackResponse.serializer(),
            "ForgotPasswordRequest" to ForgotPasswordRequest.serializer(),
            "GrantReliefAccessRequest" to GrantReliefAccessRequest.serializer(),
            "IncidentPacket" to IncidentPacket.serializer(),
            "InventoryMovementRequest" to InventoryMovementRequest.serializer(),
            "InventoryMovementResponse" to InventoryMovementResponse.serializer(),
            "InviteMintRequest" to InviteMintRequest.serializer(),
            "InviteMintResponse" to InviteMintResponse.serializer(),
            "LoginRequest" to LoginRequest.serializer(),
            "LoginResponse" to LoginResponse.serializer(),
            "MarkAttendanceRequest" to MarkAttendanceRequest.serializer(),
            "MeBranchResponse" to MeBranchResponse.serializer(),
            "MeResponse" to MeResponse.serializer(),
            "MemberAttendanceResponse" to MemberAttendanceResponse.serializer(),
            "MonthlyRemittanceSummaryResponse" to MonthlyRemittanceSummaryResponse.serializer(),
            "NotificationMarkAllReadResponse" to NotificationMarkAllReadResponse.serializer(),
            "NotificationHistoryResponse" to NotificationHistoryResponse.serializer(),
            "NotificationResponse" to NotificationResponse.serializer(),
            "NotificationUnreadCountResponse" to NotificationUnreadCountResponse.serializer(),
            "PoolSnapshot" to PoolSnapshot.serializer(),
            "ProductCategoryResponse" to ProductCategoryResponse.serializer(),
            "ProductResponse" to ProductResponse.serializer(),
            "ProductSaleResponse" to ProductSaleResponse.serializer(),
            "PromoteConcernRequest" to PromoteConcernRequest.serializer(),
            "RateResponse" to RateResponse.serializer(),
            "ReliefAccessRequest" to ReliefAccessRequest.serializer(),
            "ReliefAccessResponse" to ReliefAccessResponse.serializer(),
            "ReliefBranchOptionResponse" to ReliefBranchOptionResponse.serializer(),
            "ReliefCandidateResponse" to ReliefCandidateResponse.serializer(),
            "ReliefInviteResponse" to ReliefInviteResponse.serializer(),
            "RemittanceDayBreakdownResponse" to RemittanceDayBreakdownResponse.serializer(),
            "RemittanceDayPickerEntryResponse" to RemittanceDayPickerEntryResponse.serializer(),
            "RemittanceDetailResponse" to RemittanceDetailResponse.serializer(),
            "RemittanceDriftResponse" to RemittanceDriftResponse.serializer(),
            "RemittanceFinancialSnapshotResponse" to RemittanceFinancialSnapshotResponse.serializer(),
            "RemittanceLineResponse" to RemittanceLineResponse.serializer(),
            "RemittanceProductSalePickerEntryResponse" to RemittanceProductSalePickerEntryResponse.serializer(),
            "RemittanceResponse" to RemittanceResponse.serializer(),
            "RemittanceSessionPickerEntryResponse" to RemittanceSessionPickerEntryResponse.serializer(),
            "RemittanceSubmitResponse" to RemittanceSubmitResponse.serializer(),
            "RemovePractitionerRequest" to RemovePractitionerRequest.serializer(),
            "RemoveSessionConcernRequest" to RemoveSessionConcernRequest.serializer(),
            "ResetPasswordRequest" to ResetPasswordRequest.serializer(),
            "RestockRequest" to RestockRequest.serializer(),
            "RestoreExpenseRequest" to RestoreExpenseRequest.serializer(),
            "RoleResponse" to RoleResponse.serializer(),
            "SessionPractitionerResponse" to SessionPractitionerResponse.serializer(),
            "SessionPreviewResponse" to SessionPreviewResponse.serializer(),
            "SessionResponse" to SessionResponse.serializer(),
            "SessionVoidResponse" to SessionVoidResponse.serializer(),
            "SetRateRequest" to SetRateRequest.serializer(),
            "SubmitRemittanceRequest" to SubmitRemittanceRequest.serializer(),
            "SwapSlotsRequest" to SwapSlotsRequest.serializer(),
            "UndoRemittanceRequest" to UndoRemittanceRequest.serializer(),
            "UnvoidSessionRequest" to UnvoidSessionRequest.serializer(),
            "UpdateClientRequest" to UpdateClientRequest.serializer(),
            "UpdateCompensationRequest" to UpdateCompensationRequest.serializer(),
            "UpdateExpenseRequest" to UpdateExpenseRequest.serializer(),
            "UpdatePractitionerRemarksRequest" to UpdatePractitionerRemarksRequest.serializer(),
            "UpdateProductRequest" to UpdateProductRequest.serializer(),
            "UpdateRemittanceHeaderRequest" to UpdateRemittanceHeaderRequest.serializer(),
            "UpdateSessionFinalPriceRequest" to UpdateSessionFinalPriceRequest.serializer(),
            "UpdateSessionStatusRequest" to UpdateSessionStatusRequest.serializer(),
            "UpdateSlotRequest" to UpdateSlotRequest.serializer(),
            "UserAssignmentResponse" to UserAssignmentResponse.serializer(),
            "UserCapabilityResponse" to UserCapabilityResponse.serializer(),
            "UserRoleReplaceRequest" to UserRoleReplaceRequest.serializer(),
            "UserSummaryResponse" to UserSummaryResponse.serializer(),
            "VoidSessionRequest" to VoidSessionRequest.serializer(),
        )

    fun projectAll(): Map<String, ObjectNode> =
        catalog.mapValues { (_, serializer) -> projectTopLevel(serializer.descriptor) }

    private fun projectTopLevel(descriptor: SerialDescriptor): ObjectNode {
        require(descriptor.kind == StructureKind.CLASS || descriptor.kind == StructureKind.OBJECT) {
            "Top-level DTO must be a class: ${descriptor.serialName}"
        }
        return projectClass(descriptor)
    }

    private fun projectClass(descriptor: SerialDescriptor): ObjectNode {
        val schema = mapper.createObjectNode()
        schema.put("type", "object")
        schema.put("additionalProperties", false)
        val properties = mapper.createObjectNode()
        schema.set<ObjectNode>("properties", properties)
        val required = mapper.createArrayNode()
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            val child = descriptor.getElementDescriptor(index)
            properties.set<ObjectNode>(name, schemaFor(child))
            if (!descriptor.isElementOptional(index)) {
                required.add(name)
            }
        }
        if (required.size() > 0) {
            schema.set<ArrayNode>("required", required)
        }
        return schema
    }

    private fun schemaFor(descriptor: SerialDescriptor): ObjectNode {
        val base = baseSchemaFor(descriptor)
        if (!descriptor.isNullable) {
            return base
        }
        return wrapNullable(descriptor, base)
    }

    // #495 — non-null base shape for one descriptor kind.
    private fun baseSchemaFor(descriptor: SerialDescriptor): ObjectNode =
        when (descriptor.kind) {
            PrimitiveKind.STRING -> {
                mapper.createObjectNode().put("type", "string")
            }

            PrimitiveKind.BOOLEAN -> {
                mapper.createObjectNode().put("type", "boolean")
            }

            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> {
                mapper.createObjectNode().put("type", "integer")
            }

            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> {
                mapper.createObjectNode().put("type", "number")
            }

            PrimitiveKind.CHAR -> {
                mapper.createObjectNode().put("type", "string")
            }

            SerialKind.ENUM -> {
                projectEnum(descriptor)
            }

            StructureKind.LIST -> {
                val items = schemaFor(descriptor.getElementDescriptor(0))
                mapper.createObjectNode().put("type", "array").set<ObjectNode>("items", items)
            }

            StructureKind.CLASS, StructureKind.OBJECT -> {
                classReferenceOrInline(descriptor)
            }

            else -> {
                error("Unknown schema kind: ${descriptor.serialName} kind=${descriptor.kind}")
            }
        }

    // #495 — nested catalog DTOs stay references so components own their schemas.
    private fun classReferenceOrInline(descriptor: SerialDescriptor): ObjectNode {
        val simpleName = descriptor.serialName.substringAfterLast('.')
        if (simpleName in catalog) {
            return mapper.createObjectNode().put("\$ref", "#/components/schemas/$simpleName")
        }
        return projectClass(descriptor)
    }

    // #495 — OpenAPI 3.1 null union for an already-projected base schema.
    private fun wrapNullable(
        descriptor: SerialDescriptor,
        base: ObjectNode,
    ): ObjectNode {
        if (base.has("\$ref")) {
            val ref = base.get("\$ref").asText()
            val wrapped = mapper.createObjectNode()
            val oneOf = mapper.createArrayNode()
            oneOf.add(mapper.createObjectNode().put("\$ref", ref))
            oneOf.add(mapper.createObjectNode().put("type", "null"))
            wrapped.set<ArrayNode>("oneOf", oneOf)
            return wrapped
        }
        val type = base.get("type")?.asText()
        if (type != null) {
            base.set<ArrayNode>("type", mapper.createArrayNode().add(type).add("null"))
            return base
        }
        if (base.has("anyOf") || base.has("oneOf") || base.has("allOf")) {
            return base
        }
        error("Cannot null-wrap schema: ${descriptor.serialName}")
    }

    private fun projectEnum(descriptor: SerialDescriptor): ObjectNode {
        val schema = mapper.createObjectNode()
        schema.put("type", "string")
        val values = mapper.createArrayNode()
        for (index in 0 until descriptor.elementsCount) {
            values.add(descriptor.getElementName(index))
        }
        schema.set<ArrayNode>("enum", values)
        return schema
    }
}
