export const INDUSTRY_OPTIONS = [
    {label: "Law Firms & Legal Practices", slug: "legal"},
    {label: "Real Estate & Property Management", slug: "real-estate"},
    {label: "Healthcare & Medical Practices", slug: "healthcare"},
    {label: "Accounting & Audit Firms", slug: "accounting"},
    {label: "Banks & Lending Institutions", slug: "banking"},
    {label: "Other", slug: "other"},
] as const;

export type IndustrySlug = (typeof INDUSTRY_OPTIONS)[number]["slug"];

const STORAGE_KEY = "dh_selected_industry";

export function getStoredIndustry(): IndustrySlug | null
{
    try
    {
        const value = localStorage.getItem(STORAGE_KEY);
        if (value && INDUSTRY_OPTIONS.some((option) => option.slug === value))
        {
            return value as IndustrySlug;
        }
    }
    catch
    {
        return null;
    }
    return null;
}

export function setStoredIndustry(slug: IndustrySlug): void
{
    try
    {
        localStorage.setItem(STORAGE_KEY, slug);
    }
    catch
    {
        return;
    }
}

