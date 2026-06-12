<#assign emailTitle = (appName!'DocuHyphen') + " - Access Reactivated">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Hi ${firstName},</strong></p>
<p style="margin:0 0 14px 0;">Your access to <strong>${organizationName}</strong> on ${appName!'DocuHyphen'} has been reactivated.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Organization:</strong> ${organizationName}</p>
            <p style="margin:0;"><strong>Reactivated by:</strong> ${reactivatedBy}</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">You can sign in at <a href="${appBaseUrl}/sign-in" style="color:#1f73b7;">${appBaseUrl}/sign-in</a>.</p>

<#include "notification-opt-out.ftl">
<#include "email-footer.ftl">

