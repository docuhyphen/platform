<#assign emailTitle = (appName!'DocuHyphen') + " - Organization Updated">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Organization details have been updated.</strong></p>
<p style="margin:0 0 14px 0;">The following organization has been updated by an administrator:</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 8px 0;"><strong>Organization:</strong> ${organizationName}</p>
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

<p style="margin:0 0 14px 0;"><strong>Updated by:</strong> ${updatedBy}</p>
<p style="margin:0 0 14px 0;">If you did not authorize this change, please contact your organization administrator or reach out to support immediately.</p>

<#include "email-footer.ftl">

