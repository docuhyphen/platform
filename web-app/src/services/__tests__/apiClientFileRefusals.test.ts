// @vitest-environment jsdom
import {afterAll, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient, {setApiClientAuthToken} from "../apiClient.ts";
import {
    InformationRequestEvidenceContentUse,
    listInformationRequestEvidence,
    openInformationRequestEvidenceContent,
} from "../informationRequestEvidenceService.ts";
import {requestStepUp} from "../stepUpBroker.ts";
import {
    FILE_ROUTE_REFUSAL_TYPE,
    fileAnswer,
    jsonAnswer,
    LocalAnswer,
    LocalApiServer,
    startLocalApiServer,
    textOf,
} from "../testing/localApiServer.ts";
import {
    requirementEvidenceCommands
} from "../../app/information-requests/requirement-evidence/requirementEvidenceCommands.ts";
import {refusalMessage} from "../../app/information-requests/requirement-evidence/requirementEvidenceLabels.ts";
import {informationRequestRefusalCode} from "../../app/information-requests/shared/informationRequestRefusal.ts";

vi.mock("../stepUpBroker.ts", () => ({requestStepUp: vi.fn()}));

const EXPIRED = {
    reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED",
    errorMessage: "This request access session has expired",
};
const REVOKED = {
    reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_REVOKED",
    errorMessage: "This request access session has been revoked",
};
const NOT_RELEASED = {
    reasonCode: "INFORMATION_REQUEST_EVIDENCE_CONTENT_NOT_RELEASED",
    errorMessage: "This evidence is released once it has passed a malware scan",
};
const STEP_UP = {
    reasonCode: "STEP_UP_REQUIRED",
    action: "work with a protected Information Request item",
    errorMessage: "Confirm it is you to continue",
};
const OPEN_FAILURE = "The file could not be opened.";
const evidencePath = "/information-requests/request-a/requirements/requirement-a/evidence-artifacts";
const uses: InformationRequestEvidenceContentUse[] = ["content", "preview"];
const otherLabels = [
    "application/json",
    "application/json; charset=UTF-8",
    "application/problem+json",
    "application/octet-stream",
    "text/plain",
];

const answers: LocalAnswer[] = [];
let server: LocalApiServer;
let dropNext = false;

const fileRouteRefusal = (status: number, body: unknown): LocalAnswer => jsonAnswer(status, body, FILE_ROUTE_REFUSAL_TYPE);

const openFile = (use: InformationRequestEvidenceContentUse, accessLinkToken?: string) =>
    openInformationRequestEvidenceContent("request-a", "requirement-a", "artifact-a", "version-a", use, accessLinkToken);

const refusalOf = (read: Promise<unknown>): Promise<unknown> =>
    read.then(() => {
        throw new Error("The read was not refused");
    }, (refusal: unknown) => refusal);

describe("file reads through the API client", () => {
    beforeAll(async () => {
        server = await startLocalApiServer(() => {
            if (dropNext) {
                dropNext = false;
                return "drop";
            }
            return answers.shift() ?? jsonAnswer(500, {errorMessage: "No answer was prepared"});
        });
        apiClient.defaults.adapter = "xhr";
        apiClient.defaults.baseURL = server.origin;
    });

    afterAll(() => server.close());

    beforeEach(() => {
        vi.resetAllMocks();
        answers.splice(0);
        server.served.splice(0);
        dropNext = false;
        setApiClientAuthToken(null);
        window.sessionStorage.clear();
    });

    it.each(uses)("states the reason of a refusal of a %s read, labelled as the server labels it", async use => {
        answers.push(fileRouteRefusal(409, EXPIRED));

        await expect(openFile(use, "link-token")).rejects.toEqual(EXPIRED);
        expect(server.served).toEqual([`GET /no-auth${evidencePath}/artifact-a/versions/version-a/${use}`]);
    });

    it.each(otherLabels)("states the reason of a JSON refusal labelled %s", async contentType => {
        answers.push(jsonAnswer(409, NOT_RELEASED, contentType));

        await expect(openFile("content")).rejects.toEqual(NOT_RELEASED);
    });

    it.each(uses.flatMap(use => [
        {use, ended: "expired", refusal: EXPIRED},
        {use, ended: "revoked", refusal: REVOKED},
    ]))("reports a $ended session from a $use read to the evidence commands", async ({use, refusal}) => {
        answers.push(fileRouteRefusal(409, refusal));
        const onAccessSessionEnded = vi.fn();
        const commands = requirementEvidenceCommands({accessLinkToken: "link-token", onAccessSessionEnded});

        await expect(commands.open("request-a", "requirement-a", "artifact-a", "version-a", use)).rejects.toEqual(refusal);
        expect(onAccessSessionEnded).toHaveBeenCalledWith(refusal);
    });

    it("keeps a file it was sent as that file", async () => {
        answers.push(fileAnswer("%PDF-1.4 supporting record"));

        const content = await openFile("content", "link-token");

        expect(content).toBeInstanceOf(Blob);
        expect(content.type).toBe("application/pdf");
        expect(await textOf(content)).toBe("%PDF-1.4 supporting record");
    });

    it.each([
        {
            kind: "cut-off JSON",
            answer: {status: 409, contentType: FILE_ROUTE_REFUSAL_TYPE, body: "{\"reasonCode\":\"INFORMATION_REQUEST"}
        },
        {kind: "a JSON text", answer: fileRouteRefusal(409, "This request access session has expired")},
        {kind: "a JSON list", answer: fileRouteRefusal(409, [EXPIRED])},
        {
            kind: "a page that is not JSON",
            answer: {status: 502, contentType: "text/html", body: "<html><body>Bad gateway</body></html>"}
        },
        {
            kind: "JSON too large to be a refusal",
            answer: fileRouteRefusal(409, {...EXPIRED, errorMessage: "x".repeat(70_000)})
        },
    ])("passes a refusal of $kind on as it was sent, so its reader falls back to its own words", async ({answer}) => {
        answers.push(answer);
        const onAccessSessionEnded = vi.fn();
        const commands = requirementEvidenceCommands({accessLinkToken: "link-token", onAccessSessionEnded});

        const refusal = await refusalOf(commands.open("request-a", "requirement-a", "artifact-a", "version-a", "content"));

        expect(refusal).toBeInstanceOf(Blob);
        expect(await textOf(refusal as Blob)).toBe(answer.body);
        expect(informationRequestRefusalCode(refusal)).toBeUndefined();
        expect(refusalMessage(refusal, OPEN_FAILURE)).toBe(OPEN_FAILURE);
        expect(onAccessSessionEnded).not.toHaveBeenCalled();
    });

    it("passes an ordinary JSON refusal through as before", async () => {
        answers.push(jsonAnswer(409, EXPIRED));

        await expect(listInformationRequestEvidence("request-a", "requirement-a", "link-token")).rejects.toEqual(EXPIRED);
    });

    it("passes a dropped connection through as before", async () => {
        dropNext = true;

        await expect(openFile("content", "link-token")).rejects.toBe("Network Error");
    });

    it("asks a signed-in caller to confirm it is them when a file read needs it, then reads the file", async () => {
        vi.mocked(requestStepUp).mockResolvedValue(true);
        answers.push(
            fileRouteRefusal(401, STEP_UP),
            jsonAnswer(200, {accessToken: "renewed-token"}),
            fileRouteRefusal(401, STEP_UP),
            fileAnswer("protected record"),
        );

        const content = await openFile("content");

        expect(await textOf(content)).toBe("protected record");
        expect(requestStepUp).toHaveBeenCalledTimes(1);
        expect(requestStepUp).toHaveBeenCalledWith({action: STEP_UP.action, message: STEP_UP.errorMessage});
        expect(server.served).toEqual([
            `GET ${evidencePath}/artifact-a/versions/version-a/content`,
            "POST /auth/token/refresh",
            `GET ${evidencePath}/artifact-a/versions/version-a/content`,
            `GET ${evidencePath}/artifact-a/versions/version-a/content`,
        ]);
    });

    it("ends the signed-in session of a deprovisioned account when a file read says so", async () => {
        answers.push(fileRouteRefusal(403, {
            reasonCode: "ACCOUNT_DEPROVISIONED",
            errorMessage: "This account is no longer active"
        }));
        const ended = vi.fn();
        window.addEventListener("auth-session-expired", ended);

        await refusalOf(openFile("content"));
        window.removeEventListener("auth-session-expired", ended);

        expect(ended).toHaveBeenCalledTimes(1);
        expect((ended.mock.calls[0][0] as CustomEvent).detail).toEqual({reason: "ACCOUNT_DEPROVISIONED"});
    });
});
