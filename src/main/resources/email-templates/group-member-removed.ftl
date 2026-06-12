<#assign emailTitle = (appName!'DocuHyphen') + " - Removed From a Group">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Hi ${firstName},</strong></p>
<p style="margin:0 0 14px 0;">You have been removed from the group <strong>${groupName}</strong> in <strong>${organizationName}</strong>.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Group:</strong> ${groupName}</p>
            <p style="margin:0 0 6px 0;"><strong>Organization:</strong> ${organizationName}</p>
            <p style="margin:0;"><strong>Removed by:</strong> ${removedBy}</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">You will no longer receive new exchanges routed to this group. Your access to the rest of the organization is unchanged.</p>

<#include "notification-opt-out.ftl">
<#include "email-footer.ftl">
