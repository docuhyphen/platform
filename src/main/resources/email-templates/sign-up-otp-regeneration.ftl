<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${appName} - New Verification Code</title>
    <style>
        body {
            margin: 0;
            padding: 0;
            background-color: #f4f6f8;
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
        }
        .container {
            width: 100%;
            background-color: #f4f6f8;
            padding: 20px;
        }
        .email-wrapper {
            max-width: 480px;
            margin: 0 auto;
            background-color: #ffffff;
            border-radius: 16px;
            overflow: hidden;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        }
        .header {
            padding: 20px;
            border-bottom: 1px solid #e0e0e0;
        }
        .header img {
            width: 40px;
            height: auto;
            display: block;
        }
        .content {
            padding: 30px 20px;
            font-size: 14px;
            color: #333333;
            line-height: 1.6;
        }
        .content h2 {
            margin: 0 0 20px 0;
            font-size: 20px;
            font-weight: 600;
            color: #222222;
        }
        .content p {
            margin: 0 0 16px 0;
        }
        .otp-container {
            background-color: #f0f2f5;
            border: 2px solid #e8eaed;
            border-radius: 8px;
            padding: 20px;
            text-align: center;
            margin: 24px 0;
        }
        .otp-code {
            font-size: 32px;
            font-weight: bold;
            letter-spacing: 6px;
            color: #1f73b7;
            font-family: 'Courier New', monospace;
            margin: 0;
            word-break: break-all;
        }
        .info-box {
            background-color: #f9f9f9;
            border-left: 4px solid #1f73b7;
            padding: 12px 16px;
            margin: 16px 0;
            border-radius: 4px;
            font-size: 13px;
        }
        .warning-box {
            background-color: #fff3cd;
            border-left: 4px solid #fbc02d;
            padding: 12px 16px;
            margin: 16px 0;
            border-radius: 4px;
            font-size: 13px;
        }
        .divider {
            border: none;
            border-top: 1px solid #e0e0e0;
            margin: 0;
        }
        .footer {
            padding: 20px;
            text-align: center;
            font-size: 12px;
            color: #777777;
            border-top: 1px solid #e0e0e0;
        }
        .footer p {
            margin: 0;
        }
    </style>
</head>
<body>
<table width="100%" cellpadding="0" cellspacing="0" border="0" class="container">
    <tr>
        <td align="center">
            <table width="100%" cellpadding="0" cellspacing="0" border="0" class="email-wrapper">
                <!-- Header -->
                <tr>
                    <td class="header">
                        <img src="https://assets.docuhyphen.com/email/logo.jpg" alt="${appName}" />
                    </td>
                </tr>

                <!-- Content -->
                <tr>
                    <td class="content">
                        <h2>New Verification Code</h2>

                        <p>Here's your new verification code for ${appName}:</p>

                        <div class="otp-container">
                            <p class="otp-code">${verificationCode}</p>
                        </div>

                        <div class="info-box">
                            <strong>Expiration Notice:</strong><br>
                            This verification code expires in <strong>${expiryMinutes}</strong> minute<#if expiryMinutes != 1>s</#if>.
                        </div>

                        <div class="warning-box">
                            <strong>Important:</strong><br>
                            Only use this code if you requested it. If you didn't request a new verification code, you can safely ignore this email.
                        </div>

                        <p>Questions? Contact our support team at <a href="mailto:support@docuhyphen.com" style="color: #1f73b7; text-decoration: none;">support@docuhyphen.com</a></p>
                    </td>
                </tr>

                <!-- Footer -->
                <tr>
                    <td class="footer">
                        <p>© ${appName}. All rights reserved.</p>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>

