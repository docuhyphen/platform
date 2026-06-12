<#assign emailTitle = (appName!'DocuHyphen') + " - Document Uploaded">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>A document has been uploaded to your exchange.</strong></p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Exchange:</strong> ${name}</p>
            <p style="margin:0 0 6px 0;"><strong>Document:</strong> ${documentTitle}</p>
            <p style="margin:0 0 6px 0;"><strong>Uploaded by:</strong> ${uploaderEmail}</p>
            <p style="margin:0 0 6px 0;"><strong>Uploaded at:</strong> ${uploadedAt}</p>
        </td>
    </tr>
</table>

<#if exchangeLink??>
<p style="margin:0 0 14px 0;">Open session: <a href="${exchangeLink}" style="color:#1f73b7;">${exchangeLink}</a></p>
</#if>

<#include "notification-opt-out.ftl">
<#include "email-footer.ftl">
