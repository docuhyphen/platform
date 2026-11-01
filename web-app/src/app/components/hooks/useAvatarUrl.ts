import {useEffect, useState} from 'react';
import {resolveAvatarObjectUrl} from '../../../services/avatarCache';

export const useAvatarUrl = (markerPath?: string | null): string | undefined =>
{
    const [resolvedUrl, setResolvedUrl] = useState<string | undefined>(undefined);

    useEffect(() =>
    {
        if (!markerPath)
        {
            setResolvedUrl(undefined);
            return;
        }

        if (markerPath.startsWith('blob:') || markerPath.startsWith('data:'))
        {
            setResolvedUrl(markerPath);
            return;
        }

        let active = true;
        resolveAvatarObjectUrl(markerPath).then(url =>
        {
            if (active)
            {
                setResolvedUrl(url ?? undefined);
            }
        });

        return () =>
        {
            active = false;
        };
    }, [markerPath]);

    return resolvedUrl;
};

