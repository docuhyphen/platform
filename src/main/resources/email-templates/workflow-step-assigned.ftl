<#assign emailTitle = (appName!'DocuHyphen') + " - Action Required: Approval Request">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>You have a pending approval that requires your attention.</strong></p>

<#if initiatorName??>
<p style="margin:0 0 14px 0;">${initiatorName} has submitted an exchange that requires your approval before it can proceed.</p>
<#else>
<p style="margin:0 0 14px 0;">An exchange has been submitted and requires your approval before it can proceed.</p>
</#if>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 20px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Exchange:</strong> ${exchangeName!'(unnamed)'}</p>
            <#if workflowName??>
            <p style="margin:0 0 6px 0;"><strong>Workflow:</strong> ${workflowName}</p>
            </#if>
            <#if initiatorName??>
            <p style="margin:0;"><strong>Requested by:</strong> ${initiatorName}</p>
            </#if>
        </td>
    </tr>
</table>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0;">
    <tr>
        <td>
            <a href="${appUrl!'https://app.docuhyphen.com'}" style="display:inline-block;padding:10px 24px;background-color:${brandPrimaryColor};color:#ffffff;text-decoration:none;border-radius:4px;font-weight:bold;">
                Review &amp; Respond
            </a>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;color:#555555;font-size:13px;">You can approve or reject this exchange by signing in to ${appName!'DocuHyphen'} and opening the Pending Approvals panel.</p>

<p style="margin:0 0 14px 0;color:#555555;font-size:13px;">If you believe you received this email in error, contact <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a>.</p>

<#include "email-footer.ftl">
