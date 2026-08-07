import {useEffect, useState} from 'react';
import {resolveAvatarObjectUrl} from '../../../services/avatarCache';

/**
 * Turns an avatar marker path from a DTO into a renderable image source. Returns undefined
 * while resolving, when there is no picture, or when the lookup fails, so callers can fall back
 * to initials. A value that is already a usable object URL (e.g. the current user's avatar,
 * resolved by AuthContext) is passed through unchanged.
 */
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

