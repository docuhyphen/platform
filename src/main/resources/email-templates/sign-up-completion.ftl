<#assign emailTitle = (appName!'DocuHyphen') + " - Account Created Successfully">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Account created successfully.</strong></p>
<p style="margin:0 0 14px 0;">Welcome to ${appName!'DocuHyphen'}.</p>
<p style="margin:0 0 14px 0;">Your account has been verified and is now active.</p>
<p style="margin:0 0 14px 0;"><strong>Email:</strong> ${email}</p>

<p style="margin:0 0 14px 0;">Sign in using this link: <a href="${appBaseUrl}/sign-in" style="color:#1f73b7;">${appBaseUrl}/sign-in</a></p>

<p style="margin:0 0 14px 0;"><strong>Next steps:</strong></p>
<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0;">
    <tr><td style="padding:0 0 6px 0;">- Complete your profile</td></tr>
    <tr><td style="padding:0 0 6px 0;">- Review your security settings</td></tr>
    <tr><td style="padding:0 0 6px 0;">- Start exchanging and managing documents</td></tr>
</table>

<p style="margin:0 0 14px 0;">Need help? Contact support at <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a>.</p>

<#include "email-footer.ftl">

