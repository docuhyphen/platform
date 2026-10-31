import {describe, expect, it} from "vitest";
import {informationRequestRefusalMessage} from "./informationRequestRefusal.ts";

const FALLBACK = "The records could not be loaded.";

describe("informationRequestRefusalMessage", () => {
    it("states a readable refusal as it was sent", () => {
        expect(informationRequestRefusalMessage("The request is closed", FALLBACK)).toBe("The request is closed");
        expect(informationRequestRefusalMessage({
            errorMessage: "Access denied",
            reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
        }, FALLBACK))
            .toBe("Access denied");
        expect(informationRequestRefusalMessage(new Error("Network Error"), FALLBACK)).toBe("Network Error");
    });

    it.each([
        ["an empty body", ""],
        ["a blank body", "   "],
        ["an HTML error page", "<html><body><h1>502 Bad Gateway</h1></body></html>"],
        ["an HTML document with leading space", "\n  <!DOCTYPE html><html><title>504 Gateway Time-out</title></html>"],
    ])("states the fallback for %s", (_, body) => {
        expect(informationRequestRefusalMessage(body, FALLBACK)).toBe(FALLBACK);
    });

    it("states the fallback when the stated message is markup", () => {
        expect(informationRequestRefusalMessage({errorMessage: "<html><body>Service unavailable</body></html>"}, FALLBACK)).toBe(FALLBACK);
        expect(informationRequestRefusalMessage(new Error("<html><body>Service unavailable</body></html>"), FALLBACK)).toBe(FALLBACK);
    });
});
