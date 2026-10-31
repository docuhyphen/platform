/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestAuthorActionsDto,
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestState,
} from "../../../models/models.tsx";
import {authorActionPermissions} from "../author-workspace-content/authorActionPermissions.ts";
import {everyAuthorAction, noAuthorAction} from "../testing/authorActions.ts";
import RequestLifecycleActions from "./RequestLifecycleActions.tsx";

const requestIn = (state: InformationRequestState, kind: InformationRequestExecutionStandingKind): InformationRequestDto => ({
    id: "request-a",
    exchangeId: "exchange-a",
    templateVersionId: "version-a",
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    ownerOrganizationId: "org-a",
    state,
    gatesExchangeClosure: false,
    aggregateRevision: 1,
    requestETag: "\"request-a-1\"",
    executionStanding: {kind},
    conditionEvaluations: [],
    createdAt: Date.parse("2026-10-01T08:00:00Z"),
    updatedAt: Date.parse("2026-10-01T08:00:00Z"),
});

const renderActions = (
    request: InformationRequestDto,
    onIssue = vi.fn(),
    actions: InformationRequestAuthorActionsDto = everyAuthorAction,
) => render(
    <RequestLifecycleActions request={request}
                             permissions={authorActionPermissions(request.state, request.executionStanding, actions).lifecycle}
                             busy={false}
                             onIssue={onIssue}
                             onCancel={vi.fn()}
                             onSupersede={vi.fn()}/>,
);

const enabled = (name: string): boolean => {
    const button = screen.queryByRole("button", {name});
    return button !== null && !button.hasAttribute("disabled");
};

describe("RequestLifecycleActions", () => {
    afterEach(cleanup);

    it("issues, supersedes or cancels a draft whose owner may start new work", () => {
        const onIssue = vi.fn();
        renderActions(requestIn(InformationRequestState.DRAFT, InformationRequestExecutionStandingKind.ACTIVE), onIssue);

        fireEvent.click(screen.getByRole("button", {name: "Issue"}));

        expect(onIssue).toHaveBeenCalledTimes(1);
        expect(enabled("Supersede")).toBe(true);
        expect(enabled("Cancel request")).toBe(true);
    });

    it("offers no Supersede on a draft the server does not let the owner supersede", () => {
        renderActions(
            requestIn(InformationRequestState.DRAFT, InformationRequestExecutionStandingKind.ACTIVE),
            vi.fn(),
            {...everyAuthorAction, supersede: false},
        );

        expect(enabled("Issue")).toBe(true);
        expect(enabled("Cancel request")).toBe(true);
        expect(screen.queryByRole("button", {name: "Supersede"})).toBeNull();
    });

    it.each([
        InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED,
    ])("offers no action on a draft whose standing is %s", kind => {
        renderActions(requestIn(InformationRequestState.DRAFT, kind));

        expect(screen.queryByRole("button", {name: "Issue"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Supersede"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Cancel request"})).toBeNull();
        expect(screen.queryByRole("group", {name: "Request actions"})).toBeNull();
    });

    it("offers only cancellation on a draft whose owner may start no new work", () => {
        renderActions(requestIn(InformationRequestState.DRAFT, InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE));

        expect(screen.queryByRole("button", {name: "Issue"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Supersede"})).toBeNull();
        expect(enabled("Cancel request")).toBe(true);
    });

    it.each([
        InformationRequestExecutionStandingKind.ACTIVE,
        InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE,
        InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED,
    ])("lets the owner supersede or cancel an issued request whose standing is %s", kind => {
        renderActions(requestIn(InformationRequestState.IN_PROGRESS, kind));

        expect(enabled("Supersede")).toBe(true);
        expect(enabled("Cancel request")).toBe(true);
        expect(screen.queryByRole("button", {name: "Issue"})).toBeNull();
    });

    it.each([InformationRequestState.DRAFT, InformationRequestState.IN_PROGRESS])(
        "offers no action on a %s request the server lets the owner neither issue nor end, as after its Exchange is deleted",
        state => {
            renderActions(requestIn(state, InformationRequestExecutionStandingKind.ACTIVE), vi.fn(), noAuthorAction);

            expect(screen.queryByRole("group", {name: "Request actions"})).toBeNull();
            expect(screen.queryByRole("button", {name: "Issue"})).toBeNull();
            expect(screen.queryByRole("button", {name: "Supersede"})).toBeNull();
            expect(screen.queryByRole("button", {name: "Cancel request"})).toBeNull();
        },
    );

    it("offers only the end the server still permits", () => {
        renderActions(
            requestIn(InformationRequestState.IN_PROGRESS, InformationRequestExecutionStandingKind.ACTIVE),
            vi.fn(),
            {...noAuthorAction, cancel: true},
        );

        expect(enabled("Cancel request")).toBe(true);
        expect(screen.queryByRole("button", {name: "Supersede"})).toBeNull();
    });

    it("offers no way to end an issued request while its changes are paused", () => {
        renderActions(requestIn(InformationRequestState.IN_PROGRESS, InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED));

        expect(screen.queryByRole("button", {name: "Supersede"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Cancel request"})).toBeNull();
    });
});
