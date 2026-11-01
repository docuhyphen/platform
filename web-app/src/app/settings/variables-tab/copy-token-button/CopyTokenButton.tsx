import {Button} from '@fluentui/react-components';
import {CheckmarkRegular, CopyRegular} from '@fluentui/react-icons';
import {useCopyTokenButtonStyles} from './CopyTokenButtonStyles.tsx';

interface CopyTokenButtonProps
{
    id: string;
    token: string;
    copiedToken: string | null;
    ariaLabel: string;
    onCopy: (token: string) => void;
}

const CopyTokenButton = ({id, token, copiedToken, ariaLabel, onCopy}: CopyTokenButtonProps) =>
{
    const styles = useCopyTokenButtonStyles();
    const copied = copiedToken === token;

    return (
        <Button
            id={id}
            size="small"
            appearance="subtle"
            shape={"circular"}
            icon={copied ? <CheckmarkRegular className={styles.copySuccess}/> : <CopyRegular/>}
            aria-label={ariaLabel}
            title={copied ? 'Copied' : `Copy {{${token}}}`}
            onClick={() => onCopy(token)}
        />
    );
};

export default CopyTokenButton;
