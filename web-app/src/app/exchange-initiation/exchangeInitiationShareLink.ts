export const buildExchangeShareLink = (exchangeId: string, requiresSignIn: boolean): string =>
{
    const encodedExchangeId = encodeURIComponent(exchangeId);
    const route = requiresSignIn ? '/exchanges' : '/nas';
    return `${window.location.origin}${route}?s=${encodedExchangeId}`;
};

const copyWithClipboardApi = async (text: string): Promise<boolean> =>
{
    if (!navigator.clipboard?.writeText)
    {
        return false;
    }
    try
    {
        await navigator.clipboard.writeText(text);
        return true;
    }
    catch
    {
        return false;
    }
};

export const copyTextWithFallback = async (text: string): Promise<boolean> =>
{
    if (await copyWithClipboardApi(text))
    {
        return true;
    }

    const textArea = document.createElement('textarea');
    textArea.value = text;
    textArea.style.position = 'fixed';
    textArea.style.opacity = '0';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    const success = document.execCommand('copy');
    document.body.removeChild(textArea);
    return success;
};
