<#assign emailTitle = (appName!'DocuHyphen') + " - Document Uploaded">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>A document has been uploaded to your sharing session.</strong></p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Session:</strong> ${sessionName}</p>
            <p style="margin:0 0 6px 0;"><strong>Document:</strong> ${documentTitle}</p>
            <p style="margin:0 0 6px 0;"><strong>Uploaded by:</strong> ${uploaderEmail}</p>
            <p style="margin:0 0 6px 0;"><strong>Uploaded at:</strong> ${uploadedAt}</p>
        </td>
    </tr>
</table>

<#if sessionLink??>
<p style="margin:0 0 14px 0;">Open session: <a href="${sessionLink}" style="color:#1f73b7;">${sessionLink}</a></p>
</#if>

<#include "email-footer.ftl">
