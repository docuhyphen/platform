import {describe, expect, it} from "vitest";
import {groupElementKey, occurrenceElementKey, requirementElementKey} from "./responseElementKeys.ts";

describe("response element keys", () => {
    it("keeps a root Requirement key readable", () => {
        expect(requirementElementKey("root", "record-owner")).toBe("root-record-owner");
    });

    it("writes each entry of an occurrence path with its index", () => {
        expect(requirementElementKey("records[0]/detail[12]", "amount")).toBe("records_0_detail_12-amount");
        expect(occurrenceElementKey("records[0]/detail[12]")).toBe("records_0_detail_12");
        expect(occurrenceElementKey("root")).toBe("root");
    });

    it("gives every distinct occurrence path and Requirement key pair its own key", () => {
        const pairs: [string, string][] = [
            ["root", "record-owner"],
            ["root", "record--owner"],
            ["root", "records-0-amount"],
            ["records[0]", "amount"],
            ["records[0]", "detail-0-amount"],
            ["records[0]/detail[0]", "amount"],
            ["records[0]", "0-amount"],
            ["records[00]", "amount"],
            ["record-s[0]", "amount"],
            ["record--s[0]", "amount"],
            ["root", "Record-Owner"],
            ["root", "record_owner"],
            ["records[x]", "amount"],
            ["", "root-amount"],
        ];

        const keys = pairs.map(([path, key]) => requirementElementKey(path, key));

        expect(new Set(keys).size).toBe(pairs.length);
    });

    it("gives keys outside the key grammar their own escaped form", () => {
        expect(groupElementKey("record-group")).toBe("record-group");
        expect(groupElementKey("record--group")).toBe("record--group");
        expect(groupElementKey("Record group")).toBe("_005200650063006f00720064002000670072006f00750070");
        expect(new Set(["records[x]", "records_x", "records"].map(occurrenceElementKey)).size).toBe(3);
    });
});
