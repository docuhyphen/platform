<#assign emailTitle = (appName!'DocuHyphen') + " - Added to a Group">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Hi ${firstName},</strong></p>
<p style="margin:0 0 14px 0;">You have been added to a group at <strong>${organizationName}</strong>.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Group:</strong> ${groupName}</p>
            <p style="margin:0 0 6px 0;"><strong>Organization:</strong> ${organizationName}</p>
            <p style="margin:0;"><strong>Added by:</strong> ${addedBy}</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">As a group member you may receive exchanges routed to this group and act on them according to the permissions assigned to you.</p>
<p style="margin:0 0 14px 0;">Sign in at <a href="${appBaseUrl}/sign-in" style="color:#1f73b7;">${appBaseUrl}/sign-in</a> to view the group.</p>

<#include "email-footer.ftl">
