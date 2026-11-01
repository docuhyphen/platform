export type RiskVariantClass = "card1" | "card2" | "card3" | "card4";

export type RiskItem = {
    id: number;
    subtitle: string;
    title: string;
    body: string;
    imageSrc: string;
    imageAlt: string;
    imagePosition?: string;
    variantClass: RiskVariantClass;
};

export const risks: RiskItem[] = [
    {
        id: 1,
        subtitle: "High risk of unauthorized access",
        title: "Confidential Files Can Be Accessed by the Wrong People",
        body: "Sensitive documents can be intercepted, forwarded without permission, downloaded to unsecured devices, or stored indefinitely in inboxes and shared drives. Once sent, control is lost.",
        imageSrc: "/demo-screenshots/app-screenshot-banking-lending.JPG",
        imageAlt: "Access control and session overview screenshot",
        imagePosition: "center 18%",
        variantClass: "card1",
    },
    {
        id: 2,
        subtitle: "Uncontrolled document distribution",
        title: "Shared Documents Spread Beyond Intended Recipients",
        body: "Links can be forwarded. Emails can be mistyped. Attachments can be duplicated and redistributed. There is no reliable way to verify the intended recipient or restrict downstream sharing.",
        imageSrc: "/demo-screenshots/app-screenshot-legal.JPG",
        imageAlt: "Sharing workflow screenshot showing controlled recipients",
        imagePosition: "center 20%",
        variantClass: "card2",
    },
    {
        id: 3,
        subtitle: "No defensible audit trail",
        title: "Without Activity Logs, You Cannot Prove What Happened",
        body: "Most tools provide little visibility into who accessed documents, when they were accessed, or what actions were taken. When disputes or investigations arise, proof is often unavailable.",
        imageSrc: "/demo-screenshots/app-screenshot-accounting.JPG",
        imageAlt: "Audit and tracking related screenshot",
        imagePosition: "center 16%",
        variantClass: "card3",
    },
    {
        id: 4,
        subtitle: "Regulatory exposure (POPIA, GDPR & similar laws)",
        title: "Weak Controls Increase Compliance and Legal Risk",
        body: "Data protection regulations require secure processing, controlled access, and demonstrable accountability. Without encryption, access control, and detailed logging, organizations risk fines, legal exposure, and reputational damage.",
        imageSrc: "/demo-screenshots/app-screenshot-healthcare.JPG",
        imageAlt: "Compliance-focused secure sharing screenshot",
        imagePosition: "center 24%",
        variantClass: "card4",
    },
];
