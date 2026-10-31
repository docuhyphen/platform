import {Badge, mergeClasses, Text} from "@fluentui/react-components";
import {CommunicationScope, CommunicationSummaryDto} from "../../../models/models.tsx";
import {useCommunicationPickerItemStyles} from "./CommunicationPickerItemStyles.tsx";

interface CommunicationPickerItemProps {
    communication: CommunicationSummaryDto;
    selected: boolean;
    onSelect: () => void;
}

const scopeColor: Record<CommunicationScope, "brand" | "success" | "informative"> = {
    PERSONAL: "brand",
    ORG: "success",
    PLATFORM: "informative",
};

const CommunicationPickerItem = ({communication, selected, onSelect}: CommunicationPickerItemProps) => {
    const styles = useCommunicationPickerItemStyles();
    const id = `communication-picker-item-${communication.id}`;

    return (
        <div id={id}
             role={"button"}
             tabIndex={0}
             aria-pressed={selected}
             className={mergeClasses(styles.itemCard, selected && styles.itemCardSelected)}
             onClick={onSelect}
             onKeyDown={event => {
                 if (event.key !== "Enter" && event.key !== " ") return;
                 event.preventDefault();
                 onSelect();
             }}>
            <div id={`${id}-header`}
                 className={styles.itemHeader}>
                <Text id={`${id}-name`}
                      weight={"semibold"}
                      size={300}>
                    {communication.name}
                </Text>
                <Badge id={`${id}-scope`}
                       appearance={"tint"}
                       color={scopeColor[communication.scope] ?? "informative"}
                       size={"small"}>
                    {communication.scope}
                </Badge>
            </div>
            <Text id={`${id}-subject`}
                  size={200}
                  className={styles.itemSubject}>
                {communication.subject}
            </Text>
            {communication.summary && (
                <Text id={`${id}-summary`}
                      size={200}
                      className={styles.itemSummary}>
                    {communication.summary}
                </Text>
            )}
        </div>
    );
};

export default CommunicationPickerItem;
