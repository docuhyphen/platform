import {Button} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import type {ReactElement, ReactNode} from "react";

type LinkButtonProps = {
    to: string;
    children?: ReactNode;
    appearance?: "primary" | "outline" | "subtle" | "secondary" | "transparent";
    shape?: "circular" | "rounded" | "square";
    size?: "small" | "medium" | "large";
    icon?: ReactElement;
    iconPosition?: "before" | "after";
    className?: string;
};

export function LinkButton({to, children, ...buttonProps}: LinkButtonProps)
{
    const navigate = useNavigate();

    return (
        <Button {...buttonProps} onClick={() => navigate(to)}>
            {children}
        </Button>
    );
}
