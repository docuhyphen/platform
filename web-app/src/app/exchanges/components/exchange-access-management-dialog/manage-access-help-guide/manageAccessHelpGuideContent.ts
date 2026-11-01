export interface HelpGuideRoleContent
{
    cardId: string;
    headerId: string;
    nameBadgeId: string;
    kindBadgeId: string;
    textId: string;
    name: string;
    kind: string;
    description: string;
}

export interface HelpGuideDetailContent
{
    cardId: string;
    titleId: string;
    textId: string;
    title: string;
    description: string;
}

const elementId = (kind: string, ordinal: number): string => `manage-access-help-guide-${kind}-${ordinal}`;

const role = (ordinal: number, name: string, kind: string, description: string): HelpGuideRoleContent => ({
    cardId: elementId('div', 2 * ordinal + 1),
    headerId: elementId('div', 2 * ordinal + 2),
    nameBadgeId: elementId('badge', 2 * ordinal - 1),
    kindBadgeId: elementId('badge', 2 * ordinal),
    textId: elementId('text', ordinal + 2),
    name,
    kind,
    description,
});

const detail = (divOrdinal: number, titleTextOrdinal: number, title: string, description: string): HelpGuideDetailContent => ({
    cardId: elementId('div', divOrdinal),
    titleId: elementId('text', titleTextOrdinal),
    textId: elementId('text', titleTextOrdinal + 1),
    title,
    description,
});

export const HELP_GUIDE_ROLES: HelpGuideRoleContent[] = [
    role(1, 'Owner', 'Automatic',
        'Full control over the Exchange. Can manage access, edit settings, add/remove documents, and end the exchange. This role is structural and cannot be assigned or revoked.'),
    role(2, 'Editor', 'Assignable',
        'Can add, update, upload, and manage documents within the Exchange. Editors have broad document-level access but cannot manage exchange access or settings.'),
    role(3, 'Reviewer', 'Assignable',
        'Can view all documents and leave comments or notes. Reviewers are typically used for approval workflows or document review processes.'),
    role(4, 'Signer', 'Assignable',
        'Intended for participants who need to sign or formally acknowledge documents. Has read access plus signing capabilities.'),
    role(5, 'Viewer', 'Assignable · Supports constraints',
        'Read-only access to exchange documents. Viewers can browse and preview documents but cannot modify them. Supports additional constraints like download restrictions and watermarking.'),
    role(6, 'Commenter', 'Assignable',
        'Can view documents and leave comments or notes, but cannot modify documents or exchange settings.'),
    role(7, 'Participant', 'Assignable · Supports constraints',
        'General participant access. Can view documents with optional constraints applied. This is the most flexible role for external collaborators.'),
];

export const HELP_GUIDE_CONSTRAINTS: HelpGuideDetailContent[] = [
    detail(18, 11, 'No bulk document download',
        'Prevents the participant from downloading documents in bulk (zip download). Documents can still be previewed in the browser.'),
    detail(19, 13, 'No reshare',
        'Prevents the participant from sharing or forwarding the Exchange link to others.'),
    detail(20, 15, 'Watermark',
        'Overlays a watermark on document previews to discourage unauthorized screenshots or distribution.'),
    detail(21, 17, 'Require MFA',
        'Requires the participant to complete multi-factor authentication before accessing exchange documents, adding an extra layer of security.'),
    detail(22, 19, 'Max views',
        'Limits the number of times a participant can view the Exchange documents. Once the limit is reached, access is automatically revoked. Leave blank for unlimited views.'),
];

export const HELP_GUIDE_PERMISSIONS: HelpGuideDetailContent[] = [
    detail(24, 22, 'Allow document additions',
        'When enabled, participants with the appropriate role can add new documents to the exchange.'),
    detail(25, 24, 'Allow document deletions',
        'When enabled, participants can remove documents from the Exchange.'),
    detail(26, 26, 'Allow document zip download',
        'When enabled, participants can download all Exchange documents as a single zip archive.'),
    detail(27, 28, 'Allow document update',
        'When enabled, participants can modify or replace existing document metadata.'),
    detail(28, 30, 'Allow document upload',
        'When enabled, participants can upload new file versions for existing documents.'),
];

export const HELP_GUIDE_SETTINGS: HelpGuideDetailContent[] = [
    detail(30, 32, 'Require recipient sign in',
        'When enabled, the recipient must sign in with their account to access the exchange. When disabled, the recipient can use a one-time email access code (OTP) instead exch- useful for external parties who don\'t have an account.'),
    detail(31, 34, 'Send access code',
        'When "Require recipient sign in" is disabled, you can send a one-time access code to the recipient\'s email. The code expires after the configured number of days.'),
    detail(32, 36, 'No-auth access validity',
        'Sets how many days the one-time access code remains valid (1-30 days). After expiry, a new code must be sent.'),
];
