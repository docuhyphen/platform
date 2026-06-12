<#assign emailTitle = (appName!'DocuHyphen') + " - You've Been Added to an Organization">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Hi ${firstName},</strong></p>
<p style="margin:0 0 14px 0;">Welcome to <strong>${appName!'DocuHyphen'}</strong>. You have been added to <strong>${organizationName}</strong>.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Organization:</strong> ${organizationName}</p>
            <p style="margin:0 0 6px 0;"><strong>Role:</strong> ${role}</p>
            <p style="margin:0;"><strong>Added by:</strong> ${addedBy}</p>
        </td>
    </tr>
</table>

<#if isNewUser>
<p style="margin:0 0 14px 0;">An account has been prepared for you. Use the temporary password below to sign in, then change your password immediately.</p>
<p style="margin:0 0 6px 0;"><strong>Temporary password:</strong> ${temporaryPassword!'-'}</p>
<p style="margin:0 0 14px 0;"><strong>Temporary password expires:</strong> ${temporaryPasswordExpiresAt!'in 7 days'}</p>
<p style="margin:0 0 14px 0;">Sign in at <a href="${appBaseUrl}/sign-in" style="color:#1f73b7;">${appBaseUrl}/sign-in</a>. If you cannot find this email later, you can still recover your password from the sign-in page.</p>
<#else>
<p style="margin:0 0 14px 0;">Sign in at <a href="${appBaseUrl}/sign-in" style="color:#1f73b7;">${appBaseUrl}/sign-in</a> to access the organization.</p>
</#if>

<p style="margin:0 0 14px 0;">If you do not recognize this organization, contact <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a>.</p>

<#include "notification-opt-out.ftl">
<#include "email-footer.ftl">
