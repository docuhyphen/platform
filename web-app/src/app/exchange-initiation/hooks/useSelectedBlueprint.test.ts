// @vitest-environment jsdom

import {act, renderHook} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import {BlueprintDefinitionSummaryDto, InformationRequestTemplateStatus} from "../../models/models.tsx";
import useSelectedBlueprint from "./useSelectedBlueprint.ts";

const blueprint = (informationRequestTemplateVersionId?: string): BlueprintDefinitionSummaryDto => ({
    id: "blueprint-a",
    name: "Collection blueprint",
    scope: "ORG",
    isActive: true,
    isPublished: true,
    isTemplate: false,
    generalTags: [],
    configJson: "{}",
    informationRequestTemplateVersionId,
    exchangeDocuments: [],
    participants: [],
    createdAt: Date.parse("2026-10-01T08:00:00Z"),
    updatedAt: Date.parse("2026-10-01T08:00:00Z"),
});

describe("useSelectedBlueprint", () => {
    it("starts with no Blueprint, so the Exchange still needs documents", () => {
        const {result} = renderHook(() => useSelectedBlueprint());

        expect(result.current.selectedBlueprint).toBeNull();
        expect(result.current.startsInformationRequest).toBe(false);
    });

    it("keeps a Blueprint that starts no Information Request without relaxing the document rule", () => {
        const {result} = renderHook(() => useSelectedBlueprint());

        act(() => result.current.selectBlueprint(blueprint()));

        expect(result.current.selectedBlueprint).toEqual({id: "blueprint-a", name: "Collection blueprint"});
        expect(result.current.startsInformationRequest).toBe(false);
    });

    it("marks a Blueprint that pins a Template Version as starting an Information Request until it is cleared", () => {
        const {result} = renderHook(() => useSelectedBlueprint());

        act(() => result.current.selectBlueprint(blueprint("template-version-3")));

        expect(result.current.selectedBlueprint).toEqual({
            id: "blueprint-a",
            name: "Collection blueprint",
            informationRequestTemplateVersionId: "template-version-3",
        });
        expect(result.current.startsInformationRequest).toBe(true);

        act(() => result.current.clearBlueprint());

        expect(result.current.selectedBlueprint).toBeNull();
        expect(result.current.startsInformationRequest).toBe(false);
    });

    it("keeps the document rule for a Blueprint whose pinned Version was retired", () => {
        const {result} = renderHook(() => useSelectedBlueprint());

        act(() => result.current.selectBlueprint({
            ...blueprint("template-version-3"),
            informationRequestTemplateVersion: {
                templateId: "template-a",
                templateDisplayName: "Collection template",
                versionNumber: 3,
                status: InformationRequestTemplateStatus.RETIRED,
                latest: false,
                retiredAt: Date.parse("2026-10-10T08:00:00Z"),
            },
        }));

        expect(result.current.startsInformationRequest).toBe(false);
    });
});
