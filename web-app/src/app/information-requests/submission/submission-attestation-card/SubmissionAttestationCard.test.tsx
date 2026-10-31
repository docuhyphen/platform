/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestAttestationDecision,
    InformationRequestAttestationOrdering,
    InformationRequestAttestationState,
    InformationRequestAttestationStatusDto,
    InformationRequestAuthenticationStrength,
    InformationRequestContributorRole,
    InformationRequestExternalSignatureReferencePolicy,
    InformationRequestResponseDisposition,
} from "../../../models/models.tsx";
import SubmissionAttestationCard from "./SubmissionAttestationCard.tsx";

const status = (overrides: Partial<InformationRequestAttestationStatusDto> = {}): InformationRequestAttestationStatusDto => ({
    requirementId: "assertion-a",
    requirementKey: "recorded-assertion",
    prompt: "Confirm the recorded items are accurate",
    state: InformationRequestAttestationState.PENDING,
    requiredRoles: [InformationRequestContributorRole.PREPARER, InformationRequestContributorRole.ATTESTOR],
    missingRoles: [InformationRequestContributorRole.PREPARER, InformationRequestContributorRole.ATTESTOR],
    ordering: InformationRequestAttestationOrdering.ROLE_SEQUENCE,
    assentCount: 0,
    requiredAssentCount: 2,
    minimumAuthenticationStrength: InformationRequestAuthenticationStrength.VERIFIED_CONTACT,
    externalSignatureReference: InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED,
    callerCanAttest: true,
    waitingForRoles: [],
    attestations: [],
    ...overrides,
});

const button = (suffix: string) =>
    document.getElementById(`information-request-attestation-assertion-a-${suffix}`) as HTMLButtonElement;

describe("SubmissionAttestationCard", () => {
    afterEach(cleanup);

    it("waits for the earlier roles before the caller can confirm, while refusing stays open", () => {
        render(<SubmissionAttestationCard
            status={status({waitingForRoles: [InformationRequestContributorRole.PREPARER]})}
            busy={false}
            onAttest={vi.fn()}/>);

        expect(screen.getByText("Waiting for Preparer to confirm first.")).toBeTruthy();
        expect(button("confirm").disabled).toBe(true);
        expect(button("refuse").disabled).toBe(false);
    });

    it("lets a caller who started refusing go back with Keep while a required signature reference is still empty", () => {
        render(<SubmissionAttestationCard
            status={status({externalSignatureReference: InformationRequestExternalSignatureReferencePolicy.REQUIRED})}
            busy={false}
            onAttest={vi.fn()}/>);

        fireEvent.click(button("refuse"));
        expect(button("confirm").textContent).toContain("Keep");
        expect(button("confirm").disabled).toBe(false);

        fireEvent.click(button("confirm"));
        expect(button("confirm").textContent).toContain("Confirm");
        expect(button("confirm").disabled).toBe(true);
    });

    it("asks a caller who takes part in more than one way which party they confirm for, and names it", () => {
        const onAttest = vi.fn();
        render(<SubmissionAttestationCard
            status={status({
                attestingParties: [
                    {
                        partyId: "party-preparer",
                        role: InformationRequestContributorRole.PREPARER,
                        throughGroup: false,
                        delegated: false
                    },
                    {
                        partyId: "party-attestor",
                        role: InformationRequestContributorRole.ATTESTOR,
                        throughGroup: true,
                        delegated: false
                    },
                ],
            })}
            busy={false}
            onAttest={onAttest}/>);

        expect(button("confirm").disabled).toBe(true);
        expect(button("refuse").disabled).toBe(true);
        expect(screen.getByText("Attestor, through a group")).toBeTruthy();
        fireEvent.click(document.getElementById("information-request-attestation-assertion-a-party-party-attestor") as HTMLInputElement);
        expect(button("confirm").disabled).toBe(false);
        fireEvent.click(button("confirm"));

        expect(onAttest).toHaveBeenCalledWith({
            requirementId: "assertion-a",
            decision: InformationRequestAttestationDecision.ASSENTED,
            externalSignatureReference: undefined,
            partyId: "party-attestor",
        });
    });

    it("names no party when the caller confirms for the only one they hold", () => {
        const onAttest = vi.fn();
        render(<SubmissionAttestationCard
            status={status({
                attestingParties: [
                    {
                        partyId: "party-attestor",
                        role: InformationRequestContributorRole.ATTESTOR,
                        throughGroup: false,
                        delegated: false
                    },
                ],
            })}
            busy={false}
            onAttest={onAttest}/>);

        expect(document.getElementById("information-request-attestation-assertion-a-party-field")).toBeNull();
        fireEvent.click(button("confirm"));

        expect(onAttest).toHaveBeenCalledWith(expect.objectContaining({partyId: undefined}));
    });

    it("lets the caller confirm once no earlier role is outstanding", () => {
        render(<SubmissionAttestationCard status={status()}
                                          busy={false}
                                          onAttest={vi.fn()}/>);

        expect(screen.queryByText(/Waiting for/)).toBeNull();
        expect(button("confirm").disabled).toBe(false);
    });
});

describe("SubmissionAttestationCard exceptions", () => {
    afterEach(cleanup);

    it("records a permitted exception with its reason instead of confirming", () => {
        const onRecordException = vi.fn();
        render(<SubmissionAttestationCard status={status()}
                                          busy={false}
                                          exception={{
                                              permitted: [
                                                  InformationRequestResponseDisposition.NOT_APPLICABLE,
                                                  InformationRequestResponseDisposition.UNAVAILABLE,
                                              ],
                                              current: null,
                                          }}
                                          onAttest={vi.fn()}
                                          onRecordException={onRecordException}/>);

        fireEvent.click(screen.getByRole("button", {name: "Record an exception for Confirm the recorded items are accurate"}));
        expect(button("exception-record").disabled).toBe(true);
        fireEvent.change(screen.getByLabelText("Exception"), {target: {value: InformationRequestResponseDisposition.UNAVAILABLE}});
        fireEvent.change(document.getElementById("information-request-attestation-assertion-a-exception-reason") as HTMLTextAreaElement,
            {target: {value: "The register was lost in the move"}});
        fireEvent.click(button("exception-record"));

        expect(onRecordException).toHaveBeenCalledWith({
            requirementId: "assertion-a",
            disposition: InformationRequestResponseDisposition.UNAVAILABLE,
            reason: "The register was lost in the move",
        });
    });

    it("states a recorded exception and lets the caller change it", () => {
        render(<SubmissionAttestationCard status={status()}
                                          busy={false}
                                          exception={{
                                              permitted: [InformationRequestResponseDisposition.NOT_APPLICABLE],
                                              current: {
                                                  disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
                                                  reason: "No records were kept this period",
                                              },
                                          }}
                                          onAttest={vi.fn()}
                                          onRecordException={vi.fn()}/>);

        expect(screen.getByText("Exception recorded: Not applicable. Reason: No records were kept this period.")).toBeTruthy();
        expect(screen.getByRole("button", {name: "Change the exception for Confirm the recorded items are accurate"})).toBeTruthy();
    });

    it("offers no exception when the Template permits none", () => {
        render(<SubmissionAttestationCard status={status()}
                                          busy={false}
                                          onAttest={vi.fn()}/>);

        expect(button("exception")).toBeNull();
        expect(screen.queryByText(/Exception recorded/)).toBeNull();
    });
});
