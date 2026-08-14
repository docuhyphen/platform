import {describe, expect, it} from "vitest";
import {identityProviderDisplayName} from "./identityProviderDisplayName.ts";

describe("identityProviderDisplayName", () =>
{
    it.each([
        ["GOOGLE", "Google"],
        ["MICROSOFT", "Microsoft"],
        ["INTERNAL", "Email & Password"],
        ["custom_PROVIDER", "Custom Provider"],
    ])("formats %s for display", (provider, expected) =>
    {
        expect(identityProviderDisplayName(provider)).toBe(expected);
    });
});
