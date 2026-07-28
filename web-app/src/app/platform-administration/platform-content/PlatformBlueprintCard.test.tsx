/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {BlueprintDefinitionSummaryDto} from "../../models/models.tsx";
import PlatformBlueprintCard from "./PlatformBlueprintCard.tsx";

const blueprint: BlueprintDefinitionSummaryDto = {
    id: "blueprint-1",
    name: "Platform Blueprint",
    scope: "APP",
    isActive: true,
    isPublished: false,
    isTemplate: true,
    generalTags: [],
    configJson: "{}",
    exchangeDocuments: [],
    participants: [],
    createdAt: "2026-07-28T00:00:00Z",
    updatedAt: "2026-07-28T00:00:00Z",
};

afterEach(cleanup);

describe("PlatformBlueprintCard", () =>
{
    it("places Blueprint actions in an overflow menu", () =>
    {
        const onEdit = vi.fn();
        const onTogglePublished = vi.fn();
        const onToggleActive = vi.fn();
        const onDelete = vi.fn();
        render(
            <PlatformBlueprintCard
                blueprint={blueprint}
                onEdit={onEdit}
                onTogglePublished={onTogglePublished}
                onToggleActive={onToggleActive}
                onDelete={onDelete}/>,
        );

        expect(screen.queryByText("Edit")).toBeNull();
        const card = document.querySelector("#platform-blueprint-blueprint-1");
        const menuButton = document.querySelector("#platform-blueprint-more-blueprint-1");
        expect(menuButton?.parentElement).toBe(card);
        fireEvent.click(screen.getByLabelText("More actions for Platform Blueprint"));
        expect(screen.getByText("Edit")).toBeTruthy();
        expect(screen.getByText("Publish")).toBeTruthy();
        expect(screen.getByText("Deactivate")).toBeTruthy();
        expect(screen.getByText("Delete")).toBeTruthy();

        fireEvent.click(screen.getByText("Edit"));
        expect(onEdit).toHaveBeenCalledTimes(1);

        fireEvent.click(screen.getByLabelText("More actions for Platform Blueprint"));
        fireEvent.click(screen.getByText("Publish"));
        expect(onTogglePublished).toHaveBeenCalledTimes(1);

        fireEvent.click(screen.getByLabelText("More actions for Platform Blueprint"));
        fireEvent.click(screen.getByText("Deactivate"));
        expect(onToggleActive).toHaveBeenCalledTimes(1);

        fireEvent.click(screen.getByLabelText("More actions for Platform Blueprint"));
        fireEvent.click(screen.getByText("Delete"));
        expect(onDelete).toHaveBeenCalledTimes(1);
    });
});
