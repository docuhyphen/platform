<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${appName} - Email Verification</title>
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
        .button-container {
            text-align: center;
            margin: 24px 0;
        }
        .button {
            display: inline-block;
            padding: 12px 32px;
            background-color: #8d5a5a;
            color: #ffffff;
            text-decoration: none;
            border-radius: 6px;
            font-weight: 600;
            font-size: 14px;
            transition: background-color 0.3s ease;
        }
        .button:hover {
            background-color: #185a96;
        }
        .info-box {
            background-color: #f9f9f9;
            border-left: 4px solid #1f73b7;
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
        .warning {
            color: #d32f2f;
            font-weight: 500;
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
                        <h2>Welcome to ${appName}!</h2>

                        <p>Thank you for signing up. To complete your registration, please verify your email address using the code below:</p>

                        <div class="otp-container">
                            <p class="otp-code">${verificationCode}</p>
                        </div>

                        <p><strong>Alternatively, click the button below to verify automatically:</strong></p>

                        <div class="button-container">
                            <a href="${confirmationLink}" class="button">Verify Email Address</a>
                        </div>

                        <div class="info-box">
                            <strong>Expiration Notice:</strong><br>
                            This verification code expires in <strong>${expiryMinutes}</strong> minute<#if expiryMinutes != 1>s</#if>.
                        </div>

                        <p>If you didn't create this account, please <a href="mailto:support@docuhyphen.com">contact our support team</a> immediately.</p>

                        <div class="info-box" style="border-left-color: #fbc02d;">
                            <strong>Security:</strong><br>
                            Never share your verification code with anyone. ${appName} will never ask for this code via email, phone, or any other method.
                        </div>
                    </td>
                </tr>

                <!-- Footer -->
                <tr>
                    <td class="footer">
                        <p>© ${appName}. All rights reserved.</p>
                        <p>If you have any questions, please visit <a href="https://docuhyphen.com/help" style="color: #1f73b7; text-decoration: none;">our help center</a></p>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>

