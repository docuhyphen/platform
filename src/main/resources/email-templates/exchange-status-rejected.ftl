<#assign emailTitle = (appName!'DocuHyphen') + " - Sharing Request Rejected">
<#include "email-header.ftl">

<#if audience == "INITIATOR">
<p style="margin:0 0 14px 0;"><strong>The recipient rejected your exchange.</strong></p>
<#else>
<p style="margin:0 0 14px 0;"><strong>You rejected this exchange.</strong></p>
</#if>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Exchange:</strong> ${name}</p>
            <p style="margin:0 0 6px 0;"><strong>Status:</strong> ${statusText}</p>
            <p style="margin:0 0 6px 0;"><strong>Initiator:</strong> ${initiatorEmail}</p>
            <p style="margin:0 0 6px 0;"><strong>Recipient:</strong> ${recipientEmail}</p>
            <#if documents?has_content>
            <p style="margin:0 0 6px 0;"><strong>Documents:</strong></p>
            <table width="100%" border="0" cellpadding="0" cellspacing="0">
                <#list documents as doc>
                <tr>
                    <td style="padding:2px 0; color:#555555;">- ${doc}</td>
                </tr>
                </#list>
            </table>
            <#else>
            <p style="margin:0 0 6px 0;"><strong>Documents:</strong> No documents listed.</p>
            </#if>
            <p style="margin:6px 0 0 0;"><strong>Last activity:</strong> ${lastActivity}</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;"><strong>Rejection reason:</strong> ${rejectionReason!'No rejection reason was provided.'}</p>
<p style="margin:0 0 14px 0;">Open session: <a href="${exchangeLink}" style="color:#1f73b7;">${exchangeLink}</a></p>

<#include "notification-opt-out.ftl">
<#include "email-footer.ftl">

