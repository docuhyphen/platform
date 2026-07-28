import {describe, expect, it} from "vitest";
import {Capability} from "../../app/models/models.tsx";
import {canAdministerOrganization} from "./roles.ts";

describe("canAdministerOrganization", () =>
{
    it("does not treat APP_ADMIN as organization administration", () =>
    {
        expect(canAdministerOrganization([Capability.APP_ADMIN])).toBe(false);
    });

    it("requires ORG_POLICY_MANAGE", () =>
    {
        expect(canAdministerOrganization([Capability.ORG_POLICY_MANAGE])).toBe(true);
    });
});
