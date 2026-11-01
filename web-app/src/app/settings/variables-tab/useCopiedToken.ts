import {useState} from 'react';
import {copyText} from '../../utils/copyText';

export const useCopiedToken = () =>
{
    const [copiedToken, setCopiedToken] = useState<string | null>(null);

    const copy = async (token: string) =>
    {
        const copied = await copyText(`{{${token}}}`);
        if (copied)
        {
            setCopiedToken(token);
            window.setTimeout(() => setCopiedToken(current => current === token ? null : current), 1500);
        }
    };

    return {copiedToken, copy};
};
