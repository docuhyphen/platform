<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${appName} - Account Created Successfully</title>
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
        .success-badge {
            text-align: center;
            margin: 24px 0;
        }
        .success-icon {
            display: inline-block;
            width: 60px;
            height: 60px;
            background-color: #4caf50;
            border-radius: 50%;
            text-align: center;
            line-height: 60px;
            font-size: 32px;
            color: #ffffff;
        }
        .button-container {
            text-align: center;
            margin: 24px 0;
        }
        .button {
            display: inline-block;
            padding: 12px 32px;
            background-color: #1f73b7;
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
        .feature-list {
            background-color: #f9f9f9;
            border-left: 4px solid #1f73b7;
            padding: 16px;
            margin: 16px 0;
            border-radius: 4px;
            font-size: 13px;
        }
        .feature-list ul {
            margin: 8px 0;
            padding-left: 20px;
        }
        .feature-list li {
            margin: 6px 0;
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
        .footer-links {
            margin: 8px 0;
        }
        .footer-links a {
            color: #1f73b7;
            text-decoration: none;
            margin: 0 8px;
            font-size: 12px;
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
                        <h2>Account Created Successfully!</h2>

                        <p>Welcome to ${appName}!</p>

                        <p>Your account has been successfully created and verified.
                            You can now sign in and start using all the features available on our platform.
                        </p>

                        <p><strong>Your email:</strong> ${email}</p>

                        <div class="button-container">
                            <a href="${appBaseUrl}/sign-in" class="button">Sign In to Your Account</a>
                        </div>

                        <div class="feature-list">
                            <strong>Get Started:</strong>
                            <ul>
                                <li>Secure document management and sharing</li>
                                <li>Advanced collaboration tools</li>
                                <li>Real-time activity monitoring</li>
                                <li>Comprehensive audit trails</li>
                            </ul>
                        </div>

                        <div class="info-box">
                            <strong>Next Steps:</strong><br>
                            Complete your profile and configure your security preferences to get the best experience from ${appName}.
                        </div>

                        <p>If you need help, our support team is always available at
                            <a href="mailto:support@docuhyphen.com" style="color: #1f73b7; text-decoration: none;">
                                support@docuhyphen.com
                            </a>.
                        </p>
                    </td>
                </tr>

                <tr>
                    <td class="footer">
                        <p>© ${appName}. All rights reserved.</p>
                        <div class="footer-links">
                            <a href="${appBaseUrl}/help">Help Center</a> |
                            <a href="${appBaseUrl}/privacy">Privacy Policy</a> |
                            <a href="${appBaseUrl}/terms">Terms of Service</a>
                        </div>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>

