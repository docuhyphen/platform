<#assign emailTitle = (appName!'DocuHyphen') + " - Group Updated">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Hi ${firstName},</strong></p>
<p style="margin:0 0 14px 0;">A group you belong to has been updated.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Group:</strong> ${groupName}</p>
            <p style="margin:0 0 6px 0;"><strong>Organization:</strong> ${organizationName}</p>
            <p style="margin:0 0 8px 0;"><strong>Updated by:</strong> ${updatedBy}</p>
            <#if updatedFields?has_content>
            <p style="margin:0 0 6px 0;"><strong>Changes:</strong></p>
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

<p style="margin:0 0 14px 0;">If you have questions about this change, contact your organization administrator.</p>

<#include "notification-opt-out.ftl">
<#include "email-footer.ftl">
