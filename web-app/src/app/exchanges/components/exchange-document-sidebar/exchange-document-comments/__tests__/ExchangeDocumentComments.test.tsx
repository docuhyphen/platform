/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {DocumentDetailedDto} from "../../../../../models/models.tsx";
import ExchangeDocumentComments from "../ExchangeDocumentComments.tsx";

const mocks = vi.hoisted(() => ({
    getComments: vi.fn(),
    addComment: vi.fn(),
    markMatchingAsRead: vi.fn(),
    realtimeHandler: undefined as undefined | ((message: {exchangeId?: string; documentId?: string}) => void),
    currentSession: {
        activeOrganizationId: "organization-1",
        availableOrganizations: [{organizationId: "organization-1", name: "Acme", roles: []}],
    } as {activeOrganizationId: string | null; availableOrganizations: Array<{organizationId: string; name: string; roles: string[]}>},
}));

vi.mock("../../../../../../services/DocumentCommentService.tsx", () => ({
    DocumentCommentService: class
    {
        getComments = mocks.getComments;
        addComment = mocks.addComment;
    },
}));

vi.mock("../../../../../../services/NotificationService.tsx", () => ({
    realtimeService: {
        on: vi.fn((_type: string, handler: typeof mocks.realtimeHandler) =>
        {
            mocks.realtimeHandler = handler;
            return vi.fn();
        }),
    },
}));

vi.mock("../../../../../../context/NotificationContext.tsx", () => ({
    useNotifications: () => ({markMatchingAsRead: mocks.markMatchingAsRead}),
}));

vi.mock("../../../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({currentSession: mocks.currentSession}),
}));

const exchangeDocument = {id: "document-1", title: "Agreement"} as DocumentDetailedDto;
const comment = {
    id: "comment-1",
    createdDate: "2026-08-05T10:00:00Z",
    text: "Check this clause",
    commentedByFirstName: "Jane",
    commentedByLastName: "Doe",
    isInternal: false,
    pageNumber: 4,
    documentVersionId: "version-1",
    documentVersion: "2",
};

beforeEach(() =>
{
    vi.stubGlobal("ResizeObserver", class
    {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();
    });
    window.localStorage.clear();
    mocks.getComments.mockReset().mockResolvedValue([comment]);
    mocks.addComment.mockReset().mockResolvedValue(comment);
    mocks.markMatchingAsRead.mockReset();
    mocks.realtimeHandler = undefined;
    mocks.currentSession.activeOrganizationId = "organization-1";
});

afterEach(() =>
{
    cleanup();
    vi.unstubAllGlobals();
});

describe("ExchangeDocumentComments", () =>
{
    it("shows page and version context and navigates to the linked page", async () =>
    {
        const onNavigateToPage = vi.fn();
        render(
            <ExchangeDocumentComments
                exchangeId={"exchange-1"}
                exchangeDocument={exchangeDocument}
                pageNumber={2}
                onNavigateToPage={onNavigateToPage}
            />
        );

        await waitFor(() => expect(document.getElementById("document-comment-comment-1-page")).toBeTruthy());
        expect(document.getElementById("document-comment-comment-1-version-badge")?.textContent).toContain("Version 2");

        fireEvent.click(document.getElementById("document-comment-comment-1-page") as HTMLElement);
        expect(onNavigateToPage).toHaveBeenCalledWith(4);
    });

    it("keeps drafts and posts them with page and version context", async () =>
    {
        const firstRender = render(
            <ExchangeDocumentComments
                exchangeId={"exchange-1"}
                exchangeDocument={exchangeDocument}
                pageNumber={3}
                documentVersionId={"version-1"}
            />
        );
        const inputId = "textarea-exchange-document-comment";
        fireEvent.change(document.getElementById(inputId) as HTMLTextAreaElement, {target: {value: "Remember this"}});
        firstRender.unmount();

        render(
            <ExchangeDocumentComments
                exchangeId={"exchange-1"}
                exchangeDocument={exchangeDocument}
                pageNumber={3}
                documentVersionId={"version-1"}
            />
        );

        await waitFor(() => expect((document.getElementById(inputId) as HTMLTextAreaElement).value).toBe("Remember this"));
        fireEvent.click(document.getElementById("exchange-document-comment-send-btn") as HTMLElement);

        await waitFor(() => expect(mocks.addComment).toHaveBeenCalledWith(
            "exchange-1",
            "document-1",
            "Remember this",
            false,
            3,
            "version-1",
        ));
        expect(window.localStorage.getItem("exchanges.comments.draft.exchange-1.document-1.version-1")).toBeNull();
    });

    it("refreshes from realtime and hides internal notes without an active organization", async () =>
    {
        mocks.currentSession.activeOrganizationId = null;
        render(
            <ExchangeDocumentComments
                exchangeId={"exchange-1"}
                exchangeDocument={exchangeDocument}
            />
        );

        await waitFor(() => expect(mocks.realtimeHandler).toBeTypeOf("function"));
        expect(document.getElementById("exchange-document-comment-internal-checkbox")).toBeNull();

        mocks.realtimeHandler?.({exchangeId: "exchange-1", documentId: "document-1"});
        await waitFor(() => expect(mocks.getComments).toHaveBeenCalledTimes(2));
    });

    it("shows internal visibility guidance only after Internal is selected", async () =>
    {
        render(
            <ExchangeDocumentComments
                exchangeId={"exchange-1"}
                exchangeDocument={exchangeDocument}
            />
        );

        const checkbox = document.getElementById("exchange-document-comment-internal-checkbox") as HTMLElement;
        await waitFor(() => expect(checkbox).toBeTruthy());
        expect(document.getElementById("exchange-document-comment-internal-help")).toBeNull();

        fireEvent.click(checkbox);

        expect(document.getElementById("exchange-document-comment-internal-help")?.textContent)
            .toContain("active members of Acme");
    });

    it("shows a load error and retries without losing the composer", async () =>
    {
        mocks.getComments.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce([]);
        render(
            <ExchangeDocumentComments
                exchangeId={"exchange-1"}
                exchangeDocument={exchangeDocument}
            />
        );

        await waitFor(() => expect(document.getElementById("exchange-document-comments-load-error")).toBeTruthy());
        expect(document.getElementById("textarea-exchange-document-comment")).toBeTruthy();

        fireEvent.click(document.getElementById("exchange-document-comments-retry") as HTMLElement);
        await waitFor(() => expect(mocks.getComments).toHaveBeenCalledTimes(2));
        await waitFor(() => expect(document.getElementById("exchange-document-comments-load-error")).toBeNull());
    });

    it("keeps a failed post as a draft and provides a retry action", async () =>
    {
        mocks.addComment.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce(comment);
        render(
            <ExchangeDocumentComments
                exchangeId={"exchange-1"}
                exchangeDocument={exchangeDocument}
                pageNumber={5}
            />
        );

        fireEvent.change(
            document.getElementById("textarea-exchange-document-comment") as HTMLTextAreaElement,
            {target: {value: "Try again"}}
        );
        fireEvent.click(document.getElementById("exchange-document-comment-send-btn") as HTMLElement);

        await waitFor(() => expect(document.getElementById("exchange-document-comment-submit-error")).toBeTruthy());
        expect(window.localStorage.getItem("exchanges.comments.draft.exchange-1.document-1.latest")).toBe("Try again");

        fireEvent.click(document.getElementById("exchange-document-comment-submit-retry") as HTMLElement);
        await waitFor(() => expect(mocks.addComment).toHaveBeenCalledTimes(2));
    });
});
