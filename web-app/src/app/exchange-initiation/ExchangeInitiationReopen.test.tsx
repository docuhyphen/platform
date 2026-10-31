/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import ExchangeInitiation from "./ExchangeInitiation.tsx";
import {initiateExchange} from "../../services/exchangeApi.ts";
import {publishRecreateRejectedExchange} from "../observable/exchangeObservables.ts";
import {BlueprintDefinitionSummaryDto} from "../models/models.tsx";
import {ExchangeNewMainRecipient} from "./components/exchange-initiation-recipients-tab/new-recipient/NewRecipient.tsx";

const mocks = vi.hoisted(() => ({
    navigate: vi.fn(),
}));

const blueprintB: BlueprintDefinitionSummaryDto = {
    id: "blueprint-b",
    name: "Blueprint B",
    scope: "ORG",
    isActive: true,
    isPublished: true,
    isTemplate: false,
    generalTags: [],
    configJson: JSON.stringify({name: "Exchange from Blueprint B"}),
    informationRequestTemplateVersionId: "template-version-b",
    exchangeDocuments: [{title: "Statement"}],
    participants: [],
    createdAt: Date.parse("2026-10-01T00:00:00Z"),
    updatedAt: Date.parse("2026-10-01T00:00:00Z"),
};

interface BlueprintPickerStubProps {
    onSelect: (blueprint: BlueprintDefinitionSummaryDto) => void;
}

interface RecipientsTabStubProps {
    setNewRecipient: (recipient: ExchangeNewMainRecipient) => void;
}

interface CreatedRequestStubProps {
    onManage: (requestId: string) => void;
}

vi.mock("react-router-dom", () => ({
    useNavigate: () => mocks.navigate,
}));

vi.mock("../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        appUser: {id: "owner-a", email: "owner@process.test"},
        refreshCurrentSession: () => Promise.resolve(),
    }),
}));

vi.mock("../../hooks/subscription/usePlanFeature.ts", () => ({
    usePlanFeature: () => ({
        isKnown: true,
        isIncluded: true,
        isEnforced: true,
        isDiscoverable: true,
        isAvailable: true,
        upgradePlanCode: null,
    }),
}));

vi.mock("../../utils/useMediaQuery.ts", () => ({
    useIsMobile: () => false,
}));

vi.mock("../../services/exchangeApi.ts", () => ({
    initiateExchange: vi.fn(),
}));

vi.mock("../../services/variableService.ts", () => ({
    getAvailableVariables: () => Promise.resolve({system: [], sequences: [], org: [], personal: []}),
}));

vi.mock("../../services/fieldsService.ts", () => ({
    getResolvedSchema: () => Promise.resolve({fields: []}),
    listSchemas: () => Promise.resolve([]),
}));

vi.mock("../../services/organizationApi.ts", () => ({
    fetchMyOrganizationUsers: () => Promise.resolve([]),
}));

vi.mock("./startedInformationRequest.ts", () => ({
    findStartedInformationRequest: () => Promise.resolve({id: "request-b", title: "Request from Blueprint B"}),
}));

vi.mock("./components/blueprint-picker/BlueprintPicker.tsx", () => ({
    default: ({onSelect}: BlueprintPickerStubProps) => (
        <button id={"test-choose-blueprint"}
                onClick={() => onSelect(blueprintB)}>
            Choose Blueprint B
        </button>
    ),
}));

vi.mock("./components/exchange-initiation-recipients-tab/ExchangeInitiationRecipientsTab.tsx", () => ({
    default: ({setNewRecipient}: RecipientsTabStubProps) => (
        <button id={"test-use-recipient"}
                onClick={() => setNewRecipient({
                    email: "recipient@process.test",
                    firstName: "Rowan",
                    lastName: "Recipient",
                })}>
            Use test recipient
        </button>
    ),
}));

vi.mock("./components/created-information-request-summary/CreatedInformationRequestSummary.tsx", () => ({
    default: ({onManage}: CreatedRequestStubProps) => (
        <button id={"test-manage-request"}
                onClick={() => onManage("request-b")}>
            Manage test request
        </button>
    ),
}));

vi.mock("./components/document-library-picker/DocumentLibraryPicker.tsx", () => ({
    default: () => <div id={"test-document-library-picker"}>Document library picker</div>,
}));

Element.prototype.scrollTo = vi.fn();

const openFromMenu = (item: string) => {
    fireEvent.click(document.querySelector("#exchange-initiation-trigger") as HTMLElement);
    fireEvent.click(screen.getByText(item));
};

const startFromBlueprint = async () => {
    openFromMenu("From Blueprint");
    fireEvent.click(await screen.findByText("Choose Blueprint B"));
    expect(await screen.findByText("Blueprint B")).toBeTruthy();
    fireEvent.click(screen.getByText("Use test recipient"));
    fireEvent.click(document.querySelector("#exchange-initiate-btn") as HTMLElement);
    expect(await screen.findByText("Exchange started successfully")).toBeTruthy();
};

const startAgainByHand = async () => {
    fireEvent.click(await screen.findByText("Use test recipient"));
    fireEvent.click(document.querySelector("#details") as HTMLElement);
    fireEvent.change(
        screen.getByPlaceholderText("Exchange name, type {{ to insert a variable"),
        {target: {value: "Second Exchange"}},
    );
    fireEvent.click(document.querySelector("#documents") as HTMLElement);
    fireEvent.click(screen.getByText("Add Document"));
    fireEvent.change(
        screen.getByPlaceholderText("Document name, type {{ to insert a variable"),
        {target: {value: "Supporting record"}},
    );
    fireEvent.click(document.querySelector("#exchange-initiate-btn") as HTMLElement);
    await waitFor(() => expect(initiateExchange).toHaveBeenCalledTimes(2));
};

beforeEach(() => {
    vi.mocked(initiateExchange).mockReset().mockResolvedValue({id: "exchange-1"});
    mocks.navigate.mockReset();
});

afterEach(cleanup);

describe("Reopening the Exchange dialog after a Blueprint start", () => {
    it("starts from an empty form after Manage Information Request and sends no Blueprint", async () => {
        render(<ExchangeInitiation/>);
        await startFromBlueprint();

        fireEvent.click(screen.getByText("Manage test request"));
        expect(mocks.navigate).toHaveBeenCalledTimes(1);

        openFromMenu("Request Documents");

        expect(await screen.findByText("Use test recipient")).toBeTruthy();
        expect(screen.queryByText("Blueprint B")).toBeNull();
        await startAgainByHand();
        const second = vi.mocked(initiateExchange).mock.calls[1][0];
        expect(second.blueprintDefinitionId).toBeUndefined();
        expect(second.name).toBe("Second Exchange");
        expect(second.exchangeDocuments?.map(document => document.title)).toEqual(["Supporting record"]);
    });

    it("starts from an empty form after View Exchange", async () => {
        render(<ExchangeInitiation/>);
        await startFromBlueprint();

        fireEvent.click(document.querySelector("#exchange-view-exchange-btn") as HTMLElement);
        expect(mocks.navigate).toHaveBeenCalledTimes(1);

        openFromMenu("Send Documents");

        expect(await screen.findByText("Use test recipient")).toBeTruthy();
        expect(screen.queryByText("Blueprint B")).toBeNull();
        await startAgainByHand();
        expect(vi.mocked(initiateExchange).mock.calls[1][0].blueprintDefinitionId).toBeUndefined();
    });

    it("recreating a rejected Exchange drops a Blueprint chosen earlier", async () => {
        render(<ExchangeInitiation/>);
        openFromMenu("From Blueprint");
        fireEvent.click(await screen.findByText("Choose Blueprint B"));
        expect(await screen.findByText("Blueprint B")).toBeTruthy();

        act(() => publishRecreateRejectedExchange({
            name: "Recreated Exchange",
            description: "",
            initialShareMessage: "",
            requestRecipientSignIn: true,
            allowDocumentAddition: true,
            allowDocumentDeletion: false,
            allowDocumentDownload: false,
            allowDocumentUpdate: false,
            allowDocumentUpload: true,
            exchangeDocuments: [{title: "Supporting record", restrictType: false}],
            recipientEmail: "recipient@process.test",
            recipientFirstName: "Rowan",
            recipientLastName: "Recipient",
        }));

        await waitFor(() => expect(screen.queryByText("Blueprint B")).toBeNull());
        fireEvent.click(document.querySelector("#exchange-initiate-btn") as HTMLElement);
        await waitFor(() => expect(initiateExchange).toHaveBeenCalledTimes(1));
        const recreated = vi.mocked(initiateExchange).mock.calls[0][0];
        expect(recreated.blueprintDefinitionId).toBeUndefined();
        expect(recreated.name).toBe("Recreated Exchange");
    });
});
