<#assign emailTitle = emailTitle!((appName!'DocuHyphen') + " - New Exchange")>
<#include "email-header.ftl">

<#if introText??>
<p style="margin:0 0 14px 0;"><strong>${introText}</strong></p>
<#else>
<p style="margin:0 0 14px 0;"><strong>You have a new Document Exchange request from ${initiatorName}.</strong></p>
</#if>
<#if initiatorOrganization??>
<p style="margin:0 0 14px 0;">Sent on behalf of <strong>${initiatorOrganization}</strong>.</p>
</#if>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Exchange:</strong> ${name}</p>
            <#if sessionMessage??>
            <p style="margin:0 0 6px 0;"><strong>Message:</strong> ${sessionMessage}</p>
            </#if>
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

<#if requireSignIn?? && requireSignIn>
<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #f5a623; background-color:#fff8ee;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 8px 0;"><strong>&#9888; A registered account is required to access this request.</strong></p>
            <p style="margin:0 0 8px 0;">The sender has requested that you sign in to ${appName!'DocuHyphen'} to view and respond to this exchange. If you don&apos;t have an account yet, you can create one for free.</p>
            <p style="margin:0;">
                <a href="${signUpLink}" style="display:inline-block;padding:10px 20px;background-color:${brandPrimaryColor};color:#ffffff;text-decoration:none;border-radius:4px;font-weight:bold;">Create your free account</a>
            </p>
        </td>
    </tr>
</table>
<p style="margin:0 0 14px 0;">Already have an account? <a href="${exchangeLink}" style="color:#1f73b7;">Sign in and open the exchange</a>.</p>
<#else>
<p style="margin:0 0 14px 0;">Open the Exchange to accept and upload: <a href="${exchangeLink}" style="color:#1f73b7;">${exchangeLink}</a></p>
</#if>
<p style="margin:0 0 14px 0;">If you do not recognize this request, ignore this email or contact <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a>.</p>

<#include "notification-opt-out.ftl">
<#include "email-footer.ftl">
