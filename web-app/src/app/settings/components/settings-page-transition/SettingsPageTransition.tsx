import {ReactNode} from "react";
import {mergeClasses} from "@fluentui/react-components";
import {useSettingsPageTransitionStyles} from "./SettingsPageTransitionStyles.tsx";

export type SettingsPageTransitionDirection = "forward" | "back" | null;

interface SettingsPageTransitionProps
{
    pageId: string;
    direction: SettingsPageTransitionDirection;
    children: ReactNode;
}

const SettingsPageTransition = ({pageId, direction, children}: SettingsPageTransitionProps) =>
{
    const styles = useSettingsPageTransitionStyles();

    return (
        <div
            id={`settings-page-transition-${pageId.toLowerCase()}`}
            key={pageId}
            className={mergeClasses(
                styles.frame,
                direction === "forward" ? styles.slideInFromRight : undefined,
                direction === "back" ? styles.slideInFromLeft : undefined,
            )}
        >
            {children}
        </div>
    );
};

export default SettingsPageTransition;
