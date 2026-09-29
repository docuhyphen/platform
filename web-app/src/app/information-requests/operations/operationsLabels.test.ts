import {describe, expect, it} from "vitest";
import {formatInformationRequestTime} from "../shared/informationRequestFormatting.ts";
import {formattedTime} from "./operationsLabels.ts";

describe("formattedTime", () =>
{
    it("states a time the way every Information Request surface does, with the viewer's time zone", () =>
    {
        expect(formattedTime("2026-09-25T08:00:00Z")).toBe(formatInformationRequestTime("2026-09-25T08:00:00Z"));
        expect(formattedTime(undefined)).toBe("Not set");
    });
});
