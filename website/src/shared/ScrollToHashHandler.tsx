import {useEffect} from "react";
import {useLocation} from "react-router-dom";

/**
 * Scrolls the page to the element matching the current URL hash whenever the
 * location changes. This lets header/navigation links such as
 * "/#home-contact-info" jump straight to a section on the homepage even when
 * the click originates from a different route, since react-router client-side
 * transitions do not scroll to a hash target on their own.
 */
export function ScrollToHashHandler()
{
    const location = useLocation();

    useEffect(() =>
    {
        if (!location.hash)
        {
            return;
        }

        const targetId = location.hash.slice(1);
        const maxAttempts = 60;
        let attempt = 0;
        let animationFrame: number;

        const scrollToTarget = () =>
        {
            const target = document.getElementById(targetId);

            if (target)
            {
                target.scrollIntoView({behavior: "smooth", block: "start"});
                return;
            }

            attempt += 1;

            if (attempt < maxAttempts)
            {
                animationFrame = requestAnimationFrame(scrollToTarget);
            }
        };

        animationFrame = requestAnimationFrame(scrollToTarget);

        return () => cancelAnimationFrame(animationFrame);
    }, [location]);

    return null;
}


