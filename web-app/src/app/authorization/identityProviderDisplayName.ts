const identityProviderNames: Record<string, string> = {
    INTERNAL: "Email & Password",
    MICROSOFT: "Microsoft",
    GOOGLE: "Google",
};

export const identityProviderDisplayName = (provider?: string | null): string =>
{
    if (!provider) return "your identity provider";

    const normalized = provider.trim().toUpperCase();
    return identityProviderNames[normalized] ?? provider
        .trim()
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(/\b\w/g, character => character.toUpperCase());
};
