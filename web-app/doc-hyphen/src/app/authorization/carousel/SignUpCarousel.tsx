import React from 'react';
import SignInSignUpTipsCarousel from "./SignInSignUpTipsCarousel.tsx";

const SignUpCarousel: React.FC = () =>
{
    const slides = [
        {
            title: "Secure & Compliant Document Sharing",
            description: "Exchange sensitive documents with end-to-end encryption and compliance features, ensuring your business meets data protection laws."
        },
        {
            title: "Verified Business & Secure Access",
            description: "Every business is verified to ensure legitimacy, preventing fraud and unauthorized document exchanges. Share with confidence, knowing your data is protected."
        },
        {
            title: "Digital Signatures & Watermarking",
            description: "Every document is protected with unique digital signatures and invisible watermarking, ensuring authenticity and traceability."
        },
        {
            title: "Audit Trails & Access Logs",
            description: " Get full visibility with detailed audit trails. Track who accessed, shared, or modified documents in real time."
        }
    ];

    return <SignInSignUpTipsCarousel slides={slides}/>;
};

export default SignUpCarousel;