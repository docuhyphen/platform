export const FORCE_TOUR_VISIBLE = false;

export interface TourStep
{
    targetId: string;
    title: string;
    description: string;
    position?: 'above' | 'below' | 'before' | 'after';
    align?: 'start' | 'center' | 'end';
}

export const TOUR_STEPS: TourStep[] = [
    {
        targetId: 'tour-start-exchanging',
        title: 'Start Exchange',
        description:
            'This is your primary action. Click "Start Exchanging" to request documents from someone or send documents to a colleague. Use the dropdown arrow to choose between requesting and sending.',
        position: 'below',
        align: 'start',
    },
    {
        targetId: 'tour-sessions-btn',
        title: 'Exchanges',
        description:
            'All your active and past Exchanges live here. Click this icon at any time to jump straight back to your Exchanges list.',
        position: 'below',
        align: 'center',
    },
    {
        targetId: 'tour-notifications',
        title: 'Notifications',
        description:
            'Real-time alerts appear here exch- new document uploads, session status changes, and responses from your contacts, so nothing slips through the cracks.',
        position: 'below',
        align: 'center',
    },
    {
        targetId: 'tour-account-btn',
        title: 'Your Account',
        description:
            'Tap your profile avatar to access Settings, manage linked sign-in accounts, switch themes, review active sessions, and sign out.',
        position: 'below',
        align: 'end',
    },
];
