import {AppUserPublicDto, BlueprintParticipantConfig} from "../models/models.tsx";
import {ExchangeShareRoleName} from "../../services/types/roles.ts";

export const blueprintParticipantsOf = (users: AppUserPublicDto[]): BlueprintParticipantConfig[] =>
    users.map(user => ({principalId: user.id, principalKind: "APP_USER", roleName: ExchangeShareRoleName.PARTICIPANT}));

export const participantUsersOf = (
    participants: BlueprintParticipantConfig[],
    directory: AppUserPublicDto[],
): AppUserPublicDto[] =>
    participants
        .filter(participant => participant.principalKind === "APP_USER")
        .map(participant => directory.find(user => user.id === participant.principalId))
        .filter((user): user is AppUserPublicDto => user !== undefined);
