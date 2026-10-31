import {describe, expect, it} from "vitest";
import {AppUserPublicDto, BlueprintParticipantConfig} from "../models/models.tsx";
import {ExchangeShareRoleName} from "../../services/types/roles.ts";
import {blueprintParticipantsOf, participantUsersOf} from "./blueprintParticipants.ts";

const user = (id: string): AppUserPublicDto => ({
    id,
    email: `${id}@example.test`,
    isActive: true,
    appRoles: [],
    organizationRoles: [],
});

describe("blueprintParticipants", () => {
    it("records chosen participants in the shape a Blueprint stores", () => {
        expect(blueprintParticipantsOf([user("user-a"), user("user-b")])).toEqual([
            {principalId: "user-a", principalKind: "APP_USER", roleName: ExchangeShareRoleName.PARTICIPANT},
            {principalId: "user-b", principalKind: "APP_USER", roleName: ExchangeShareRoleName.PARTICIPANT},
        ]);
    });

    it("turns a Blueprint's participant defaults back into the users an Exchange is started with", () => {
        const defaults: BlueprintParticipantConfig[] = [
            {principalId: "user-b", principalKind: "APP_USER", roleName: ExchangeShareRoleName.PARTICIPANT},
            {principalId: "group-a", principalKind: "PRINCIPAL_GROUP", roleName: ExchangeShareRoleName.PARTICIPANT},
            {principalId: "user-gone", principalKind: "APP_USER", roleName: ExchangeShareRoleName.PARTICIPANT},
        ];

        expect(participantUsersOf(defaults, [user("user-a"), user("user-b")]).map(chosen => chosen.id)).toEqual(["user-b"]);
    });

    it("keeps a participant through saving and applying a Blueprint", () => {
        const chosen = [user("user-a")];

        expect(participantUsersOf(blueprintParticipantsOf(chosen), chosen)).toEqual(chosen);
    });
});
