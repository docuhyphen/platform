/** @vitest-environment jsdom */
import {cleanup, render, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {FieldScopeKind} from "../../../models/models.tsx";
import BlueprintBusinessFieldsTab from "./BlueprintBusinessFieldsTab.tsx";

const listSchemas = vi.fn();

vi.mock("../../../../services/fieldsService.ts", () => ({
    listSchemas: (...args: unknown[]) => listSchemas(...args),
}));

vi.mock(
    "../../../exchange-initiation/components/exchange-initiation-fields-tab/creationFieldsUtils.ts",
    () => ({
        buildBlueprintFieldDefaults: () => [],
        filterEligibleExchangeSchemas: (schemas: unknown[]) => schemas,
    }),
);

vi.mock(
    "../../../exchange-initiation/components/exchange-initiation-fields-tab/ExchangeInitiationFieldsTab.tsx",
    () => ({
        default: () => <div id={"test-exchange-fields"}/>,
    }),
);

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("BlueprintBusinessFieldsTab", () =>
{
    it("requests only PLATFORM schemas in enforced APP mode", async () =>
    {
        listSchemas.mockResolvedValue([]);

        render(
            <BlueprintBusinessFieldsTab
                enforcedScope={"APP"}
                onChange={vi.fn()}/>,
        );

        await waitFor(() =>
            expect(listSchemas).toHaveBeenCalledWith({scopeKind: FieldScopeKind.PLATFORM}),
        );
        expect(listSchemas).toHaveBeenCalledTimes(1);
    });
});
