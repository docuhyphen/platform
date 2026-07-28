/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import PlatformContent from "./PlatformContent.tsx";

const designerProps = vi.fn();

vi.mock("./PlatformWorkflowTemplates.tsx", () => ({
    default: ({onCreate}: {onCreate: () => void}) => (
        <button
            id={"test-create-platform-workflow"}
            onClick={onCreate}>
            Create platform workflow
        </button>
    ),
}));

vi.mock("./PlatformBlueprints.tsx", () => ({
    default: () => <div id={"test-platform-blueprints"}>Platform Blueprints</div>,
}));

vi.mock("./PlatformCommunications.tsx", () => ({
    default: () => <div id={"test-platform-communications"}>Platform Communications</div>,
}));

vi.mock("./PlatformDocuments.tsx", () => ({
    default: () => <div id={"test-platform-documents"}>Platform Documents</div>,
}));

vi.mock("./PlatformFields.tsx", () => ({
    default: () => <div id={"test-platform-fields"}>Platform Fields</div>,
}));

vi.mock("../../settings/workflows-tab/workflow-designer/WorkflowDesigner.tsx", () => ({
    default: (props: Record<string, unknown>) =>
    {
        designerProps(props);
        return <div id={"test-platform-workflow-designer"}>Platform workflow editor</div>;
    },
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("PlatformContent", () =>
{
    it("opens the workflow editor with an enforced APP template scope", () =>
    {
        render(<PlatformContent/>);

        const navigation = document.querySelector("#platform-content-navigation");
        const selectedTab = document.querySelector("#platform-content-selected-tab");
        expect(navigation?.querySelector("#platform-content-tabs")).toBeTruthy();
        expect(selectedTab?.querySelector("#platform-content-tabs")).toBeNull();
        fireEvent.click(screen.getByText("Create platform workflow"));

        expect(screen.getByText("Platform workflow editor")).toBeTruthy();
        expect(designerProps).toHaveBeenCalledWith(expect.objectContaining({
            scope: "APP",
            enforcedScope: "APP",
            createAsTemplate: true,
        }));
    });

    it("opens platform Blueprint management from the content tabs", () =>
    {
        render(<PlatformContent/>);

        fireEvent.click(document.querySelector("#platform-content-blueprints-tab") as HTMLElement);

        expect(screen.getByText("Platform Blueprints")).toBeTruthy();
    });

    it("opens platform Communication management from the content tabs", () =>
    {
        render(<PlatformContent/>);

        fireEvent.click(document.querySelector("#platform-content-communications-tab") as HTMLElement);

        expect(screen.getByText("Platform Communications")).toBeTruthy();
    });

    it("opens platform Document Library management from the content tabs", () =>
    {
        render(<PlatformContent/>);

        fireEvent.click(document.querySelector("#platform-content-documents-tab") as HTMLElement);

        expect(screen.getByText("Platform Documents")).toBeTruthy();
    });

    it("opens platform Field and Schema management from the content tabs", () =>
    {
        render(<PlatformContent/>);

        fireEvent.click(document.querySelector("#platform-content-fields-tab") as HTMLElement);

        expect(screen.getByText("Platform Fields")).toBeTruthy();
    });
});
