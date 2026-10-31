/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {FluentProvider, webLightTheme} from "@fluentui/react-components";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import ExchangeSettingsPanel from "./ExchangeSettingsPanel.tsx";

const renderPanel = (requireRecipientSignIn: boolean) =>
    render(
        <FluentProvider theme={webLightTheme}>
            <ExchangeSettingsPanel requireRecipientSignIn={requireRecipientSignIn}
                                   setRequireRecipientSignIn={vi.fn()}
                                   sendingAccessCode={false}
                                   sendingInvitation={false}
                                   resendCooldownRemaining={0}
                                   onSendAccessCode={vi.fn()}
                                   onResendInvitation={vi.fn()}
                                   buttonWithLoadingClassName={""}
                                   noAuthAccessValidityDays={"7"}
                                   setNoAuthAccessValidityDays={vi.fn()}
                                   accessCodeStatus={""}
                                   accessCodeError={""}
                                   invitationStatus={""}
                                   invitationError={""}/>
        </FluentProvider>,
    );

describe("ExchangeSettingsPanel", () => {
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

    it("warns that requiring sign-in stops request access links and how to bring back someone added by email", () => {
        renderPanel(true);

        expect(screen.getByText(/Access links on this Exchange's requests stop working/)).toBeTruthy();
        expect(screen.getByText(/assign their part to a recipient of this Exchange/)).toBeTruthy();
    });

    it("says nothing about request access links while recipients may use access codes", () => {
        renderPanel(false);

        expect(screen.queryByText(/Access links on this Exchange's requests stop working/)).toBeNull();
    });
});
