import React from 'react';
import SignInSignUpTipsCarousel from "./SignInSignUpTipsCarousel.tsx";

const AccountRecoveryCarousel: React.FC = () =>
{
    const slides = [
        {
            title: "Keep Your Recovery Options Updated",
            description: "Ensure your recovery email and phone number are current so you can regain access if needed."
        },
        {
            title: "Use Secure Password Reset Links",
            description: "Only reset your password through official links sent to your email. Avoid clicking on random reset links from unknown sources."
        },
        {
            title: "Beware of Social Engineering",
            description: "Support will never ask for your password or security codes. Avoid sharing personal details with unverified contacts."
        },
        {
            title: "Use a Password Manager",
            description: "Store and manage recovery codes securely with a trusted password manager to avoid losing access."
        },
        {
            title: "Enable Multi-Factor Authentication (MFA)",
            description: "MFA adds extra security, making it harder for attackers to take over your account even if they have your password."
        }
    ];

    return <SignInSignUpTipsCarousel slides={slides}/>;
};

export default AccountRecoveryCarousel;
