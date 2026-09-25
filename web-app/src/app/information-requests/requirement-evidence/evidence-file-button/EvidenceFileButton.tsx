import {ReactElement, useRef} from "react";
import {Button} from "@fluentui/react-components";
import {useEvidenceFileButtonStyles} from "./EvidenceFileButtonStyles.tsx";

interface Props
{
    id: string;
    label: string;
    disabled: boolean;
    appearance?: "primary" | "secondary";
    size?: "small" | "medium";
    icon?: ReactElement;
    onFile: (file: File) => void;
}

const EvidenceFileButton = ({id, label, disabled, appearance = "secondary", size = "medium", icon, onFile}: Props) =>
{
    const styles = useEvidenceFileButtonStyles();
    const input = useRef<HTMLInputElement>(null);

    return (
        <span id={`${id}-control`}
              className={styles.control}>
            <input id={`${id}-input`}
                   ref={input}
                   type="file"
                   className={styles.hiddenInput}
                   disabled={disabled}
                   aria-label={label}
                   onChange={event =>
                   {
                       const file = event.target.files?.[0];
                       event.target.value = "";
                       if (file) onFile(file);
                   }}/>
            <Button id={id}
                    shape="circular"
                    appearance={appearance}
                    size={size}
                    icon={icon}
                    disabled={disabled}
                    onClick={() => input.current?.click()}>
                {label}
            </Button>
        </span>
    );
};

export default EvidenceFileButton;
