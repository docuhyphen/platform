import {render} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import InformationRequestRespondentWorkspace from "../InformationRequestRespondentWorkspace.tsx";
import {InformationRequestRespondentAccessMode} from "../useInformationRequestRespondentWorkspace.ts";

export interface RespondentShell {
    mode: InformationRequestRespondentAccessMode;
    entry: string;
    route: string;
    base: string;
    accessToken?: string;
}

export const respondentShells: RespondentShell[] = [
    {
        mode: "authenticated",
        entry: "/information-requests/request-a/respond",
        route: "/information-requests/:requestId/respond",
        base: "/information-requests/request-a",
    },
    {
        mode: "no-auth",
        entry: "/respond?s=request-a",
        route: "/respond",
        base: "/no-auth/information-requests/request-a",
        accessToken: "bootstrap-a",
    },
];

export const seedVerifiedAccessLink = () => {
    window.sessionStorage.setItem("information-request-access:request-a", "bootstrap-a");
    window.sessionStorage.setItem("information-request-session:request-a", "session-secret-a");
    window.sessionStorage.setItem("information-request-session-id:request-a", "session-a");
};

export const openRespondentShell = (shell: RespondentShell) => render(
    <MemoryRouter initialEntries={[shell.entry]}>
        <Routes>
            <Route path={shell.route}
                   element={<InformationRequestRespondentWorkspace accessMode={shell.mode}/>}/>
        </Routes>
    </MemoryRouter>,
);
