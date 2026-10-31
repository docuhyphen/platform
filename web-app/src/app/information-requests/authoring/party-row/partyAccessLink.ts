import {
    InformationRequestAccessLinkDto,
    InformationRequestAccessLinkStatus,
    InformationRequestPartyDto,
} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";

export interface PartyAccessLink {
    link: InformationRequestAccessLinkDto;
    expired: boolean;
}

const hasExpired = (link: InformationRequestAccessLinkDto, now: number): boolean =>
    link.status === InformationRequestAccessLinkStatus.EXPIRED ||
    (link.expiresAt !== undefined && link.expiresAt !== null && link.expiresAt <= now);

export const currentAccessLink = (
    links: InformationRequestAccessLinkDto[],
    now: number = Date.now(),
): PartyAccessLink | undefined => {
    const unrevoked = links.filter(link => link.status !== InformationRequestAccessLinkStatus.REVOKED);
    const usable = unrevoked.find(link => !hasExpired(link, now));
    if (usable) return {link: usable, expired: false};
    const expired = unrevoked.find(link => hasExpired(link, now));
    return expired ? {link: expired, expired: true} : undefined;
};

const linkStatus = (current?: PartyAccessLink): string => {
    if (!current) return "No access link";
    const {link, expired} = current;
    if (expired) {
        return link.expiresAt ? `Access link expired on ${formatInformationRequestTime(link.expiresAt)}` : "Access link expired";
    }
    const active = link.expiresAt
        ? `Access link active until ${formatInformationRequestTime(link.expiresAt)}`
        : "Access link active";
    return link.rotationCount > 0 ? `${active}, resent ${link.rotationCount} ${link.rotationCount === 1 ? "time" : "times"}` : active;
};

export const partyAccessStatus = (party: InformationRequestPartyDto, current?: PartyAccessLink): string => {
    if (party.principalKind === "USER") return "Signs in";
    if (party.accountLinked) return "Signs in with a linked account";
    if (party.principalKind === "PRINCIPAL_GROUP") return "Group access";
    return linkStatus(current);
};
