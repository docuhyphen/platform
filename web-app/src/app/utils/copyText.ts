export const copyText = async (text: string): Promise<boolean> =>
{
    try
    {
        if (navigator.clipboard?.writeText)
        {
            await navigator.clipboard.writeText(text);
            return true;
        }
    }
    catch (_)
    {
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
