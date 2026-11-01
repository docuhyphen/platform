import {Button, Popover, PopoverSurface, PopoverTrigger, Text, Tooltip} from "@fluentui/react-components";
import {InfoIcon} from "../../../../../components/IconBundles.tsx";
import {useReplacePrimaryRecipientActionStyles} from "./ReplacePrimaryRecipientActionStyles.tsx";

interface ReplacePrimaryRecipientActionProps
{
    onReplacePrimary: () => void;
}

const ReplacePrimaryRecipientAction = ({onReplacePrimary}: ReplacePrimaryRecipientActionProps) =>
{
    const styles = useReplacePrimaryRecipientActionStyles();
    return (
        <div id={"people-summary-panel-div-3"}
             className={styles.replacementActions}>
            <div
                id={"access-mgmt-replace-primary-action-row"}
                className={styles.replacementActionRow}
            >
                <Button
                    id={"access-mgmt-replace-primary-btn"}
                    appearance={"secondary"}
                    shape={"circular"}
                    size={"small"}
                    onClick={onReplacePrimary}
                >
                    Replace recipient
                </Button>
                <Popover positioning={"below-start"}>
                    <PopoverTrigger disableButtonEnhancement>
                        <Tooltip
                            content={"Why replace a recipient"}
                            relationship={"description"}
                        >
                            <Button
                                id={"access-mgmt-replace-primary-info-btn"}
                                appearance={"subtle"}
                                aria-label={"Why replace a recipient"}
                                icon={<InfoIcon/>}
                                shape={"circular"}
                                size={"small"}
                            />
                        </Tooltip>
                    </PopoverTrigger>
                    <PopoverSurface
                        id={"access-mgmt-replace-primary-info-popover"}
                        className={styles.replacementInfoPopover}
                    >
                        <Text id={"people-summary-panel-text-5"}
                              size={200}>
                            Recover an invitation whose trusted verification is no longer valid.
                        </Text>
                    </PopoverSurface>
                </Popover>
            </div>
        </div>
    );
};

export default ReplacePrimaryRecipientAction;
