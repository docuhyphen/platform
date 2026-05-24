<#assign emailTitle = (appName!'DocuHyphen') + " - Email Address Changed">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>The email on your account has been changed.</strong></p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Previous email:</strong> ${oldEmail}</p>
            <p style="margin:0;"><strong>New email:</strong> ${newEmail}</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">All sessions have been signed out. The new address must now be used to sign in.</p>
<p style="margin:0 0 14px 0;">If you did not authorize this change, contact <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a> immediately.</p>

<#include "email-footer.ftl">
