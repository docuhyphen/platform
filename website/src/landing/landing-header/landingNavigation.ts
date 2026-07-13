export type LandingNavLink = {
    label: string;
    to: string;
};

export const LANDING_INDUSTRIES: LandingNavLink[] = [
    {label: "Real Estate", to: "/industries/real-estate"},
    {label: "Legal", to: "/industries/legal"},
    {label: "Healthcare", to: "/industries/healthcare"},
    {label: "Accounting", to: "/industries/accounting"},
    {label: "Banking", to: "/industries/banking"},
];

export const toNavigationId = (label: string) => label.toLowerCase().replaceAll(" ", "-");
