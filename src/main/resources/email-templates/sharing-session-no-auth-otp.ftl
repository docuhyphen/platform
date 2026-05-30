<#assign emailTitle = (appName!'DocuHyphen') + " - Sharing Session Verification Code">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Your verification code for the sharing session is ready.</strong></p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Session:</strong> ${sessionName!'-'}</p>
            <#if initiatorName??>
            <p style="margin:0 0 6px 0;"><strong>Requested by:</strong> ${initiatorName}</p>
            </#if>
            <p style="margin:0 0 6px 0;"><strong>Verification code:</strong> ${verificationCode}</p>
            <p style="margin:0;"><strong>Expires in:</strong> ${expiryMinutes} minutes</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">Open the session here: <a href="${sessionLink}" style="color:#1f73b7;">${sessionLink}</a></p>
<p style="margin:0 0 14px 0;">If you did not request this code, you can ignore this email.</p>

<#include "email-footer.ftl">

