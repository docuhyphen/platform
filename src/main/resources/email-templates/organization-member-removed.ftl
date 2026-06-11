<#assign emailTitle = (appName!'DocuHyphen') + " - Removed From Organization">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Hi ${firstName},</strong></p>
<p style="margin:0 0 14px 0;">You have been removed from <strong>${organizationName}</strong>.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Organization:</strong> ${organizationName}</p>
            <p style="margin:0;"><strong>Removed by:</strong> ${removedBy}</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">You will no longer have access to this organization's documents, groups, or exchanges.</p>
<p style="margin:0 0 14px 0;">If this was a mistake, contact your organization administrator or <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a>.</p>

<#include "email-footer.ftl">
