/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestAccessLinkDto,
    InformationRequestAccessLinkStatus,
    InformationRequestPartyDto,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";
import PartyRow from "./PartyRow.tsx";

const party = (trustSuspended?: boolean): InformationRequestPartyDto => ({
    id: "party-a",
    informationRequestId: "request-a",
    roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
    active: true,
    principalId: "participant-a",
    principalKind: "PARTICIPANT",
    exchangeRecipientId: "recipient-a",
    assignedAt: Date.parse("2026-09-20T08:00:00Z"),
    partyRevision: 1,
    partyETag: "\"party-a-1\"",
    label: "Counterparty desk",
    trustSuspended,
});

const EVERY_PARTY_ACTION = {change: true, reduceAccess: true, issueLinks: true};

const ACCESS_REDUCTION_ONLY = {change: false, reduceAccess: true, issueLinks: false};

const NO_NEW_LINKS = {change: true, reduceAccess: true, issueLinks: false};

const activeLink: InformationRequestAccessLinkDto = {
    shareLinkId: "link-a",
    status: InformationRequestAccessLinkStatus.ACTIVE,
    expiresAt: Date.parse("2099-10-30T12:00:00Z"),
    maxUses: 25,
    rotationCount: 0,
    partyId: "party-a",
};

const renderRow = (
    trustSuspended?: boolean,
    links: InformationRequestAccessLinkDto[] = [],
    permissions = EVERY_PARTY_ACTION,
) => render(
    <ul>
        <PartyRow party={party(trustSuspended)}
                  links={links}
                  busy={false}
                  permissions={permissions}
                  onIssueLink={vi.fn()}
                  onResendLink={vi.fn()}
                  onRevokeLink={vi.fn()}
                  onReassign={vi.fn()}
                  onRemove={vi.fn()}/>
    </ul>,
);

describe("PartyRow", () =>
{
    afterEach(cleanup);

    it("offers to reassign an acting party and never a subject", () => {
        const onReassign = vi.fn();
        const {rerender} = render(
            <ul>
                <PartyRow party={party()}
                          links={[]}
                          busy={false}
                          permissions={EVERY_PARTY_ACTION}
                          onIssueLink={vi.fn()}
                          onResendLink={vi.fn()}
                          onRevokeLink={vi.fn()}
                          onReassign={onReassign}
                          onRemove={vi.fn()}/>
            </ul>,
        );

        fireEvent.click(screen.getByRole("button", {name: "Reassign Counterparty desk"}));
        expect(onReassign).toHaveBeenCalledWith(party());

        rerender(
            <ul>
                <PartyRow party={{
                    ...party(),
                    roleKey: InformationRequestShareRoleKey.SUBJECT,
                    principalId: undefined,
                    subjectIdentityRefId: "subject-a"
                }}
                          links={[]}
                          busy={false}
                          permissions={EVERY_PARTY_ACTION}
                          onIssueLink={vi.fn()}
                          onResendLink={vi.fn()}
                          onRevokeLink={vi.fn()}
                          onReassign={onReassign}
                          onRemove={vi.fn()}/>
            </ul>,
        );
        expect(screen.queryByRole("button", {name: "Reassign Counterparty desk"})).toBeNull();
        expect(screen.getByRole("button", {name: "Remove Counterparty desk"})).toBeTruthy();
    });

    it("marks a party whose trusted relationship is suspended and says what continues", () =>
    {
        renderRow(true);

        expect(screen.getByText("Trusted relationship suspended")).toBeTruthy();
        expect(screen.getByText(
            "This party keeps answering what was already issued. New trusted assignments are paused until the relationship is restored.",
        )).toBeTruthy();
    });

    it("says until when an active access link works", () =>
    {
        const expiresAt = Date.parse("2099-10-30T12:00:00Z");
        renderRow(false, [{
            shareLinkId: "link-a",
            status: InformationRequestAccessLinkStatus.ACTIVE,
            expiresAt,
            maxUses: 25,
            rotationCount: 0,
            partyId: "party-a",
        }]);

        const expected = `Contributor. Access link active until ${formatInformationRequestTime(expiresAt)}`.replace(/\s+/g, " ");
        expect(screen.getByText(expected)).toBeTruthy();
    });

    it.each([
        ["past its expiry", InformationRequestAccessLinkStatus.ACTIVE],
        ["marked expired", InformationRequestAccessLinkStatus.EXPIRED],
    ])("shows a link %s as expired and offers to revoke it instead of resending it", (_case, status) => {
        const expiresAt = Date.parse("2020-01-01T08:00:00Z");
        renderRow(false, [{
            shareLinkId: "link-a",
            status,
            expiresAt,
            maxUses: 25,
            rotationCount: 0,
            partyId: "party-a"
        }]);

        const expected = `Contributor. Access link expired on ${formatInformationRequestTime(expiresAt)}`.replace(/\s+/g, " ");
        expect(screen.getByText(expected)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Resend link to Counterparty desk"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Create link for Counterparty desk"})).toBeNull();
        expect(screen.getByRole("button", {name: "Revoke link for Counterparty desk"})).toBeTruthy();
    });

    it("offers a new link once the expired one is revoked", () => {
        renderRow(false, [{
            shareLinkId: "link-a",
            status: InformationRequestAccessLinkStatus.REVOKED,
            expiresAt: Date.parse("2020-01-01T08:00:00Z"),
            rotationCount: 0,
            partyId: "party-a",
        }]);

        expect(screen.getByText("Contributor. No access link")).toBeTruthy();
        expect(screen.getByRole("button", {name: "Create link for Counterparty desk"})).toBeTruthy();
    });

    it("names every control of a party with a link and a trust marker", () =>
    {
        renderRow(true, [{
            shareLinkId: "link-a",
            status: InformationRequestAccessLinkStatus.ACTIVE,
            expiresAt: Date.parse("2099-10-30T12:00:00Z"),
            maxUses: 25,
            rotationCount: 1,
            partyId: "party-a",
        }]);

        expect(unnamedControls(document.body)).toEqual([]);

        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("keeps removing the party and revoking its link when only access reductions remain", () => {
        renderRow(false, [activeLink], ACCESS_REDUCTION_ONLY);

        expect(screen.getByRole("button", {name: "Remove Counterparty desk"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Revoke link for Counterparty desk"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Resend link to Counterparty desk"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Reassign Counterparty desk"})).toBeNull();
    });

    it("offers no new link when only access reductions remain", () => {
        renderRow(false, [], ACCESS_REDUCTION_ONLY);

        expect(screen.getByText("Contributor. No access link")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Create link for Counterparty desk"})).toBeNull();
        expect(screen.getByRole("button", {name: "Remove Counterparty desk"})).toBeTruthy();
    });

    it("offers no new link while the Exchange requires recipients to sign in, but still reassigns and removes the party", () => {
        renderRow(false, [], NO_NEW_LINKS);

        expect(screen.getByText("Contributor. No access link")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Create link for Counterparty desk"})).toBeNull();
        expect(screen.getByRole("button", {name: "Reassign Counterparty desk"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Remove Counterparty desk"})).toBeTruthy();
    });

    it("keeps revoking an existing link while the Exchange requires recipients to sign in, but offers no resend", () => {
        renderRow(false, [activeLink], NO_NEW_LINKS);

        expect(screen.getByRole("button", {name: "Revoke link for Counterparty desk"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Resend link to Counterparty desk"})).toBeNull();
    });

    it("offers no party action once nothing on the request can change", () => {
        renderRow(false, [activeLink], {change: false, reduceAccess: false, issueLinks: false});

        expect(document.getElementById("information-request-party-party-a-actions")).toBeNull();
    });

    it("shows no trust marker for a party in good standing", () =>
    {
        renderRow();

        expect(screen.queryByText("Trusted relationship suspended")).toBeNull();
    });

    it("shows account access without offering a bootstrap link to a signed-in user", () =>
    {
        render(
            <ul>
                <PartyRow party={{...party(), principalKind: "USER"}}
                          links={[]}
                          busy={false}
                          permissions={EVERY_PARTY_ACTION}
                          onIssueLink={vi.fn()}
                          onResendLink={vi.fn()}
                          onRevokeLink={vi.fn()}
                          onReassign={vi.fn()}
                          onRemove={vi.fn()}/>
            </ul>,
        );

        expect(screen.getByText("Contributor. Signs in")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Create link for Counterparty desk"})).toBeNull();
    });

    it("shows a participant who linked an account as signing in and offers no new access link", () => {
        render(
            <ul>
                <PartyRow party={{...party(), accountLinked: true}}
                          links={[]}
                          busy={false}
                          permissions={EVERY_PARTY_ACTION}
                          onIssueLink={vi.fn()}
                          onResendLink={vi.fn()}
                          onRevokeLink={vi.fn()}
                          onReassign={vi.fn()}
                          onRemove={vi.fn()}/>
            </ul>,
        );

        expect(screen.getByText("Contributor. Signs in with a linked account")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Create link for Counterparty desk"})).toBeNull();
        expect(screen.getByRole("button", {name: "Reassign Counterparty desk"})).toBeTruthy();
    });
});
