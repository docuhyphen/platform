<#assign emailTitle = (appName!'DocuHyphen') + " - Password Changed">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Your password has been changed.</strong></p>
<p style="margin:0 0 14px 0;">This is a confirmation that the password for your ${appName!'DocuHyphen'} account was just changed.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Account:</strong> ${email}</p>
            <p style="margin:0;"><strong>When:</strong> ${changedAt}</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">If you did not make this change, your account may be compromised. Reset your password immediately and contact <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a>.</p>

<#include "email-footer.ftl">
