/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestAccessLinkDto,
    InformationRequestAccessLinkStatus,
    InformationRequestPartyDto,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
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
    assignedAt: "2026-09-20T08:00:00Z",
    partyRevision: 1,
    partyETag: "\"party-a-1\"",
    label: "Counterparty desk",
    trustSuspended,
});

const renderRow = (trustSuspended?: boolean, links: InformationRequestAccessLinkDto[] = []) => render(
    <ul>
        <PartyRow party={party(trustSuspended)}
                  links={links}
                  busy={false}
                  editable={true}
                  onIssueLink={vi.fn()}
                  onResendLink={vi.fn()}
                  onRevokeLink={vi.fn()}
                  onRemove={vi.fn()}/>
    </ul>,
);

describe("PartyRow", () =>
{
    afterEach(cleanup);

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
        const expiresAt = "2026-10-30T12:00:00Z";
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

    it("names every control of a party with a link and a trust marker", () =>
    {
        renderRow(true, [{
            shareLinkId: "link-a",
            status: InformationRequestAccessLinkStatus.ACTIVE,
            expiresAt: "2026-10-30T12:00:00Z",
            maxUses: 25,
            rotationCount: 1,
            partyId: "party-a",
        }]);

        expect(unnamedControls(document.body)).toEqual([]);
    });

    it("shows no trust marker for a party in good standing", () =>
    {
        renderRow();

        expect(screen.queryByText("Trusted relationship suspended")).toBeNull();
    });
});
