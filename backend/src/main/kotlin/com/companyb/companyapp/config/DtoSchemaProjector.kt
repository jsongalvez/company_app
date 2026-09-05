package com.companyb.companyapp.config

import com.companyb.companyapp.dto.AcceptInviteRequest
import com.companyb.companyapp.dto.AddDayBreakdownRequest
import com.companyb.companyapp.dto.AddInventoryCardRequest
import com.companyb.companyapp.dto.AddPractitionerRequest
import com.companyb.companyapp.dto.AddSessionConcernRequest
import com.companyb.companyapp.dto.AllowanceResponse
import com.companyb.companyapp.dto.AssignDelegateRequest
import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.AttendanceMarkResponse
import com.companyb.companyapp.dto.AuditLogBrowseResponse
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.dto.AuditLogTableResponse
import com.companyb.companyapp.dto.BranchDayTodayResponse
import com.companyb.companyapp.dto.BranchDayUserResponse
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.BranchMemberResponse
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.ClockInRequest
import com.companyb.companyapp.dto.ClockInResponse
import com.companyb.companyapp.dto.ClockOutRequest
import com.companyb.companyapp.dto.ClockOutResponse
import com.companyb.companyapp.dto.CommissionInclusionResponse
import com.companyb.companyapp.dto.CommissionSplitResponse
import com.companyb.companyapp.dto.CompensationResponse
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.CreateAllowanceRequest
import com.companyb.companyapp.dto.CreateAssignmentRequest
import com.companyb.companyapp.dto.CreateBranchRequest
import com.companyb.companyapp.dto.CreateClientRequest
import com.companyb.companyapp.dto.CreateCommissionInclusionRequest
import com.companyb.companyapp.dto.CreateCompensationRequest
import com.companyb.companyapp.dto.CreateExpenseRequest
import com.companyb.companyapp.dto.CreateProductCategoryRequest
import com.companyb.companyapp.dto.CreateProductRequest
import com.companyb.companyapp.dto.CreateProductSaleRequest
import com.companyb.companyapp.dto.CreateReliefInviteRequest
import com.companyb.companyapp.dto.CreateRemittanceDraftRequest
import com.companyb.companyapp.dto.CreateRemittanceLineRequest
import com.companyb.companyapp.dto.CreateSessionRequest
import com.companyb.companyapp.dto.DailySalesSummaryBrowseResponse
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.dto.DashboardCommissionResponse
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.DashboardResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.DelegateResponse
import com.companyb.companyapp.dto.DeleteExpenseRequest
import com.companyb.companyapp.dto.DenyReliefAccessRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.ExpenseResponse
import com.companyb.companyapp.dto.FeedbackRequest
import com.companyb.companyapp.dto.FeedbackResponse
import com.companyb.companyapp.dto.ForgotPasswordRequest
import com.companyb.companyapp.dto.GrantReliefAccessRequest
import com.companyb.companyapp.dto.IncidentPacket
import com.companyb.companyapp.dto.InventoryMovementRequest
import com.companyb.companyapp.dto.InventoryMovementResponse
import com.companyb.companyapp.dto.InviteMintRequest
import com.companyb.companyapp.dto.InviteMintResponse
import com.companyb.companyapp.dto.LoginRequest
import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.dto.MarkAttendanceRequest
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.MemberAttendanceResponse
import com.companyb.companyapp.dto.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.dto.NotificationMarkAllReadResponse
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.dto.PoolSnapshot
import com.companyb.companyapp.dto.ProductCategoryResponse
import com.companyb.companyapp.dto.ProductResponse
import com.companyb.companyapp.dto.ProductSaleResponse
import com.companyb.companyapp.dto.PromoteConcernRequest
import com.companyb.companyapp.dto.RateResponse
import com.companyb.companyapp.dto.ReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.dto.ReliefBranchOptionResponse
import com.companyb.companyapp.dto.ReliefCandidateResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.dto.RemittanceDayBreakdownResponse
import com.companyb.companyapp.dto.RemittanceDayPickerEntryResponse
import com.companyb.companyapp.dto.RemittanceDetailResponse
import com.companyb.companyapp.dto.RemittanceDriftResponse
import com.companyb.companyapp.dto.RemittanceFinancialSnapshotResponse
import com.companyb.companyapp.dto.RemittanceLineResponse
import com.companyb.companyapp.dto.RemittanceProductSalePickerEntryResponse
import com.companyb.companyapp.dto.RemittanceResponse
import com.companyb.companyapp.dto.RemittanceSessionPickerEntryResponse
import com.companyb.companyapp.dto.RemittanceSubmitResponse
import com.companyb.companyapp.dto.RemovePractitionerRequest
import com.companyb.companyapp.dto.RemoveSessionConcernRequest
import com.companyb.companyapp.dto.ResetPasswordRequest
import com.companyb.companyapp.dto.RestockRequest
import com.companyb.companyapp.dto.RestoreExpenseRequest
import com.companyb.companyapp.dto.RoleResponse
import com.companyb.companyapp.dto.SessionPractitionerResponse
import com.companyb.companyapp.dto.SessionPreviewResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.dto.SessionVoidResponse
import com.companyb.companyapp.dto.SetRateRequest
import com.companyb.companyapp.dto.SubmitRemittanceRequest
import com.companyb.companyapp.dto.SwapSlotsRequest
import com.companyb.companyapp.dto.UndoRemittanceRequest
import com.companyb.companyapp.dto.UnvoidSessionRequest
import com.companyb.companyapp.dto.UpdateClientRequest
import com.companyb.companyapp.dto.UpdateCompensationRequest
import com.companyb.companyapp.dto.UpdateExpenseRequest
import com.companyb.companyapp.dto.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.dto.UpdateProductRequest
import com.companyb.companyapp.dto.UpdateRemittanceHeaderRequest
import com.companyb.companyapp.dto.UpdateSessionFinalPriceRequest
import com.companyb.companyapp.dto.UpdateSessionStatusRequest
import com.companyb.companyapp.dto.UpdateSlotRequest
import com.companyb.companyapp.dto.UserAssignmentResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.dto.UserRoleReplaceRequest
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.dto.VoidSessionRequest
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
            "NotificationResponse" to NotificationResponse.serializer(),
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
