import {describe, expect, it} from "vitest";
import {
    InformationRequestExecutionStandingKind,
    InformationRequestStandingReason,
} from "../../models/models.tsx";
import {
    executionStandingBadge,
    executionStandingNotice,
    mayChangeUnderStanding,
    standingReasonSentence,
} from "./executionStandingText.ts";

describe("execution standing text", () =>
{
    it("says nothing about an active request", () =>
    {
        expect(executionStandingNotice({kind: InformationRequestExecutionStandingKind.ACTIVE})).toBeNull();
        expect(executionStandingBadge({kind: InformationRequestExecutionStandingKind.ACTIVE})).toBeNull();
        expect(mayChangeUnderStanding({kind: InformationRequestExecutionStandingKind.ACTIVE})).toBe(true);
    });

    it("tells a manager that issued work continues after a lapse and why", () =>
    {
        const notice = executionStandingNotice({
            kind: InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE,
            reason: InformationRequestStandingReason.TRIAL_ENDED,
        });

        expect(notice?.text).toMatch(/can still answer and review/i);
        expect(notice?.text).toContain(standingReasonSentence[InformationRequestStandingReason.TRIAL_ENDED]);
        expect(mayChangeUnderStanding({kind: InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE})).toBe(true);
    });

    it("tells anyone that a paused or stopped request stays readable, naming the reason only when it is given", () =>
    {
        const paused = executionStandingNotice({kind: InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED});
        const stopped = executionStandingNotice({kind: InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED});

        expect(paused?.text).toMatch(/paused/i);
        expect(paused?.text).toMatch(/still read/i);
        expect(paused?.text).not.toMatch(/suspended/i);
        expect(stopped?.text).toMatch(/stopped/i);
        expect(mayChangeUnderStanding({kind: InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED})).toBe(false);
        expect(mayChangeUnderStanding({kind: InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED})).toBe(false);
        expect(mayChangeUnderStanding({kind: InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE})).toBe(false);
    });

    it("labels every standing but active", () =>
    {
        const labelled = Object.values(InformationRequestExecutionStandingKind)
            .filter(kind => kind !== InformationRequestExecutionStandingKind.ACTIVE)
            .map(kind => executionStandingBadge({kind}));

        expect(labelled.every(label => typeof label === "string" && label.length > 0)).toBe(true);
    });
});
