<#assign emailTitle = (appName!'DocuHyphen') + " - Your Role Was Changed">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Hi ${firstName},</strong></p>
<p style="margin:0 0 14px 0;">Your role in <strong>${organizationName}</strong> has been changed.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Previous role:</strong> ${oldRole}</p>
            <p style="margin:0 0 6px 0;"><strong>New role:</strong> ${newRole}</p>
            <p style="margin:0;"><strong>Changed by:</strong> ${changedBy}</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">If you did not expect this change, contact your organization administrator or <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a>.</p>

<#include "email-footer.ftl">
