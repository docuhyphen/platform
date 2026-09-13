// @vitest-environment jsdom
import {act, renderHook, waitFor} from "@testing-library/react";
import {createElement, type ReactNode} from "react";
import {MemoryRouter} from "react-router-dom";
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../../../services/apiClient.ts";
import {useInformationRequestRespondentWorkspace} from "./useInformationRequestRespondentWorkspace.ts";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));

const wrapper = ({children}: {children: ReactNode}) => createElement(MemoryRouter, {
    initialEntries: ["/respond?s=request-a&t=bootstrap"], children,
});

describe("respondent session verification", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        window.sessionStorage.clear();
        vi.mocked(apiClient.get).mockResolvedValue({data: {templateVersion: {sections: []}}});
        vi.mocked(apiClient.post).mockResolvedValue({data: {sessionToken: "independent-secret"}});
    });

    it("requires proof despite an old verified flag and stores the returned credential before loading", async () =>
    {
        window.sessionStorage.setItem("information-request-access-verified:request-a", "true");
        const {result} = renderHook(() => useInformationRequestRespondentWorkspace("no-auth"), {wrapper});
        await waitFor(() => expect(result.current.accessLinkToken).toBe("bootstrap"));
        expect(result.current.accessVerified).toBe(false);
        expect(apiClient.get).not.toHaveBeenCalled();
        act(() => result.current.setCode("123456"));
        await act(async () => result.current.verifyCode());
        await waitFor(() => expect(result.current.workspace).not.toBeNull());
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("independent-secret");
        expect(apiClient.get).toHaveBeenCalledWith(expect.any(String), {
            headers: {"X-Request-Access-Token": "bootstrap", "X-Request-Session-Token": "independent-secret"},
        });
    });

    it("returns to verification and clears content when the saved session has expired", async () =>
    {
        window.sessionStorage.setItem("information-request-access:request-a", "bootstrap");
        window.sessionStorage.setItem("information-request-session:request-a", "expired-secret");
        vi.mocked(apiClient.get).mockRejectedValue({isAxiosError: true, response: {
            status: 409, data: {reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED", errorMessage: "Session expired"},
        }});
        const {result} = renderHook(() => useInformationRequestRespondentWorkspace("no-auth"), {wrapper});
        await waitFor(() => expect(result.current.error).toBe("Session expired"));
        expect(result.current.accessVerified).toBe(false);
        expect(result.current.workspace).toBeNull();
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("");
    });
});
