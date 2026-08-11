/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import ExchangeInitiationDialogTrigger
    from "./components/exchange-initiation-dialog-trigger/ExchangeInitiationDialogTrigger.tsx";
import ExchangeInitiationDialogTitleSection
    from "./components/exchange-initiation-dialog-title-section/ExchangeInitiationDialogTitleSection.tsx";
import ExchangeInitiationDocumentsTab
    from "./components/exchange-initiation-documents-tab/ExchangeInitiationDocumentsTab.tsx";
import RecipientRoleSelector
    from "./components/exchange-initiation-recipients-tab/recipient-role-selector/RecipientRoleSelector.tsx";
import {ExchangeShareRoleName} from "../../services/types/roles.ts";

Element.prototype.scrollTo = vi.fn();

const mediaMock = vi.hoisted(() => ({
    isMobile: false,
}));

vi.mock("../../utils/useMediaQuery.ts", () => ({
    useIsMobile: () => mediaMock.isMobile,
}));

vi.mock("./components/document-library-picker/DocumentLibraryPicker.tsx", () => ({
    default: () => <div id={"test-document-library-picker"}>Document library picker</div>,
}));

beforeEach(() =>
{
    mediaMock.isMobile = false;
});

afterEach(cleanup);

describe("Exchange initiation tier UI gates", () =>
{
    it("hides the blueprint entry point when the active plan cannot use blueprints", () =>
    {
        render(
            <ExchangeInitiationDialogTrigger
                onRequestingDocumentsChange={vi.fn()}
                onChooseBlueprint={vi.fn()}
                canUseBlueprints={false}
            />,
        );

        fireEvent.click(document.querySelector("#exchange-initiation-trigger") as HTMLElement);

        expect(screen.getByText("Request Documents")).toBeTruthy();
        expect(screen.getByText("Send Documents")).toBeTruthy();
        expect(screen.queryByText("From Blueprint")).toBeNull();
    });

    it("shows the blueprint entry point when the active plan includes blueprints", () =>
    {
        render(
            <ExchangeInitiationDialogTrigger
                onRequestingDocumentsChange={vi.fn()}
                onChooseBlueprint={vi.fn()}
                canUseBlueprints={true}
            />,
        );

        fireEvent.click(document.querySelector("#exchange-initiation-trigger") as HTMLElement);

        expect(screen.getByText("From Blueprint")).toBeTruthy();
    });

    it("shows Save as Blueprint only when blueprint management is available", () =>
    {
        const {rerender} = render(
            <ExchangeInitiationDialogTitleSection
                exchangeInitiatedSuccessfully={false}
                choosingBlueprint={false}
                requestingDocuments={true}
                selectedTab={"recipients-tab"}
                showFieldsTab={false}
                onTabSelect={vi.fn()}
            />,
        );

        expect(screen.queryByText("Save as Blueprint")).toBeNull();

        rerender(
            <ExchangeInitiationDialogTitleSection
                exchangeInitiatedSuccessfully={false}
                choosingBlueprint={false}
                requestingDocuments={true}
                selectedTab={"recipients-tab"}
                showFieldsTab={false}
                onTabSelect={vi.fn()}
                onSaveAsBlueprint={vi.fn()}
            />,
        );

        expect(screen.getByText("Save as Blueprint")).toBeTruthy();
    });

    it("adds the Business Fields tab only for business-field capable sessions", () =>
    {
        const {rerender} = render(
            <ExchangeInitiationDialogTitleSection
                exchangeInitiatedSuccessfully={false}
                choosingBlueprint={false}
                requestingDocuments={true}
                selectedTab={"recipients-tab"}
                showFieldsTab={false}
                onTabSelect={vi.fn()}
            />,
        );

        expect(screen.queryByText("Business Fields")).toBeNull();

        rerender(
            <ExchangeInitiationDialogTitleSection
                exchangeInitiatedSuccessfully={false}
                choosingBlueprint={false}
                requestingDocuments={true}
                selectedTab={"recipients-tab"}
                showFieldsTab={true}
                onTabSelect={vi.fn()}
            />,
        );

        expect(screen.getAllByText("Business Fields").length).toBeGreaterThan(0);
    });

    it("keeps mobile tabs icon-only while preserving accessible labels", () =>
    {
        mediaMock.isMobile = true;

        render(
            <ExchangeInitiationDialogTitleSection
                exchangeInitiatedSuccessfully={false}
                choosingBlueprint={false}
                requestingDocuments={true}
                selectedTab={"documents-tab"}
                showFieldsTab={true}
                onTabSelect={vi.fn()}
            />,
        );

        expect(screen.getByRole("tab", {name: "Business Fields"})).toBeTruthy();
        expect(screen.getByTitle("Business Fields")).toBeTruthy();
        expect(screen.getByText("Documents")).toBeTruthy();
    });

    it("hides document-library picking from tiers without document library access", () =>
    {
        const {rerender} = render(
            <ExchangeInitiationDocumentsTab
                documents={[]}
                onDocumentNameChange={vi.fn()}
                onDocumentTypeChange={vi.fn()}
                onRestrictDocumentTypeChange={vi.fn()}
                onDeleteDocument={vi.fn()}
                onRequiredChange={vi.fn()}
                onUnlink={vi.fn()}
                addNewDocument={vi.fn()}
                addLibraryDocument={vi.fn()}
                canUseDocumentLibrary={false}
            />,
        );

        expect(screen.getByText("Add Document")).toBeTruthy();
        expect(screen.queryByText("Pick from Library")).toBeNull();

        rerender(
            <ExchangeInitiationDocumentsTab
                documents={[]}
                onDocumentNameChange={vi.fn()}
                onDocumentTypeChange={vi.fn()}
                onRestrictDocumentTypeChange={vi.fn()}
                onDeleteDocument={vi.fn()}
                onRequiredChange={vi.fn()}
                onUnlink={vi.fn()}
                addNewDocument={vi.fn()}
                addLibraryDocument={vi.fn()}
                canUseDocumentLibrary={true}
            />,
        );

        expect(screen.getByText("Pick from Library")).toBeTruthy();
    });

    it("renders an upgrade message instead of advanced recipient constraints when blocked", () =>
    {
        render(
            <RecipientRoleSelector
                recipientRole={ExchangeShareRoleName.VIEWER}
                setRecipientRole={vi.fn()}
                recipientConstraints={{watermark: true}}
                setRecipientConstraints={vi.fn()}
                allowAdvancedAccessControls={false}
            />,
        );

        expect(screen.getByText("Advanced recipient constraints require Personal or Business.")).toBeTruthy();
        expect(document.querySelector("#exchange-recipient-constraints")).toBeNull();
    });

    it("shows advanced recipient constraints when the tier allows them", () =>
    {
        render(
            <RecipientRoleSelector
                recipientRole={ExchangeShareRoleName.VIEWER}
                setRecipientRole={vi.fn()}
                recipientConstraints={{watermark: true}}
                setRecipientConstraints={vi.fn()}
                allowAdvancedAccessControls={true}
            />,
        );

        expect(document.querySelector("#exchange-recipient-constraints")).toBeTruthy();
        expect(screen.queryByText("Advanced recipient constraints require Personal or Business.")).toBeNull();
    });
});
