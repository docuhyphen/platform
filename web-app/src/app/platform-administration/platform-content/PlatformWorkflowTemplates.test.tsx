/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import PlatformWorkflowTemplates from "./PlatformWorkflowTemplates.tsx";

const listPlatformWorkflowTemplates = vi.fn();

vi.mock("../../../services/platformWorkflowService.ts", () => ({
    listPlatformWorkflowTemplates: (...args: unknown[]) => listPlatformWorkflowTemplates(...args),
    setPlatformWorkflowActive: vi.fn(),
    setPlatformWorkflowPublished: vi.fn(),
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("PlatformWorkflowTemplates", () =>
{
    it("keeps its icon action outside the scrollable results", async () =>
    {
        const onCreate = vi.fn();
        listPlatformWorkflowTemplates.mockResolvedValue([]);
        render(
            <PlatformWorkflowTemplates
                onCreate={onCreate}
                onEdit={vi.fn()}/>,
        );

        await waitFor(() => expect(listPlatformWorkflowTemplates).toHaveBeenCalledTimes(1));
        expect(screen.queryByText(
            "Manage APP-scoped workflow templates without loading organization workflow data.",
        )).toBeNull();
        expect(document.querySelector("#platform-workflow-template-create svg")).toBeTruthy();
        expect(document.querySelector(
            "#platform-workflow-templates-scrollable-content #platform-workflow-template-create",
        )).toBeNull();

        fireEvent.click(screen.getByText("Create platform workflow"));
        expect(onCreate).toHaveBeenCalledTimes(1);
    });
});
