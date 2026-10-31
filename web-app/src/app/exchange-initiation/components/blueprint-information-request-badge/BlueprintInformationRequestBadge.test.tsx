/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it} from "vitest";
import {BlueprintDefinitionSummaryDto, InformationRequestTemplateStatus} from "../../../models/models.tsx";
import BlueprintInformationRequestBadge from "./BlueprintInformationRequestBadge.tsx";

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

describe("BlueprintInformationRequestBadge", () => {
    afterEach(cleanup);

    it("marks a Blueprint that starts an Information Request", () => {
        render(<BlueprintInformationRequestBadge blueprint={blueprint("template-version-3")}/>);

        expect(screen.getByText("Starts an Information Request")).toBeTruthy();
        expect(document.querySelector("#blueprint-information-request-badge-blueprint-a")).toBeTruthy();
    });

    it("marks a Blueprint whose pinned Version was retired as unable to start its request", () => {
        render(<BlueprintInformationRequestBadge blueprint={{
            ...blueprint("template-version-3"),
            informationRequestTemplateVersion: {
                templateId: "template-a",
                templateDisplayName: "Collection template",
                versionNumber: 3,
                status: InformationRequestTemplateStatus.RETIRED,
                latest: false,
                retiredAt: Date.parse("2026-10-10T08:00:00Z"),
            },
        }}/>);

        expect(screen.queryByText("Starts an Information Request")).toBeNull();
        expect(screen.getByText("Cannot start: its Template Version is retired")).toBeTruthy();
    });

    it("marks nothing on a Blueprint that starts no Information Request", () => {
        const {container} = render(<BlueprintInformationRequestBadge blueprint={blueprint()}/>);

        expect(container.textContent).toBe("");
    });
});
