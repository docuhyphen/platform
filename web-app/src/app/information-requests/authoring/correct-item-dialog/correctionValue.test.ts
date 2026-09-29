import {describe, expect, it} from "vitest";
import {correctedValueOf} from "./correctionValue.ts";

describe("correctedValueOf", () =>
{
    it("keeps the submitted value's type", () =>
    {
        expect(correctedValueOf("Mornings", " Afternoons ")).toEqual({value: "Afternoons"});
        expect(correctedValueOf(4, "5.5")).toEqual({value: 5.5});
        expect(correctedValueOf(true, "No")).toEqual({value: false});
        expect(correctedValueOf(["first"], "first, second")).toEqual({value: ["first", "second"]});
    });

    it("names a value that does not fit and sends nothing for an empty one", () =>
    {
        expect(correctedValueOf(4, "four")).toEqual({problem: "Enter a number."});
        expect(correctedValueOf(false, "maybe")).toEqual({problem: "Enter yes or no."});
        expect(correctedValueOf("Mornings", "  ")).toEqual({});
    });
});
