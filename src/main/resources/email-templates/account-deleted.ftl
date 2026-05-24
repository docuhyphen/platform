<#assign emailTitle = (appName!'DocuHyphen') + " - Account Deleted">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Your account has been deleted.</strong></p>
<p style="margin:0 0 14px 0;">The ${appName!'DocuHyphen'} account associated with this email has been deleted and can no longer be used to sign in.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Account:</strong> ${email}</p>
            <p style="margin:0;"><strong>When:</strong> ${deletedAt}</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">If you did not request this deletion, contact <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a> immediately.</p>

<#include "email-footer.ftl">
