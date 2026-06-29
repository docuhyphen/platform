export const providerDefinitions = [
    {
        provider: "INTERNAL",
        idPrefix: "internal",
        title: "Email & Password",
        description: "Built-in account login for your DocuHyphen profile.",
        providerMark: "E",
    },
    {
        provider: "MICROSOFT",
        idPrefix: "microsoft",
        title: "Microsoft",
        description: "Connect your Microsoft account for faster sign-in.",
        providerMark: "M",
    },
    {
        provider: "GOOGLE",
        idPrefix: "google",
        title: "Google",
        description: "Connect your Google account for faster sign-in.",
        providerMark: "G",
    }
] as const;
