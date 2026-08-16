export type BillingFrequency = "annual" | "monthly";

export type PricingPlan = {
    name: string;
    description: string;
    annualPrice: string;
    monthlyPrice: string;
    annualDetail: string;
    monthlyDetail: string;
    cadence: string;
};

export const pricingPlans: PricingPlan[] = [
    {
        name: "Free",
        description: "For occasional, straightforward document sharing.",
        annualPrice: "R0",
        monthlyPrice: "R0",
        annualDetail: "One registered sender",
        monthlyDetail: "One registered sender",
        cadence: "forever",
    },
    {
        name: "Personal",
        description: "For individual professionals working repeatedly with clients.",
        annualPrice: "R149",
        monthlyPrice: "R179",
        annualDetail: "Billed annually at R1,788",
        monthlyDetail: "Billed monthly",
        cadence: "/month",
    },
    {
        name: "Business",
        description: "For organizations that need shared processes and governance.",
        annualPrice: "R299",
        monthlyPrice: "R349",
        annualDetail: "R3,588 per seat, billed annually",
        monthlyDetail: "Billed monthly per active seat",
        cadence: "/seat/month",
    },
];

export const featureRows = [
    ["Subscription owner", "Individual", "Individual", "Registered organization"],
    ["Paid seats", "One included user", "One user", "Each active organization member"],
    ["External recipients", "Unlimited", "Unlimited", "Unlimited"],
    ["No-account client access", "Included", "Included", "Included with organization policy controls"],
    ["New Exchanges", "5 per month", "Unlimited under reasonable use", "Unlimited under reasonable use"],
    ["Active Exchanges", "Up to 3", "Unlimited under reasonable use", "Unlimited under reasonable use"],
    ["Storage", "Unlimited under reasonable use", "Unlimited under reasonable use", "Unlimited under reasonable use"],
    ["Participants", "Primary recipient", "Multiple participants", "Members, groups and external parties"],
    ["Blueprints", "Not included", "Personal and platform", "Personal, organization and platform"],
    ["Document Library", "Not included", "Personal library", "Personal and shared organization libraries"],
    ["Shared document comments", "Included", "Included", "Included"],
    ["Document version history", "Not included", "Included", "Included"],
    ["Advanced access controls", "Basic permissions", "Roles, restrictions, watermarking and MFA", "Roles, restrictions, policies and internal notes"],
    ["Variables and sequences", "Not included", "Personal", "Personal and organization"],
    ["Business Fields and schemas", "Not included", "Not included", "Included"],
    ["Workflow automation", "Not included", "Coming soon", "Approvals, conditions, actions and escalations"],
    ["Organization administration", "Not included", "Not included", "Roles, groups, member policies and shared content"],
    ["Audit and governance", "Basic Exchange activity", "Extended Exchange activity", "Audit workspace, exports, retention and integrity"],
    ["Identity and integrations", "Not included", "Not included", "SSO, SCIM, registered applications and webhooks"],
    ["Support", "Self-service help", "Email support", "Priority support"],
] as const;
