/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import CommunicationPickerDialog from "./CommunicationPickerDialog.tsx";

const listCommunications = vi.fn();

vi.mock("../../../services/communicationService.ts", () => ({
    listCommunications: (...args: unknown[]) => listCommunications(...args),
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

const summary = (id: string, name: string, scope: "PERSONAL" | "ORG" | "PLATFORM") => ({
    id,
    name,
    scope,
    subject: `${name} subject`,
    isActive: true,
    isPublished: true,
    isTemplate: false,
    generalTags: [] as string[],
    createdAt: "2026-08-01T08:00:00Z",
    updatedAt: "2026-08-01T08:00:00Z",
});

describe("CommunicationPickerDialog", () =>
{
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    it("offers only the allowed scopes, starting with the first one", async () => {
        listCommunications.mockImplementation(async (params: { scope: string }) =>
            params.scope === "PLATFORM" ? [summary("platform-a", "Platform notice", "PLATFORM")] : [summary("personal-a", "My notice", "PERSONAL")]);
        const onSelect = vi.fn();

        render(
            <CommunicationPickerDialog
                open={true}
                allowedScopes={["PERSONAL", "PLATFORM"]}
                onClose={vi.fn()}
                onSelect={onSelect}/>,
        );

        expect(await screen.findByText("My notice")).toBeTruthy();
        expect(screen.getByRole("tab", {name: "My Communications"})).toBeTruthy();
        expect(screen.queryByRole("tab", {name: "Organization"})).toBeNull();
        fireEvent.click(screen.getByRole("tab", {name: "Platform"}));
        fireEvent.click(await screen.findByRole("button", {name: /Platform notice/}));
        fireEvent.click(screen.getByRole("button", {name: "Select Communication"}));

        expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({id: "platform-a"}));
        expect(listCommunications).toHaveBeenCalledWith({scope: "PERSONAL"});
        expect(listCommunications).toHaveBeenCalledWith({scope: "PLATFORM"});
        expect(listCommunications).not.toHaveBeenCalledWith({scope: "ORG"});
    });

    it("uses only the fixed platform scope in Platform Administration", async () =>
    {
        listCommunications.mockResolvedValue([]);

        const {queryByText} = render(
            <CommunicationPickerDialog
                open={true}
                fixedScope={"PLATFORM"}
                onClose={vi.fn()}
                onSelect={vi.fn()}/>,
        );

        await waitFor(() =>
            expect(listCommunications).toHaveBeenCalledWith({scope: "PLATFORM"}),
        );
        expect(queryByText("My Communications")).toBeNull();
        expect(queryByText("Organization")).toBeNull();
        expect(listCommunications).toHaveBeenCalledTimes(1);
    });
});
