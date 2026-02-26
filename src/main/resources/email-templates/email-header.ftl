<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>DocuHyphen Verification Code</title>
</head>
<body style="margin:0; padding:0; background-color:#f4f6f8; font-family: Arial, Helvetica, sans-serif;">

<table width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f6f8;">
    <tr>
        <td align="center" style="padding:20px;">

            <table width="100%" cellpadding="0" cellspacing="0" border="0"
                   style="max-width:480px; background-color:#ffffff; border-radius:16px;">
                <tr>
                    <td align="left" style="padding:20px;">
                        <img src="https://assets.docuhyphen.com/email/logo.jpg"
                             alt="DocuHyphen"
                             width="40"
                        />
                        <#--                        <h1 style="margin:0; font-size:22px; color:#222222;">Docu-Hyphen</h1>-->
                    </td>
                </tr>
                <tr>
                    <td style="padding:0 20px;">
                        <hr style="border:none; border-top:1px solid #e0e0e0;">
                    </td>
                </tr>
                <tr>
                    <td style="padding:20px; font-size:14px; color:#333333;">
                        <p><strong>Your new sign-in verification code is:</strong></p>

                        <table align="center" style="margin:20px auto;">
                            <tr>
                                <td style="
                  padding:15px 25px;
                  font-size:24px;
                  letter-spacing:4px;
                  font-weight:bold;
                  background-color:#f0f2f5;
                  border:1px solid #dddddd;">
                                    ${verificationCode}
                                </td>
                            </tr>
                        </table>

                        <p>This code will expire in <strong>${expiryMinutes}</strong> minutes.</p>

                        <p>If you didn’t request this code, you can safely disregard this email.
                            If you notice suspicious activity, please change your password or contact support for further actions.</p>
                    </td>
                </tr>
                <tr>
                    <td style="padding:0 20px;">
                        <hr style="border:none; border-top:1px solid #e0e0e0;">
                    </td>
                </tr>
                <tr align="center">
                    <td style="padding:20px; font-size:12px; color:#777777;">
                        <p style="margin:0;"> © DocuHyphen. All rights reserved. </p>
                    </td>
                </tr>
            </table>

        </td>
    </tr>
</table>

</body>
</html>
