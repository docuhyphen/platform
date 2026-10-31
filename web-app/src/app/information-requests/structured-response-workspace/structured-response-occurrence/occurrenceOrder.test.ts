import {describe, expect, it} from "vitest";
import {movedOccurrenceOrder} from "./occurrenceOrder.ts";

describe("movedOccurrenceOrder", () => {
    it("swaps an entry with the one just above or below it", () => {
        expect(movedOccurrenceOrder(["first", "second", "third"], "third", -1)).toEqual(["first", "third", "second"]);
        expect(movedOccurrenceOrder(["first", "second", "third"], "first", 1)).toEqual(["second", "first", "third"]);
        expect(movedOccurrenceOrder(["first", "second", "third"], "second", -1)).toEqual(["second", "first", "third"]);
    });

    it("keeps the order when an entry cannot move further or is not in the list", () => {
        expect(movedOccurrenceOrder(["first", "second"], "first", -1)).toEqual(["first", "second"]);
        expect(movedOccurrenceOrder(["first", "second"], "second", 1)).toEqual(["first", "second"]);
        expect(movedOccurrenceOrder(["first", "second"], "other", 1)).toEqual(["first", "second"]);
    });
});
