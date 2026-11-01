export type CommunicationEditorTab = 'details' | 'preview';

export interface CommunicationEditorValues
{
    name: string;
    summary: string;
    description: string;
    subject: string;
    body: string;
    tags: string[];
}
