import {useCallback, useState} from 'react';
import {RenderedCommunication} from '../../models/models';
import {previewCommunication} from '../../../services/communicationService';

export const useCommunicationPreview = (communicationId: string | undefined) =>
{
    const [previewVars, setPreviewVars] = useState('');
    const [previewing, setPreviewing] = useState(false);
    const [previewResult, setPreviewResult] = useState<RenderedCommunication | null>(null);
    const [previewError, setPreviewError] = useState<string | null>(null);

    const resetPreview = useCallback(() =>
    {
        setPreviewResult(null);
        setPreviewVars('');
        setPreviewError(null);
    }, []);

    const renderPreview = async () =>
    {
        if (!communicationId) return;
        setPreviewing(true);
        setPreviewError(null);
        setPreviewResult(null);
        try
        {
            let sampleVariables: Record<string, string> = {};
            if (previewVars.trim())
            {
                try { sampleVariables = JSON.parse(previewVars); }
                catch { setPreviewError('Sample variables must be valid JSON'); setPreviewing(false); return; }
            }
            const result = await previewCommunication(communicationId, {sampleVariables});
            setPreviewResult(result);
        }
        catch (e: unknown)
        {
            setPreviewError(typeof e === 'string' ? e : 'Preview failed');
        }
        finally
        {
            setPreviewing(false);
        }
    };

    return {previewVars, setPreviewVars, previewing, previewResult, previewError, renderPreview, resetPreview};
};
