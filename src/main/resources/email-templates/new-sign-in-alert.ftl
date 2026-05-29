<#assign emailTitle = (appName!'DocuHyphen') + " - New sign-in to your account">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Hi ${firstName!'there'},</strong></p>
<p style="margin:0 0 14px 0;">A new sign-in to your ${appName!'DocuHyphen'} account was just completed.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>When:</strong> ${signInAtIso}</p>
            <p style="margin:0 0 6px 0;"><strong>Device:</strong> ${device}</p>
            <p style="margin:0;"><strong>IP address:</strong> ${ipAddress}</p>
            <#if locationHint??>
            <p style="margin:6px 0 0 0;"><strong>Location hint:</strong> ${locationHint}</p>
            </#if>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">If this wasn't you, change your password immediately and sign out of all devices from your profile settings.</p>
<p style="margin:0 0 14px 0;">Security settings: <a href="${securityUrl}" style="color:#1f73b7;">${securityUrl}</a></p>

<#include "email-footer.ftl">

