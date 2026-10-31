import {describe, expect, it} from "vitest";
import {
    InformationRequestAccessLinkDto,
    InformationRequestAccessLinkStatus,
    InformationRequestPartyDto,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {currentAccessLink, partyAccessStatus} from "./partyAccessLink.ts";

const now = Date.parse("2026-10-15T09:00:00Z");
const hour = 60 * 60 * 1000;

const link = (shareLinkId: string, status: InformationRequestAccessLinkStatus, expiresAt?: number | null): InformationRequestAccessLinkDto => ({
    shareLinkId,
    status,
    expiresAt,
    rotationCount: 0,
    partyId: "party-a",
});

const participant: InformationRequestPartyDto = {
    id: "party-a",
    informationRequestId: "request-a",
    roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
    active: true,
    principalId: "participant-a",
    principalKind: "PARTICIPANT",
    assignedAt: Date.parse("2026-10-01T08:00:00Z"),
    partyRevision: 1,
    partyETag: "\"party-a-1\"",
    label: "Counterparty desk",
};

describe("currentAccessLink", () => {
    it("states one resend in the singular", () => {
        const current = currentAccessLink([{
            ...link("link-a", InformationRequestAccessLinkStatus.ACTIVE),
            rotationCount: 1
        }], now);
        expect(partyAccessStatus(participant, current)).toBe("Access link active, resent 1 time");
    });

    it("recognises an active link whose expiry, sent as epoch milliseconds, has passed", () => {
        const expiresAt = now - hour;

        const current = currentAccessLink([link("link-a", InformationRequestAccessLinkStatus.ACTIVE, expiresAt)], now);

        expect(current).toEqual({
            link: link("link-a", InformationRequestAccessLinkStatus.ACTIVE, expiresAt),
            expired: true
        });
        expect(partyAccessStatus(participant, current)).toBe(`Access link expired on ${formatInformationRequestTime(expiresAt)}`);
    });

    it("treats a link as expired from the exact millisecond of its expiry", () => {
        expect(currentAccessLink([link("link-a", InformationRequestAccessLinkStatus.ACTIVE, now)], now)?.expired).toBe(true);
        expect(currentAccessLink([link("link-a", InformationRequestAccessLinkStatus.ACTIVE, now + 1)], now)?.expired).toBe(false);
    });

    it("prefers a usable link over an expired one and says until when it works", () => {
        const expiresAt = now + hour;

        const current = currentAccessLink([
            link("link-a", InformationRequestAccessLinkStatus.ACTIVE, now - hour),
            link("link-b", InformationRequestAccessLinkStatus.ACTIVE, expiresAt),
        ], now);

        expect(current?.link.shareLinkId).toBe("link-b");
        expect(current?.expired).toBe(false);
        expect(partyAccessStatus(participant, current)).toBe(`Access link active until ${formatInformationRequestTime(expiresAt)}`);
    });

    it("keeps a link without an expiry active and ignores revoked links", () => {
        const current = currentAccessLink([
            link("link-a", InformationRequestAccessLinkStatus.REVOKED, now + hour),
            link("link-b", InformationRequestAccessLinkStatus.ACTIVE),
        ], now);

        expect(current?.link.shareLinkId).toBe("link-b");
        expect(partyAccessStatus(participant, current)).toBe("Access link active");
    });

    it("keeps an active link whose expiry the server sends as null active", () => {
        const current = currentAccessLink([link("link-a", InformationRequestAccessLinkStatus.ACTIVE, null)], now);

        expect(current?.expired).toBe(false);
        expect(partyAccessStatus(participant, current)).toBe("Access link active");
    });
});
