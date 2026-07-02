<#assign emailTitle = (appName!'DocuHyphen') + " - Document Request">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>${initiatorName} has sent you a document exchange request.</strong></p>
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
            <p style="margin:0 0 6px 0;"><strong>Documents requested:</strong></p>
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

<p style="margin:0 0 14px 0;">You do not need an account to respond. Use the link and verification code below to open the exchange.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Your verification code:</strong></p>
            <p style="margin:0 0 6px 0; font-size:28px; letter-spacing:6px; font-family:monospace;">${verificationCode}</p>
            <p style="margin:0; color:#888888; font-size:13px;">This code is valid for ${expiryLabel}. If it expires, open the link below to request a new one.</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">
    <a href="${exchangeLink}" style="display:inline-block;padding:10px 20px;background-color:${brandPrimaryColor};color:#ffffff;text-decoration:none;border-radius:4px;font-weight:bold;">Open Exchange</a>
</p>
<p style="margin:0 0 14px 0; color:#888888; font-size:13px;">Or copy this link into your browser: <a href="${exchangeLink}" style="color:#1f73b7;">${exchangeLink}</a></p>

<p style="margin:0 0 14px 0;">If you do not recognize this request, ignore this email or contact <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a>.</p>

<#include "email-footer.ftl">
