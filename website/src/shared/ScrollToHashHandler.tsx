import {useEffect} from "react";
import {useLocation} from "react-router-dom";

const HASH_SCROLL_GAP = 16;
const HEADER_ID = "landing-header";

const getHeaderOffset = (): number =>
{
    const header = document.getElementById(HEADER_ID);

    if (!header)
        return HASH_SCROLL_GAP;

    return header.getBoundingClientRect().height + HASH_SCROLL_GAP;
};

export function ScrollToHashHandler()
{
    const location = useLocation();

    useEffect(() =>
    {
        if (!location.hash)
        {
            window.scrollTo({
                top: 0,
                left: 0,
                behavior: "auto",
            });
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
                const targetTop = target.getBoundingClientRect().top + window.scrollY;

                window.scrollTo({
                    top: Math.max(targetTop - getHeaderOffset(), 0),
                    left: 0,
                    behavior: "smooth",
                });
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

