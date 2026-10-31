/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {FluentProvider, webLightTheme} from "@fluentui/react-components";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import ContactProofPanel from "./ContactProofPanel.tsx";

const renderPanel = (code: string) => {
    const onVerify = vi.fn();
    render(
        <FluentProvider theme={webLightTheme}>
            <ContactProofPanel code={code}
                               challengeSent={true}
                               busy={false}
                               onCodeChange={vi.fn()}
                               onIssueChallenge={vi.fn()}
                               onVerify={onVerify}/>
        </FluentProvider>,
    );
    return onVerify;
};

describe("ContactProofPanel", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    afterEach(cleanup);

    it("does not verify when Enter is pressed before a code is entered", () => {
        const onVerify = renderPanel("");

        fireEvent.keyDown(screen.getByRole("textbox", {name: "Verification code"}), {key: "Enter"});

        expect(onVerify).not.toHaveBeenCalled();
    });

    it("names its verification section", () => {
        renderPanel("");

        expect(screen.getByRole("region", {name: "Verify access"})).toBe(document.getElementById("information-request-contact-proof"));
    });

    it("verifies the entered code when Enter is pressed", () => {
        const onVerify = renderPanel("123456");

        fireEvent.keyDown(screen.getByRole("textbox", {name: "Verification code"}), {key: "Enter"});

        expect(onVerify).toHaveBeenCalledTimes(1);
    });
});
