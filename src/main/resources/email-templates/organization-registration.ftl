<#assign emailTitle = (appName!'DocuHyphen') + " - Organization Registration Received">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;">Hi <strong>${firstName} ${lastName}</strong>,</p>
<p style="margin:0 0 14px 0;">Thank you for registering your organization with ${appName!'DocuHyphen'}. Your request has been received and is pending administrator review.</p>

<!-- Status badge -->
<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 18px 0;">
    <tr>
        <td align="center" style="padding:0;">
            <table border="0" cellpadding="0" cellspacing="0" style="border:1px solid #f0ad4e; background-color:#fcf8e3;">
                <tr>
                    <td style="padding:8px 16px; font-size:13px; font-weight:bold; color:#8a6d3b;">&#9679; Status: Pending Review</td>
                </tr>
            </table>
        </td>
    </tr>
</table>

<!-- Registration summary -->
<p style="margin:0 0 10px 0;"><strong>Registration Summary</strong></p>
<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 18px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:14px 20px;">
            <table width="100%" border="0" cellpadding="0" cellspacing="0">
                <tr>
                    <td style="padding:0 0 8px 0; color:#555555; width:160px; vertical-align:top;"><strong>Organization Name:</strong></td>
                    <td style="padding:0 0 8px 0; color:#333333;">${organizationName}</td>
                </tr>
                <tr>
                    <td style="padding:0 0 8px 0; color:#555555; width:160px; vertical-align:top;"><strong>Registration No:</strong></td>
                    <td style="padding:0 0 8px 0; color:#333333;">${registrationNumber}</td>
                </tr>
                <#if organizationEmail??>
                <tr>
                    <td style="padding:0 0 8px 0; color:#555555; width:160px; vertical-align:top;"><strong>Contact Email:</strong></td>
                    <td style="padding:0 0 8px 0; color:#333333;">${organizationEmail}</td>
                </tr>
                </#if>
                <#if organizationPhone??>
                <tr>
                    <td style="padding:0 0 8px 0; color:#555555; width:160px; vertical-align:top;"><strong>Contact Phone:</strong></td>
                    <td style="padding:0 0 8px 0; color:#333333;">${organizationPhone}</td>
                </tr>
                </#if>
                <tr>
                    <td style="padding:0; color:#555555; width:160px; vertical-align:top;"><strong>Your Role:</strong></td>
                    <td style="padding:0; color:#333333;">Organization Admin</td>
                </tr>
            </table>
        </td>
    </tr>
</table>

<!-- What happens next -->
<p style="margin:0 0 10px 0;"><strong>What happens next?</strong></p>
<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 18px 0;">
    <tr><td style="padding:0 0 6px 0; color:#333333;">1. We will review your registration details.</td></tr>
    <tr><td style="padding:0 0 6px 0; color:#333333;">2. You may be contacted if additional information is required.</td></tr>
    <tr><td style="padding:0 0 6px 0; color:#333333;">3. Once approved, you will receive a confirmation email and your organization will be activated.</td></tr>
</table>

<p style="margin:0 0 14px 0;">You can check your registration status at any time by signing in and navigating to <strong>Settings &gt; Your Organization</strong>: <a href="${appBaseUrl}/sign-in" style="color:#1f73b7;">${appBaseUrl}/sign-in</a></p>

<p style="margin:0 0 14px 0; font-size:12px; color:#777777;">If you did not submit this registration, please contact support immediately at <a href="mailto:support@docuhyphen.com" style="color:#1f73b7;">support@docuhyphen.com</a>.</p>

<#include "email-footer.ftl">
