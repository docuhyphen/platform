export type PricingTier = {
    name: string;
    price: string;
    cadence: string;
    bestFor: string;
    blurb: string;
    bullets: string[];
    cta: string;
    featured?: boolean;
};

export const pricingTiers: PricingTier[] = [
    {
        name: "Starter",
        price: "R0",
        cadence: "per user / month, cancel anytime",
        bestFor: "Best for: solo professionals and very small teams",
        blurb: "For small teams starting secure document exchange with essential controls.",
        bullets: [
            "Up to 3 users",
            "Collaborative Exchanges",
            "30-day audit log retention",
            "Email support",
        ],
        cta: "Start free",
    },
    {
        name: "Business",
        price: "R180",
        cadence: "per user / month, cancel anytime",
        bestFor: "Best for: growing teams that need SSO and governance",
        blurb: "Everything in Starter, plus SSO, role controls, and compliance-ready audit exports.",
        bullets: [
            "Everything in Starter",
            "Unlimited Exchanges",
            "Microsoft & Google SSO",
            "Role-based permissions",
            "1-year audit retention and export",
            "Priority support",
            "WhatsApp integration",
            "Google Drive / OneDrive backups",
            "Microsoft Teams integration",
            "Slack integration",
        ],
        cta: "Start trial",
        featured: true,
    },
    {
        name: "Enterprise",
        price: "Custom",
        cadence: "tailored to your org",
        bestFor: "Best for: regulated organizations with procurement workflows",
        blurb: "Everything in Business, plus advanced security, procurement, and deployment requirements.",
        bullets: [
            "Everything in Business",
            "Custom user caps",
            "SOC 2 report access and DPA",
            "Tenant isolation review",
            "Dedicated customer success manager",
            "Custom data residency",
            "Integration onboarding and support",
            "FTP backups",
        ],
        cta: "Talk to sales",
    },
];
