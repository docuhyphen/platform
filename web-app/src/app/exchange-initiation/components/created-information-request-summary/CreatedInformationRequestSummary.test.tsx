/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import CreatedInformationRequestSummary from "./CreatedInformationRequestSummary.tsx";

describe("CreatedInformationRequestSummary", () => {
    afterEach(cleanup);

    it("names the draft request the Exchange started and opens its management workspace", () => {
        const onManage = vi.fn();
        render(<CreatedInformationRequestSummary request={{id: "request-a", title: "Periodic records"}}
                                                 onManage={onManage}/>);

        expect(screen.getByText("Information Request")).toBeTruthy();
        expect(screen.getByText("Periodic records")).toBeTruthy();
        expect(screen.getByText("Saved as a draft for you to review and issue.")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Manage Information Request"}));

        expect(onManage).toHaveBeenCalledWith("request-a");
    });

    it("points to the Exchange when the started request cannot be named", () => {
        render(<CreatedInformationRequestSummary request={null}
                                                 onManage={vi.fn()}/>);

        expect(screen.getByText("A draft Information Request was created. Open it from the Exchange's Information Requests tab.")).toBeTruthy();
        expect(screen.queryByRole("button")).toBeNull();
    });
});
