<#assign emailTitle = (appName!'DocuHyphen') + " - Exchange Sent">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Your exchange has been sent.</strong></p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Exchange:</strong> ${name}</p>
            <p style="margin:0 0 6px 0;"><strong>Recipient:</strong> ${recipientLabel}</p>
            <#if documents?has_content>
            <p style="margin:0 0 6px 0;"><strong>Documents:</strong></p>
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

<p style="margin:0 0 14px 0;">You will be notified when the recipient acts on this request.</p>
<p style="margin:0 0 14px 0;">View the exchange: <a href="${exchangeLink}" style="color:#1f73b7;">${exchangeLink}</a></p>

<#include "notification-opt-out.ftl">
<#include "email-footer.ftl">
