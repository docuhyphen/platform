<#assign emailTitle = (appName!'DocuHyphen') + " - New Document Request">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>You have a new document request from ${initiatorName}.</strong></p>
<#if initiatorOrganization??>
<p style="margin:0 0 14px 0;">Sent on behalf of <strong>${initiatorOrganization}</strong>.</p>
</#if>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Session:</strong> ${sessionName}</p>
            <#if sessionMessage??>
            <p style="margin:0 0 6px 0;"><strong>Message:</strong> ${sessionMessage}</p>
            </#if>
            <#if documents?has_content>
            <p style="margin:0 0 6px 0;"><strong>Requested documents:</strong></p>
            <table width="100%" border="0" cellpadding="0" cellspacing="0">
                <#list documents as doc>
                <tr>
                    <td style="padding:2px 0; color:#555555;">- ${doc}</td>
                </tr>
                </#list>
            </table>
            </#if>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">Open the session to accept and upload: <a href="${sessionLink}" style="color:#1f73b7;">${sessionLink}</a></p>
<p style="margin:0 0 14px 0;">If you do not recognize this request, ignore this email or contact <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a>.</p>

<#include "email-footer.ftl">
