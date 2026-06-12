<#assign emailTitle = (appName!'DocuHyphen') + " - Access Deactivated">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Hi ${firstName},</strong></p>
<p style="margin:0 0 14px 0;">Your access to <strong>${organizationName}</strong> on ${appName!'DocuHyphen'} has been deactivated.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Organization:</strong> ${organizationName}</p>
            <p style="margin:0;"><strong>Deactivated by:</strong> ${deactivatedBy}</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">If you believe this was done in error, contact your organization administrator or <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a>.</p>

<#include "notification-opt-out.ftl">
<#include "email-footer.ftl">

