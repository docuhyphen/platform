import React from 'react';
import SignInSignUpTipsCarousel from "./SignInSignUpTipsCarousel.tsx";

const SignUpCarousel: React.FC = () =>
{
    const slides = [
        {
            title: "Effortless Sharing & Real-Time Viewing",
            description: "Share documents securely with internal and external teams, enabling real-time access and seamless collaboration. Stay in sync as everyone views the latest version, with full control over who can access what."
        },
        {
            title: "Secure & Compliant Document Sharing",
            description: "Exchange sensitive documents with security and compliance features, ensuring your business meets data protection laws."
        },
        {
            title: "Verified Business & Secure Access",
            description: "Every business is verified to ensure legitimacy, preventing fraud and unauthorized document exchanges. Share with confidence, knowing your data is protected."
        },
        {
            title: "Audit Trails & Access Logs",
            description: " Get full visibility with detailed audit trails. Track who accessed, shared, or modified documents in real time."
        }
    ];

    return <SignInSignUpTipsCarousel slides={slides}/>;
};

export default SignUpCarousel;