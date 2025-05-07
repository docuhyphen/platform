package com.dochyphen.app.api.model

import com.dochyphen.app.api.model.dto.AppUserDetailedDto
import com.dochyphen.app.api.model.dto.ContactDetailsDetailedDto
import com.dochyphen.app.api.model.dto.DocumentAuditDetailedDto
import com.dochyphen.app.api.model.dto.DocumentCommentDetailedDto
import com.dochyphen.app.api.model.dto.DocumentDetailedDto
import com.dochyphen.app.api.model.dto.DocumentVersionDetailedDto
import com.dochyphen.app.api.model.dto.OrganizationDetailedDto
import com.dochyphen.app.api.model.dto.OrganizationGroupDetailedDto
import com.dochyphen.app.api.model.dto.OrganizationGroupMemberDetailedDto
import com.dochyphen.app.api.model.dto.OrganizationGroupMemberPermissionDto
import com.dochyphen.app.api.model.dto.PersonDetailedDto
import com.dochyphen.app.api.model.dto.SharingSessionDetailedDto
import com.dochyphen.app.api.model.entity.AppUser
import com.dochyphen.app.api.model.entity.ContactDetails
import com.dochyphen.app.api.model.entity.Document
import com.dochyphen.app.api.model.entity.DocumentAuditLog
import com.dochyphen.app.api.model.entity.DocumentVersion
import com.dochyphen.app.api.model.entity.Organization
import com.dochyphen.app.api.model.entity.OrganizationGroup
import com.dochyphen.app.api.model.entity.OrganizationGroupMember
import com.dochyphen.app.api.model.entity.OrganizationGroupMemberPermission
import com.dochyphen.app.api.model.entity.Person
import com.dochyphen.app.api.model.entity.SharingSession
import com.dochyphen.app.api.model.entity.SharingSessionDocumentComment

class DetailedEntityToDtoTransformer
{
    companion object
    {
        fun toDto(document: Document?): DocumentDetailedDto?
        {
            return document?.let {
                with(document)
                {
                    DocumentDetailedDto(
                        id,
                        createdDate,
                        uploadDate,
                        title,
                        type.toString(),
                        restrictedType.toString(),
                        restrictedType != null,
                        hash,
                        mutableListOf(),
                    )
                }
            }
        }

        fun toDto(documentVersion: DocumentVersion?): DocumentVersionDetailedDto?
        {
            return documentVersion?.let {
                with(documentVersion)
                {
                    DocumentVersionDetailedDto(
                        id,
                        document.id.toString(),
                        createdDate,
                        version,
                        storagePath,
                        createdByEmail,
                        "TODO"
//                        createdBy!!.id.toString()
                    )
                }
            }
        }

        fun toDto(sharingSession: SharingSession?): SharingSessionDetailedDto?
        {
            return sharingSession?.let {
                with(sharingSession)
                {
                    SharingSessionDetailedDto(
                        id,
                        createdDate,
                        endDate,
                        endNote,
                        lastActivity,
                        sessionName,
                        initialShareMessage,
                        description,
                        toDto(initiator),
                        toDto(recipient),
                        status.toString(),
                        requireRecipientSignIn,
                        allowDocumentAddition,
                        allowDocumentDeletion,
                        allowDocumentDownload,
                        allowDocumentUpdate,
                        allowDocumentUpload,
                        documents.map { toDto(it) }
                    )
                }
            }
        }

        fun toDto(appUser: AppUser?): AppUserDetailedDto?
        {
            return appUser?.let {
                with(appUser)
                {
                    AppUserDetailedDto(
                        id,
                        createdDate,
                        isActive,
                        email,
                        role,
                        toDto(person)
                    )
                }
            }
        }

        fun toDto(person: Person?): PersonDetailedDto?
        {
            return person?.let {
                with(person)
                {
                    PersonDetailedDto(
                        id,
                        createdDate,
                        firstName,
                        lastName,
                        identificationNumber,
                        personIDType?.toString(),
                        toDto(contactDetails)
                    )
                }
            }
        }

        fun toDto(contactDetails: ContactDetails?): ContactDetailsDetailedDto?
        {
            return contactDetails?.let {
                with(contactDetails)
                {
                    ContactDetailsDetailedDto(
                        id,
                        createdDate,
                        isPhoneVerified,
                        email,
                        phoneNumber
                    )
                }
            }
        }

        fun toDto(documentAuditLog: DocumentAuditLog?): DocumentAuditDetailedDto?
        {
            return documentAuditLog?.let {
                with(documentAuditLog)
                {
                    DocumentAuditDetailedDto(
                        id,
                        timestamp,
                        action.toString(),
                        toDto(performedBy),
                        performedByEmail
                    )
                }
            }
        }

        fun toDto(comment: SharingSessionDocumentComment?): DocumentCommentDetailedDto?
        {
            return comment?.let {
                with(comment)
                {
                    DocumentCommentDetailedDto(
                        id,
                        createdDate,
                        commentText,
                        commentedBy.person?.firstName,
                        commentedBy.person?.lastName,
                        commentedBy.email
                    )
                }
            }
        }

        fun toDto(organization: Organization?): OrganizationDetailedDto?
        {
            return organization?.let {
                with(organization)
                {
                    OrganizationDetailedDto(
                        id,
                        createdDate,
                        isActive,
                        name,
                        registrationNumber,
                        toDto(contactDetails),
                    )
                }
            }
        }

        fun toDto(organizationGroup: OrganizationGroup?): OrganizationGroupDetailedDto?
        {
            return organizationGroup?.let {
                with(organizationGroup)
                {
                    OrganizationGroupDetailedDto(
                        id,
                        createdDate,
                        isActive,
                        name,
                        members.map { toDto(it) }
                    )
                }
            }
        }

        fun toDto(member: OrganizationGroupMember?): OrganizationGroupMemberDetailedDto?
        {
            return member?.let {
                OrganizationGroupMemberDetailedDto(
                    toDto(member.appUser),
                    toDto(member.permissions)
                )
            }
        }

        fun toDto(permission: OrganizationGroupMemberPermission?): OrganizationGroupMemberPermissionDto?
        {
            return permission?.let {
                with(permission) {
                    OrganizationGroupMemberPermissionDto(
                        allowSessionAccept,
                        allowSessionReject,
                        allowSessionEdit,
                        allowSessionDelete,
                        allowSessionEnd,
                        allowDocumentAddition,
                        allowDocumentDeletion,
                        allowDocumentDownload,
                        allowDocumentUpdate,
                        allowDocumentUpload
                    )
                }
            }
        }
    }
}