import {describe, expect, it} from "vitest";
import {InformationRequestLineageViewDto, InformationRequestRecurrenceUnit} from "../../../models/models.tsx";
import {nextOccurrenceReady} from "./followUpAvailability.ts";

const now = Date.parse("2026-10-24T12:00:00Z");

const recurring = (change: Partial<InformationRequestLineageViewDto>): InformationRequestLineageViewDto => ({
    informationRequestId: "request-a",
    successors: [],
    recurrence: {
        id: "recurrence-a",
        originRequestId: "request-a",
        intervalUnit: InformationRequestRecurrenceUnit.WEEK,
        intervalCount: 1,
        firstDueAt: Date.parse("2026-10-01T08:00:00Z"),
        createdAt: Date.parse("2026-09-30T08:00:00Z"),
    },
    nextOccurrenceDueAt: null,
    everyOccurrenceCreated: false,
    ...change,
});

describe("nextOccurrenceReady", () => {
    it("is ready once the next request's due time has come", () => {
        expect(nextOccurrenceReady(recurring({nextOccurrenceDueAt: Date.parse("2026-10-22T08:00:00Z")}), now)).toBe(true);
        expect(nextOccurrenceReady(recurring({nextOccurrenceDueAt: now}), now)).toBe(true);
    });

    it("is not ready before the next request is due", () => {
        expect(nextOccurrenceReady(recurring({nextOccurrenceDueAt: Date.parse("2026-10-29T08:00:00Z")}), now)).toBe(false);
    });

    it("is never ready once every scheduled request has been created", () => {
        expect(nextOccurrenceReady(recurring({everyOccurrenceCreated: true}), now)).toBe(false);
    });

    it("leaves the answer to the server when the due time is withheld", () => {
        expect(nextOccurrenceReady(recurring({}), now)).toBe(true);
    });

    it("is never ready without a recurrence", () => {
        expect(nextOccurrenceReady(null, now)).toBe(false);
        expect(nextOccurrenceReady(recurring({recurrence: null}), now)).toBe(false);
    });
});
