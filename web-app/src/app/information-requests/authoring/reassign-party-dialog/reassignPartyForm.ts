import {ReassignInformationRequestPartyRequest} from "../../../models/models.tsx";
import {
    holderRequestFrom,
    PartyHolder,
    PartyHolderSelection,
    TrustedPartyCandidate,
} from "../party-holder-fields/partyHolder.ts";

export const REASSIGN_HOLDERS: PartyHolder[] = ["user", "group"];

export const emptyReassignPartyForm = (): PartyHolderSelection => ({
    holder: "user",
    email: "",
    userId: "",
    groupId: "",
    trustedRecipientId: "",
});

export const reassignRequestFrom = (
    selection: PartyHolderSelection,
    trusted: TrustedPartyCandidate[],
): ReassignInformationRequestPartyRequest => holderRequestFrom(selection, trusted);
