/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest';
import TrustedParticipantPanel from './TrustedParticipantPanel.tsx';

const mocks = vi.hoisted(() => ({
    invite: vi.fn(),
    clearRecipientUserCallbacks: new Set<() => void>(),
}));

vi.mock('../../../../../services/exchangeApi.ts', () => ({
    inviteTrustedParticipant: (...args: unknown[]) => mocks.invite(...args),
}));

vi.mock(
    '../../../../exchange-initiation/components/exchange-initiation-recipients-tab/trusted-organization-recipients/TrustedOrganizationRecipients.tsx',
    () => ({
        default: (props: {
            setRecipientOrgUser: () => void;
            setRecipientOrg: (organization?: {
                id: string;
                name: string;
                registrationNumber: string;
                verificationComplete: boolean;
                isActive: boolean;
            }) => void;
            setRecipientResolution: (resolution?: {
                id: string;
                organizationId: string;
                organizationName: string;
                email: string;
                verifiedAt: number;
                expiresAt: number;
            }) => void;
        }) =>
        {
            mocks.clearRecipientUserCallbacks.add(props.setRecipientOrgUser);
            return (
            <button
                id={"test-verify-trusted-person"}
                type="button"
                onClick={() =>
                {
                    props.setRecipientOrg({
                        id: 'organization-1',
                        name: 'Trusted Partner',
                        registrationNumber: 'partner-1',
                        verificationComplete: true,
                        isActive: true,
                    });
                    props.setRecipientResolution({
                        id: 'resolution-1',
                        organizationId: 'organization-1',
                        organizationName: 'Trusted Partner',
                        email: 'member@partner.example',
                        verifiedAt: 1,
                        expiresAt: 2,
                    });
                }}
            >
                Verify trusted person
            </button>
            );
        },
    }),
);

describe('TrustedParticipantPanel', () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal('ResizeObserver', class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });
    beforeEach(() =>
    {
        mocks.invite.mockReset();
        mocks.clearRecipientUserCallbacks.clear();
    });
    afterEach(cleanup);

    it('keeps invitation disabled until trusted evidence exists', () =>
    {
        render(<TrustedParticipantPanel
            exchangeId={"exchange-1"}
            onCancel={vi.fn()}
            onInvited={vi.fn()}
        />);

        expect((screen.getByRole('button', {name: 'Invite participant'}) as HTMLButtonElement).disabled).toBe(true);
        expect(mocks.invite).not.toHaveBeenCalled();
    });

    it('submits a trusted person invitation and waits for backend activation', async () =>
    {
        mocks.invite.mockResolvedValue([]);
        const onInvited = vi.fn();
        render(<TrustedParticipantPanel
            exchangeId={"exchange-1"}
            onCancel={vi.fn()}
            onInvited={onInvited}
        />);

        fireEvent.click(screen.getByRole('button', {name: 'Verify trusted person'}));
        fireEvent.click(screen.getByRole('button', {name: 'Invite participant'}));

        await waitFor(() => expect(mocks.invite).toHaveBeenCalledWith('exchange-1', {
            selection: {type: 'TRUSTED_PERSON', resolutionId: 'resolution-1'},
            roleName: 'VIEWER',
        }));
        expect(mocks.clearRecipientUserCallbacks.size).toBe(1);
        expect(onInvited).toHaveBeenCalledOnce();
    });

});
