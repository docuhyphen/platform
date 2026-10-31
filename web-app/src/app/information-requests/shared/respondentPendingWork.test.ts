import {describe, expect, it} from "vitest";
import {IDLE_RESPONSE_WORK, pendingWorkBlocker} from "./respondentPendingWork.ts";

describe("pendingWorkBlocker", () => {
    it("names nothing when no answer or file is waiting", () => {
        expect(pendingWorkBlocker(IDLE_RESPONSE_WORK, [{sending: false, kept: false}])).toBeNull();
    });

    it("leaves answers that are only unsaved to be saved before review", () => {
        expect(pendingWorkBlocker({...IDLE_RESPONSE_WORK, unsaved: true}, [])).toBeNull();
    });

    it("names a failed or overtaken save only while its answers are still unsaved", () => {
        expect(pendingWorkBlocker({...IDLE_RESPONSE_WORK, unsaved: true, failed: true}, [])).toBe("failed");
        expect(pendingWorkBlocker({...IDLE_RESPONSE_WORK, unsaved: true, stale: true, failed: true}, [])).toBe("stale");
        expect(pendingWorkBlocker({...IDLE_RESPONSE_WORK, failed: true, stale: true}, [])).toBeNull();
    });

    it("names a save in progress before any file and a file being sent before a kept file", () => {
        const files = [{sending: false, kept: true}, {sending: true, kept: false}];

        expect(pendingWorkBlocker({...IDLE_RESPONSE_WORK, saving: true}, files)).toBe("saving");
        expect(pendingWorkBlocker(IDLE_RESPONSE_WORK, files)).toBe("sending");
        expect(pendingWorkBlocker(IDLE_RESPONSE_WORK, [files[0]])).toBe("kept");
    });
});
