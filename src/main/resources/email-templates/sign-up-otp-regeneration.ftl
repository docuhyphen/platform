<#assign emailTitle = (appName!'DocuHyphen') + " - New Verification Code">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>You requested a new verification code.</strong></p>
<p style="margin:0 0 14px 0;">Use this code to continue:</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0;">
    <tr>
        <td align="center" style="padding:0;" height="100">
            <table border="0" cellpadding="0" cellspacing="0" style="border:1px solid #dddddd; background-color:#f0f2f5;">
                <tr>
                    <td style="padding:12px 20px; font-size:24px; font-weight:bold; letter-spacing:4px; font-family:'Courier New', Courier, monospace; color:#222222;">${verificationCode}</td>
                </tr>
            </table>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">Verification link: <a href="${confirmationLink}" style="color:#1f73b7;">${confirmationLink}</a></p>
<p style="margin:0 0 14px 0;">This code expires in <strong>${expiryMinutes}</strong> minute<#if expiryMinutes != 1>s</#if>.</p>
<p style="margin:0 0 14px 0;">If you did not request this code, please ignore this email.</p>

<#include "email-footer.ftl">

