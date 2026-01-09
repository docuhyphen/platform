import React from 'react';
import SignInSignUpTipsCarousel from "./SignInSignUpTipsCarousel.tsx";

const SignInCarousel: React.FC = () =>
{
    const slides = [
        {
            title: "Use Strong & Unique Passwords",
            description: "Ensure your password is long, complex, and unique for each account. Avoid using common words or personal information."
        },
        {
            title: "Enable Two-Factor Authentication (2FA)",
            description: "Add an extra layer of security by enabling 2FA. This helps prevent unauthorized access even if your password is compromised."
        },
        {
            title: "Beware of Phishing Attempts",
            description: "Always verify email senders and website URLs before entering your credentials. Don't click on suspicious links or attachments."
        },
        {
            title: "Keep Your Devices Secure",
            description: "Use updated antivirus software, enable automatic updates, and avoid signing in from public or shared computers."
        },
        {
            title: "Verify Recipients Before Sharing",
            description: "Always double-check email addresses and business details before sharing sensitive documents to prevent fraud."
        }
    ];

    return <SignInSignUpTipsCarousel slides={slides}/>;
};

export default SignInCarousel;
