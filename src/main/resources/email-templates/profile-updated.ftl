<#assign emailTitle = (appName!'DocuHyphen') + " - Profile Updated">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Your profile details have been updated.</strong></p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <#if updatedFields?has_content>
            <p style="margin:0 0 8px 0;"><strong>Changes:</strong></p>
            <table width="100%" border="0" cellpadding="0" cellspacing="0">
                <#list updatedFields as field>
                <tr>
                    <td style="padding:2px 0; color:#555555;">- ${field}</td>
                </tr>
                </#list>
            </table>
            </#if>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">If you did not make this change, please reset your password and contact <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a>.</p>

<#include "notification-opt-out.ftl">
<#include "email-footer.ftl">
