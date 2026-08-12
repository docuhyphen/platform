import type {LegalDocument} from "./LegalDocumentPage.tsx";

export const privacyPolicyDocument: LegalDocument = {
    title: "Privacy Policy",
    pageId: "privacy-policy-page",
    summary: "This policy explains how DocuHyphen handles personal information when you use our website and document Exchange platform.",
    sections: [
        {
            heading: "Information we collect",
            paragraphs: [
                "We collect information you provide, such as your name, email address, organization details, account settings, support messages, and documents or other content submitted through an Exchange.",
                "We also collect basic technical and usage information, such as your IP address, browser, device, sign-in activity, and actions taken in the service.",
            ],
        },
        {
            heading: "How we use information",
            paragraphs: [
                "We use personal information to provide and secure DocuHyphen, manage accounts and Exchanges, deliver notifications, respond to support requests, improve the service, process subscriptions, and meet legal obligations.",
            ],
        },
        {
            heading: "How we share information",
            paragraphs: [
                "Information and documents are shared with the people and organizations you authorize through an Exchange. We may also use trusted service providers for hosting, storage, communications, payments, security, and support. We do not sell personal information.",
                "Some service providers may process information outside South Africa. Where required, we use appropriate safeguards for cross-border processing.",
                "We may disclose information where required by law, to protect rights or safety, or as part of a business restructuring with appropriate safeguards.",
            ],
        },
        {
            heading: "Storage, retention, and security",
            paragraphs: [
                "We use reasonable technical and organizational safeguards to protect personal information. No online service can guarantee complete security.",
                "We retain information only for as long as needed to provide the service, meet legal or contractual requirements, resolve disputes, and maintain necessary business records.",
            ],
        },
        {
            heading: "Your choices and rights",
            paragraphs: [
                "Subject to applicable law, including POPIA, you may ask to access, correct, delete, or object to certain processing of your personal information. You may also withdraw consent where processing relies on consent.",
                "To make a privacy request or ask a question, contact support@docuhyphen.com. We may need to verify your identity before completing a request.",
            ],
        },
        {
            heading: "Changes to this policy",
            paragraphs: [
                "We may update this policy as DocuHyphen or applicable law changes. We will publish the revised policy on this page and update the effective date.",
            ],
        },
    ],
};

export const termsOfServiceDocument: LegalDocument = {
    title: "Terms of Service",
    pageId: "terms-of-service-page",
    summary: "These terms govern your access to and use of the DocuHyphen website and document Exchange platform.",
    sections: [
        {
            heading: "Using DocuHyphen",
            paragraphs: [
                "You must provide accurate account information, keep your sign-in details secure, and be legally able to agree to these terms. If you use DocuHyphen for an organization, you confirm that you are authorized to act for it.",
                "You are responsible for activity under your account and for configuring access to your Exchanges appropriately.",
            ],
        },
        {
            heading: "Your content",
            paragraphs: [
                "You retain ownership of documents and other content you submit. You give DocuHyphen permission to host, process, transmit, and display that content only as needed to provide, secure, and support the service.",
                "You must have the rights and permissions needed to upload and share content, including personal or confidential information.",
            ],
        },
        {
            heading: "Acceptable use",
            paragraphs: [
                "You may not use DocuHyphen unlawfully, infringe another person's rights, upload malicious content, attempt unauthorized access, disrupt the service, or use the platform to distribute spam or harmful material.",
            ],
        },
        {
            heading: "Plans and availability",
            paragraphs: [
                "Paid features, prices, billing periods, and cancellation terms are presented when you subscribe. Unless required by law, fees already paid are non-refundable.",
                "We may maintain, improve, change, suspend, or discontinue parts of the service. We aim to keep DocuHyphen available, but do not guarantee uninterrupted or error-free operation.",
            ],
        },
        {
            heading: "Suspension and termination",
            paragraphs: [
                "You may stop using DocuHyphen at any time. We may restrict or end access if you breach these terms, create security or legal risk, fail to pay applicable fees, or misuse the service. Where practical, we will provide notice and an opportunity to address the issue.",
            ],
        },
        {
            heading: "Disclaimers and liability",
            paragraphs: [
                "DocuHyphen is provided on an as-available basis. To the extent permitted by law, we exclude implied warranties and are not liable for indirect, incidental, special, or consequential loss arising from use of the service.",
                "Nothing in these terms limits liability that cannot lawfully be excluded or limits any mandatory consumer rights.",
            ],
        },
        {
            heading: "Governing law and changes",
            paragraphs: [
                "These terms are governed by the laws of South Africa. We may update them from time to time and will publish the revised terms with a new effective date. Continued use after the revised terms take effect means you accept them.",
                "Questions about these terms may be sent to support@docuhyphen.com.",
            ],
        },
    ],
};
