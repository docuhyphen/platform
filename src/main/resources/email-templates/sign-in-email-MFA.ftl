<#assign emailTitle = (appName!'DocuHyphen') + " - Sign In Verification Code">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Your sign-in verification code is:</strong></p>

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

<p style="margin:0 0 14px 0;">This code expires in <strong>${expiryMinutes}</strong> minutes.</p>
<p style="margin:0 0 14px 0;">If you did not request this code, please ignore this email and secure your account.</p>

<#include "email-footer.ftl">
