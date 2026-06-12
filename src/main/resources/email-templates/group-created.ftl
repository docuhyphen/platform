<#assign emailTitle = (appName!'DocuHyphen') + " - Group Created">
<#include "email-header.ftl">

<p style="margin:0 0 14px 0;"><strong>Hi ${firstName},</strong></p>
<p style="margin:0 0 14px 0;">You have successfully created the group <strong>${groupName}</strong><#if organizationName??> at <strong>${organizationName}</strong></#if>.</p>

<table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin:0 0 14px 0; border:1px solid #dddddd; background-color:#f0f2f5;">
    <tr>
        <td style="padding:12px 20px;">
            <p style="margin:0 0 6px 0;"><strong>Group:</strong> ${groupName}</p>
            <#if organizationName??>
            <p style="margin:0 0 6px 0;"><strong>Organization:</strong> ${organizationName}</p>
            </#if>
            <p style="margin:0;"><strong>Members added:</strong> ${memberCount}</p>
        </td>
    </tr>
</table>

<p style="margin:0 0 14px 0;">You are the owner of this group and can manage its members at any time from your Settings page.</p>
<p style="margin:0 0 14px 0;">Sign in at <a href="${appBaseUrl}/sign-in" style="color:#1f73b7;">${appBaseUrl}/sign-in</a> to view and manage the group.</p>

<#include "notification-opt-out.ftl">
<#include "email-footer.ftl">

