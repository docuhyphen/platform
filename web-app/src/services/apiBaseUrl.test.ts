import {describe, expect, it} from "vitest";
import {resolveApiBaseUrl} from "./apiBaseUrl.ts";

describe("resolveApiBaseUrl", () =>
{
    it("aligns a local API hostname with the browser hostname", () =>
    {
        expect(resolveApiBaseUrl("http://192.168.3.5:8080", true, "localhost"))
            .toBe("http://localhost:8080");
    });

    it("keeps the configured local hostname when the browser uses it", () =>
    {
        expect(resolveApiBaseUrl("http://192.168.3.5:8080", true, "192.168.3.5"))
            .toBe("http://192.168.3.5:8080");
    });

    it("does not rewrite remote or production API hosts", () =>
    {
        expect(resolveApiBaseUrl("https://api.docuhyphen.com", true, "localhost"))
            .toBe("https://api.docuhyphen.com");
        expect(resolveApiBaseUrl("http://192.168.3.5:8080", false, "localhost"))
            .toBe("http://192.168.3.5:8080");
    });
});
